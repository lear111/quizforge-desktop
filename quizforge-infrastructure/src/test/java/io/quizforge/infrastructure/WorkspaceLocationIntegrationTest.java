package io.quizforge.infrastructure;

import io.quizforge.core.workspace.model.WorkspaceFileType;
import io.quizforge.core.workspace.service.WorkspaceService;
import io.quizforge.infrastructure.filesystem.QuizForgeDataDirectory;
import io.quizforge.infrastructure.filesystem.workspace.LocalWorkspaceFileOperations;
import io.quizforge.infrastructure.filesystem.workspace.WorkspaceManifestStore;
import io.quizforge.infrastructure.filesystem.workspace.WorkspacePathResolver;
import io.quizforge.infrastructure.persistence.SqliteDatabase;
import io.quizforge.infrastructure.persistence.SqliteWorkspaceRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class WorkspaceLocationIntegrationTest {
    @TempDir Path temporary;

    @Test void unavailableWorkspaceDoesNotBlockListingOrRecreateItsDirectory() throws Exception {
        var data=new QuizForgeDataDirectory(temporary.resolve("listing-data"));
        var repository=new SqliteWorkspaceRepository(new SqliteDatabase(data));
        var paths=new WorkspacePathResolver(data,repository);
        var service=new WorkspaceService(repository,paths,Clock.systemUTC());
        var unavailable=service.createWorkspace("Unavailable");
        var available=service.createWorkspace("Available");
        Path original=paths.workspaceRoot(unavailable.id());
        Files.move(original,temporary.resolve("moved-workspace"));
        assertEquals(2,service.listWorkspaces().size());
        assertFalse(Files.exists(original));
        assertEquals(available.id(),service.getWorkspace(available.id()).id());
        assertFalse(Files.exists(original));
    }

    @Test void createsWorkspaceInSelectedFolderAndReopensItByRegisteredPath() throws Exception {
        var data = new QuizForgeDataDirectory(temporary.resolve("app-data"));
        var repository = new SqliteWorkspaceRepository(new SqliteDatabase(data));
        var paths = new WorkspacePathResolver(data, repository);
        var service = new WorkspaceService(repository, paths, Clock.systemUTC());
        Path selectedParent = Files.createDirectory(temporary.resolve("my-study-folders"));

        var workspace = service.createWorkspace("Java 学习", selectedParent);
        Path expected = selectedParent.resolve("Java 学习").toRealPath();
        assertEquals(expected, paths.workspaceRoot(workspace.id()));
        assertEquals(expected, repository.findById(workspace.id()).orElseThrow().rootPath());
        assertEquals(workspace.id(), new WorkspaceManifestStore().readId(expected));
        assertTrue(Files.isDirectory(expected.resolve("sources")));
        assertTrue(Files.isDirectory(expected.resolve("documents")));
        assertTrue(Files.isDirectory(expected.resolve("question-banks")));
        assertTrue(Files.isRegularFile(expected.resolve(".quizforge/workspace.db")));

        var reopened = new WorkspacePathResolver(data,
                new SqliteWorkspaceRepository(new SqliteDatabase(data)));
        assertEquals(expected, reopened.workspaceRoot(workspace.id()));
        assertEquals("notes.md", new LocalWorkspaceFileOperations(reopened)
                .createFile(workspace.id(), "", "notes", WorkspaceFileType.MARKDOWN));
        assertTrue(Files.isRegularFile(expected.resolve("notes.md")));
        assertEquals(workspace.id(), service.getWorkspace(workspace.id()).id());
    }

    @Test void existingDestinationIsNeverReusedOrOverwritten() throws Exception {
        var data = new QuizForgeDataDirectory(temporary.resolve("app-data"));
        var repository = new SqliteWorkspaceRepository(new SqliteDatabase(data));
        var service = new WorkspaceService(repository, new WorkspacePathResolver(data, repository), Clock.systemUTC());
        Path parent = Files.createDirectory(temporary.resolve("selected"));
        Path existing = Files.createDirectory(parent.resolve("Java"));
        Files.writeString(existing.resolve("keep.md"), "keep");

        assertThrows(RuntimeException.class, () -> service.createWorkspace("Java", parent));
        assertEquals("keep", Files.readString(existing.resolve("keep.md")));
        assertFalse(Files.exists(existing.resolve(".quizforge")));
        assertTrue(repository.list().isEmpty());
    }

    @Test void opensAnExistingWorkspaceFolderWithoutReplacingItsFiles() throws Exception {
        var sourceData = new QuizForgeDataDirectory(temporary.resolve("source-app"));
        var sourceRepository = new SqliteWorkspaceRepository(new SqliteDatabase(sourceData));
        var sourceService = new WorkspaceService(sourceRepository,
                new WorkspacePathResolver(sourceData, sourceRepository), Clock.systemUTC());
        var source = sourceService.createWorkspace("Imported Notes", temporary);
        Path root = temporary.resolve("Imported Notes").toRealPath();
        Files.writeString(root.resolve("study.md"), "# Keep this file\n");

        var targetData = new QuizForgeDataDirectory(temporary.resolve("target-app"));
        var targetRepository = new SqliteWorkspaceRepository(new SqliteDatabase(targetData));
        var targetPaths = new WorkspacePathResolver(targetData, targetRepository);
        var targetService = new WorkspaceService(targetRepository, targetPaths, Clock.systemUTC());
        var opened = targetService.registerExistingWorkspace(root);

        assertEquals(source.id(), opened.id());
        assertEquals(root, opened.rootPath());
        assertEquals("Imported Notes", opened.name());
        assertEquals("# Keep this file\n", Files.readString(root.resolve("study.md")));
        assertEquals(root, targetPaths.workspaceRoot(source.id()));
        assertEquals(1, targetService.listWorkspaces().size());
        assertEquals(source.id(), targetService.registerExistingWorkspace(root).id());
        assertEquals(1, targetRepository.list().size());
    }

    @Test void openingAnOrdinaryFolderDoesNotRegisterOrModifyIt() throws Exception {
        Path ordinary = Files.createDirectory(temporary.resolve("ordinary"));
        Files.writeString(ordinary.resolve("notes.md"), "# Notes\n");
        var data = new QuizForgeDataDirectory(temporary.resolve("app-data"));
        var repository = new SqliteWorkspaceRepository(new SqliteDatabase(data));
        var service = new WorkspaceService(repository,
                new WorkspacePathResolver(data, repository), Clock.systemUTC());

        assertThrows(RuntimeException.class, () -> service.registerExistingWorkspace(ordinary));
        assertTrue(repository.list().isEmpty());
        assertEquals("# Notes\n", Files.readString(ordinary.resolve("notes.md")));
        assertFalse(Files.exists(ordinary.resolve(".quizforge")));
    }
}
