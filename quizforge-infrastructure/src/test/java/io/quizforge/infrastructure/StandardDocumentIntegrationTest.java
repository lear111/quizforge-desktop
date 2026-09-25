package io.quizforge.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quizforge.core.ErrorCode;
import io.quizforge.core.QuizForgeException;
import io.quizforge.core.document.DocumentNormalizationService;
import io.quizforge.core.document.StandardDocument;
import io.quizforge.core.material.Material;
import io.quizforge.core.material.MaterialId;
import io.quizforge.core.material.MaterialService;
import io.quizforge.core.port.StandardDocumentRepository;
import io.quizforge.core.workspace.Workspace;
import io.quizforge.core.workspace.WorkspaceId;
import io.quizforge.core.workspace.WorkspaceService;
import io.quizforge.extension.ai.AiProvider;
import io.quizforge.extension.document.DocumentProcessRequest;
import io.quizforge.extension.document.DocumentProcessResult;
import io.quizforge.extension.document.DocumentProcessor;
import io.quizforge.extension.document.DocumentValidationResult;
import io.quizforge.extension.document.DocumentValidator;
import io.quizforge.extension.document.SourceMaterial;
import io.quizforge.infrastructure.filesystem.LocalMaterialFileStorage;
import io.quizforge.infrastructure.filesystem.LocalStandardDocumentFileStorage;
import io.quizforge.infrastructure.filesystem.QuizForgeDataDirectory;
import io.quizforge.infrastructure.filesystem.WorkspacePathResolver;
import io.quizforge.infrastructure.persistence.SqliteDatabase;
import io.quizforge.infrastructure.persistence.SqliteMaterialRepository;
import io.quizforge.infrastructure.persistence.SqliteStandardDocumentRepository;
import io.quizforge.infrastructure.persistence.SqliteWorkspaceRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StandardDocumentIntegrationTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-25T00:00:00Z"), ZoneOffset.UTC);
    @TempDir Path temporaryDirectory;
    private QuizForgeDataDirectory directory;
    private SqliteDatabase database;
    private SqliteMaterialRepository materialRepository;
    private LocalMaterialFileStorage materialFiles;
    private LocalStandardDocumentFileStorage documentFiles;
    private SqliteStandardDocumentRepository documentRepository;
    private WorkspaceService workspaces;
    private MaterialService materials;
    private final AtomicReference<String> candidate = new AtomicReference<>("first version");
    private final AtomicReference<List<SourceMaterial>> capturedSources = new AtomicReference<>();
    private final AtomicInteger processorCalls = new AtomicInteger();

    @BeforeEach
    void setUp() {
        directory = new QuizForgeDataDirectory(temporaryDirectory.resolve("data"));
        database = new SqliteDatabase(directory);
        var paths = new WorkspacePathResolver(directory);
        materialRepository = new SqliteMaterialRepository(database);
        materialFiles = new LocalMaterialFileStorage(paths);
        documentFiles = new LocalStandardDocumentFileStorage(directory);
        documentRepository = new SqliteStandardDocumentRepository(database);
        workspaces = new WorkspaceService(new SqliteWorkspaceRepository(database), paths, CLOCK);
        materials = new MaterialService(workspaces, materialRepository, materialFiles, CLOCK, 10_000);
    }

    @Test
    void oneMaterialCreatesFileAndSurvivesRestart() throws Exception {
        Workspace workspace = workspaces.createWorkspace("Java");
        Material one = importSource(workspace, "ArrayList.md", "# ArrayList\nContent");
        var generated = service(1000, documentRepository).generate(workspace.id(), List.of(one.id()), ignored -> {});
        assertEquals("Generated", generated.document().title());
        assertEquals(List.of(one.id()), generated.document().sourceMaterialIds());
        Path file = documentPath(workspace);
        assertTrue(Files.exists(file));
        assertEquals("first version", Files.readString(file));
        var restarted = new SqliteStandardDocumentRepository(new SqliteDatabase(directory));
        assertEquals(generated.document(), restarted.findByWorkspace(workspace.id()).orElseThrow());
        assertEquals("first version", documentFiles.read(workspace.id()));
    }

    @Test
    void multipleMaterialsPreserveNamesContentAndProvenance() throws Exception {
        Workspace workspace = workspaces.createWorkspace("Java");
        Material one = importSource(workspace, "ArrayList.md", "List content");
        Material two = importSource(workspace, "HashMap.md", "Map content");
        var generated = service(1000, documentRepository).generate(workspace.id(),
                List.of(one.id(), two.id()), ignored -> {});
        assertEquals(List.of(one.id(), two.id()), generated.document().sourceMaterialIds());
        assertEquals(List.of("ArrayList.md", "HashMap.md"),
                capturedSources.get().stream().map(SourceMaterial::name).toList());
        assertEquals(List.of("List content", "Map content"),
                capturedSources.get().stream().map(SourceMaterial::content).toList());
        assertEquals(generated.document().sourceMaterialIds(),
                documentRepository.findByWorkspace(workspace.id()).orElseThrow().sourceMaterialIds());
    }

    @Test
    void validationFailureNeverSavesAndFailedRegenerateKeepsOldDocument() throws Exception {
        Workspace workspace = workspaces.createWorkspace("Java");
        Material one = importSource(workspace, "One.md", "source");
        candidate.set("INVALID");
        assertCode(ErrorCode.STANDARD_DOCUMENT_VALIDATION_FAILED,
                () -> service(1000, documentRepository).generate(workspace.id(), List.of(one.id()), ignored -> {}));
        assertTrue(documentRepository.findByWorkspace(workspace.id()).isEmpty());
        assertFalse(Files.exists(documentPath(workspace)));
        candidate.set("old valid document");
        StandardDocument old = service(1000, documentRepository).generate(workspace.id(),
                List.of(one.id()), ignored -> {}).document();
        candidate.set("INVALID");
        assertCode(ErrorCode.STANDARD_DOCUMENT_VALIDATION_FAILED,
                () -> service(1000, documentRepository).generate(workspace.id(), List.of(one.id()), ignored -> {}));
        assertEquals(old, documentRepository.findByWorkspace(workspace.id()).orElseThrow());
        assertEquals("old valid document", Files.readString(documentPath(workspace)));
    }

    @Test
    void rejectsOtherWorkspaceNoSelectionAndTooLargeInputBeforeAi() throws Exception {
        Workspace one = workspaces.createWorkspace("One");
        Workspace two = workspaces.createWorkspace("Two");
        Material foreign = importSource(two, "Foreign.md", "foreign");
        assertCode(ErrorCode.NO_MATERIAL_SELECTED,
                () -> service(20, documentRepository).generate(one.id(), List.of(), ignored -> {}));
        assertCode(ErrorCode.MATERIAL_NOT_IN_WORKSPACE,
                () -> service(20, documentRepository).generate(one.id(), List.of(foreign.id()), ignored -> {}));
        Material large = importSource(one, "Large.md", "x".repeat(80));
        assertCode(ErrorCode.DOCUMENT_INPUT_TOO_LARGE,
                () -> service(20, documentRepository).generate(one.id(), List.of(large.id()), ignored -> {}));
        assertEquals(0, processorCalls.get());
        assertTrue(documentRepository.findByWorkspace(one.id()).isEmpty());
    }

    @Test
    void databaseFailureDuringRegenerateRestoresOldFileAndMetadata() throws Exception {
        Workspace workspace = workspaces.createWorkspace("Java");
        Material one = importSource(workspace, "One.md", "source");
        StandardDocument old = service(1000, documentRepository).generate(workspace.id(),
                List.of(one.id()), ignored -> {}).document();
        candidate.set("new valid document");
        StandardDocumentRepository failing = new StandardDocumentRepository() {
            @Override
            public Optional<StandardDocument> findByWorkspace(WorkspaceId id) {
                return documentRepository.findByWorkspace(id);
            }
            @Override
            public void save(StandardDocument document) {
                throw new QuizForgeException(ErrorCode.PERSISTENCE_FAILED, "Injected DB failure.");
            }
        };
        assertCode(ErrorCode.PERSISTENCE_FAILED,
                () -> service(1000, failing).generate(workspace.id(), List.of(one.id()), ignored -> {}));
        assertEquals(old, documentRepository.findByWorkspace(workspace.id()).orElseThrow());
        assertEquals("first version", Files.readString(documentPath(workspace)));
    }

    private DocumentNormalizationService service(int limit, StandardDocumentRepository repository) {
        DocumentProcessor processor = new DocumentProcessor() {
            @Override public String formatId() { return "quizforge-standard-markdown"; }
            @Override public String formatVersion() { return "1.0"; }
            @Override public DocumentProcessResult process(DocumentProcessRequest request) {
                processorCalls.incrementAndGet();
                capturedSources.set(request.sources());
                return new DocumentProcessResult(candidate.get());
            }
        };
        DocumentValidator validator = new DocumentValidator() {
            @Override public String formatId() { return "quizforge-standard-markdown"; }
            @Override public String formatVersion() { return "1.0"; }
            @Override public DocumentValidationResult validate(String content) {
                return new DocumentValidationResult("Generated",
                        "INVALID".equals(content) ? List.of("INVALID_SCHEMA_VERSION") : List.of());
            }
        };
        AiProvider fakeProvider = new AiProvider() {
            @Override public String id() { return "fake"; }
            @Override public io.quizforge.extension.ai.AiResponse generate(
                    io.quizforge.extension.ai.AiRequest request) {
                throw new AssertionError("Fake processor should not invoke AI");
            }
        };
        return new DocumentNormalizationService(workspaces, materialRepository, materialFiles,
                repository, documentFiles, () -> fakeProvider, processor, validator, CLOCK, limit);
    }

    private Material importSource(Workspace workspace, String name, String content) throws Exception {
        Path source = temporaryDirectory.resolve(name);
        Files.writeString(source, content);
        return materials.importMaterial(workspace.id(), source.toString());
    }

    private Path documentPath(Workspace workspace) {
        return directory.workspacesDirectory().resolve(workspace.id().toString())
                .resolve("document").resolve("study.md");
    }

    private void assertCode(ErrorCode expected, Runnable action) {
        QuizForgeException error = assertThrows(QuizForgeException.class, action::run);
        assertEquals(expected, error.code());
    }
}
