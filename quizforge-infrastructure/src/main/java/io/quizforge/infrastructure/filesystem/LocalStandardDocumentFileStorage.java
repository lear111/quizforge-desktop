package io.quizforge.infrastructure.filesystem;

import io.quizforge.core.ErrorCode;
import io.quizforge.core.QuizForgeException;
import io.quizforge.core.port.StandardDocumentFileStorage;
import io.quizforge.core.workspace.WorkspaceId;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class LocalStandardDocumentFileStorage implements StandardDocumentFileStorage {
    private final QuizForgeDataDirectory directory;

    public LocalStandardDocumentFileStorage(QuizForgeDataDirectory directory) {
        this.directory = directory;
    }

    @Override
    public StagedDocument stage(WorkspaceId workspaceId, String content) {
        Path folder = documentDirectory(workspaceId);
        try {
            Path candidate = Files.createTempFile(folder, "study-", ".tmp");
            Files.writeString(candidate, content, StandardCharsets.UTF_8);
            return new Pending(candidate, folder.resolve("study.md"));
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

    private Path documentDirectory(WorkspaceId id) {
        Path folder = directory.workspacesDirectory().resolve(id.toString()).resolve("document");
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

    private static final class Pending implements StagedDocument {
        private final Path candidate;
        private final Path target;
        private Path backup;
        private boolean published;
        private boolean completed;

        private Pending(Path candidate, Path target) {
            this.candidate = candidate;
            this.target = target;
        }

        @Override
        public void publish() {
            try {
                if (Files.isSymbolicLink(target)) {
                    throw failure("replace standard document", null);
                }
                if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                    backup = Files.createTempFile(target.getParent(), "study-backup-", ".tmp");
                    move(target, backup);
                }
                move(candidate, target);
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
