package io.quizforge.infrastructure.persistence;

import io.quizforge.core.ErrorCode;
import io.quizforge.core.QuizForgeException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.sqlite.SQLiteConfig;

/** The disposable, rebuildable index database inside one workspace. */
public final class WorkspaceAssetDatabase {
    private final String jdbcUrl;

    public WorkspaceAssetDatabase(Path workspaceRoot) {
        Path internal = workspaceRoot.resolve(".quizforge");
        Path database = internal.resolve("workspace.db");
        if (!Files.isDirectory(internal) || Files.isSymbolicLink(internal)
                || Files.isSymbolicLink(database)) {
            throw new QuizForgeException(ErrorCode.WORKSPACE_STORAGE_FAILED,
                    "Workspace asset database path is invalid.");
        }
        jdbcUrl = "jdbc:sqlite:" + database;
        try {
            Flyway.configure().dataSource(jdbcUrl, "", "")
                    .locations("classpath:db/workspace-migration").load().migrate();
        } catch (RuntimeException error) {
            throw new QuizForgeException(ErrorCode.PERSISTENCE_FAILED,
                    "Could not initialize workspace asset index.", error);
        }
    }

    public Connection openConnection() throws SQLException {
        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        return DriverManager.getConnection(jdbcUrl, config.toProperties());
    }
}
