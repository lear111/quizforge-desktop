package io.quizforge.infrastructure.persistence;

import io.quizforge.core.ErrorCode;
import io.quizforge.core.QuizForgeException;
import io.quizforge.core.document.StandardDocument;
import io.quizforge.core.document.StandardDocumentId;
import io.quizforge.core.document.StandardDocumentStatus;
import io.quizforge.core.material.MaterialId;
import io.quizforge.core.port.StandardDocumentRepository;
import io.quizforge.core.workspace.WorkspaceId;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SqliteStandardDocumentRepository implements StandardDocumentRepository {
    private final SqliteDatabase database;

    public SqliteStandardDocumentRepository(SqliteDatabase database) {
        this.database = database;
    }

    @Override
    public Optional<StandardDocument> findByWorkspace(WorkspaceId workspaceId) {
        String sql = "SELECT id, workspace_id, title, format_id, format_version, file_name, "
                + "status, created_at, updated_at FROM standard_document WHERE workspace_id = ?";
        try (Connection connection = database.openConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, workspaceId.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                StandardDocumentId id = StandardDocumentId.parse(result.getString("id"));
                List<MaterialId> sources = new ArrayList<>();
                try (PreparedStatement sourceQuery = connection.prepareStatement(
                        "SELECT material_id FROM standard_document_material "
                        + "WHERE standard_document_id = ? ORDER BY rowid")) {
                    sourceQuery.setString(1, id.toString());
                    try (ResultSet sourceRows = sourceQuery.executeQuery()) {
                        while (sourceRows.next()) {
                            sources.add(MaterialId.parse(sourceRows.getString(1)));
                        }
                    }
                }
                return Optional.of(new StandardDocument(id,
                        WorkspaceId.parse(result.getString("workspace_id")),
                        result.getString("title"), result.getString("format_id"),
                        result.getString("format_version"), result.getString("file_name"),
                        StandardDocumentStatus.valueOf(result.getString("status")),
                        Instant.parse(result.getString("created_at")),
                        Instant.parse(result.getString("updated_at")), sources));
            }
        } catch (SQLException e) {
            throw failure("load", e);
        }
    }

    @Override
    public void save(StandardDocument document) {
        String upsert = "INSERT INTO standard_document(id, workspace_id, title, format_id, "
                + "format_version, file_name, status, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT(workspace_id) DO UPDATE SET "
                + "title=excluded.title, format_id=excluded.format_id, "
                + "format_version=excluded.format_version, file_name=excluded.file_name, "
                + "status=excluded.status, updated_at=excluded.updated_at";
        try (Connection connection = database.openConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement(upsert)) {
                    statement.setString(1, document.id().toString());
                    statement.setString(2, document.workspaceId().toString());
                    statement.setString(3, document.title());
                    statement.setString(4, document.formatId());
                    statement.setString(5, document.formatVersion());
                    statement.setString(6, document.fileName());
                    statement.setString(7, document.status().name());
                    statement.setString(8, document.createdAt().toString());
                    statement.setString(9, document.updatedAt().toString());
                    statement.executeUpdate();
                }
                try (PreparedStatement delete = connection.prepareStatement(
                        "DELETE FROM standard_document_material WHERE standard_document_id = ?")) {
                    delete.setString(1, document.id().toString());
                    delete.executeUpdate();
                }
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO standard_document_material(standard_document_id, material_id) "
                        + "VALUES (?, ?)")) {
                    for (MaterialId id : document.sourceMaterialIds()) {
                        insert.setString(1, document.id().toString());
                        insert.setString(2, id.toString());
                        insert.addBatch();
                    }
                    insert.executeBatch();
                }
                connection.commit();
            } catch (SQLException failure) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                throw failure;
            }
        } catch (SQLException e) {
            throw failure("save", e);
        }
    }

    private QuizForgeException failure(String action, SQLException cause) {
        return new QuizForgeException(ErrorCode.PERSISTENCE_FAILED,
                "Could not " + action + " standard document.", cause);
    }
}
