package io.quizforge.infrastructure.persistence;

import io.quizforge.core.ErrorCode;
import io.quizforge.core.QuizForgeException;
import io.quizforge.core.asset.Asset;
import io.quizforge.core.asset.AssetType;
import io.quizforge.core.port.AssetIndexRepository;
import io.quizforge.core.workspace.model.WorkspaceId;
import io.quizforge.infrastructure.filesystem.workspace.WorkspacePathResolver;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class SqliteAssetIndexRepository implements AssetIndexRepository {
    private final WorkspacePathResolver paths;

    public SqliteAssetIndexRepository(WorkspacePathResolver paths) {
        this.paths = paths;
    }

    @Override
    public Optional<Asset> findById(WorkspaceId workspaceId, String assetId) {
        try (Connection connection = database(workspaceId).openConnection();
                PreparedStatement query = connection.prepareStatement(
                        "SELECT asset_id, asset_type, content_id, current_path, title, schema_version "
                        + "FROM asset_registry WHERE asset_id = ?")) {
            query.setString(1, assetId);
            try (ResultSet rows = query.executeQuery()) {
                return rows.next() ? Optional.of(map(rows)) : Optional.empty();
            }
        } catch (SQLException error) {
            throw failure("read asset index", error);
        }
    }

    @Override
    public List<Asset> list(WorkspaceId workspaceId) {
        try (Connection connection = database(workspaceId).openConnection();
                PreparedStatement query = connection.prepareStatement(
                        "SELECT asset_id, asset_type, content_id, current_path, title, schema_version "
                        + "FROM asset_registry ORDER BY current_path, asset_id");
                ResultSet rows = query.executeQuery()) {
            List<Asset> assets = new ArrayList<>();
            while (rows.next()) assets.add(map(rows));
            return assets;
        } catch (SQLException error) {
            throw failure("list asset index", error);
        }
    }

    @Override
    public void synchronize(WorkspaceId workspaceId, List<Asset> assets, Instant indexedAt) {
        try (Connection connection = database(workspaceId).openConnection()) {
            connection.setAutoCommit(false);
            try {
                Set<String> present = new HashSet<>();
                try (PreparedStatement upsert = connection.prepareStatement(
                        "INSERT INTO asset_registry(asset_id, asset_type, content_id, current_path, title, "
                        + "schema_version, indexed_at) VALUES (?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT(asset_id) DO UPDATE SET asset_type=excluded.asset_type, "
                        + "content_id=excluded.content_id, current_path=excluded.current_path, "
                        + "title=excluded.title, schema_version=excluded.schema_version, "
                        + "indexed_at=excluded.indexed_at")) {
                    for (Asset asset : assets) {
                        if (!present.add(asset.assetId())) {
                            throw new IllegalArgumentException("Duplicate asset ID in scan: " + asset.assetId());
                        }
                        upsert.setString(1, asset.assetId());
                        upsert.setString(2, asset.assetType() == AssetType.REGISTERED_MARKDOWN ? "STANDARD_DOCUMENT" : asset.assetType().name());
                        upsert.setString(3, asset.contentId());
                        upsert.setString(4, asset.currentPath());
                        upsert.setString(5, asset.title());
                        upsert.setString(6, asset.schemaVersion());
                        upsert.setString(7, indexedAt.toString());
                        upsert.executeUpdate();
                    }
                }
                List<String> stale = new ArrayList<>();
                try (PreparedStatement query = connection.prepareStatement("SELECT asset_id FROM asset_registry");
                        ResultSet rows = query.executeQuery()) {
                    while (rows.next()) {
                        String id = rows.getString(1);
                        if (!present.contains(id)) stale.add(id);
                    }
                }
                try (PreparedStatement delete = connection.prepareStatement("DELETE FROM asset_registry WHERE asset_id = ?")) {
                    for (String id : stale) {
                        delete.setString(1, id);
                        delete.executeUpdate();
                    }
                }
                connection.commit();
            } catch (SQLException | RuntimeException error) {
                try { connection.rollback(); } catch (SQLException rollback) { error.addSuppressed(rollback); }
                throw error;
            }
        } catch (SQLException error) {
            throw failure("synchronize asset index", error);
        }
    }

    private WorkspaceAssetDatabase database(WorkspaceId workspaceId) {
        return new WorkspaceAssetDatabase(paths.workspaceRoot(workspaceId));
    }

    private Asset map(ResultSet row) throws SQLException {
        return new Asset(row.getString("asset_id"), storedType(row.getString("asset_type")),
                row.getString("current_path"), row.getString("title"),
                row.getString("content_id"), row.getString("schema_version"));
    }

    private QuizForgeException failure(String action, SQLException error) {
        return new QuizForgeException(ErrorCode.PERSISTENCE_FAILED, "Could not " + action + ".", error);
    }
    private static AssetType storedType(String name) {
        return "STANDARD_DOCUMENT".equals(name) ? AssetType.REGISTERED_MARKDOWN : AssetType.valueOf(name);
    }
}
