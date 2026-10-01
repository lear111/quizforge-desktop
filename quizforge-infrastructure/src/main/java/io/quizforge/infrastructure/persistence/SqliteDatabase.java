package io.quizforge.infrastructure.persistence;

import io.quizforge.core.ErrorCode;
import io.quizforge.core.QuizForgeException;
import io.quizforge.infrastructure.filesystem.QuizForgeDataDirectory;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.sqlite.SQLiteConfig;

public final class SqliteDatabase {
    private final String jdbcUrl;

    public SqliteDatabase(QuizForgeDataDirectory directory) {
        this(directory.databaseFile());
    }

    /** Explicit database location, also used by the Workspace-scoped durable practice store. */
    public SqliteDatabase(Path databaseFile) {
        jdbcUrl = "jdbc:sqlite:" + databaseFile;
        try {
            Flyway.configure().dataSource(jdbcUrl, "", "").load().migrate();
        } catch (RuntimeException e) {
            throw new QuizForgeException(ErrorCode.PERSISTENCE_FAILED,
                    "Could not initialize the QuizForge database.", e);
        }
    }

    public Connection openConnection() throws SQLException {
        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        return DriverManager.getConnection(jdbcUrl, config.toProperties());
    }

    public Connection openPracticeTransactionConnection() throws SQLException {
        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        config.setTransactionMode(SQLiteConfig.TransactionMode.IMMEDIATE);
        return DriverManager.getConnection(jdbcUrl, config.toProperties());
    }
}
