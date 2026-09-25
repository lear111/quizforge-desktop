package io.quizforge.infrastructure.persistence;

import io.quizforge.core.ErrorCode;
import io.quizforge.core.QuizForgeException;
import io.quizforge.core.material.Material;
import io.quizforge.core.material.MaterialId;
import io.quizforge.core.material.MaterialStatus;
import io.quizforge.core.port.MaterialRepository;
import io.quizforge.core.workspace.WorkspaceId;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SqliteMaterialRepository implements MaterialRepository {
    private final SqliteDatabase database;

    public SqliteMaterialRepository(SqliteDatabase database) {
        this.database = database;
    }

    @Override
    public void save(Material material) {
        String sql = "INSERT INTO material(id, workspace_id, original_file_name, stored_file_name, "
                + "file_size, status, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = database.openConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, material.id().toString());
            statement.setString(2, material.workspaceId().toString());
            statement.setString(3, material.originalFileName());
            statement.setString(4, material.storedFileName());
            statement.setLong(5, material.fileSize());
            statement.setString(6, material.status().name());
            statement.setString(7, material.createdAt().toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw failure("save material", e);
        }
    }

    @Override
    public List<Material> listByWorkspace(WorkspaceId workspaceId) {
        String sql = "SELECT id, workspace_id, original_file_name, stored_file_name, file_size, "
                + "status, created_at FROM material WHERE workspace_id = ? ORDER BY created_at, id";
        try (Connection connection = database.openConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, workspaceId.toString());
            try (ResultSet results = statement.executeQuery()) {
                List<Material> materials = new ArrayList<>();
                while (results.next()) {
                    materials.add(map(results));
                }
                return materials;
            }
        } catch (SQLException e) {
            throw failure("list materials", e);
        }
    }

    @Override
    public Optional<Material> findById(MaterialId id) {
        String sql = "SELECT id, workspace_id, original_file_name, stored_file_name, file_size, "
                + "status, created_at FROM material WHERE id = ?";
        try (Connection connection = database.openConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id.toString());
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? Optional.of(map(results)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw failure("load material", e);
        }
    }

    @Override
    public void delete(MaterialId id) {
        try (Connection connection = database.openConnection();
                PreparedStatement statement = connection.prepareStatement("DELETE FROM material WHERE id = ?")) {
            statement.setString(1, id.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw failure("delete material", e);
        }
    }

    private Material map(ResultSet results) throws SQLException {
        return new Material(MaterialId.parse(results.getString("id")),
                WorkspaceId.parse(results.getString("workspace_id")),
                results.getString("original_file_name"), results.getString("stored_file_name"),
                results.getLong("file_size"), MaterialStatus.valueOf(results.getString("status")),
                Instant.parse(results.getString("created_at")));
    }

    private QuizForgeException failure(String operation, SQLException cause) {
        return new QuizForgeException(ErrorCode.PERSISTENCE_FAILED,
                "Could not " + operation + ".", cause);
    }
}
