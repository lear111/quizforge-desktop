package io.quizforge.infrastructure.filesystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.quizforge.core.QuizForgeException;
import io.quizforge.core.asset.WorkspaceScanResult;
import io.quizforge.core.port.WorkspaceAssetScanner;
import io.quizforge.core.workspace.WorkspaceService;
import io.quizforge.infrastructure.persistence.SqliteDatabase;
import io.quizforge.infrastructure.persistence.SqliteWorkspaceRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MarkdownRegistrationWriteFailureTest {
    @TempDir Path temporary;

    @Test void failedPublishLeavesOriginalMarkdownAndNoTemporaryFiles() throws Exception {
        var data = new QuizForgeDataDirectory(temporary.resolve("data"));
        var paths = new WorkspacePathResolver(data);
        var workspace = new WorkspaceService(
                new SqliteWorkspaceRepository(new SqliteDatabase(data)), paths, Clock.systemUTC())
                .createWorkspace("Write failure");
        Path file = paths.workspaceRoot(workspace.id()).resolve("documents/study.md");
        String original = "# Original\r\n\r\nExact body.\r\n";
        Files.writeString(file, original);
        WorkspaceAssetScanner scanner = id -> new WorkspaceScanResult(List.of(), List.of());
        var service = new MarkdownDocumentRegistrationService(paths, scanner,
                (candidate, target) -> { throw new IOException("Simulated publish failure"); });

        assertThrows(QuizForgeException.class,
                () -> service.register(workspace.id(), "documents/study.md"));
        assertEquals(original, Files.readString(file));
        try (var files = Files.list(file.getParent())) {
            assertEquals(List.of(file), files.toList());
        }
    }
}
