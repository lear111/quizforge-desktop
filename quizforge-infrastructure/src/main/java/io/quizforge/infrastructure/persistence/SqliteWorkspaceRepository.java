package io.quizforge.infrastructure.persistence;

import io.quizforge.core.ErrorCode;
import io.quizforge.core.QuizForgeException;
import io.quizforge.core.port.WorkspaceRepository;
import io.quizforge.core.workspace.Workspace;
import io.quizforge.core.workspace.WorkspaceId;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SqliteWorkspaceRepository implements WorkspaceRepository {
    private final SqliteDatabase database;

    public SqliteWorkspaceRepository(SqliteDatabase database) {
        this.database = database;
    }

    @Override
    public void save(Workspace workspace) {
        String sql = "INSERT INTO workspace(id, name, created_at, updated_at) VALUES (?, ?, ?, ?)";
        try (Connection connection = database.openConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, workspace.id().toString());
            statement.setString(2, workspace.name());
            statement.setString(3, workspace.createdAt().toString());
            statement.setString(4, workspace.updatedAt().toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw failure("save workspace", e);
        }
    }

    @Override
    public List<Workspace> list() {
        String sql = "SELECT id, name, created_at, updated_at FROM workspace ORDER BY created_at, id";
        try (Connection connection = database.openConnection();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet results = statement.executeQuery()) {
            List<Workspace> workspaces = new ArrayList<>();
            while (results.next()) {
                workspaces.add(map(results));
            }
            return workspaces;
        } catch (SQLException e) {
            throw failure("list workspaces", e);
        }
    }

    @Override
    public Optional<Workspace> findById(WorkspaceId id) {
        String sql = "SELECT id, name, created_at, updated_at FROM workspace WHERE id = ?";
        try (Connection connection = database.openConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id.toString());
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? Optional.of(map(results)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw failure("load workspace", e);
        }
    }

    private Workspace map(ResultSet results) throws SQLException {
        return new Workspace(WorkspaceId.parse(results.getString("id")), results.getString("name"),
                Instant.parse(results.getString("created_at")),
                Instant.parse(results.getString("updated_at")));
    }

    private QuizForgeException failure(String operation, SQLException cause) {
        return new QuizForgeException(ErrorCode.PERSISTENCE_FAILED,
                "Could not " + operation + ".", cause);
    }
}
