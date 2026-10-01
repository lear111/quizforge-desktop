package io.quizforge.infrastructure.persistence.practice;

import io.quizforge.infrastructure.persistence.SqliteDatabase;
import java.sql.Connection;
import java.sql.SQLException;

/** Standalone repositories own connections; transaction-scoped repositories only borrow them. */
record PracticeConnectionScope(Connection connection, boolean owned) implements AutoCloseable {
    static PracticeConnectionScope open(SqliteDatabase database, Connection transaction) throws SQLException {
        if (transaction == null) return new PracticeConnectionScope(database.openConnection(), true);
        if (transaction.isClosed()) throw new SQLException("Practice transaction is already closed.");
        return new PracticeConnectionScope(transaction, false);
    }

    @Override public void close() throws SQLException {
        if (owned) connection.close();
    }
}
