package io.quizforge.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quizforge.core.ErrorCode;
import io.quizforge.core.QuizForgeException;
import io.quizforge.core.material.Material;
import io.quizforge.core.material.MaterialId;
import io.quizforge.core.material.MaterialService;
import io.quizforge.core.material.MaterialStatus;
import io.quizforge.core.port.MaterialFileStorage;
import io.quizforge.core.port.MaterialRepository;
import io.quizforge.core.port.WorkspaceRepository;
import io.quizforge.core.workspace.Workspace;
import io.quizforge.core.workspace.WorkspaceId;
import io.quizforge.core.workspace.WorkspaceService;
import io.quizforge.infrastructure.filesystem.LocalMaterialFileStorage;
import io.quizforge.infrastructure.filesystem.QuizForgeDataDirectory;
import io.quizforge.infrastructure.filesystem.WorkspacePathResolver;
import io.quizforge.infrastructure.persistence.SqliteDatabase;
import io.quizforge.infrastructure.persistence.SqliteMaterialRepository;
import io.quizforge.infrastructure.persistence.SqliteWorkspaceRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceMaterialIntegrationTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-25T00:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path temporaryDirectory;

    private QuizForgeDataDirectory directory;
    private SqliteDatabase database;
    private SqliteWorkspaceRepository workspaceRepository;
    private SqliteMaterialRepository materialRepository;
    private WorkspacePathResolver paths;
    private LocalMaterialFileStorage storage;
    private WorkspaceService workspaces;
    private MaterialService materials;

    @BeforeEach
    void setUp() {
        directory = new QuizForgeDataDirectory(temporaryDirectory.resolve("data"));
        database = new SqliteDatabase(directory);
        workspaceRepository = new SqliteWorkspaceRepository(database);
        materialRepository = new SqliteMaterialRepository(database);
        paths = new WorkspacePathResolver(directory);
        storage = new LocalMaterialFileStorage(paths);
        workspaces = new WorkspaceService(workspaceRepository, paths, CLOCK);
        materials = new MaterialService(workspaces, materialRepository, storage, CLOCK, 1024);
    }

    @Test
    void createsTrimmedWorkspaceWithUtcTimestamps() {
        Workspace workspace = workspaces.createWorkspace("  Java Collections  ");
        assertEquals("Java Collections", workspace.name());
        assertEquals(Instant.parse("2026-09-25T00:00:00Z"), workspace.createdAt());
        assertEquals(workspace.createdAt(), workspace.updatedAt());
        assertEquals(workspace, workspaces.getWorkspace(workspace.id()));
        assertTrue(Files.isDirectory(directory.workspacesDirectory()
                .resolve(workspace.id().toString()).resolve("materials")));
    }

    @Test
    void rejectsBlankWorkspaceName() {
        assertCode(ErrorCode.INVALID_WORKSPACE_NAME, () -> workspaces.createWorkspace("  "));
        assertCode(ErrorCode.INVALID_WORKSPACE_NAME, () -> workspaces.createWorkspace(null));
        assertTrue(workspaces.listWorkspaces().isEmpty());
    }

    @Test
    void removesWorkspaceDirectoryWhenDatabaseSaveFails() throws Exception {
        WorkspaceRepository failing = new WorkspaceRepository() {
            @Override
            public void save(Workspace workspace) {
                throw new QuizForgeException(ErrorCode.PERSISTENCE_FAILED, "Simulated database failure.");
            }

            @Override
            public List<Workspace> list() {
                return workspaceRepository.list();
            }

            @Override
            public Optional<Workspace> findById(WorkspaceId id) {
                return workspaceRepository.findById(id);
            }
        };
        WorkspaceService failingService = new WorkspaceService(failing, paths, CLOCK);
        assertCode(ErrorCode.PERSISTENCE_FAILED, () -> failingService.createWorkspace("Notes"));
        try (var entries = Files.list(directory.workspacesDirectory())) {
            assertEquals(0, entries.count());
        }
        assertTrue(workspaceRepository.list().isEmpty());
    }

    @Test
    void listsWorkspacesAndLoadsThemAfterRepositoryReopen() {
        Workspace first = workspaces.createWorkspace("First");
        Workspace second = workspaces.createWorkspace("Second");
        assertNotEquals(first.id(), second.id());

        SqliteWorkspaceRepository reopened = new SqliteWorkspaceRepository(new SqliteDatabase(directory));
        assertEquals(Set.of(first, second), Set.copyOf(reopened.list()));
        assertEquals(Optional.of(first), reopened.findById(first.id()));
        assertCode(ErrorCode.WORKSPACE_NOT_FOUND,
                () -> workspaces.getWorkspace(WorkspaceId.newId()));
    }

    @Test
    void importsMdAndMarkdownAndKeepsCopiedContentAfterSourceDeletion() throws Exception {
        Workspace workspace = workspaces.createWorkspace("Notes");
        String irregularMarkdown = "# Java\n\n乱七八糟的笔记\n\n##### HashMap\n";
        Path md = source("HashMap.md", irregularMarkdown);
        Path markdown = source("ArrayList.markdown", "# ArrayList\n");

        Material first = materials.importMaterial(workspace.id(), md.toString());
        Material second = materials.importMaterial(workspace.id(), markdown.toString());
        assertEquals(MaterialStatus.IMPORTED, first.status());
        assertEquals(Set.of(first, second), Set.copyOf(materials.listMaterials(workspace.id())));
        assertEquals(irregularMarkdown, materials.readMaterial(first.id()));
        assertEquals("# ArrayList\n", materials.readMaterial(second.id()));
        assertEquals(first.id() + ".md", first.storedFileName());
        assertTrue(Files.exists(paths.materialPath(first)));
        assertTrue(paths.materialPath(first).startsWith(directory.root()));

        Files.delete(md);
        Files.delete(markdown);
        assertEquals(irregularMarkdown, materials.readMaterial(first.id()));
        assertEquals("# ArrayList\n", materials.readMaterial(second.id()));
    }

    @Test
    void rejectsUnsupportedFormat() throws Exception {
        Workspace workspace = workspaces.createWorkspace("Notes");
        Path source = source("notes.txt", "text");
        assertCode(ErrorCode.UNSUPPORTED_MATERIAL_FORMAT,
                () -> materials.importMaterial(workspace.id(), source.toString()));
        assertTrue(materials.listMaterials(workspace.id()).isEmpty());
    }

    @Test
    void rejectsEmptyMaterial() throws Exception {
        Workspace workspace = workspaces.createWorkspace("Notes");
        Path source = source("empty.md", "");
        assertCode(ErrorCode.EMPTY_MATERIAL,
                () -> materials.importMaterial(workspace.id(), source.toString()));
        assertTrue(materials.listMaterials(workspace.id()).isEmpty());
    }

    @Test
    void rejectsOversizedMaterial() throws Exception {
        Workspace workspace = workspaces.createWorkspace("Notes");
        Path source = source("large.md", "1234567890");
        MaterialService limited = new MaterialService(workspaces, materialRepository, storage, CLOCK, 5);
        assertCode(ErrorCode.MATERIAL_TOO_LARGE,
                () -> limited.importMaterial(workspace.id(), source.toString()));
        assertTrue(materials.listMaterials(workspace.id()).isEmpty());
    }

    @Test
    void rejectsMalformedUtf8AndMissingSource() throws Exception {
        Workspace workspace = workspaces.createWorkspace("Notes");
        Path invalid = temporaryDirectory.resolve("invalid.md");
        Files.write(invalid, new byte[] {(byte) 0xc3, (byte) 0x28});
        assertCode(ErrorCode.MATERIAL_READ_FAILED,
                () -> materials.importMaterial(workspace.id(), invalid.toString()));
        assertCode(ErrorCode.MATERIAL_READ_FAILED,
                () -> materials.importMaterial(workspace.id(), temporaryDirectory.resolve("gone.md").toString()));
    }

    @Test
    void deletesDatabaseRecordAndInternalCopyButPreservesOriginal() throws Exception {
        Workspace workspace = workspaces.createWorkspace("Notes");
        Path source = source("keep.md", "# Keep original\n");
        Material material = materials.importMaterial(workspace.id(), source.toString());
        Path stored = paths.materialPath(material);

        materials.deleteMaterial(material.id());

        assertTrue(Files.exists(source));
        assertFalse(Files.exists(stored));
        assertTrue(new SqliteMaterialRepository(new SqliteDatabase(directory))
                .findById(material.id()).isEmpty());
        assertCode(ErrorCode.MATERIAL_NOT_FOUND, () -> materials.readMaterial(material.id()));
    }

    @Test
    void removesCopiedFileWhenDatabaseSaveFails() throws Exception {
        Workspace workspace = workspaces.createWorkspace("Notes");
        Path source = source("notes.md", "# Notes\n");
        MaterialRepository failing = new MaterialRepository() {
            @Override
            public void save(Material material) {
                throw new QuizForgeException(ErrorCode.PERSISTENCE_FAILED, "Simulated database failure.");
            }

            @Override
            public List<Material> listByWorkspace(WorkspaceId id) {
                return materialRepository.listByWorkspace(id);
            }

            @Override
            public Optional<Material> findById(MaterialId id) {
                return materialRepository.findById(id);
            }

            @Override
            public void delete(MaterialId id) {
                materialRepository.delete(id);
            }
        };
        MaterialService failingService = new MaterialService(workspaces, failing, storage, CLOCK, 1024);

        assertCode(ErrorCode.PERSISTENCE_FAILED,
                () -> failingService.importMaterial(workspace.id(), source.toString()));
        Path materialDirectory = directory.workspacesDirectory().resolve(workspace.id().toString())
                .resolve("materials");
        try (var entries = Files.list(materialDirectory)) {
            assertEquals(0, entries.count());
        }
        assertTrue(materialRepository.listByWorkspace(workspace.id()).isEmpty());
    }

    @Test
    void restoresDatabaseRecordWhenFileDeletionFails() throws Exception {
        Workspace workspace = workspaces.createWorkspace("Notes");
        Material material = materials.importMaterial(workspace.id(), source("notes.md", "# Notes").toString());
        MaterialFileStorage failingStorage = new MaterialFileStorage() {
            @Override
            public io.quizforge.core.port.StoredMaterialFile store(
                    WorkspaceId workspaceId, MaterialId id, String sourceFile, long maxBytes) {
                return storage.store(workspaceId, id, sourceFile, maxBytes);
            }

            @Override
            public String read(Material value) {
                return storage.read(value);
            }

            @Override
            public void delete(Material value) {
                throw new QuizForgeException(ErrorCode.MATERIAL_STORAGE_FAILED, "Simulated file failure.");
            }
        };
        MaterialService failingService = new MaterialService(workspaces, materialRepository,
                failingStorage, CLOCK, 1024);

        assertCode(ErrorCode.MATERIAL_STORAGE_FAILED, () -> failingService.deleteMaterial(material.id()));
        assertEquals(Optional.of(material), materialRepository.findById(material.id()));
        assertTrue(Files.exists(paths.materialPath(material)));
    }

    @Test
    void appliesFlywayOnceAndEnforcesSqliteForeignKeys() throws Exception {
        assertTrue(Files.exists(directory.databaseFile()));
        new SqliteDatabase(directory);
        try (Connection connection = database.openConnection(); Statement statement = connection.createStatement()) {
            try (var result = statement.executeQuery("SELECT count(*) FROM flyway_schema_history WHERE success = 1")) {
                assertTrue(result.next());
                assertEquals(5, result.getInt(1));
            }
            try (var result = statement.executeQuery("PRAGMA foreign_keys")) {
                assertTrue(result.next());
                assertEquals(1, result.getInt(1));
            }
            assertThrows(SQLException.class, () -> statement.executeUpdate("INSERT INTO material "
                    + "(id, workspace_id, original_file_name, stored_file_name, file_size, status, created_at) "
                    + "VALUES ('one', 'missing', 'a.md', 'one.md', 1, 'IMPORTED', '2026-09-25T00:00:00Z')"));
        }
    }

    @Test
    void rejectsStoredFilenameThatCouldEscapeWorkspace() {
        Workspace workspace = workspaces.createWorkspace("Notes");
        Material invalid = new Material(MaterialId.newId(), workspace.id(), "a.md", "../../outside.md",
                1, MaterialStatus.IMPORTED, CLOCK.instant());
        assertCode(ErrorCode.MATERIAL_STORAGE_FAILED, () -> storage.read(invalid));
    }

    private Path source(String name, String content) throws Exception {
        Path path = temporaryDirectory.resolve(name);
        return Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    private void assertCode(ErrorCode expected, Runnable action) {
        QuizForgeException error = assertThrows(QuizForgeException.class, action::run);
        assertEquals(expected, error.code());
    }
}
