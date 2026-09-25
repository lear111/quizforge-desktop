package io.quizforge.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quizforge.core.ErrorCode;
import io.quizforge.core.QuizForgeException;
import io.quizforge.core.asset.Asset;
import io.quizforge.core.asset.AssetType;
import io.quizforge.core.document.FileStandardDocumentGenerationService;
import io.quizforge.core.document.StandardDocument;
import io.quizforge.core.document.StandardDocumentId;
import io.quizforge.core.document.StandardDocumentStatus;
import io.quizforge.core.material.Material;
import io.quizforge.core.material.MaterialService;
import io.quizforge.core.port.WorkspaceAssetScanner;
import io.quizforge.core.workspace.Workspace;
import io.quizforge.core.workspace.WorkspaceService;
import io.quizforge.extension.ai.AiFailureKind;
import io.quizforge.extension.ai.AiProvider;
import io.quizforge.extension.ai.AiProviderException;
import io.quizforge.extension.document.DocumentProcessRequest;
import io.quizforge.extension.document.DocumentProcessResult;
import io.quizforge.extension.document.DocumentProcessor;
import io.quizforge.extension.document.DocumentValidationResult;
import io.quizforge.extension.document.DocumentValidator;
import io.quizforge.infrastructure.filesystem.FileSystemWorkspaceAssetScanner;
import io.quizforge.infrastructure.filesystem.LocalMaterialFileStorage;
import io.quizforge.infrastructure.filesystem.LocalStandardDocumentFileStorage;
import io.quizforge.infrastructure.filesystem.QuizForgeDataDirectory;
import io.quizforge.infrastructure.filesystem.StandardKnowledgeDocumentAssembler;
import io.quizforge.infrastructure.filesystem.StandardKnowledgeDocumentV1;
import io.quizforge.infrastructure.filesystem.WorkspacePathResolver;
import io.quizforge.infrastructure.persistence.SqliteAssetIndexRepository;
import io.quizforge.infrastructure.persistence.SqliteDatabase;
import io.quizforge.infrastructure.persistence.SqliteMaterialRepository;
import io.quizforge.infrastructure.persistence.SqliteStandardDocumentRepository;
import io.quizforge.infrastructure.persistence.SqliteWorkspaceRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileStandardDocumentGenerationIntegrationTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-25T00:00:00Z"), ZoneOffset.UTC);
    private static final String DRAFT = """
            ---
            quizforge_version: "1.0"
            quizforge_id: "doc_ai_forged"
            title: "Java Collections"
            language: "en-US"
            ---
            # Java Collections
            ## Lists
            <!-- qf:id=chapter_ai_forged -->
            ### ArrayList
            <!-- qf:id=section_ai_forged -->
            Lists preserve order.
            """;
    @TempDir Path temporaryDirectory;
    private QuizForgeDataDirectory directory;
    private SqliteDatabase global;
    private WorkspacePathResolver paths;
    private WorkspaceService workspaces;
    private SqliteMaterialRepository materialRepository;
    private LocalMaterialFileStorage materialFiles;
    private LocalStandardDocumentFileStorage documentFiles;
    private SqliteAssetIndexRepository index;
    private WorkspaceAssetScanner scanner;
    private Workspace workspace;
    private Material material;
    private final AtomicReference<String> draft = new AtomicReference<>(DRAFT);
    private final AtomicBoolean aiFails = new AtomicBoolean();
    private final AtomicBoolean draftRejected = new AtomicBoolean();

    @BeforeEach void setup() throws Exception {
        directory = new QuizForgeDataDirectory(temporaryDirectory.resolve("data"));
        global = new SqliteDatabase(directory);
        paths = new WorkspacePathResolver(directory);
        workspaces = new WorkspaceService(new SqliteWorkspaceRepository(global), paths, CLOCK);
        materialRepository = new SqliteMaterialRepository(global);
        materialFiles = new LocalMaterialFileStorage(paths);
        documentFiles = new LocalStandardDocumentFileStorage(directory);
        index = new SqliteAssetIndexRepository(paths);
        scanner = new FileSystemWorkspaceAssetScanner(paths, index, CLOCK);
        workspace = workspaces.createWorkspace("Java");
        Path source = temporaryDirectory.resolve("source.md");
        Files.writeString(source, "# Source\nStudy Java collections.");
        material = new MaterialService(workspaces, materialRepository, materialFiles, CLOCK, 10_000)
                .importMaterial(workspace.id(), source.toString());
    }

    @Test void createsDistinctFilesAndDiscoversThemAfterRestartWithoutOldDocumentRow() throws Exception {
        var first = service(scanner).create(workspace.id(), List.of(material.id()), ignored -> { });
        var second = service(scanner).create(workspace.id(), List.of(material.id()), ignored -> { });
        assertNotEquals(first.asset().assetId(), second.asset().assetId());
        assertEquals("documents/Java Collections.md", first.asset().currentPath());
        assertEquals("documents/Java Collections (2).md", second.asset().currentPath());
        assertEquals(AssetType.STANDARD_DOCUMENT, first.asset().assetType());
        assertEquals(first.asset().contentId(), new StandardKnowledgeDocumentV1()
                .parseIfStandard(first.markdown()).orElseThrow().contentId());
        assertFalse(first.markdown().contains("doc_ai_forged"));
        assertFalse(first.markdown().contains("chapter_ai_forged"));
        assertFalse(first.markdown().contains("section_ai_forged"));
        assertTrue(new SqliteStandardDocumentRepository(global).findByWorkspace(workspace.id()).isEmpty());

        // A legacy row is compatible metadata, never needed to rediscover either file.
        var legacy = new StandardDocument(StandardDocumentId.newId(), workspace.id(), "Old Draft",
                "quizforge-standard-markdown", "1.0", "study.md", StandardDocumentStatus.VALID,
                CLOCK.instant(), CLOCK.instant(), List.of());
        new SqliteStandardDocumentRepository(global).save(legacy);
        try (Connection connection = global.openConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM standard_document WHERE workspace_id='" + workspace.id() + "'");
        }
        workspaces = new WorkspaceService(new SqliteWorkspaceRepository(new SqliteDatabase(directory)),
                new WorkspacePathResolver(directory), CLOCK);
        var reopened = service(new FileSystemWorkspaceAssetScanner(paths,
                new SqliteAssetIndexRepository(paths), CLOCK));
        assertEquals(2, reopened.list(workspace.id()).size());
        assertEquals(first.asset().contentId(), reopened.findById(workspace.id(), first.asset().assetId())
                .orElseThrow().asset().contentId());
        assertEquals(first.markdown(), reopened.findById(workspace.id(), first.asset().assetId())
                .orElseThrow().markdown());
    }

    @Test void regenerateKeepsAssetIdentityAndChangesContentRevision() throws Exception {
        var first = service(scanner).create(workspace.id(), List.of(material.id()), ignored -> { });
        draft.set(DRAFT.replace("preserve order", "preserve insertion order"));
        var changed = service(scanner).regenerate(workspace.id(), first.asset().assetId(),
                List.of(material.id()), ignored -> { });
        assertEquals(first.asset().assetId(), changed.asset().assetId());
        assertEquals(first.asset().currentPath(), changed.asset().currentPath());
        assertNotEquals(first.asset().contentId(), changed.asset().contentId());
        assertEquals(changed.asset().contentId(), index.findById(workspace.id(), first.asset().assetId())
                .orElseThrow().contentId());
    }

    @Test void movedGeneratedDocumentRetainsIdentityAndRevision() throws Exception {
        var generated = service(scanner).create(workspace.id(), List.of(material.id()), ignored -> { });
        Path moved = Files.createDirectories(paths.workspaceRoot(workspace.id()).resolve("Java/Lists"))
                .resolve("Collections.md");
        Files.move(paths.workspaceRoot(workspace.id()).resolve(generated.asset().currentPath()), moved);
        Asset registered = scanner.scan(workspace.id()).getFirst();
        assertEquals(generated.asset().assetId(), registered.assetId());
        assertEquals(generated.asset().contentId(), registered.contentId());
        assertEquals("Java/Lists/Collections.md", registered.currentPath());
    }

    @Test void invalidDraftAndInvalidFormalStructureNeverCreateFiles() throws Exception {
        draftRejected.set(true);
        assertCode(ErrorCode.STANDARD_DOCUMENT_VALIDATION_FAILED,
                () -> service(scanner).create(workspace.id(), List.of(material.id()), ignored -> { }));
        draftRejected.set(false);
        draft.set(DRAFT.replace("### ArrayList", "#### ArrayList"));
        assertCode(ErrorCode.STANDARD_DOCUMENT_VALIDATION_FAILED,
                () -> service(scanner).create(workspace.id(), List.of(material.id()), ignored -> { }));
        assertTrue(scanner.scan(workspace.id()).stream()
                .noneMatch(asset -> asset.assetType() == AssetType.STANDARD_DOCUMENT));
        assertEquals(0, documentFileCount());
    }

    @Test void aiAndFilesystemFailuresLeaveOldFileIntact() throws Exception {
        var first = service(scanner).create(workspace.id(), List.of(material.id()), ignored -> { });
        Path file = paths.workspaceRoot(workspace.id()).resolve(first.asset().currentPath());
        aiFails.set(true);
        assertCode(ErrorCode.AI_PROVIDER_UNAVAILABLE,
                () -> service(scanner).regenerate(workspace.id(), first.asset().assetId(),
                        List.of(material.id()), ignored -> { }));
        aiFails.set(false);
        draftRejected.set(true);
        assertCode(ErrorCode.STANDARD_DOCUMENT_VALIDATION_FAILED,
                () -> service(scanner).regenerate(workspace.id(), first.asset().assetId(),
                        List.of(material.id()), ignored -> { }));
        draftRejected.set(false);
        draft.set(DRAFT.replace("### ArrayList", "#### ArrayList"));
        assertCode(ErrorCode.STANDARD_DOCUMENT_VALIDATION_FAILED,
                () -> service(scanner).regenerate(workspace.id(), first.asset().assetId(),
                        List.of(material.id()), ignored -> { }));
        draft.set(DRAFT);
        assertEquals(first.markdown(), Files.readString(file));
        // A directory in place of the default save folder prevents a new file write.
        Path defaultFolder = paths.workspaceRoot(workspace.id()).resolve("documents");
        Files.move(defaultFolder, paths.workspaceRoot(workspace.id()).resolve("saved-documents"));
        Files.writeString(defaultFolder, "blocked");
        assertCode(ErrorCode.STANDARD_DOCUMENT_STORAGE_FAILED,
                () -> service(scanner).create(workspace.id(), List.of(material.id()), ignored -> { }));
        assertTrue(Files.isRegularFile(paths.workspaceRoot(workspace.id())
                .resolve("saved-documents/Java Collections.md")));
    }

    @Test void registryFailureKeepsNewFileAndRestoresOldRegeneration() throws Exception {
        WorkspaceAssetScanner failing = id -> { throw new IllegalStateException("Injected registry failure"); };
        QuizForgeException failure = assertThrows(QuizForgeException.class,
                () -> service(failing).create(workspace.id(), List.of(material.id()), ignored -> { }));
        assertEquals(ErrorCode.STANDARD_DOCUMENT_STORAGE_FAILED, failure.code());
        assertTrue(failure.getMessage().contains("documents/Java Collections.md"));
        assertEquals(1, documentFileCount());
        Asset previous = scanner.scan(workspace.id()).getFirst();
        String previousMarkdown = Files.readString(paths.workspaceRoot(workspace.id())
                .resolve(previous.currentPath()));
        draft.set(DRAFT.replace("preserve order", "is ordered"));
        WorkspaceAssetScanner failAfterLookup = new WorkspaceAssetScanner() {
            private int scans;
            @Override public io.quizforge.core.asset.WorkspaceScanResult scanWithReport(
                    io.quizforge.core.workspace.WorkspaceId id) {
                if (++scans >= 3) throw new IllegalStateException("Injected registry failure");
                return scanner.scanWithReport(id);
            }
        };
        assertThrows(IllegalStateException.class,
                () -> service(failAfterLookup).regenerate(workspace.id(), previous.assetId(),
                        List.of(material.id()), ignored -> { }));
        assertEquals(previousMarkdown, Files.readString(paths.workspaceRoot(workspace.id())
                .resolve(previous.currentPath())));
        assertEquals(previous.contentId(), scanner.scan(workspace.id()).getFirst().contentId());
    }

    @Test void titleBasedNamesHandleInvalidCharactersAndBlankFallback() throws Exception {
        try (var staged = documentFiles.stageCreate(workspace.id(), "Java: Collections", "example")) {
            assertEquals("documents/Java_ Collections.md", staged.currentPath());
            staged.publish();
            staged.complete();
        }
        try (var staged = documentFiles.stageCreate(workspace.id(), "  ", "example")) {
            assertEquals("documents/Untitled.md", staged.currentPath());
            staged.publish();
            staged.complete();
        }
        assertTrue(Files.exists(paths.workspaceRoot(workspace.id()).resolve("documents/Untitled.md")));
    }

    private FileStandardDocumentGenerationService service(WorkspaceAssetScanner assetScanner) {
        DocumentProcessor processor = new DocumentProcessor() {
            @Override public String formatId() { return "quizforge-standard-markdown"; }
            @Override public String formatVersion() { return "1.0"; }
            @Override public DocumentProcessResult process(DocumentProcessRequest request) {
                if (aiFails.get()) throw new AiProviderException(AiFailureKind.UNAVAILABLE, "Fake AI failure");
                return new DocumentProcessResult(draft.get());
            }
        };
        DocumentValidator validator = new DocumentValidator() {
            @Override public String formatId() { return "quizforge-standard-markdown"; }
            @Override public String formatVersion() { return "1.0"; }
            @Override public DocumentValidationResult validate(String candidate) {
                return new DocumentValidationResult("Java Collections",
                        draftRejected.get() ? List.of("REJECTED_DRAFT") : List.of());
            }
        };
        AiProvider fake = new AiProvider() {
            @Override public String id() { return "fake"; }
            @Override public io.quizforge.extension.ai.AiResponse generate(
                    io.quizforge.extension.ai.AiRequest request) { throw new AssertionError(); }
        };
        return new FileStandardDocumentGenerationService(workspaces, materialRepository, materialFiles,
                () -> fake, processor, validator, new StandardKnowledgeDocumentAssembler(),
                documentFiles, assetScanner, 100_000);
    }

    private void assertCode(ErrorCode expected, Runnable action) {
        assertEquals(expected, assertThrows(QuizForgeException.class, action::run).code());
    }

    private long documentFileCount() throws Exception {
        try (var entries = Files.list(paths.workspaceRoot(workspace.id()).resolve("documents"))) {
            return entries.count();
        }
    }
}
