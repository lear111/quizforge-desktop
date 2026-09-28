package io.quizforge.infrastructure.persistence;

import io.quizforge.core.ErrorCode;
import io.quizforge.core.QuizForgeException;
import io.quizforge.core.port.PracticeSessionQuestionRepository;
import io.quizforge.core.practice.PracticePayload;
import io.quizforge.core.practice.PracticeSessionQuestion;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SqlitePracticeSessionQuestionRepository implements PracticeSessionQuestionRepository {
    private final SqliteDatabase database;
    private final PracticePayloadJsonCodec json = new PracticePayloadJsonCodec();

    public SqlitePracticeSessionQuestionRepository(SqliteDatabase database) { this.database = database; }

    @Override public void createAll(List<PracticeSessionQuestion> questions) {
        try (Connection connection = database.openConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO practice_session_question(id, session_id, question_id, question_order,
                        question_type, stem_snapshot, options_snapshot_json, correct_answer_snapshot_json,
                        analysis_snapshot, source_refs_snapshot_json, practice_state, draft_answer_json,
                        created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                for (PracticeSessionQuestion question : questions) {
                    insert.setString(1, question.id());
                    insert.setString(2, question.sessionId());
                    insert.setString(3, question.questionId());
                    insert.setInt(4, question.questionOrder());
                    bindSnapshot(insert, 5, question.snapshot());
                    insert.setString(11, question.practiceState().name());
                    insert.setString(12, json.encode(question.draftAnswer()));
                    insert.setString(13, question.createdAt().toString());
                    insert.setString(14, question.updatedAt().toString());
                    insert.executeUpdate();
                }
                connection.commit();
            } catch (SQLException | RuntimeException error) {
                try { connection.rollback(); } catch (SQLException rollback) { error.addSuppressed(rollback); }
                throw error;
            }
        } catch (SQLException error) { throw failure("create practice questions", error); }
    }

    @Override public List<PracticeSessionQuestion> findBySessionId(String sessionId) {
        try (Connection connection = database.openConnection();
                PreparedStatement select = connection.prepareStatement("""
                        SELECT * FROM practice_session_question WHERE session_id = ? ORDER BY question_order, id
                        """)) {
            select.setString(1, sessionId);
            try (ResultSet rows = select.executeQuery()) {
                List<PracticeSessionQuestion> questions = new ArrayList<>();
                while (rows.next()) questions.add(map(rows));
                return List.copyOf(questions);
            }
        } catch (SQLException error) { throw failure("list practice questions", error); }
    }

    @Override public Optional<PracticeSessionQuestion> findBySessionIdAndQuestionId(String sessionId, String questionId) {
        try (Connection connection = database.openConnection();
                PreparedStatement select = connection.prepareStatement(
                        "SELECT * FROM practice_session_question WHERE session_id = ? AND question_id = ?")) {
            select.setString(1, sessionId);
            select.setString(2, questionId);
            try (ResultSet row = select.executeQuery()) {
                return row.next() ? Optional.of(map(row)) : Optional.empty();
            }
        } catch (SQLException error) { throw failure("read practice question", error); }
    }

    @Override public void updateState(String sessionId, String questionId,
            PracticeSessionQuestion.State state, Instant updatedAt) {
        try (Connection connection = database.openConnection();
                PreparedStatement update = connection.prepareStatement("""
                        UPDATE practice_session_question SET practice_state = ?, updated_at = ?
                        WHERE session_id = ? AND question_id = ?
                        """)) {
            update.setString(1, state.name());
            update.setString(2, updatedAt.toString());
            update.setString(3, sessionId);
            update.setString(4, questionId);
            update.executeUpdate();
        } catch (SQLException error) { throw failure("update practice question state", error); }
    }

    @Override public void updateDraft(String sessionId, String questionId, PracticePayload draft,
            PracticeSessionQuestion.State state, Instant updatedAt) {
        try (Connection connection = database.openConnection();
                PreparedStatement update = connection.prepareStatement("""
                        UPDATE practice_session_question SET draft_answer_json = ?, practice_state = ?, updated_at = ?
                        WHERE session_id = ? AND question_id = ?
                        """)) {
            update.setString(1, json.encode(draft));
            update.setString(2, state.name());
            update.setString(3, updatedAt.toString());
            update.setString(4, sessionId);
            update.setString(5, questionId);
            update.executeUpdate();
        } catch (SQLException error) { throw failure("save practice draft", error); }
    }

    @Override public void updateSnapshot(String sessionId, String questionId, int questionOrder,
            PracticeSessionQuestion.Snapshot snapshot, Instant updatedAt) {
        try (Connection connection = database.openConnection();
                PreparedStatement update = connection.prepareStatement("""
                        UPDATE practice_session_question SET question_order = ?, question_type = ?,
                            stem_snapshot = ?, options_snapshot_json = ?, correct_answer_snapshot_json = ?,
                            analysis_snapshot = ?, source_refs_snapshot_json = ?, updated_at = ?
                        WHERE session_id = ? AND question_id = ?
                        """)) {
            update.setInt(1, questionOrder);
            bindSnapshot(update, 2, snapshot);
            update.setString(8, updatedAt.toString());
            update.setString(9, sessionId);
            update.setString(10, questionId);
            update.executeUpdate();
        } catch (SQLException error) { throw failure("update practice snapshot", error); }
    }

    @Override public void deleteBySessionIdAndQuestionId(String sessionId, String questionId) {
        try (Connection connection = database.openConnection();
                PreparedStatement delete = connection.prepareStatement("""
                        DELETE FROM practice_session_question WHERE session_id = ? AND question_id = ?
                        AND EXISTS (SELECT 1 FROM practice_session WHERE id = session_id AND status = 'ACTIVE')
                        """)) {
            delete.setString(1, sessionId);
            delete.setString(2, questionId);
            delete.executeUpdate();
        } catch (SQLException error) { throw failure("delete active practice question", error); }
    }

    private void bindSnapshot(PreparedStatement statement, int offset,
            PracticeSessionQuestion.Snapshot snapshot) throws SQLException {
        statement.setString(offset, snapshot.questionType());
        statement.setString(offset + 1, snapshot.stem());
        statement.setString(offset + 2, json.encode(snapshot.options()));
        statement.setString(offset + 3, json.encode(snapshot.correctAnswer()));
        statement.setString(offset + 4, snapshot.analysis());
        statement.setString(offset + 5, json.encode(snapshot.sourceRefs()));
    }

    private PracticeSessionQuestion map(ResultSet row) throws SQLException {
        var snapshot = new PracticeSessionQuestion.Snapshot(row.getString("question_type"),
                row.getString("stem_snapshot"), json.decode(row.getString("options_snapshot_json")),
                json.decode(row.getString("correct_answer_snapshot_json")), row.getString("analysis_snapshot"),
                json.decode(row.getString("source_refs_snapshot_json")));
        return new PracticeSessionQuestion(row.getString("id"), row.getString("session_id"),
                row.getString("question_id"), row.getInt("question_order"), snapshot,
                PracticeSessionQuestion.State.valueOf(row.getString("practice_state")),
                json.decode(row.getString("draft_answer_json")), Instant.parse(row.getString("created_at")),
                Instant.parse(row.getString("updated_at")));
    }

    private QuizForgeException failure(String action, SQLException error) {
        return new QuizForgeException(ErrorCode.PERSISTENCE_FAILED, "Could not " + action + ".", error);
    }
}
