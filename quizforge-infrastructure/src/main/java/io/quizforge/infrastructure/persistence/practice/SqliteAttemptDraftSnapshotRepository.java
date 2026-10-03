package io.quizforge.infrastructure.persistence.practice;

import io.quizforge.core.ErrorCode;
import io.quizforge.core.QuizForgeException;
import io.quizforge.core.port.AttemptDraftSnapshotRepository;
import io.quizforge.core.practice.draft.AttemptDraftSnapshot;
import io.quizforge.infrastructure.persistence.SqliteDatabase;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

public final class SqliteAttemptDraftSnapshotRepository implements AttemptDraftSnapshotRepository {
    private final SqliteDatabase database;
    private final Connection transactionConnection;
    private final DraftCanvasJsonCodec codec = new DraftCanvasJsonCodec();
    public SqliteAttemptDraftSnapshotRepository(SqliteDatabase database) { this.database = database; transactionConnection = null; }
    SqliteAttemptDraftSnapshotRepository(Connection connection) { database = null; transactionConnection = connection; }
    @Override public Optional<AttemptDraftSnapshot> find(String id) {
        try (var scope = PracticeConnectionScope.open(database, transactionConnection);
                var statement = scope.connection().prepareStatement("SELECT * FROM attempt_draft_snapshot WHERE attempt_id = ?")) {
            statement.setString(1, id);
            try (var row = statement.executeQuery()) {
                return row.next() ? Optional.of(new AttemptDraftSnapshot(id, DraftCanvasJsonCodec.decode(row.getString("document_json")),
                        Instant.parse(row.getString("created_at")))) : Optional.empty();
            }
        } catch (SQLException e) { throw failure(e); }
    }
    @Override public void append(AttemptDraftSnapshot snapshot) {
        try (var scope = PracticeConnectionScope.open(database, transactionConnection);
                var statement = scope.connection().prepareStatement("INSERT INTO attempt_draft_snapshot(attempt_id, document_json, created_at) VALUES (?, ?, ?)")) {
            statement.setString(1, snapshot.attemptId()); statement.setString(2, codec.encode(snapshot.document()));
            statement.setString(3, snapshot.createdAt().toString()); statement.executeUpdate();
        } catch (SQLException e) { throw failure(e); }
    }
    private QuizForgeException failure(SQLException e) { return new QuizForgeException(ErrorCode.PERSISTENCE_FAILED, "Could not append/read frozen DraftSnapshot.", e); }
}
