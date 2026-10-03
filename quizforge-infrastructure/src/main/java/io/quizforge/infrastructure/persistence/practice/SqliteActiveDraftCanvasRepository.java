package io.quizforge.infrastructure.persistence.practice;

import io.quizforge.core.ErrorCode;
import io.quizforge.core.QuizForgeException;
import io.quizforge.core.port.ActiveDraftCanvasRepository;
import io.quizforge.core.practice.draft.ActiveDraftCanvas;
import io.quizforge.infrastructure.persistence.SqliteDatabase;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

public final class SqliteActiveDraftCanvasRepository implements ActiveDraftCanvasRepository {
    private final SqliteDatabase database;
    private final Connection transactionConnection;
    private final DraftCanvasJsonCodec codec = new DraftCanvasJsonCodec();
    public SqliteActiveDraftCanvasRepository(SqliteDatabase database) { this.database = database; transactionConnection = null; }
    SqliteActiveDraftCanvasRepository(Connection connection) { database = null; transactionConnection = connection; }
    @Override public Optional<ActiveDraftCanvas> find(String id) {
        try (var scope = PracticeConnectionScope.open(database, transactionConnection);
                var statement = scope.connection().prepareStatement("SELECT * FROM practice_draft_canvas WHERE session_question_id = ?")) {
            statement.setString(1, id);
            try (var row = statement.executeQuery()) {
                return row.next() ? Optional.of(new ActiveDraftCanvas(id, DraftCanvasJsonCodec.decode(row.getString("document_json")),
                        Instant.parse(row.getString("updated_at")))) : Optional.empty();
            }
        } catch (SQLException e) { throw failure(e); }
    }
    @Override public void save(ActiveDraftCanvas draft) {
        try (var scope = PracticeConnectionScope.open(database, transactionConnection);
                var statement = scope.connection().prepareStatement("""
                    INSERT INTO practice_draft_canvas(session_question_id, document_json, updated_at) VALUES (?, ?, ?)
                    ON CONFLICT(session_question_id) DO UPDATE SET document_json=excluded.document_json, updated_at=excluded.updated_at
                    """)) {
            statement.setString(1, draft.sessionQuestionId()); statement.setString(2, codec.encode(draft.document()));
            statement.setString(3, draft.updatedAt().toString()); statement.executeUpdate();
        } catch (SQLException e) { throw failure(e); }
    }
    @Override public void delete(String id) {
        try (var scope = PracticeConnectionScope.open(database, transactionConnection);
                var statement = scope.connection().prepareStatement("DELETE FROM practice_draft_canvas WHERE session_question_id = ?")) {
            statement.setString(1, id); statement.executeUpdate();
        } catch (SQLException e) { throw failure(e); }
    }
    private QuizForgeException failure(SQLException e) { return new QuizForgeException(ErrorCode.PERSISTENCE_FAILED, "Could not persist active Draft Canvas.", e); }
}
