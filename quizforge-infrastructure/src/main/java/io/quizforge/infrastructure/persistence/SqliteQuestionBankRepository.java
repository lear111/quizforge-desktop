package io.quizforge.infrastructure.persistence;

import io.quizforge.core.ErrorCode;
import io.quizforge.core.QuizForgeException;
import io.quizforge.core.document.StandardDocumentId;
import io.quizforge.core.port.QuestionBankRepository;
import io.quizforge.core.question.GenerationScopeType;
import io.quizforge.core.question.StoredQuestion;
import io.quizforge.core.question.StoredQuestionBank;
import io.quizforge.core.question.QuestionBankId;
import io.quizforge.core.question.QuestionId;
import io.quizforge.core.question.QuestionOption;
import io.quizforge.core.question.QuestionType;
import io.quizforge.core.workspace.WorkspaceId;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class SqliteQuestionBankRepository implements QuestionBankRepository {
    private final SqliteDatabase database;

    public SqliteQuestionBankRepository(SqliteDatabase database) { this.database = database; }

    @Override
    public Optional<StoredQuestionBank> findByWorkspace(WorkspaceId workspaceId) {
        try (Connection connection = database.openConnection();
                PreparedStatement select = connection.prepareStatement("SELECT * FROM question_bank WHERE workspace_id = ?")) {
            select.setString(1, workspaceId.toString());
            try (ResultSet row = select.executeQuery()) {
                if (!row.next()) return Optional.empty();
                QuestionBankId bankId = QuestionBankId.parse(row.getString("id"));
                List<StoredQuestion> questions = readQuestions(connection, bankId);
                return Optional.of(new StoredQuestionBank(bankId, workspaceId,
                        StandardDocumentId.parse(row.getString("source_document_id")), row.getString("name"),
                        GenerationScopeType.valueOf(row.getString("generation_scope_type")),
                        row.getString("source_chapter"), row.getString("source_section"),
                        row.getInt("requested_question_count"), Instant.parse(row.getString("created_at")),
                        Instant.parse(row.getString("updated_at")), Instant.parse(row.getString("generated_at")), questions));
            }
        } catch (SQLException error) { throw failure("read", error); }
    }

    private List<StoredQuestion> readQuestions(Connection connection, QuestionBankId bankId) throws SQLException {
        List<StoredQuestion> questions = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT * FROM question WHERE question_bank_id = ? ORDER BY sort_order")) {
            select.setString(1, bankId.toString());
            try (ResultSet row = select.executeQuery()) {
                while (row.next()) {
                    QuestionId questionId = QuestionId.parse(row.getString("id"));
                    questions.add(new StoredQuestion(questionId, bankId, QuestionType.valueOf(row.getString("type")),
                            row.getString("stem"), row.getString("analysis"), row.getString("source_chapter"),
                            row.getString("source_section"), row.getInt("sort_order"),
                            Instant.parse(row.getString("created_at")), readOptions(connection, questionId)));
                }
            }
        }
        return questions;
    }

    private List<QuestionOption> readOptions(Connection connection, QuestionId questionId) throws SQLException {
        List<QuestionOption> options = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT * FROM question_option WHERE question_id = ? ORDER BY sort_order")) {
            select.setString(1, questionId.toString());
            try (ResultSet row = select.executeQuery()) {
                while (row.next()) {
                    options.add(new QuestionOption(UUID.fromString(row.getString("id")), questionId,
                            row.getString("option_key"), row.getString("content"),
                            row.getInt("correct") == 1, row.getInt("sort_order")));
                }
            }
        }
        return options;
    }

    @Override
    public void replace(StoredQuestionBank bank) {
        try (Connection connection = database.openConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement delete = connection.prepareStatement("DELETE FROM question_bank WHERE workspace_id = ?")) {
                    delete.setString(1, bank.workspaceId().toString());
                    delete.executeUpdate();
                }
                insertBank(connection, bank);
                insertQuestions(connection, bank.questions());
                connection.commit();
            } catch (SQLException | RuntimeException error) {
                try { connection.rollback(); } catch (SQLException rollback) { error.addSuppressed(rollback); }
                throw error;
            }
        } catch (SQLException error) { throw failure("replace", error); }
    }

    private void insertBank(Connection connection, StoredQuestionBank bank) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO question_bank VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            insert.setString(1, bank.id().toString());
            insert.setString(2, bank.workspaceId().toString());
            insert.setString(3, bank.sourceDocumentId().toString());
            insert.setString(4, bank.name());
            insert.setString(5, bank.generationScopeType().name());
            insert.setString(6, bank.sourceChapter());
            insert.setString(7, bank.sourceSection());
            insert.setInt(8, bank.requestedQuestionCount());
            insert.setString(9, bank.createdAt().toString());
            insert.setString(10, bank.updatedAt().toString());
            insert.setString(11, bank.generatedAt().toString());
            insert.executeUpdate();
        }
    }

    private void insertQuestions(Connection connection, List<StoredQuestion> questions) throws SQLException {
        try (PreparedStatement insertQuestion = connection.prepareStatement("INSERT INTO question VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)");
                PreparedStatement insertOption = connection.prepareStatement("INSERT INTO question_option VALUES (?, ?, ?, ?, ?, ?)")) {
            for (StoredQuestion question : questions) {
                insertQuestion.setString(1, question.id().toString());
                insertQuestion.setString(2, question.questionBankId().toString());
                insertQuestion.setString(3, question.type().name());
                insertQuestion.setString(4, question.stem());
                insertQuestion.setString(5, question.analysis());
                insertQuestion.setString(6, question.sourceChapter());
                insertQuestion.setString(7, question.sourceSection());
                insertQuestion.setInt(8, question.sortOrder());
                insertQuestion.setString(9, question.createdAt().toString());
                insertQuestion.executeUpdate();
                for (QuestionOption option : question.options()) {
                    insertOption.setString(1, option.id().toString());
                    insertOption.setString(2, option.questionId().toString());
                    insertOption.setString(3, option.key());
                    insertOption.setString(4, option.content());
                    insertOption.setInt(5, option.correct() ? 1 : 0);
                    insertOption.setInt(6, option.sortOrder());
                    insertOption.executeUpdate();
                }
            }
        }
    }

    private QuizForgeException failure(String action, SQLException error) {
        return new QuizForgeException(ErrorCode.PERSISTENCE_FAILED, "Could not " + action + " question bank.", error);
    }
}
