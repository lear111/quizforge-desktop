package io.quizforge.infrastructure.filesystem.workspace;

import io.quizforge.core.ErrorCode;
import io.quizforge.core.QuizForgeException;
import io.quizforge.core.port.WorkspaceDirectoryStorage;
import io.quizforge.core.port.WorkspaceRepository;
import io.quizforge.core.workspace.model.Workspace;
import io.quizforge.core.workspace.model.WorkspaceFolder;
import io.quizforge.core.workspace.model.WorkspaceId;
import io.quizforge.infrastructure.filesystem.QuizForgeDataDirectory;
import io.quizforge.infrastructure.persistence.WorkspaceAssetDatabase;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.stream.Stream;

public final class WorkspacePathResolver implements WorkspaceDirectoryStorage {
    private final QuizForgeDataDirectory dataDirectory;
    private final WorkspaceRepository repository;

    public WorkspacePathResolver(QuizForgeDataDirectory dataDirectory) {
        this(dataDirectory, null);
    }

    public WorkspacePathResolver(QuizForgeDataDirectory dataDirectory, WorkspaceRepository repository) {
        this.dataDirectory = dataDirectory;
        this.repository = repository;
    }

    @Override
    public Path defaultParent() {
        return dataDirectory.workspacesDirectory();
    }

    @Override
    public WorkspaceFolder inspectExisting(Path root) {
        if (root == null) {
            throw new QuizForgeException(ErrorCode.WORKSPACE_STORAGE_FAILED,
                    "Select a workspace folder.");
        }
        Path selected = root.toAbsolutePath().normalize();
        Path internal = selected.resolve(".quizforge");
        Path manifest = internal.resolve("workspace.json");
        if (!Files.isDirectory(selected, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(selected)
                || !Files.isDirectory(internal, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(internal)
                || !Files.isRegularFile(manifest, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(manifest)) {
            throw new QuizForgeException(ErrorCode.WORKSPACE_STORAGE_FAILED,
                    "This folder does not contain a valid .quizforge/workspace.json.");
        }
        try {
            Path real = selected.toRealPath();
            WorkspacePathGuard.requireInside(real, real.resolve(".quizforge/workspace.json"));
            var metadata = new WorkspaceManifestStore().read(real);
            return new WorkspaceFolder(metadata.id(), metadata.name(), real);
        } catch (IOException error) {
            throw new QuizForgeException(ErrorCode.WORKSPACE_STORAGE_FAILED,
                    "Could not access workspace folder.", error);
        }
    }

    @Override
    public Path rootOf(WorkspaceId id) {
        return workspaceRoot(id);
    }

    @Override
    public void create(Workspace workspace) {
        Path root = workspacePath(workspace);
        boolean rootCreated = false;
        try {
            Files.createDirectory(root);
            rootCreated = true;
            initializeFoundation(root, workspace, true);
        } catch (IOException | RuntimeException failure) {
            if (rootCreated) {
                try {
                    deleteIfEmpty(workspace);
                } catch (RuntimeException cleanup) {
                    failure.addSuppressed(cleanup);
                }
            }
            if (failure instanceof QuizForgeException known) throw known;
            if (failure instanceof FileAlreadyExistsException) {
                throw new QuizForgeException(ErrorCode.WORKSPACE_STORAGE_FAILED,
                        "A file or folder with this workspace name already exists in the selected location.", failure);
            }
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
    public void deleteIfEmpty(Workspace workspace) {
        Path root = workspacePath(workspace);
        try {
            if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
            if (Files.isSymbolicLink(root) || (workspace.rootPath() == null
                    && !root.toRealPath().startsWith(dataDirectory.workspacesDirectory().toRealPath()))) {
                throw new QuizForgeException(ErrorCode.WORKSPACE_STORAGE_FAILED,
                        "Workspace directory is unavailable for cleanup.");
            }
            Path internal = root.resolve(".quizforge");
            if (Files.isSymbolicLink(internal)) {
                throw new QuizForgeException(ErrorCode.WORKSPACE_STORAGE_FAILED,
                        "Workspace internal directory cannot be a symbolic link during cleanup.");
            }
            Path manifest = internal.resolve("workspace.json");
            if (Files.exists(manifest, LinkOption.NOFOLLOW_LINKS)
                    && !new WorkspaceManifestStore().readId(root).equals(workspace.id())) {
                throw new QuizForgeException(ErrorCode.WORKSPACE_STORAGE_FAILED,
                        "Workspace manifest ID does not match during cleanup.");
            }
            Files.deleteIfExists(internal.resolve("workspace.json"));
            Files.deleteIfExists(internal.resolve("workspace.db"));
            Files.deleteIfExists(internal.resolve("workspace.db-wal"));
            Files.deleteIfExists(internal.resolve("workspace.db-shm"));
            Files.deleteIfExists(internal);
            Files.deleteIfExists(root.resolve("materials"));
            Files.deleteIfExists(root.resolve("question-banks"));
            Files.deleteIfExists(root.resolve("documents"));
            Files.deleteIfExists(root.resolve("sources"));
            Files.deleteIfExists(root);
        } catch (IOException e) {
            throw new QuizForgeException(ErrorCode.WORKSPACE_STORAGE_FAILED,
                    "Could not clean up the workspace directory.", e);
        }
    }

    public Path workspaceRoot(WorkspaceId id) {
        Workspace registered = repository == null ? null : repository.findById(id).orElse(null);
        Path root = registered != null && registered.rootPath() != null
                ? workspacePath(registered) : locateWorkspacePath(id);
        try {
            if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(root)) {
                throw new QuizForgeException(ErrorCode.WORKSPACE_STORAGE_FAILED,
                        "Workspace directory was not found.");
            }
            Path real = root.toRealPath();
            if (registered != null && registered.rootPath() != null) {
                if (!new WorkspaceManifestStore().readId(real).equals(id)) {
                    throw new QuizForgeException(ErrorCode.WORKSPACE_STORAGE_FAILED,
                            "Workspace manifest ID does not match the selected directory.");
                }
            } else if (!real.startsWith(dataDirectory.workspacesDirectory().toRealPath())) {
                throw new QuizForgeException(ErrorCode.WORKSPACE_STORAGE_FAILED,
                        "Workspace directory is outside the workspace storage area.");
            }
            WorkspacePathGuard.requireInside(real, real.resolve(".quizforge"));
            WorkspacePathGuard.requireInside(real, real.resolve(".quizforge/workspace.json"));
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

    private Path workspacePath(Workspace workspace) {
        if (workspace.rootPath() == null) return checkedWorkspacePath(workspace.id());
        Path root = workspace.rootPath();
        if (!root.isAbsolute() || !root.equals(root.normalize()) || root.getParent() == null) {
            throw new QuizForgeException(ErrorCode.WORKSPACE_STORAGE_FAILED,
                    "Invalid workspace directory path.");
        }
        return root;
    }

}
