package io.quizforge.infrastructure.persistence;

import io.quizforge.core.ErrorCode;
import io.quizforge.core.QuizForgeException;
import io.quizforge.core.port.PracticeSessionRepository;
import io.quizforge.core.practice.PracticeSession;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SqlitePracticeSessionRepository implements PracticeSessionRepository {
    // Fixed fractional width preserves true chronological order in SQLite TEXT, including nanoseconds.
    private static final DateTimeFormatter UTC = new DateTimeFormatterBuilder().appendInstant(9).toFormatter();
    private final SqliteDatabase database;

    public SqlitePracticeSessionRepository(SqliteDatabase database) { this.database = database; }

    @Override public void create(PracticeSession session) {
        try (Connection connection = database.openConnection();
                PreparedStatement insert = connection.prepareStatement("""
                        INSERT INTO practice_session(id, question_bank_asset_id, question_bank_content_id,
                            bank_title_snapshot, status, current_view, current_question_id,
                            started_at, last_activity_at, archived_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)) {
            insert.setString(1, session.id());
            insert.setString(2, session.questionBankAssetId());
            insert.setString(3, session.questionBankContentId());
            insert.setString(4, session.bankTitleSnapshot());
            insert.setString(5, session.status().name());
            insert.setString(6, session.currentView().name());
            insert.setString(7, session.currentQuestionId());
            insert.setString(8, UTC.format(session.startedAt()));
            insert.setString(9, UTC.format(session.lastActivityAt()));
            insert.setString(10, session.archivedAt() == null ? null : UTC.format(session.archivedAt()));
            insert.executeUpdate();
        } catch (SQLException error) { throw failure("create practice session", error); }
    }

    @Override public Optional<PracticeSession> findById(String sessionId) {
        return findOne("SELECT * FROM practice_session WHERE id = ?", sessionId);
    }

    @Override public Optional<PracticeSession> findActiveByQuestionBankAssetId(String assetId) {
        return findOne("SELECT * FROM practice_session WHERE question_bank_asset_id = ? AND status = 'ACTIVE'", assetId);
    }

    private Optional<PracticeSession> findOne(String sql, String id) {
        try (Connection connection = database.openConnection();
                PreparedStatement select = connection.prepareStatement(sql)) {
            select.setString(1, id);
            try (ResultSet row = select.executeQuery()) {
                return row.next() ? Optional.of(map(row)) : Optional.empty();
            }
        } catch (SQLException error) { throw failure("read practice session", error); }
    }

    @Override public List<PracticeSession> listArchivedByQuestionBankAssetId(String assetId) {
        try (Connection connection = database.openConnection();
                PreparedStatement select = connection.prepareStatement("""
                        SELECT * FROM practice_session WHERE question_bank_asset_id = ? AND status = 'ARCHIVED'
                        ORDER BY archived_at DESC, id
                        """)) {
            select.setString(1, assetId);
            try (ResultSet rows = select.executeQuery()) {
                List<PracticeSession> sessions = new ArrayList<>();
                while (rows.next()) sessions.add(map(rows));
                return List.copyOf(sessions);
            }
        } catch (SQLException error) { throw failure("list archived practice sessions", error); }
    }

    @Override public void updateCurrentPosition(String sessionId, PracticeSession.View view, String questionId) {
        try (Connection connection = database.openConnection();
                PreparedStatement update = connection.prepareStatement("""
                        UPDATE practice_session SET current_view = ?, current_question_id = ?
                        WHERE id = ? AND status = 'ACTIVE'
                        """)) {
            update.setString(1, view.name());
            update.setString(2, questionId);
            update.setString(3, sessionId);
            update.executeUpdate();
        } catch (SQLException error) { throw failure("update practice position", error); }
    }

    @Override public void touch(String sessionId, Instant lastActivityAt) {
        try (Connection connection = database.openConnection();
                PreparedStatement update = connection.prepareStatement("""
                        UPDATE practice_session SET last_activity_at = ? WHERE id = ? AND status = 'ACTIVE'
                        """)) {
            update.setString(1, UTC.format(lastActivityAt));
            update.setString(2, sessionId);
            update.executeUpdate();
        } catch (SQLException error) { throw failure("touch practice session", error); }
    }

    @Override public void archive(String sessionId, Instant archivedAt) {
        try (Connection connection = database.openConnection();
                PreparedStatement update = connection.prepareStatement("""
                        UPDATE practice_session SET status = 'ARCHIVED', archived_at = ?, last_activity_at = ?
                        WHERE id = ? AND status = 'ACTIVE'
                        """)) {
            update.setString(1, UTC.format(archivedAt));
            update.setString(2, UTC.format(archivedAt));
            update.setString(3, sessionId);
            update.executeUpdate();
        } catch (SQLException error) { throw failure("archive practice session", error); }
    }

    @Override public void deleteArchived(String sessionId) {
        try (Connection connection = database.openConnection();
                PreparedStatement delete = connection.prepareStatement(
                        "DELETE FROM practice_session WHERE id = ? AND status = 'ARCHIVED'")) {
            delete.setString(1, sessionId);
            delete.executeUpdate();
        } catch (SQLException error) { throw failure("delete archived practice session", error); }
    }

    private PracticeSession map(ResultSet row) throws SQLException {
        String archivedAt = row.getString("archived_at");
        return new PracticeSession(row.getString("id"), row.getString("question_bank_asset_id"),
                row.getString("question_bank_content_id"), row.getString("bank_title_snapshot"),
                PracticeSession.Status.valueOf(row.getString("status")),
                PracticeSession.View.valueOf(row.getString("current_view")), row.getString("current_question_id"),
                Instant.parse(row.getString("started_at")), Instant.parse(row.getString("last_activity_at")),
                archivedAt == null ? null : Instant.parse(archivedAt));
    }

    private QuizForgeException failure(String action, SQLException error) {
        return new QuizForgeException(ErrorCode.PERSISTENCE_FAILED, "Could not " + action + ".", error);
    }
}
