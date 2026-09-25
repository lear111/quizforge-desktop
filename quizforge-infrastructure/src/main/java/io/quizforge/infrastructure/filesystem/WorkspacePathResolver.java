package io.quizforge.infrastructure.filesystem;

import io.quizforge.core.ErrorCode;
import io.quizforge.core.QuizForgeException;
import io.quizforge.core.material.Material;
import io.quizforge.core.material.MaterialId;
import io.quizforge.core.port.WorkspaceDirectoryStorage;
import io.quizforge.core.workspace.Workspace;
import io.quizforge.core.workspace.WorkspaceId;
import io.quizforge.infrastructure.persistence.WorkspaceAssetDatabase;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.stream.Stream;

public final class WorkspacePathResolver implements WorkspaceDirectoryStorage {
    private final QuizForgeDataDirectory dataDirectory;

    public WorkspacePathResolver(QuizForgeDataDirectory dataDirectory) {
        this.dataDirectory = dataDirectory;
    }

    @Override
    public void create(Workspace workspace) {
        Path root = checkedWorkspacePath(workspace.id());
        boolean rootCreated = false;
        try {
            Files.createDirectory(root);
            rootCreated = true;
            initializeFoundation(root, workspace, true);
            // The existing Material pipeline still writes to its legacy directory.
            Files.createDirectory(root.resolve("materials"));
        } catch (IOException | RuntimeException failure) {
            if (rootCreated) {
                try {
                    deleteIfEmpty(workspace.id());
                } catch (RuntimeException cleanup) {
                    failure.addSuppressed(cleanup);
                }
            }
            if (failure instanceof QuizForgeException known) throw known;
            throw new QuizForgeException(ErrorCode.WORKSPACE_STORAGE_FAILED,
                    "Could not initialize workspace directory.", failure);
        }
    }

    @Override
    public void ensure(Workspace workspace) {
        Path root = workspaceRoot(workspace.id());
        try {
            boolean legacyWorkspace = !Files.exists(root.resolve(".quizforge/workspace.json"),
                    LinkOption.NOFOLLOW_LINKS);
            initializeFoundation(root, workspace, legacyWorkspace);
        } catch (IOException error) {
            throw new QuizForgeException(ErrorCode.WORKSPACE_STORAGE_FAILED,
                    "Could not initialize existing workspace directory.", error);
        }
    }

    private void initializeFoundation(Path root, Workspace workspace, boolean createDefaultFolders) throws IOException {
        if (createDefaultFolders) {
            ensureDirectory(root, "sources");
            ensureDirectory(root, "documents");
            ensureDirectory(root, "question-banks");
        }
        ensureDirectory(root, ".quizforge");
        Path manifest = root.resolve(".quizforge/workspace.json");
        if (Files.isSymbolicLink(manifest)) {
            throw new QuizForgeException(ErrorCode.WORKSPACE_STORAGE_FAILED,
                    "Workspace manifest cannot be a symbolic link.");
        }
        WorkspaceManifestStore manifests = new WorkspaceManifestStore();
        if (Files.exists(manifest, LinkOption.NOFOLLOW_LINKS)) {
            if (!manifests.readId(root).equals(workspace.id())) {
                throw new QuizForgeException(ErrorCode.WORKSPACE_STORAGE_FAILED,
                        "Workspace manifest ID does not match the workspace.");
            }
        } else {
            manifests.write(root, workspace);
        }
        Path indexDatabase = root.resolve(".quizforge/workspace.db");
        if (!Files.exists(indexDatabase, LinkOption.NOFOLLOW_LINKS)) {
            new WorkspaceAssetDatabase(root);
        }
    }

    private void ensureDirectory(Path root, String name) throws IOException {
        Path child = root.resolve(name);
        if (Files.isSymbolicLink(child)) {
            throw new QuizForgeException(ErrorCode.WORKSPACE_STORAGE_FAILED,
                    "Workspace internal directory cannot be a symbolic link.");
        }
        Files.createDirectories(child);
        if (!child.toRealPath().startsWith(root)) {
            throw new QuizForgeException(ErrorCode.WORKSPACE_STORAGE_FAILED,
                    "Workspace internal directory is outside the workspace.");
        }
    }

