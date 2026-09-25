package io.quizforge.infrastructure.filesystem;

import io.quizforge.core.ErrorCode;
import io.quizforge.core.QuizForgeException;
import io.quizforge.core.asset.Asset;
import io.quizforge.core.asset.AssetType;
import io.quizforge.core.port.QuestionBankFileStorage;
import io.quizforge.core.workspace.WorkspaceId;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Staged publication and rollback follow the existing document-file storage policy. */
public final class LocalQuestionBankFileStorage implements QuestionBankFileStorage {
    private final WorkspacePathResolver paths;

    public LocalQuestionBankFileStorage(QuizForgeDataDirectory directory) {
        this.paths = new WorkspacePathResolver(directory);
    }

    @Override
    public StagedFile stageCreate(WorkspaceId workspaceId, String title, String json) {
        Path root = paths.workspaceRoot(workspaceId);
        Path folder = root.resolve("question-banks");
        try {
            if (Files.isSymbolicLink(folder)) throw failure("access question-bank directory", null);
            Files.createDirectories(folder);
            if (!folder.toRealPath().startsWith(root)) throw failure("access question-bank directory", null);
            String base = safeFileName(title);
            Path target = folder.resolve(base + ".qbank");
            for (int suffix = 2; Files.exists(target, LinkOption.NOFOLLOW_LINKS); suffix++) {
                target = folder.resolve(base + " (" + suffix + ").qbank");
            }
            return stage(folder, target, root, json, false);
        } catch (IOException error) { throw failure("stage QuestionBank", error); }
    }

    @Override
    public StagedFile stageReplace(WorkspaceId workspaceId, String relativePath, String json) {
        Path root = paths.workspaceRoot(workspaceId);
        Path target = checked(root, relativePath);
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(target)) {
            throw failure("replace missing QuestionBank", null);
        }
        try { return stage(target.getParent(), target, root, json, true); }
        catch (IOException error) { throw failure("stage QuestionBank replacement", error); }
    }

    @Override
    public String read(WorkspaceId workspaceId, String relativePath) {
        Path target = checked(paths.workspaceRoot(workspaceId), relativePath);
        if (Files.isSymbolicLink(target)) throw failure("read linked QuestionBank", null);
        try { return Files.readString(target, StandardCharsets.UTF_8); }
        catch (IOException error) { throw failure("read QuestionBank", error); }
    }

    private StagedFile stage(Path folder, Path target, Path root, String json,
            boolean replace) throws IOException {
        Path candidate = Files.createTempFile(folder, ".qf-bank-", ".tmp");
        try {
            Files.writeString(candidate, json, StandardCharsets.UTF_8);
            return new Pending(candidate, target, root.relativize(target).toString().replace('\\', '/'), replace);
        } catch (IOException | RuntimeException error) {
            Files.deleteIfExists(candidate);
            throw error;
        }
    }

    private Path checked(Path root, String relativePath) {
        new Asset("qb_storage", AssetType.QUESTION_BANK, relativePath, "QuestionBank");
        if (relativePath.equals(".quizforge") || relativePath.startsWith(".quizforge/")) {
            throw failure("access internal QuestionBank path", null);
        }
        Path target = root.resolve(relativePath).normalize();
        if (!target.startsWith(root)) throw failure("access QuestionBank outside workspace", null);
        Path part = root;
        for (Path segment : root.relativize(target.getParent())) {
            part = part.resolve(segment);
            if (Files.isSymbolicLink(part)) throw failure("access linked QuestionBank directory", null);
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

    private static void move(Path source, Path destination) throws IOException {
        try { Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException error) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static QuizForgeException failure(String action, Throwable cause) {
        return new QuizForgeException(ErrorCode.QUESTION_BANK_STORAGE_FAILED,
                "Could not " + action + ".", cause);
    }

    private static final class Pending implements StagedFile {
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
                if (Files.isSymbolicLink(target)) throw failure("replace linked QuestionBank", null);
                if (replace && Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                    backup = Files.createTempFile(target.getParent(), ".qf-bank-backup-", ".tmp");
                    move(target, backup);
                }
                if (replace) move(candidate, target);
                else Files.move(candidate, target);
                published = true;
            } catch (IOException | RuntimeException error) {
                try { rollback(); } catch (RuntimeException rollback) { error.addSuppressed(rollback); }
                if (error instanceof QuizForgeException known) throw known;
                throw failure("publish QuestionBank", error);
            }
        }

        @Override
        public void rollback() {
            try {
                if (published) { Files.deleteIfExists(target); published = false; }
                if (backup != null && Files.exists(backup)) {
                    move(backup, target);
                    backup = null;
                }
            } catch (IOException error) { throw failure("restore previous QuestionBank", error); }
        }

        @Override
        public void complete() {
            completed = true;
            if (backup != null) {
                try { Files.deleteIfExists(backup); }
                catch (IOException ignored) { /* Published content remains valid. */ }
            }
        }

        @Override
        public void close() {
            if (!completed) rollback();
            try { Files.deleteIfExists(candidate); }
            catch (IOException ignored) { /* A temp file never replaces the published asset. */ }
        }
    }
}
