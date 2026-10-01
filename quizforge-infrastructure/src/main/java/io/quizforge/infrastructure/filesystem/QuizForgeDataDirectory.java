package io.quizforge.infrastructure.filesystem;

import io.quizforge.core.ErrorCode;
import io.quizforge.core.QuizForgeException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class QuizForgeDataDirectory {
    public static final String OVERRIDE_PROPERTY = "quizforge.dataDir";
    private final Path root;

    public static QuizForgeDataDirectory defaultDirectory() {
        String override = System.getProperty(OVERRIDE_PROPERTY);
        Path path = override == null || override.isBlank()
                ? Path.of(System.getProperty("user.home"), ".quizforge")
                : Path.of(override);
        return new QuizForgeDataDirectory(path);
    }

    public QuizForgeDataDirectory(Path path) {
        try {
            Files.createDirectories(path);
            root = path.toRealPath();
            Files.createDirectories(workspacesDirectory());
        } catch (IOException e) {
            throw new QuizForgeException(ErrorCode.WORKSPACE_STORAGE_FAILED,
                    "Could not create the QuizForge data directory.", e);
        }
    }

    public Path root() {
        return root;
    }

    public Path databaseFile() {
        return root.resolve("quizforge.db");
    }

    public Path workspacesDirectory() {
        return root.resolve("workspaces");
    }
}
