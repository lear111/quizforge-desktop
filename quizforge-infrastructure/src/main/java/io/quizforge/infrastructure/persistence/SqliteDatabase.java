package io.quizforge.infrastructure.persistence;

import io.quizforge.core.ErrorCode;
import io.quizforge.core.QuizForgeException;
import io.quizforge.infrastructure.filesystem.QuizForgeDataDirectory;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.sqlite.SQLiteConfig;

public final class SqliteDatabase {
    private final String jdbcUrl;

    public SqliteDatabase(QuizForgeDataDirectory directory) {
        jdbcUrl = "jdbc:sqlite:" + directory.databaseFile();
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
}
