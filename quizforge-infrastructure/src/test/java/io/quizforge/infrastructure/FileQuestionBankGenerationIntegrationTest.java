package io.quizforge.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import io.quizforge.core.ErrorCode;
import io.quizforge.core.QuizForgeException;
import io.quizforge.core.asset.Asset;
import io.quizforge.core.asset.AssetType;
import io.quizforge.core.port.WorkspaceAssetScanner;
import io.quizforge.core.question.FileQuestionBankGenerationService;
import io.quizforge.core.question.GenerationScopeType;
import io.quizforge.core.question.QuestionBankFile;
import io.quizforge.core.question.QuestionBankReferenceResolver;
import io.quizforge.core.question.QuestionBankV1Assembler;
import io.quizforge.core.question.QuestionType;
import io.quizforge.core.question.StandardDocumentSelection;
import io.quizforge.core.workspace.Workspace;
import io.quizforge.core.workspace.WorkspaceService;
import io.quizforge.extension.question.SourceAwareQuestionGenerator;
import io.quizforge.infrastructure.filesystem.FileSystemWorkspaceAssetScanner;
import io.quizforge.infrastructure.filesystem.FormalMarkdownDocumentReader;
import io.quizforge.infrastructure.filesystem.LocalQuestionBankFileStorage;
import io.quizforge.infrastructure.filesystem.LocalStandardDocumentFileStorage;
import io.quizforge.infrastructure.filesystem.QuizForgeDataDirectory;
import io.quizforge.infrastructure.filesystem.QuestionBankV1Codec;
import io.quizforge.infrastructure.filesystem.WorkspacePathResolver;
import io.quizforge.infrastructure.persistence.SqliteAssetIndexRepository;
import io.quizforge.infrastructure.persistence.SqliteDatabase;
import io.quizforge.infrastructure.persistence.SqliteWorkspaceRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileQuestionBankGenerationIntegrationTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-25T00:00:00Z"), ZoneOffset.UTC);
    @TempDir Path temporaryDirectory;
    private QuizForgeDataDirectory directory;
    private WorkspacePathResolver paths;
    private WorkspaceService workspaces;
    private WorkspaceAssetScanner scanner;
    private SqliteAssetIndexRepository index;
    private Workspace workspace;
    private Path root;
    private final QuestionBankV1Codec codec = new QuestionBankV1Codec();

    @BeforeEach void setup() throws Exception {
        directory = new QuizForgeDataDirectory(temporaryDirectory.resolve("data"));
        paths = new WorkspacePathResolver(directory);
        workspaces = new WorkspaceService(new SqliteWorkspaceRepository(new SqliteDatabase(directory)), paths, CLOCK);
        workspace = workspaces.createWorkspace("Java");
        root = paths.workspaceRoot(workspace.id());
        index = new SqliteAssetIndexRepository(paths);
        scanner = new FileSystemWorkspaceAssetScanner(paths, index, CLOCK);
        document("documents/A.md", "doc_a", "A", "section_a", "ArrayList stores values.");
        document("More/B.md", "doc_b", "B", "section_b", "JVM executes bytecode.");
    }

    @Test void createsFromTwoRealDocumentsWithLocalIdsAndExactSourceRevisions() throws Exception {
        var outcome = service(request -> List.of(candidate("doc_a", "section_a"),
                candidate("doc_b", "section_b"))).create(workspace.id(), "Java Bank",
                List.of(StandardDocumentSelection.whole("doc_a"), StandardDocumentSelection.whole("doc_b")),
                EnumSet.of(QuestionType.SINGLE_CHOICE), 2, ignored -> { });
        assertEquals(2, outcome.accepted());
        assertEquals(AssetType.QUESTION_BANK, outcome.asset().assetType());
        assertEquals("question-banks/Java Bank.qbank", outcome.asset().currentPath());
        assertNull(outcome.asset().contentId());
        QuestionBankFile bank = codec.parse(Files.readString(root.resolve(outcome.asset().currentPath())));
        assertEquals(2, bank.sourceDocuments().size());
        assertEquals(2, bank.questions().size());
        assertTrue(bank.questions().getFirst().id().startsWith("q_"));
        assertTrue(bank.questions().getFirst().data().options().getFirst().id().startsWith("opt_"));
        assertNotEquals("q_forged", bank.questions().getFirst().id());
        assertNotEquals("opt_forged", bank.questions().getFirst().data().options().getFirst().id());
        assertEquals("doc_a", bank.questions().getFirst().sourceRefs().getFirst().documentAssetId());
        assertEquals("section_a", bank.questions().getFirst().sourceRefs().getFirst().sectionId());
        assertEquals(bank.sourceDocuments().getFirst().contentId(),
                bank.questions().getFirst().sourceRefs().getFirst().documentContentId());
        assertFalse(Files.readString(root.resolve(outcome.asset().currentPath())).contains("sourcePath"));
        assertEquals(outcome.asset(), index.findById(workspace.id(), bank.id()).orElseThrow());
        assertEquals(bank, service(request -> List.of()).read(workspace.id(), bank.id()));
        try (Connection connection = new SqliteDatabase(directory).openConnection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM question_bank");
        }
        assertEquals(bank, service(request -> List.of()).read(workspace.id(), bank.id()));
        Path moved = Files.createDirectories(root.resolve("Custom/Nested")).resolve("Moved.qbank");
        Files.move(root.resolve(outcome.asset().currentPath()), moved);
        assertEquals("Custom/Nested/Moved.qbank", scanner.scan(workspace.id()).stream()
                .filter(a -> a.assetId().equals(bank.id())).findFirst().orElseThrow().currentPath());
        assertEquals(bank, service(request -> List.of()).read(workspace.id(), bank.id()));
    }

    @Test void selectedScopeAndSourceIdsRejectInvalidCandidatesButKeepValidOnes() {
        var outcome = service(request -> List.of(candidate("doc_a", "section_a"),
                candidate("doc_missing", "section_a"), candidate("doc_a", "section_missing"),
                candidate("doc_b", "section_b"), candidateWithoutRefs())).create(workspace.id(), "Scoped",
                List.of(new StandardDocumentSelection("doc_a", GenerationScopeType.SECTION,
                        "chapter_a", "section_a")), EnumSet.of(QuestionType.SINGLE_CHOICE), 5, ignored -> { });
        assertEquals(1, outcome.accepted());
        assertEquals(4, outcome.rejected());
        assertEquals(1, outcome.bank().questions().size());
    }

    @Test void mixedScopesAndMultipleReferencesAcrossDocumentsAreSupported() {
        var source = new SourceAwareQuestionGenerator.Candidate("MULTIPLE_CHOICE", "Which statements are true?",
                "Both statements are in the source documents.",
                List.of(new SourceAwareQuestionGenerator.Option("A", "A fact"),
                        new SourceAwareQuestionGenerator.Option("B", "B fact"),
                        new SourceAwareQuestionGenerator.Option("C", "Wrong")), List.of("A", "B"),
                List.of(new SourceAwareQuestionGenerator.SourceRef("doc_a", "section_a"),
                        new SourceAwareQuestionGenerator.SourceRef("doc_b", "section_b")));
        var outcome = service(request -> List.of(source)).create(workspace.id(), "Mixed",
                List.of(new StandardDocumentSelection("doc_a", GenerationScopeType.CHAPTER, "chapter_a", null),
                        StandardDocumentSelection.whole("doc_b")),
                EnumSet.of(QuestionType.MULTIPLE_CHOICE), 1, ignored -> { });
        assertEquals(2, outcome.bank().questions().getFirst().sourceRefs().size());
        assertEquals(2, outcome.bank().questions().getFirst().data().correctOptionIds().size());
    }

    @Test void sameDocumentCanSelectTwoSectionsAndRejectOneOutsideSelection() throws Exception {
        Path file = root.resolve("documents/A.md");
        Files.writeString(file, Files.readString(file) + "### LinkedList\n<!-- qf:id=section_linked -->\n"
                + "LinkedList stores nodes.\n### HashMap\n<!-- qf:id=section_map -->\nHashMap stores keys.\n");
        var one = new SourceAwareQuestionGenerator.SourceRef("doc_a", "section_a");
        var two = new SourceAwareQuestionGenerator.SourceRef("doc_a", "section_linked");
        var accepted = new SourceAwareQuestionGenerator.Candidate("SINGLE_CHOICE", "What is true?", "Source facts.",
                List.of(new SourceAwareQuestionGenerator.Option("A", "True"),
                        new SourceAwareQuestionGenerator.Option("B", "False")), List.of("A"), List.of(one, two));
        var outside = new SourceAwareQuestionGenerator.Candidate("SINGLE_CHOICE", "Outside?", "Source facts.",
                accepted.options(), List.of("A"),
                List.of(new SourceAwareQuestionGenerator.SourceRef("doc_a", "section_map")));
        var outcome = service(request -> List.of(accepted, outside)).create(workspace.id(), "Two sections",
                List.of(new StandardDocumentSelection("doc_a", GenerationScopeType.SECTION,
                                "chapter_a", "section_a"),
                        new StandardDocumentSelection("doc_a", GenerationScopeType.SECTION,
                                "chapter_a", "section_linked")),
                EnumSet.of(QuestionType.SINGLE_CHOICE), 2, ignored -> { });
        assertEquals(1, outcome.accepted());
        assertEquals(1, outcome.rejected());
        assertEquals(2, outcome.bank().questions().getFirst().sourceRefs().size());
    }

    @Test void partialSuccessAndZeroValidPreserveExpectedFileState() throws Exception {
        List<SourceAwareQuestionGenerator.Candidate> ten = new ArrayList<>();
        for (int i = 0; i < 8; i++) ten.add(candidate("doc_a", "section_a"));
        ten.add(candidateWithoutRefs());
        ten.add(candidate("doc_a", "missing"));
        var outcome = service(request -> ten).create(workspace.id(), "Partial",
                List.of(StandardDocumentSelection.whole("doc_a")),
                EnumSet.of(QuestionType.SINGLE_CHOICE), 10, ignored -> { });
        assertEquals(8, outcome.accepted());
        assertEquals(2, outcome.rejected());
        String previous = Files.readString(root.resolve(outcome.asset().currentPath()));
        assertEquals(ErrorCode.NO_VALID_QUESTION_GENERATED,
                assertThrows(QuizForgeException.class, () -> service(request -> List.of(candidateWithoutRefs()))
                        .regenerate(workspace.id(), outcome.bank().id(), "Partial",
                                List.of(StandardDocumentSelection.whole("doc_a")),
                                EnumSet.of(QuestionType.SINGLE_CHOICE), 1, ignored -> { })).code());
        assertEquals(previous, Files.readString(root.resolve(outcome.asset().currentPath())));
    }

    @Test void createCollisionAndRegeneratePreserveBankIdentity() throws Exception {
        var first = createOne("Same", candidate("doc_a", "section_a"));
        var second = createOne("Same", candidate("doc_a", "section_a"));
        assertNotEquals(first.bank().id(), second.bank().id());
        assertEquals("question-banks/Same (2).qbank", second.asset().currentPath());
        String secondFile = Files.readString(root.resolve(second.asset().currentPath()));
        var changed = service(request -> List.of(candidate("doc_b", "section_b")))
                .regenerate(workspace.id(), first.bank().id(), "Changed",
                        List.of(StandardDocumentSelection.whole("doc_b")),
                        EnumSet.of(QuestionType.SINGLE_CHOICE), 1, ignored -> { });
        assertEquals(first.bank().id(), changed.bank().id());
        assertEquals(first.asset().currentPath(), changed.asset().currentPath());
        assertEquals(secondFile, Files.readString(root.resolve(second.asset().currentPath())));
    }

    @Test void sourceChangedInsideAiCallAbortsBeforeWritingOrReplacing() throws Exception {
        var first = createOne("Existing", candidate("doc_a", "section_a"));
        String previous = Files.readString(root.resolve(first.asset().currentPath()));
        var changing = service(request -> {
            try { Files.writeString(root.resolve("documents/A.md"),
                    Files.readString(root.resolve("documents/A.md")).replace("stores", "retains")); }
            catch (Exception error) { throw new RuntimeException(error); }
            return List.of(candidate("doc_a", "section_a"));
        });
        assertEquals(ErrorCode.SOURCE_DOCUMENT_CHANGED_DURING_GENERATION,
                assertThrows(QuizForgeException.class, () -> changing.regenerate(workspace.id(), first.bank().id(),
                        "Existing", List.of(StandardDocumentSelection.whole("doc_a")),
                        EnumSet.of(QuestionType.SINGLE_CHOICE), 1, ignored -> { })).code());
        assertEquals(previous, Files.readString(root.resolve(first.asset().currentPath())));
    }

    @Test void externalBankIsRecognizedAndSourcesResolveWithoutExistingFiles() throws Exception {
        var generated = createOne("External", candidate("doc_a", "section_a"));
        Path external = Files.createDirectories(root.resolve("Import")).resolve("Imported.qbank");
        Files.move(root.resolve(generated.asset().currentPath()), external);
        assertEquals("Import/Imported.qbank", scanner.scan(workspace.id()).stream()
                .filter(a -> a.assetId().equals(generated.bank().id())).findFirst().orElseThrow().currentPath());
        var resolver = new QuestionBankReferenceResolver(scanner);
        assertEquals(QuestionBankReferenceResolver.Status.EXACT_MATCH,
                resolver.resolve(workspace.id(), generated.bank()).getFirst().status());
        Files.writeString(root.resolve("documents/A.md"),
                Files.readString(root.resolve("documents/A.md")).replace("stores", "retains"));
        assertEquals(QuestionBankReferenceResolver.Status.DIFFERENT_REVISION,
                resolver.resolve(workspace.id(), generated.bank()).getFirst().status());
        Files.delete(root.resolve("documents/A.md"));
        assertEquals(QuestionBankReferenceResolver.Status.MISSING,
                resolver.resolve(workspace.id(), generated.bank()).getFirst().status());
        assertEquals(generated.bank(), codec.parse(Files.readString(external)));
    }

    @Test void exactContentFallbackReportsAmbiguityWithoutRebinding() throws Exception {
        var generated = createOne("Fallback", candidate("doc_a", "section_a"));
        String original = Files.readString(root.resolve("documents/A.md"));
        Files.delete(root.resolve("documents/A.md"));
        Path copy = Files.createDirectories(root.resolve("Copy"));
        Files.writeString(copy.resolve("C.md"), original.replace("doc_a", "doc_c"));
        var resolver = new QuestionBankReferenceResolver(scanner);
        var one = resolver.resolve(workspace.id(), generated.bank()).getFirst();
        assertEquals(QuestionBankReferenceResolver.Status.EXACT_CONTENT_MATCH, one.status());
        assertEquals("doc_c", one.candidates().getFirst().assetId());
        Files.writeString(copy.resolve("D.md"), original.replace("doc_a", "doc_d"));
        var two = resolver.resolve(workspace.id(), generated.bank()).getFirst();
        assertTrue(two.ambiguous());
        assertEquals(2, two.candidates().size());
        assertEquals("doc_a", generated.bank().sourceDocuments().getFirst().assetId());
    }

    @Test void formalParserAndSchemaValidatorRejectInvalidContentWithoutRequiringResolvedSources() {
        var generated = createOne("Validate", candidate("doc_a", "section_a"));
        var bank = generated.bank();
        var invalid = new QuestionBankFile(bank.format(), bank.schemaVersion(), bank.id(), bank.title(),
                bank.sourceDocuments(), List.of(bank.questions().getFirst(), bank.questions().getFirst()));
        assertEquals(ErrorCode.QUESTION_BANK_FILE_INVALID,
                assertThrows(QuizForgeException.class, () -> codec.validate(invalid)).code());
        assertEquals(bank, codec.parse(codec.write(bank)));
    }

    @Test void invalidJsonAndInvalidFormalFieldsAreRejected() {
        assertEquals(ErrorCode.QUESTION_BANK_FILE_INVALID,
                assertThrows(QuizForgeException.class, () -> codec.parse("not json")).code());
        var bank = createOne("Valid", candidate("doc_a", "section_a")).bank();
        String json = codec.write(bank);
        assertEquals(ErrorCode.QUESTION_BANK_FILE_INVALID,
                assertThrows(QuizForgeException.class, () -> codec.parse(
                        json.replace("SINGLE_CHOICE", "ESSAY"))).code());
        assertEquals(ErrorCode.QUESTION_BANK_FILE_INVALID,
                assertThrows(QuizForgeException.class, () -> codec.parse(
                        json.replace("section_a", "bad_section"))).code());
    }

    @Test void storageFailureAndRegistryFailurePreservePreviousFile() throws Exception {
        var original = createOne("Keep", candidate("doc_a", "section_a"));
        Path file = root.resolve(original.asset().currentPath());
        String before = Files.readString(file);
        Path saveFolder = root.resolve("question-banks");
        Files.move(saveFolder, root.resolve("saved-banks"));
        Files.writeString(saveFolder, "blocked");
        assertEquals(ErrorCode.QUESTION_BANK_STORAGE_FAILED,
                assertThrows(QuizForgeException.class, () -> createOne("Blocked",
                        candidate("doc_a", "section_a"))).code());
        Files.delete(saveFolder);
        Files.move(root.resolve("saved-banks"), saveFolder);
        WorkspaceAssetScanner failAfterLookup = new WorkspaceAssetScanner() {
            private int scans;
            @Override public io.quizforge.core.asset.WorkspaceScanResult scanWithReport(
                    io.quizforge.core.workspace.WorkspaceId id) {
                if (++scans >= 4) throw new IllegalStateException("Registry unavailable");
                return scanner.scanWithReport(id);
            }
        };
        var failing = new FileQuestionBankGenerationService(workspaces, failAfterLookup,
                new FormalMarkdownDocumentReader(new LocalStandardDocumentFileStorage(directory)),
                request -> List.of(candidate("doc_a", "section_a")), new QuestionBankV1Assembler(),
                codec, new LocalQuestionBankFileStorage(directory));
        assertThrows(RuntimeException.class, () -> failing.regenerate(workspace.id(), original.bank().id(),
                "Keep", List.of(StandardDocumentSelection.whole("doc_a")),
                EnumSet.of(QuestionType.SINGLE_CHOICE), 1, ignored -> { }));
        assertEquals(before, Files.readString(file));
    }

    @Test void emptyGenerationDoesNotCreateAFile() throws Exception {
        assertEquals(ErrorCode.NO_VALID_QUESTION_GENERATED,
                assertThrows(QuizForgeException.class, () -> service(request -> List.of(candidateWithoutRefs()))
                        .create(workspace.id(), "Empty", List.of(StandardDocumentSelection.whole("doc_a")),
                                EnumSet.of(QuestionType.SINGLE_CHOICE), 1, ignored -> { })).code());
        try (var entries = Files.list(root.resolve("question-banks"))) {
            assertEquals(0, entries.count());
        }
    }

    @Test void aiFailureBeforePublicationKeepsExistingBank() throws Exception {
        var original = createOne("AI failure", candidate("doc_a", "section_a"));
        Path path = root.resolve(original.asset().currentPath());
        String before = Files.readString(path);
        assertEquals(ErrorCode.QUESTION_GENERATION_FAILED,
                assertThrows(QuizForgeException.class, () -> service(request -> {
                    throw new IllegalStateException("fake failure");
                }).regenerate(workspace.id(), original.bank().id(), "AI failure",
                        List.of(StandardDocumentSelection.whole("doc_a")),
                        EnumSet.of(QuestionType.SINGLE_CHOICE), 1, ignored -> { })).code());
        assertEquals(before, Files.readString(path));
    }

    private FileQuestionBankGenerationService.Outcome createOne(String title,
            SourceAwareQuestionGenerator.Candidate candidate) {
        return service(request -> List.of(candidate)).create(workspace.id(), title,
                List.of(StandardDocumentSelection.whole("doc_a")),
                EnumSet.of(QuestionType.SINGLE_CHOICE), 1, ignored -> { });
    }

    private FileQuestionBankGenerationService service(
            Function<SourceAwareQuestionGenerator.Request, List<SourceAwareQuestionGenerator.Candidate>> response) {
        return new FileQuestionBankGenerationService(workspaces, scanner,
                new FormalMarkdownDocumentReader(new LocalStandardDocumentFileStorage(directory)), response::apply,
                new QuestionBankV1Assembler(), codec, new LocalQuestionBankFileStorage(directory));
    }

    private void document(String relative, String assetId, String title, String sectionId, String body) throws Exception {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "---\nquizforge_format: \"study-document\"\nschema_version: \"1.0\"\n"
                + "quizforge_id: \"" + assetId + "\"\ntitle: \"" + title + "\"\nlanguage: \"en-US\"\n---\n"
                + "# " + title + "\n## Chapter\n<!-- qf:id=chapter_" + assetId.substring(4) + " -->\n"
                + "### Section\n<!-- qf:id=" + sectionId + " -->\n" + body + "\n");
    }

    private SourceAwareQuestionGenerator.Candidate candidate(String documentId, String sectionId) {
        return new SourceAwareQuestionGenerator.Candidate("SINGLE_CHOICE", "What is true?", "The source says so.",
                List.of(new SourceAwareQuestionGenerator.Option("A", "Correct fact"),
                        new SourceAwareQuestionGenerator.Option("B", "Wrong fact")), List.of("A"),
                List.of(new SourceAwareQuestionGenerator.SourceRef(documentId, sectionId)));
    }

    private SourceAwareQuestionGenerator.Candidate candidateWithoutRefs() {
        return new SourceAwareQuestionGenerator.Candidate("SINGLE_CHOICE", "No source", "Explanation",
                List.of(new SourceAwareQuestionGenerator.Option("A", "One"),
                        new SourceAwareQuestionGenerator.Option("B", "Two")), List.of("A"), List.of());
    }
}
