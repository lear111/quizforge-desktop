package io.quizforge.infrastructure.filesystem;

import io.quizforge.core.ErrorCode;
import io.quizforge.core.QuizForgeException;
import io.quizforge.core.material.Material;
import io.quizforge.core.material.MaterialId;
import io.quizforge.core.port.WorkspaceDirectoryStorage;
import io.quizforge.core.workspace.WorkspaceId;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class WorkspacePathResolver implements WorkspaceDirectoryStorage {
    private final QuizForgeDataDirectory dataDirectory;

    public WorkspacePathResolver(QuizForgeDataDirectory dataDirectory) {
        this.dataDirectory = dataDirectory;
    }

    @Override
    public void create(WorkspaceId id) {
        materialsDirectory(id);
    }

    @Override
    public void deleteIfEmpty(WorkspaceId id) {
        Path workspace = checkedPath(dataDirectory.workspacesDirectory(), id.toString());
        try {
            if (Files.exists(workspace) && !workspace.toRealPath().startsWith(dataDirectory.root())) {
                throw new QuizForgeException(ErrorCode.MATERIAL_STORAGE_FAILED,
                        "Workspace directory is outside the QuizForge data directory.");
            }
            Files.deleteIfExists(workspace.resolve("materials"));
            Files.deleteIfExists(workspace);
        } catch (IOException e) {
            throw new QuizForgeException(ErrorCode.MATERIAL_STORAGE_FAILED,
                    "Could not clean up the workspace directory.", e);
        }
    }

    public Path materialPath(WorkspaceId workspaceId, MaterialId materialId) {
        return checkedMaterialPath(materialsDirectory(workspaceId), materialId + ".md");
    }

    public Path materialPath(Material material) {
        String expected = material.id() + ".md";
        if (!expected.equals(material.storedFileName())) {
            throw new QuizForgeException(ErrorCode.MATERIAL_STORAGE_FAILED,
                    "Stored material filename is invalid.");
        }
        return checkedMaterialPath(materialsDirectory(material.workspaceId()), expected);
    }

    private Path materialsDirectory(WorkspaceId workspaceId) {
        Path directory = checkedPath(dataDirectory.workspacesDirectory(), workspaceId.toString())
                .resolve("materials");
        try {
            Files.createDirectories(directory);
            Path realDirectory = directory.toRealPath();
            if (!realDirectory.startsWith(dataDirectory.root())) {
                throw new QuizForgeException(ErrorCode.MATERIAL_STORAGE_FAILED,
                        "Material directory is outside the QuizForge data directory.");
            }
            return realDirectory;
        } catch (IOException e) {
            throw new QuizForgeException(ErrorCode.MATERIAL_STORAGE_FAILED,
                    "Could not access the workspace material directory.", e);
        }
    }

    private Path checkedPath(Path parent, String child) {
        Path path = parent.resolve(child).normalize();
        if (!path.startsWith(dataDirectory.root())) {
            throw new QuizForgeException(ErrorCode.MATERIAL_STORAGE_FAILED,
                    "Material path is outside the QuizForge data directory.");
        }
        return path;
    }

    private Path checkedMaterialPath(Path parent, String name) {
        Path path = checkedPath(parent, name);
        if (Files.isSymbolicLink(path)) {
            throw new QuizForgeException(ErrorCode.MATERIAL_STORAGE_FAILED,
                    "Stored material cannot be a symbolic link.");
        }
        return path;
    }
}
