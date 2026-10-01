package io.quizforge.infrastructure.filesystem;

import io.quizforge.core.ErrorCode;
import io.quizforge.core.QuizForgeException;
import io.quizforge.core.port.FileDocumentStorage;
import io.quizforge.core.port.QuestionBankFileStorage;
import io.quizforge.infrastructure.filesystem.workspace.WorkspacePathGuard;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/** Publishes without overwriting a newly created external version; retains recovery copies on conflicts. */
public final class SafeFilePublication implements FileDocumentStorage.StagedFile,
        QuestionBankFileStorage.StagedFile {
    private final Path candidate, target, root;
    private final String currentPath;
    private final byte[] original, edited;
    private final ErrorCode errorCode;
    private Path originalBackup, editBackup, stagedOriginal;
    private boolean published, completed, conflict;

    public SafeFilePublication(Path candidate, Path target, Path root, String currentPath,
            boolean replace, ErrorCode errorCode) throws IOException {
        this.candidate=candidate; this.target=target; this.root=root;
        this.currentPath=currentPath; this.errorCode=errorCode;
        WorkspacePathGuard.requireInside(root,target);
        original=replace && Files.exists(target,LinkOption.NOFOLLOW_LINKS) ? digest(target) : null;
        edited=digest(candidate);
        if (original != null) {
            stagedOriginal=Files.createTempFile(target.getParent(),".qf-opened-", ".tmp");
            Files.copy(target,stagedOriginal,StandardCopyOption.REPLACE_EXISTING);
            if (!matches(stagedOriginal,original)) throw changed("File changed while staging");
        }
    }
    @Override public String currentPath() { return currentPath; }

    @Override public void rejectConflict(String message) {
        conflict=true;
        throw changed(message);
    }

    @Override public void publish() {
        if (published || completed || conflict) throw new IllegalStateException("Publication is no longer pending");
        try {
            WorkspacePathGuard.requireInside(root,target);
            if (!matches(target,original)) throw changed("File changed before publication");
            editBackup=Files.createTempFile(target.getParent(),".qf-edited-", ".tmp");
            Files.copy(candidate,editBackup,StandardCopyOption.REPLACE_EXISTING);
            if (original != null) {
                originalBackup=Files.createTempFile(target.getParent(),".qf-original-", ".tmp");
                Files.move(target,originalBackup,StandardCopyOption.REPLACE_EXISTING);
                if (!matches(originalBackup,original)) throw changed("File changed during publication");
            }
            // Do not use REPLACE_EXISTING: another writer may have created target
            // after it was moved to the original backup.
            Files.move(candidate,target);
            published=true;
        } catch (IOException | RuntimeException failure) {
            try { rollback(); } catch (RuntimeException recovery) { failure.addSuppressed(recovery); }
            conflict=true;
            if (failure instanceof RuntimeException runtime) throw runtime;
            throw new QuizForgeException(errorCode,"Could not publish file",failure);
        }
    }

    @Override public void rollback() {
        if (completed || conflict) return;
        try {
            WorkspacePathGuard.requireInside(root,target);
            if (published) {
                if (!matches(target,edited)) { conflict=true; throw changed("External version retained during rollback"); }
                Files.delete(target);
                published=false;
            }
            if (originalBackup != null && Files.exists(originalBackup)) {
                if (Files.exists(target,LinkOption.NOFOLLOW_LINKS)) {
                    conflict=true;
                    throw changed("External version retained; original backup was not restored over it");
                }
                Files.move(originalBackup,target);
                originalBackup=null;
            }
        } catch (IOException | RuntimeException failure) {
            conflict=true;
            if (failure instanceof RuntimeException runtime) throw runtime;
            throw new QuizForgeException(errorCode,recoveryMessage("Could not restore original file"),failure);
        }
    }

    @Override public void complete() {
        try {
            WorkspacePathGuard.requireInside(root,target);
            if (!published || !matches(target,edited)) {
                conflict=true; throw changed("File changed before save completion");
            }
            completed=true;
            cleanup(originalBackup); cleanup(editBackup); cleanup(stagedOriginal);
        } catch (IOException failure) { throw new QuizForgeException(errorCode,"Could not verify saved file",failure); }
    }

    @Override public void close() {
        if (!completed) rollback();
        if (!conflict) { cleanup(candidate); cleanup(editBackup); cleanup(stagedOriginal); }
    }
    private void cleanup(Path file) {
        if (file == null) return;
        try { WorkspacePathGuard.requireInside(root,file); Files.deleteIfExists(file); }
        catch (IOException | RuntimeException ignored) { /* Keep recoverable data on cleanup failure. */ }
    }
    private IllegalStateException changed(String message) { return new IllegalStateException(recoveryMessage(message)); }
    private String recoveryMessage(String message) {
        return message + ". Reload before saving. Recovery files: " + stagedOriginal + ", " + originalBackup + ", "
                + (editBackup == null ? candidate : editBackup);
    }
    private static boolean matches(Path file, byte[] expected) throws IOException {
        return expected == null ? !Files.exists(file,LinkOption.NOFOLLOW_LINKS)
                : Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS) && Arrays.equals(expected,digest(file));
    }
    private static byte[] digest(Path file) throws IOException {
        try {
            var digest=MessageDigest.getInstance("SHA-256");
            try (var input=Files.newInputStream(file)) {
                byte[] buffer=new byte[8192];
                for (int count; (count=input.read(buffer)) != -1;) digest.update(buffer,0,count);
            }
            return digest.digest();
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
