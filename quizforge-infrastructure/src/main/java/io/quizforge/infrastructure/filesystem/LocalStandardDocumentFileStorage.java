package io.quizforge.infrastructure.filesystem;

import io.quizforge.core.ErrorCode;
import io.quizforge.core.QuizForgeException;
import io.quizforge.core.asset.Asset;
import io.quizforge.core.asset.AssetType;
import io.quizforge.core.port.FileDocumentStorage;
import io.quizforge.core.port.StandardDocumentFileStorage;
import io.quizforge.core.workspace.WorkspaceId;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class LocalStandardDocumentFileStorage implements StandardDocumentFileStorage, FileDocumentStorage {
    private final QuizForgeDataDirectory directory;
    private final WorkspacePathResolver paths;

    public LocalStandardDocumentFileStorage(QuizForgeDataDirectory directory) {
        this.directory = directory;
        this.paths = new WorkspacePathResolver(directory);
    }

    @Override
    public StagedDocument stage(WorkspaceId workspaceId, String content) {
        Path folder = documentDirectory(workspaceId);
        try {
            Path candidate = Files.createTempFile(folder, "study-", ".tmp");
            Files.writeString(candidate, content, StandardCharsets.UTF_8);
            return new Pending(candidate, folder.resolve("study.md"), "document/study.md", true);
        } catch (IOException e) {
            throw failure("stage standard document", e);
        }
    }

    @Override
    public String read(WorkspaceId workspaceId) {
        Path target = documentDirectory(workspaceId).resolve("study.md");
        if (Files.isSymbolicLink(target)) {
            throw failure("read standard document", null);
        }
        try {
            return Files.readString(target, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw failure("read standard document", e);
        }
    }

    @Override
    public FileDocumentStorage.StagedFile stageCreate(WorkspaceId workspaceId, String title, String content) {
        Path root = paths.workspaceRoot(workspaceId);
        Path folder = root.resolve("documents");
        try {
            if (Files.isSymbolicLink(folder)) throw failure("access document directory", null);
            Files.createDirectories(folder);
            if (!folder.toRealPath().startsWith(root)) throw failure("access document directory", null);
            String base = safeFileName(title);
            Path target = folder.resolve(base + ".md");
            for (int suffix = 2; Files.exists(target, LinkOption.NOFOLLOW_LINKS); suffix++) {
                target = folder.resolve(base + " (" + suffix + ").md");
            }
            return stageAsset(folder, target, root, content, false);
        } catch (IOException error) {
            throw failure("stage new document", error);
        }
    }

    @Override
    public FileDocumentStorage.StagedFile stageReplace(WorkspaceId workspaceId,
            String currentPath, String content) {
        Path root = paths.workspaceRoot(workspaceId);
        Path target = assetPath(root, currentPath);
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(target)) {
            throw failure("replace missing document", null);
        }
        try {
            return stageAsset(target.getParent(), target, root, content, true);
        } catch (IOException error) {
            throw failure("stage document replacement", error);
        }
    }

    @Override
    public String read(WorkspaceId workspaceId, String currentPath) {
        Path target = assetPath(paths.workspaceRoot(workspaceId), currentPath);
        if (Files.isSymbolicLink(target)) throw failure("read document", null);
        try { return Files.readString(target, StandardCharsets.UTF_8); }
        catch (IOException error) { throw failure("read document", error); }
    }

    private Pending stageAsset(Path folder, Path target, Path root, String content,
            boolean replace) throws IOException {
        Path candidate = Files.createTempFile(folder, ".qf-document-", ".tmp");
        try {
            Files.writeString(candidate, content, StandardCharsets.UTF_8);
            String relative = root.relativize(target).toString().replace('\\', '/');
            return new Pending(candidate, target, relative, replace);
        } catch (IOException | RuntimeException error) {
            Files.deleteIfExists(candidate);
            throw error;
        }
    }

    private Path assetPath(Path root, String currentPath) {
        new Asset("doc_storage", AssetType.STANDARD_DOCUMENT, currentPath, "Document");
        if (currentPath.equals(".quizforge") || currentPath.startsWith(".quizforge/")) {
            throw failure("access internal document path", null);
        }
        Path target = root.resolve(currentPath).normalize();
        if (!target.startsWith(root)) throw failure("access document outside workspace", null);
        Path part = root;
        for (Path segment : root.relativize(target.getParent())) {
            part = part.resolve(segment);
            if (Files.isSymbolicLink(part)) throw failure("access linked document directory", null);
        }
        return target;
    }

    private String safeFileName(String title) {
        String name = title == null ? "" : title.trim()
                .replaceAll("[<>:\"/\\\\|?*\\x00-\\x1F]", "_")
                .replaceAll("[. ]+$", "");
        if (name.isBlank()) name = "Untitled";
        if (name.length() > 100) name = name.substring(0, 100).replaceAll("[. ]+$", "");
        if (name.matches("(?i)^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])$")) name = "_" + name;
        return name;
    }

    private Path documentDirectory(WorkspaceId id) {
        Path folder = paths.workspaceRoot(id).resolve("document");
        if (!folder.normalize().startsWith(directory.root()) || Files.isSymbolicLink(folder)) {
            throw failure("access standard document directory", null);
        }
        try {
            Files.createDirectories(folder);
            Path real = folder.toRealPath();
            if (!real.startsWith(directory.root())) {
                throw failure("access standard document directory", null);
            }
            return real;
        } catch (IOException e) {
            throw failure("access standard document directory", e);
        }
    }

    private static void move(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static QuizForgeException failure(String action, Throwable cause) {
        return new QuizForgeException(ErrorCode.STANDARD_DOCUMENT_STORAGE_FAILED,
                "Could not " + action + ".", cause);
    }

    private static final class Pending implements StagedDocument, FileDocumentStorage.StagedFile {
        private final Path candidate;
        private final Path target;
        private final String currentPath;
        private final boolean replace;
        private Path backup;
        private boolean published;
        private boolean completed;

        private Pending(Path candidate, Path target, String currentPath, boolean replace) {
            this.candidate = candidate;
            this.target = target;
            this.currentPath = currentPath;
            this.replace = replace;
        }

        @Override public String currentPath() { return currentPath; }

        @Override
        public void publish() {
            try {
                if (Files.isSymbolicLink(target)) {
                    throw failure("replace standard document", null);
                }
                if (replace && Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                    backup = Files.createTempFile(target.getParent(), "study-backup-", ".tmp");
                    move(target, backup);
                }
                if (replace) move(candidate, target);
                else Files.move(candidate, target);
                published = true;
            } catch (IOException | RuntimeException e) {
                try {
                    rollback();
                } catch (RuntimeException rollbackFailure) {
                    e.addSuppressed(rollbackFailure);
                }
                if (e instanceof QuizForgeException known) {
                    throw known;
                }
                throw failure("publish standard document", e);
            }
        }

        @Override
        public void rollback() {
            try {
                if (published) {
                    Files.deleteIfExists(target);
                    published = false;
                }
                if (backup != null && Files.exists(backup)) {
                    move(backup, target);
                    backup = null;
                }
            } catch (IOException e) {
                throw failure("restore previous standard document", e);
            }
        }

        @Override
        public void complete() {
            completed = true;
            if (backup != null) {
                try {
                    Files.deleteIfExists(backup);
                } catch (IOException ignored) {
                    // The committed document is valid; a stale backup is harmless.
                }
            }
        }

        @Override
        public void close() {
            if (!completed) {
                rollback();
            }
            try {
                Files.deleteIfExists(candidate);
            } catch (IOException ignored) {
                // A temporary file never replaces the committed document.
            }
        }
    }
}
