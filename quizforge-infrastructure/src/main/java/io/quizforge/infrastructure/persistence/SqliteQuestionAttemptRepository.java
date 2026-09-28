package io.quizforge.infrastructure.persistence;

import io.quizforge.core.ErrorCode;
import io.quizforge.core.QuizForgeException;
import io.quizforge.core.port.QuestionAttemptRepository;
import io.quizforge.core.practice.QuestionAttempt;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SqliteQuestionAttemptRepository implements QuestionAttemptRepository {
    private final SqliteDatabase database;
    private final Connection transactionConnection;
    private final PracticePayloadJsonCodec json = new PracticePayloadJsonCodec();

    public SqliteQuestionAttemptRepository(SqliteDatabase database) {
        this.database = database;
        this.transactionConnection = null;
    }

    SqliteQuestionAttemptRepository(Connection transactionConnection) {
        this.database = null;
        this.transactionConnection = transactionConnection;
    }

    @Override public void append(QuestionAttempt attempt) {
        try (var scope = PracticeConnectionScope.open(database, transactionConnection);
                PreparedStatement insert = scope.connection().prepareStatement("""
                        INSERT INTO question_attempt(id, session_question_id, attempt_no, attempt_mode,
                            answer_json, result, score, max_score, submitted_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)) {
            insert.setString(1, attempt.id());
            insert.setString(2, attempt.sessionQuestionId());
            insert.setInt(3, attempt.attemptNo());
            insert.setString(4, attempt.attemptMode().name());
            insert.setString(5, json.encode(attempt.answer()));
            insert.setString(6, attempt.result().name());
            if (attempt.score() == null) insert.setNull(7, Types.REAL);
            else insert.setDouble(7, attempt.score());
            if (attempt.maxScore() == null) insert.setNull(8, Types.REAL);
            else insert.setDouble(8, attempt.maxScore());
            insert.setString(9, attempt.submittedAt().toString());
            insert.executeUpdate();
        } catch (SQLException error) { throw failure("append question attempt", error); }
    }

    @Override public List<QuestionAttempt> listBySessionQuestion(String sessionQuestionId) {
        try (var scope = PracticeConnectionScope.open(database, transactionConnection);
                PreparedStatement select = scope.connection().prepareStatement(
                        "SELECT * FROM question_attempt WHERE session_question_id = ? ORDER BY attempt_no ASC")) {
            select.setString(1, sessionQuestionId);
            try (ResultSet rows = select.executeQuery()) {
                List<QuestionAttempt> attempts = new ArrayList<>();
                while (rows.next()) attempts.add(map(rows));
                return List.copyOf(attempts);
            }
        } catch (SQLException error) { throw failure("list question attempts", error); }
    }

    @Override public Optional<QuestionAttempt> findLatest(String sessionQuestionId) {
        try (var scope = PracticeConnectionScope.open(database, transactionConnection);
                PreparedStatement select = scope.connection().prepareStatement("""
                        SELECT * FROM question_attempt WHERE session_question_id = ? ORDER BY attempt_no DESC LIMIT 1
                        """)) {
            select.setString(1, sessionQuestionId);
            try (ResultSet row = select.executeQuery()) {
                return row.next() ? Optional.of(map(row)) : Optional.empty();
            }
        } catch (SQLException error) { throw failure("read latest question attempt", error); }
    }

    @Override public long countBySessionQuestion(String sessionQuestionId) {
        return aggregate("SELECT count(*) FROM question_attempt WHERE session_question_id = ?", sessionQuestionId);
    }

    @Override public int nextAttemptNo(String sessionQuestionId) {
        return Math.toIntExact(aggregate("SELECT coalesce(max(attempt_no), 0) + 1 FROM question_attempt "
                + "WHERE session_question_id = ?", sessionQuestionId));
    }

    private long aggregate(String sql, String sessionQuestionId) {
        try (var scope = PracticeConnectionScope.open(database, transactionConnection);
                PreparedStatement select = scope.connection().prepareStatement(sql)) {
            select.setString(1, sessionQuestionId);
            try (ResultSet row = select.executeQuery()) {
                row.next();
                return row.getLong(1);
            }
        } catch (SQLException error) { throw failure("count question attempts", error); }
    }

    private QuestionAttempt map(ResultSet row) throws SQLException {
        double score = row.getDouble("score");
        Double nullableScore = row.wasNull() ? null : score;
        double maxScore = row.getDouble("max_score");
        Double nullableMaxScore = row.wasNull() ? null : maxScore;
        return new QuestionAttempt(row.getString("id"), row.getString("session_question_id"),
                row.getInt("attempt_no"), QuestionAttempt.Mode.valueOf(row.getString("attempt_mode")),
                json.decode(row.getString("answer_json")), QuestionAttempt.Result.valueOf(row.getString("result")),
                nullableScore, nullableMaxScore, Instant.parse(row.getString("submitted_at")));
    }

    private QuizForgeException failure(String action, SQLException error) {
        return new QuizForgeException(ErrorCode.PERSISTENCE_FAILED, "Could not " + action + ".", error);
    }
}
