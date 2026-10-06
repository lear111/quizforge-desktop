package io.quizforge.infrastructure;

import io.quizforge.core.question.content.TextContent;
import io.quizforge.core.question.model.Question;
import io.quizforge.core.question.model.QuestionBank;
import io.quizforge.core.question.resource.QBankResource;
import io.quizforge.core.question.resource.ResourceKind;
import io.quizforge.core.question.service.QuestionBankEditorModel;
import io.quizforge.core.question.service.QuestionBankFileEditService;
import io.quizforge.core.question.source.QuestionSourceDocument;
import io.quizforge.core.question.source.SourceRef;
import io.quizforge.core.question.model.choice.ChoiceAnswerSpec;
import io.quizforge.core.question.model.choice.ChoiceOption;
import io.quizforge.core.question.model.choice.ChoicePayload;
import io.quizforge.core.workspace.model.Workspace;
import io.quizforge.core.workspace.service.WorkspaceService;
import io.quizforge.infrastructure.filesystem.QuizForgeDataDirectory;
import io.quizforge.infrastructure.filesystem.markdown.FileDocumentNodeLookup;
import io.quizforge.infrastructure.filesystem.markdown.LegacyMarkdownCodec;
import io.quizforge.infrastructure.filesystem.markdown.LocalMarkdownFileStorage;
import io.quizforge.infrastructure.filesystem.qbank.LocalQuestionBankFileStorage;
import io.quizforge.infrastructure.filesystem.qbank.QuestionBankV2Codec;
import io.quizforge.infrastructure.filesystem.workspace.FileSystemWorkspaceAssetScanner;
import io.quizforge.infrastructure.filesystem.workspace.LocalWorkspaceFileCatalog;
import io.quizforge.infrastructure.filesystem.workspace.WorkspacePathResolver;
import io.quizforge.infrastructure.persistence.SqliteAssetIndexRepository;
import io.quizforge.infrastructure.persistence.SqliteDatabase;
import io.quizforge.infrastructure.persistence.SqliteWorkspaceRepository;
import io.quizforge.infrastructure.testing.QBankTestPackageBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class QuestionBankFileEditIntegrationTest {
    @TempDir Path temp;
    private WorkspaceService workspaces;
    private Workspace workspace;
    private WorkspacePathResolver paths;
    private LocalQuestionBankFileStorage banks;
    private LocalMarkdownFileStorage documents;
    private FileSystemWorkspaceAssetScanner scanner;
    private SqliteAssetIndexRepository index;
    private final QuestionBankV2Codec codec = new QuestionBankV2Codec();
    private final LegacyMarkdownCodec markdown = new LegacyMarkdownCodec();
    private String bankPath;
    private String documentPath;
    private QuestionBank original;

    @BeforeEach void setup() throws Exception {
        QuizForgeDataDirectory directory = new QuizForgeDataDirectory(temp.resolve("data"));
        paths = new WorkspacePathResolver(directory);
        workspaces = new WorkspaceService(new SqliteWorkspaceRepository(new SqliteDatabase(directory)),
                paths, Clock.systemUTC());
        workspace = workspaces.createWorkspace("Editor");
        banks = new LocalQuestionBankFileStorage(directory);
        documents = new LocalMarkdownFileStorage(directory);
        index = new SqliteAssetIndexRepository(paths);
        scanner = new FileSystemWorkspaceAssetScanner(paths, index, Clock.systemUTC());
        documentPath = "documents/Source.md";
        Files.writeString(paths.workspaceRoot(workspace.id()).resolve(documentPath), document());
        String revision = markdown.parseLegacy(document()).orElseThrow().contentId();
        var source = new QuestionSourceDocument("doc_source", revision, "Source");
        var ref = SourceRef.anchor("doc_source", revision, "section_one", 1, "Source", "One");
        var question = Question.choice("q_one", "SINGLE_CHOICE", new TextContent("Original?"), new TextContent("Reason"), List.of(ref), new ChoicePayload(List.of(
                        new ChoiceOption("opt_a", new TextContent("A")),
                        new ChoiceOption("opt_b", new TextContent("B")))), new ChoiceAnswerSpec(List.of("opt_a")));
        original = new QuestionBank("qb_editor", "Bank", "2.0", List.of(), List.of(question), List.of());
        bankPath = "question-banks/Bank.qbank";
        new io.quizforge.infrastructure.filesystem.qbank.QBankPackageWriter()
                .write(paths.workspaceRoot(workspace.id()).resolve(bankPath), original);
        scanner.scan(workspace.id());
    }

    private String document() {
        return "---\nquizforge_format: \"study-document\"\nschema_version: \"1.0\"\n"
                + "quizforge_id: \"doc_source\"\ntitle: \"Source\"\nlanguage: \"en-US\"\n---\n"
                + "# Source\n\n## Chapter\n<!-- qf:id=chapter_one -->\n\n"
                + "<!-- qf:anchor=section_one -->\n### One\n<!-- qf:id=section_one -->\n\nLearning content\n";
    }

    private QuestionBankFileEditService service() {
        return new QuestionBankFileEditService(workspaces, banks, codec, scanner,
                new FileDocumentNodeLookup(new LocalWorkspaceFileCatalog(paths, codec)));
    }

    @Test void saveRereadAndRescanKeepIdentityAndUpdateRevision() {
        var edit = new QuestionBankEditorModel(original);
        edit.setStem(0, "Changed?");
        edit.setOptionContent(0, 0, "Changed option");
        edit.setAnalysis(0, "Changed analysis");
        edit.setCorrect(0, "opt_b", true);
        var saved = service().save(workspace.id(), bankPath, codec.contentId(original), edit.bank());
        assertEquals(original.assetId(), saved.assetId());
        assertNotEquals(codec.contentId(original), saved.contentId());
        assertEquals(codec.parse(codec.write(edit.bank())),
                banks.read(workspace.id(), bankPath));
        assertEquals(saved, index.findById(workspace.id(), original.assetId()).orElseThrow());
        assertEquals(saved.contentId(), scanner.scan(workspace.id()).stream()
                .filter(asset -> asset.assetId().equals(original.assetId())).findFirst().orElseThrow().contentId());
    }

    @Test void invalidEditAndExternalChangeNeverOverwriteExistingFile() throws Exception {
        var edit = new QuestionBankEditorModel(original);
        edit.setStem(0, "");
        QuestionBank old = banks.read(workspace.id(), bankPath);
        assertThrows(RuntimeException.class,
                () -> service().save(workspace.id(), bankPath, codec.contentId(original), edit.bank()));
        assertEquals(old, banks.read(workspace.id(), bankPath));
        var externalEdit = new QuestionBankEditorModel(original);
        externalEdit.setTitle("External change");
        String external = codec.write(externalEdit.bank());
        QBankTestPackageBuilder.write(paths.workspaceRoot(workspace.id()).resolve(bankPath), external);
        edit.setStem(0, "My change");
        assertThrows(IllegalStateException.class,
                () -> service().save(workspace.id(), bankPath, codec.contentId(original), edit.bank()));
        assertEquals(externalEdit.bank(), banks.read(workspace.id(), bankPath));
    }

    @Test void registryFailureRollsBackPublishedQuestionBank() throws Exception {
        byte[] beforeBytes = Files.readAllBytes(paths.workspaceRoot(workspace.id()).resolve(bankPath));
        QuestionBank before = banks.read(workspace.id(), bankPath);
        var edit = new QuestionBankEditorModel(original);
        edit.setTitle("Changed");
        var failing = new QuestionBankFileEditService(workspaces, banks, codec,
                id -> { throw new IllegalStateException("Injected registry failure"); },
                null);
        assertThrows(IllegalStateException.class,
                () -> failing.save(workspace.id(), bankPath, codec.contentId(original), edit.bank()));
        assertEquals(before, banks.read(workspace.id(), bankPath));
        assertArrayEquals(beforeBytes, Files.readAllBytes(paths.workspaceRoot(workspace.id()).resolve(bankPath)));
    }

    @Test void editingPackagePreservesResourceBytesAndActualHashes() throws Exception {
        var resource = new QBankResource("res_image", ResourceKind.IMAGE, "image/png", "resources/image.png", "a".repeat(64));
        byte[] bytes = new byte[]{0, 1, 2, 3, -1};
        Path file = paths.workspaceRoot(workspace.id()).resolve(bankPath);
        original = new io.quizforge.infrastructure.filesystem.qbank.QBankPackageWriter().write(file,
                new QuestionBank(original.assetId(), original.title(), original.stimuli(), original.questions(), List.of(resource)),
                ignored -> new java.io.ByteArrayInputStream(bytes));
        var edit = new QuestionBankEditorModel(original); edit.setTitle("Edited with image metadata");
        service().save(workspace.id(), bankPath, codec.contentId(original), edit.bank());
        try (var loaded = new io.quizforge.infrastructure.filesystem.qbank.QBankPackageReader().open(file);
                var stream = loaded.open(loaded.bank().resources().getFirst())) {
            assertArrayEquals(bytes, stream.readAllBytes());
            assertEquals(original.resources(), loaded.bank().resources());
        }
    }
    @Test void repackingOnlyZipMetadataDoesNotCreateEditConflict() throws Exception {
        Path file = paths.workspaceRoot(workspace.id()).resolve(bankPath);
        var entries = QBankTestPackageBuilder.entries(file);
        QBankTestPackageBuilder.zip(file, entries, 1700000000000L, 0);
        var edit = new QuestionBankEditorModel(original); edit.setTitle("Saved after repack");
        var saved = service().save(workspace.id(), bankPath, codec.contentId(original), edit.bank());
        assertEquals(codec.contentId(edit.bank()), saved.contentId());
    }

    @Test void onlyValidMarkdownSectionsCanBecomeNewReferences() {
        assertTrue(scanner.scan(workspace.id()).stream().anyMatch(asset->asset.assetId().equals("doc_source")));
        var edit = new QuestionBankEditorModel(original);
        var ref = original.questions().getFirst().sourceRefs().getFirst();
        edit.addSourceRef(0, SourceRef.anchor(ref.documentAssetId(), ref.documentContentId(), "section_missing", 1, ref.documentTitle(), "Missing"));
        assertThrows(IllegalArgumentException.class,
                () -> service().save(workspace.id(), bankPath, codec.contentId(original), edit.bank()));
        assertEquals(original, banks.read(workspace.id(), bankPath));
    }

    @Test void historicalReferencesRemainPortableWhenSourceChanges() throws Exception {
        var edit = new QuestionBankEditorModel(original);
        edit.setTitle("Edited after source change");
        String changedSource = document().replace("Learning content", "Revised");
        Files.writeString(paths.workspaceRoot(workspace.id()).resolve(documentPath), changedSource);
        var saved = service().save(workspace.id(), bankPath, codec.contentId(original), edit.bank());
        assertEquals(original.sourceDocuments(), banks.read(workspace.id(), bankPath).sourceDocuments());
        assertNotEquals(markdown.parseLegacy(changedSource).orElseThrow().contentId(),
                original.sourceDocuments().getFirst().contentId());
    }

    @Test void emptyDraftCanBecomeFormalBankWithoutLosingExternalEditProtection() throws Exception {
        String path = "draft.qbank";
        String draft = codec.write(new QuestionBank("qb_draft", "Draft", List.of(), List.of(), List.of()));
        QBankTestPackageBuilder.write(paths.workspaceRoot(workspace.id()).resolve(path), draft);
        QuestionBank empty = codec.parseEmptyDraft(draft);
        var edit = new QuestionBankEditorModel(empty);
        edit.addQuestion("SINGLE_CHOICE");
        var source = scanner.scan(workspace.id()).stream().filter(asset->asset.assetId().equals("doc_source")).findFirst().orElseThrow();
        edit.addSourceRef(0, SourceRef.anchor(source.assetId(), source.contentId(), "section_one", 1, source.title(), "One"));
        QuestionBank changed = new QuestionBank(empty.assetId(), "Externally changed", List.of(), List.of(), List.of());
        QBankTestPackageBuilder.write(paths.workspaceRoot(workspace.id()).resolve(path), codec.write(changed));
        assertThrows(IllegalStateException.class,
                () -> service().save(workspace.id(), path, null, codec.contentId(empty), edit.bank()));
        assertEquals(changed, banks.read(workspace.id(), path));
        var saved = service().save(workspace.id(), path, null, codec.contentId(changed), edit.bank());
        assertEquals("qb_draft", saved.assetId());
        assertEquals(codec.contentId(edit.bank()), saved.contentId());
        assertEquals(codec.parse(codec.write(edit.bank())),
                banks.read(workspace.id(), path));
    }

}
