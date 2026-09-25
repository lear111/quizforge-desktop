package io.quizforge.infrastructure.persistence;

import io.quizforge.core.ErrorCode;
import io.quizforge.core.QuizForgeException;
import io.quizforge.core.ai.AiProviderConfig;
import io.quizforge.core.port.AiProviderConfigRepository;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

public final class SqliteAiProviderConfigRepository implements AiProviderConfigRepository {
    private final SqliteDatabase database;

    public SqliteAiProviderConfigRepository(SqliteDatabase database) {
        this.database = database;
    }

    @Override
    public Optional<AiProviderConfig> findDefault() {
        try (Connection connection = database.openConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT id, provider_type, base_url, model, credential_ref, updated_at "
                        + "FROM ai_provider_config WHERE id = 'default'");
                ResultSet result = statement.executeQuery()) {
            if (!result.next()) {
                return Optional.empty();
            }
            return Optional.of(new AiProviderConfig(result.getString("id"),
                    result.getString("provider_type"), result.getString("base_url"),
                    result.getString("model"), result.getString("credential_ref"),
                    Instant.parse(result.getString("updated_at"))));
        } catch (SQLException e) {
            throw new QuizForgeException(ErrorCode.PERSISTENCE_FAILED,
                    "Could not load AI provider configuration.", e);
        }
    }

    @Override
    public void save(AiProviderConfig config) {
        String sql = "INSERT INTO ai_provider_config(id, provider_type, base_url, model, "
                + "credential_ref, updated_at) VALUES (?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT(id) DO UPDATE SET provider_type=excluded.provider_type, "
                + "base_url=excluded.base_url, model=excluded.model, "
                + "credential_ref=excluded.credential_ref, updated_at=excluded.updated_at";
        try (Connection connection = database.openConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, config.id());
            statement.setString(2, config.providerType());
            statement.setString(3, config.baseUrl());
            statement.setString(4, config.model());
            statement.setString(5, config.credentialRef());
            statement.setString(6, config.updatedAt().toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new QuizForgeException(ErrorCode.PERSISTENCE_FAILED,
                    "Could not save AI provider configuration.", e);
        }
    }
}
