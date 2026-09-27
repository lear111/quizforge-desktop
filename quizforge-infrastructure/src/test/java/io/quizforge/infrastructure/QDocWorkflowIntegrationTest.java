package io.quizforge.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import io.quizforge.core.asset.AssetType;
import io.quizforge.core.document.FileStandardDocumentGenerationService;
import io.quizforge.core.document.qdoc.*;
import io.quizforge.core.material.MaterialService;
import io.quizforge.core.question.*;
import io.quizforge.core.workspace.Workspace;
import io.quizforge.core.workspace.WorkspaceService;
import io.quizforge.core.workspace.WorkspaceFileKind;
import io.quizforge.extension.ai.AiProvider;
import io.quizforge.extension.document.*;
import io.quizforge.extension.question.SourceAwareQuestionGenerator;
import io.quizforge.infrastructure.filesystem.*;
import io.quizforge.infrastructure.persistence.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class QDocWorkflowIntegrationTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-26T00:00:00Z"), ZoneOffset.UTC);
    private static final String DRAFT = """
            ---
            quizforge_version: "1.0"
            quizforge_id: "doc_forged"
            title: "Java Collections"
            language: "en-US"
            ---
            # Java Collections
            ## Lists
            <!-- qf:id=chapter_forged -->
            ### ArrayList
            <!-- qf:id=section_forged -->
            Lists preserve insertion order.

            - first item
            - second item
            """;
    @TempDir Path temp;
    private QuizForgeDataDirectory directory;
    private WorkspacePathResolver paths;
    private WorkspaceService workspaces;
    private Workspace workspace;
    private SqliteMaterialRepository materials;
    private LocalMaterialFileStorage materialFiles;
    private LocalStandardDocumentFileStorage documentFiles;
    private FileSystemWorkspaceAssetScanner scanner;
    private SqliteAssetIndexRepository index;
    private io.quizforge.core.material.Material material;
    private final QDocV1Codec qdocs = new QDocV1Codec();
    private final AtomicReference<String> draft = new AtomicReference<>(DRAFT);

    @BeforeEach void setup() throws Exception {
        directory = new QuizForgeDataDirectory(temp.resolve("data"));
        paths = new WorkspacePathResolver(directory);
        var database = new SqliteDatabase(directory);
        workspaces = new WorkspaceService(new SqliteWorkspaceRepository(database), paths, CLOCK);
        workspace = workspaces.createWorkspace("Java");
        materials = new SqliteMaterialRepository(database);
        materialFiles = new LocalMaterialFileStorage(paths);
        documentFiles = new LocalStandardDocumentFileStorage(directory);
        index = new SqliteAssetIndexRepository(paths);
        scanner = new FileSystemWorkspaceAssetScanner(paths, index, CLOCK);
        Path source = temp.resolve("source.md");
        Files.writeString(source, "# Source\nJava collections.");
        material = new MaterialService(workspaces, materials, materialFiles, CLOCK, 10_000)
                .importMaterial(workspace.id(), source.toString());
    }

    @Test void aiDraftCreatesRealQDocWithLocalIdentityAndImmediateRegistry() throws Exception {
        var created = documentService().create(workspace.id(), List.of(material.id()), ignored -> { });
        assertEquals("documents/Java Collections.qdoc", created.asset().currentPath());
        assertEquals(AssetType.STANDARD_DOCUMENT, created.asset().assetType());
        assertEquals(created.asset(), index.findById(workspace.id(), created.asset().assetId()).orElseThrow());
        var model = qdocs.parse(Files.readString(root().resolve(created.asset().currentPath())));
        assertEquals(created.asset().assetId(), model.id());
        assertEquals(created.asset().contentId(), qdocs.contentId(model));
        assertNotEquals("doc_forged", model.id());
        assertNotEquals("chapter_forged", model.content().getFirst().id());
        var section = (io.quizforge.core.document.qdoc.DocumentNode) model.content().getFirst().children().getFirst();
        assertNotEquals("section_forged", section.id());
        assertEquals(2, section.children().size());
        assertFalse(created.markdown().contains("quizforge_format"));
        assertEquals(created.asset(), scanner.scan(workspace.id()).getFirst());
    }

    @Test void aiDraftCodeFenceWithoutLanguageBecomesTypedCodeBlock() {
        String candidate = DRAFT.replace("Lists preserve insertion order.", "```\nint x = 1;\n```");
        var assembled = new QDocKnowledgeDocumentAssembler().assemble(candidate,
                "Java Collections", "en-US", null);
        var model = qdocs.parse(assembled.content());
        var section = (DocumentNode) model.content().getFirst().children().getFirst();
        assertTrue(section.children().stream().anyMatch(child -> child instanceof ContentBlock block
                && block.type() == ContentBlockType.CODE_BLOCK && block.text().contains("int x = 1;")
                && block.language() == null));
    }

    @Test void regenerateAndMoveRetainAssetIdentityButTrackContentRevisionAndPath() throws Exception {
        var original = documentService().create(workspace.id(), List.of(material.id()), ignored -> { });
        draft.set(DRAFT.replace("insertion order", "insertion order and indexes"));
        var regenerated = documentService().regenerate(workspace.id(), original.asset().assetId(),
                List.of(material.id()), ignored -> { });
        assertEquals(original.asset().assetId(), regenerated.asset().assetId());
        assertNotEquals(original.asset().contentId(), regenerated.asset().contentId());
        Path renamed = root().resolve("documents/Renamed.qdoc");
        Files.move(root().resolve(regenerated.asset().currentPath()), renamed);
        var afterRename = scanner.scan(workspace.id()).getFirst();
        assertEquals(regenerated.asset().assetId(), afterRename.assetId());
        assertEquals(regenerated.asset().contentId(), afterRename.contentId());
        assertEquals("documents/Renamed.qdoc", afterRename.currentPath());
        Path moved = root().resolve("Custom/Deep/Renamed.qdoc");
        Files.createDirectories(moved.getParent());
        Files.move(renamed, moved);
        var discovered = scanner.scan(workspace.id()).getFirst();
        assertEquals(regenerated.asset().assetId(), discovered.assetId());
        assertEquals(regenerated.asset().contentId(), discovered.contentId());
        assertEquals("Custom/Deep/Renamed.qdoc", discovered.currentPath());
        assertEquals(discovered, index.findById(workspace.id(), discovered.assetId()).orElseThrow());
    }

    @Test void ordinaryMarkdownStaysUnregisteredAndQDocFeedsQuestionBankContext() throws Exception {
        Files.writeString(root().resolve("note.md"), "# Ordinary Markdown\nNo asset metadata.");
        var document = documentService().create(workspace.id(), List.of(material.id()), ignored -> { });
        assertEquals(1, scanner.scan(workspace.id()).size());
        var snapshot = new QDocFormalDocumentReader(documentFiles)
                .read(workspace.id(), document.asset().currentPath());
        assertEquals(document.asset().contentId(), snapshot.contentId());
        assertTrue(snapshot.chapters().getFirst().sections().getFirst().markdown().contains("first item"));
        String sectionId = snapshot.chapters().getFirst().sections().getFirst().id();
        AtomicReference<SourceAwareQuestionGenerator.Request> request = new AtomicReference<>();
        SourceAwareQuestionGenerator generator = prompt -> {
            request.set(prompt);
            return List.of(new SourceAwareQuestionGenerator.Candidate("SINGLE_CHOICE", "Which list?", "Source says so.",
                    List.of(new SourceAwareQuestionGenerator.Option("A", "ArrayList"),
                            new SourceAwareQuestionGenerator.Option("B", "HashSet")), List.of("A"),
                    List.of(new SourceAwareQuestionGenerator.SourceRef(document.asset().assetId(), sectionId))));
        };
        var service = new FileQuestionBankGenerationService(workspaces, scanner,
                new QDocFormalDocumentReader(documentFiles), generator, new QuestionBankV1Assembler(),
                new QuestionBankV1Codec(), new LocalQuestionBankFileStorage(directory));
        var outcome = service.create(workspace.id(), "Lists bank",
                List.of(StandardDocumentSelection.whole(document.asset().assetId())),
                EnumSet.of(QuestionType.SINGLE_CHOICE), 1, ignored -> { });
        assertEquals(1, outcome.accepted());
        assertTrue(request.get().sourceContext().contains("sectionId: " + sectionId));
        assertEquals(document.asset().contentId(), outcome.bank().sourceDocuments().getFirst().contentId());
        assertEquals(sectionId, outcome.bank().questions().getFirst().sourceRefs().getFirst().nodeId());
        assertEquals(document.asset().contentId(), outcome.bank().questions().getFirst().sourceRefs().getFirst().documentContentId());
        assertTrue(Files.exists(root().resolve(outcome.asset().currentPath())));
    }

    @Test void newMainScannerTreatsEvenLegacyFormalMarkdownAsOrdinaryMarkdown() throws Exception {
        String legacy = "---\nquizforge_format: study-document\nschema_version: \"1.0\"\n"
                + "quizforge_id: doc_legacy\ntitle: Legacy\nlanguage: en-US\n---\n"
                + "# Legacy\n## Chapter\n<!-- qf:id=chapter_old -->\n"
                + "### Section\n<!-- qf:id=section_old -->\nOld content.\n";
        Files.writeString(root().resolve("Legacy.md"), legacy);
        var mainScanner = new FileSystemWorkspaceAssetScanner(paths, index, CLOCK, false);
        assertTrue(mainScanner.scan(workspace.id()).isEmpty());
        var catalog = new LocalWorkspaceFileCatalog(paths, new QuestionBankV1Codec(), false);
        assertEquals(WorkspaceFileKind.MARKDOWN, catalog.inspect(workspace.id(), "Legacy.md").kind());
        assertEquals("doc_legacy", new StandardKnowledgeDocumentV1().parseIfStandard(legacy).orElseThrow().assetId());
    }

    @Test void subsectionSelectionSendsOnlyChosenContentWithParentSectionReference() throws Exception {
        var selected = new DocumentNode("subsection_selected", DocumentNodeType.SUBSECTION, "Selected",
                List.of(ContentBlock.text(ContentBlockType.PARAGRAPH, "Only selected knowledge.")));
        var other = new DocumentNode("subsection_other", DocumentNodeType.SUBSECTION, "Other",
                List.of(ContentBlock.text(ContentBlockType.PARAGRAPH, "Excluded knowledge.")));
        var section = new DocumentNode("section_scope", DocumentNodeType.SECTION, "Section",
                List.of(ContentBlock.text(ContentBlockType.PARAGRAPH, "Direct section knowledge."), selected, other));
        var chapter = new DocumentNode("chapter_scope", DocumentNodeType.CHAPTER, "Chapter", List.of(section));
        var model = new QDocDocument("quizforge-document", "1.0", "doc_scope",
                DocumentTemplate.GENERAL_KNOWLEDGE.reference(), "Scoped", "en-US", List.of(chapter));
        Path path = root().resolve("Custom/Scoped.qdoc");
        Files.createDirectories(path.getParent());
        Files.writeString(path, qdocs.write(model));
        AtomicReference<SourceAwareQuestionGenerator.Request> prompt = new AtomicReference<>();
        SourceAwareQuestionGenerator generator = request -> {
            prompt.set(request);
            return List.of(new SourceAwareQuestionGenerator.Candidate("SINGLE_CHOICE", "What was selected?",
                    "The selected subsection says so.",
                    List.of(new SourceAwareQuestionGenerator.Option("A", "Selected"),
                            new SourceAwareQuestionGenerator.Option("B", "Other")), List.of("A"),
                    List.of(new SourceAwareQuestionGenerator.SourceRef("doc_scope", "section_scope"))));
        };
        var service = new FileQuestionBankGenerationService(workspaces, scanner,
                new QDocFormalDocumentReader(documentFiles), generator, new QuestionBankV1Assembler(),
                new QuestionBankV1Codec(), new LocalQuestionBankFileStorage(directory));
        var selection = new StandardDocumentSelection("doc_scope", GenerationScopeType.SUBSECTION,
                "chapter_scope", "section_scope", "subsection_selected");
        var result = service.create(workspace.id(), "Subsection bank", List.of(selection),
                EnumSet.of(QuestionType.SINGLE_CHOICE), 1, ignored -> { });
        assertEquals(1, result.accepted());
        assertTrue(prompt.get().sourceContext().contains("Only selected knowledge."));
        assertFalse(prompt.get().sourceContext().contains("Excluded knowledge."));
        assertFalse(prompt.get().sourceContext().contains("Direct section knowledge."));
        assertEquals("section_scope", result.bank().questions().getFirst().sourceRefs().getFirst().nodeId());
        assertThrows(RuntimeException.class, () -> service.create(workspace.id(), "Invalid",
                List.of(new StandardDocumentSelection("doc_scope", GenerationScopeType.SUBSECTION,
                        "chapter_scope", "section_scope", "subsection_missing")),
                EnumSet.of(QuestionType.SINGLE_CHOICE), 1, ignored -> { }));
    }

    private FileStandardDocumentGenerationService documentService() {
        DocumentProcessor processor = new DocumentProcessor() {
            @Override public String formatId() { return "quizforge-standard-markdown"; }
            @Override public String formatVersion() { return "1.0"; }
            @Override public DocumentProcessResult process(DocumentProcessRequest request) {
                return new DocumentProcessResult(draft.get());
            }
        };
        DocumentValidator validator = new DocumentValidator() {
            @Override public String formatId() { return "quizforge-standard-markdown"; }
            @Override public String formatVersion() { return "1.0"; }
            @Override public DocumentValidationResult validate(String candidate) {
                return new DocumentValidationResult("Java Collections", List.of());
            }
        };
        AiProvider fake = new AiProvider() {
            @Override public String id() { return "fake"; }
            @Override public io.quizforge.extension.ai.AiResponse generate(io.quizforge.extension.ai.AiRequest request) {
                throw new AssertionError("Transport must not be called by fake processor");
            }
        };
        return new FileStandardDocumentGenerationService(workspaces, materials, materialFiles,
                () -> fake, processor, validator, new QDocKnowledgeDocumentAssembler(),
                documentFiles, scanner, 100_000);
    }

    private Path root() { return paths.workspaceRoot(workspace.id()); }
}
