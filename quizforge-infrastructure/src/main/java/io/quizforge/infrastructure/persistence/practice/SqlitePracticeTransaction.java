package io.quizforge.infrastructure.persistence.practice;

import io.quizforge.core.ErrorCode;
import io.quizforge.core.QuizForgeException;
import io.quizforge.core.port.PracticeTransaction;
import io.quizforge.infrastructure.persistence.SqliteDatabase;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Function;

/** One connection/transaction across all practice repositories, including reads of restored state. */
public final class SqlitePracticeTransaction implements PracticeTransaction {
    private final SqliteDatabase database;

    public SqlitePracticeTransaction(SqliteDatabase database) { this.database = database; }

    @Override public <T> T execute(Function<Repositories, T> operation) {
        try (Connection connection = database.openPracticeTransactionConnection()) {
            // IMMEDIATE acquires the writer reservation before lookup, serializing concurrent opens.
            connection.setAutoCommit(false);
            try {
                var repositories = new Repositories(new SqlitePracticeSessionRepository(connection),
                        new SqlitePracticeSessionQuestionRepository(connection), new SqliteQuestionAttemptRepository(connection),
                        new SqliteActiveDraftCanvasRepository(connection), new SqliteAttemptDraftSnapshotRepository(connection));
                T result = operation.apply(repositories);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException | Error failure) {
                try { connection.rollback(); } catch (SQLException rollback) { failure.addSuppressed(rollback); }
                throw failure;
            }
        } catch (SQLException error) {
            throw new QuizForgeException(ErrorCode.PERSISTENCE_FAILED, "Could not execute practice transaction.", error);
        }
    }
}