    @Override
    public void deleteIfEmpty(WorkspaceId id) {
        Path workspace = checkedWorkspacePath(id);
        try {
            if (!Files.exists(workspace, LinkOption.NOFOLLOW_LINKS)) return;
            if (Files.isSymbolicLink(workspace)
                    || !workspace.toRealPath().startsWith(dataDirectory.workspacesDirectory().toRealPath())) {
                throw new QuizForgeException(ErrorCode.WORKSPACE_STORAGE_FAILED,
                        "Workspace directory is outside the workspace storage area.");
            }
            Path internal = workspace.resolve(".quizforge");
            Files.deleteIfExists(internal.resolve("workspace.json"));
            Files.deleteIfExists(internal.resolve("workspace.db"));
            Files.deleteIfExists(internal.resolve("workspace.db-wal"));
            Files.deleteIfExists(internal.resolve("workspace.db-shm"));
            Files.deleteIfExists(internal);
            Files.deleteIfExists(workspace.resolve("materials"));
            Files.deleteIfExists(workspace.resolve("question-banks"));
            Files.deleteIfExists(workspace.resolve("documents"));
            Files.deleteIfExists(workspace.resolve("sources"));
            Files.deleteIfExists(workspace);
        } catch (IOException e) {
            throw new QuizForgeException(ErrorCode.WORKSPACE_STORAGE_FAILED,
                    "Could not clean up the workspace directory.", e);
        }
    }

    public Path workspaceRoot(WorkspaceId id) {
        Path root = locateWorkspacePath(id);
        try {
            if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(root)) {
                throw new QuizForgeException(ErrorCode.WORKSPACE_STORAGE_FAILED,
                        "Workspace directory was not found.");
            }
            Path real = root.toRealPath();
            if (!real.startsWith(dataDirectory.workspacesDirectory().toRealPath())) {
                throw new QuizForgeException(ErrorCode.WORKSPACE_STORAGE_FAILED,
                        "Workspace directory is outside the workspace storage area.");
            }
            return real;
        } catch (IOException error) {
            throw new QuizForgeException(ErrorCode.WORKSPACE_STORAGE_FAILED,
                    "Could not access workspace directory.", error);
        }
    }

    private Path locateWorkspacePath(WorkspaceId id) {
        Path expected = checkedWorkspacePath(id);
        if (Files.exists(expected, LinkOption.NOFOLLOW_LINKS)) return expected;
        Path matches = null;
        try (Stream<Path> children = Files.list(dataDirectory.workspacesDirectory())) {
            for (Path candidate : children.toList()) {
                if (!Files.isDirectory(candidate, LinkOption.NOFOLLOW_LINKS)
                        || Files.isSymbolicLink(candidate)
                        || !Files.isRegularFile(candidate.resolve(".quizforge/workspace.json"),
                                LinkOption.NOFOLLOW_LINKS)) continue;
                boolean sameId;
                try {
                    sameId = new WorkspaceManifestStore().readId(candidate).equals(id);
                } catch (QuizForgeException invalidManifest) {
                    // An unrelated directory with a damaged manifest is not this workspace.
                    continue;
                }
                if (sameId) {
                    if (matches != null) {
                        throw new QuizForgeException(ErrorCode.WORKSPACE_STORAGE_FAILED,
                                "Multiple workspace directories have the same ID.");
                    }
                    matches = candidate;
                }
            }
        } catch (IOException error) {
            throw new QuizForgeException(ErrorCode.WORKSPACE_STORAGE_FAILED,
                    "Could not locate workspace directory.", error);
        }
        return matches == null ? expected : matches;
    }

    private Path checkedWorkspacePath(WorkspaceId id) {
        Path root = dataDirectory.workspacesDirectory().resolve(id.toString()).normalize();
        if (!root.startsWith(dataDirectory.workspacesDirectory())
                || !root.getFileName().toString().equals(id.toString())) {
            throw new QuizForgeException(ErrorCode.WORKSPACE_STORAGE_FAILED,
                    "Invalid workspace directory path.");
        }
        return root;
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
        Path directory = workspaceRoot(workspaceId).resolve("materials");
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
