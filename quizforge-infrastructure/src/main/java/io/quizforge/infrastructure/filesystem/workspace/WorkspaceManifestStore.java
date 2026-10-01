package io.quizforge.infrastructure.filesystem.workspace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quizforge.core.ErrorCode;
import io.quizforge.core.QuizForgeException;
import io.quizforge.core.workspace.model.Workspace;
import io.quizforge.core.workspace.model.WorkspaceId;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Reads and writes the stable identity stored with a workspace directory. */
public final class WorkspaceManifestStore {
    private static final ObjectMapper JSON = new ObjectMapper();
    public static final String FORMAT = "quizforge-workspace";
    public static final String SCHEMA_VERSION = "1.0";

    public void write(Path root, Workspace workspace) {
        ObjectNode manifest = JSON.createObjectNode();
        manifest.put("format", FORMAT);
        manifest.put("schemaVersion", SCHEMA_VERSION);
        manifest.put("workspaceId", workspace.id().toString());
        manifest.put("name", workspace.name());
        try (var output = Files.newOutputStream(root.resolve(".quizforge").resolve("workspace.json"),
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            JSON.writeValue(output, manifest);
        } catch (IOException error) {
            throw new QuizForgeException(ErrorCode.WORKSPACE_STORAGE_FAILED,
                    "Could not write workspace.json.", error);
        }
    }

    public WorkspaceId readId(Path root) {
        return read(root).id();
    }

    public Manifest read(Path root) {
        try {
            JsonNode manifest = JSON.readTree(root.resolve(".quizforge").resolve("workspace.json").toFile());
            if (manifest == null || !FORMAT.equals(manifest.path("format").asText())
                    || !SCHEMA_VERSION.equals(manifest.path("schemaVersion").asText())) {
                throw new IllegalArgumentException("Unsupported workspace manifest");
            }
            String name = manifest.path("name").asText().trim();
            if (name.isEmpty()) throw new IllegalArgumentException("Workspace name is missing");
            return new Manifest(WorkspaceId.parse(manifest.path("workspaceId").asText()), name);
        } catch (IOException | IllegalArgumentException error) {
            throw new QuizForgeException(ErrorCode.WORKSPACE_STORAGE_FAILED,
                    "Could not read workspace.json.", error);
        }
    }

    public record Manifest(WorkspaceId id, String name) { }
}
