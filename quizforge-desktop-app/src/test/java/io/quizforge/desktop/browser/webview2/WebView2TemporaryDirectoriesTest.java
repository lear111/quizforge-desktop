package io.quizforge.desktop.browser.webview2;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WebView2TemporaryDirectoriesTest {
    @TempDir Path temporary;

    @Test void waitsForActualBrowserExitBeforeRemovingProfiles() throws Exception {
        var manager = new WebView2TemporaryDirectories.Manager(temporary.resolve("profiles"), path -> false);
        for (var kind : new String[]{"editor-", "learning-", "history-"}) {
            Path directory = manager.create(kind);
            Files.createDirectories(directory.resolve("browser-profile/cache"));
            Files.writeString(directory.resolve("browser-profile/cache/content"), "temporary browser bytes");
            var exited = new CompletableFuture<Void>();
            var cleaned = manager.release(directory, exited).toCompletableFuture();
            assertFalse(cleaned.isDone());
            assertTrue(Files.exists(directory));
            exited.complete(null);
            cleaned.get(5, TimeUnit.SECONDS);
            assertFalse(Files.exists(directory));
        }
    }

    @Test void startupRecoversStaleAndLegacyProfilesWithoutDeletingUnrelatedData() throws Exception {
        Path root = Files.createDirectories(temporary.resolve("profiles"));
        Path stale = Files.createDirectory(root.resolve("editor-crashed"));
        Files.writeString(stale.resolve(WebView2TemporaryDirectories.Manager.LEASE), "");
        Path legacy = Files.createDirectory(root.resolve("history-old"));
        Files.writeString(legacy.resolve("cache"), "old version cache");
        Files.setLastModifiedTime(legacy, FileTime.from(Instant.now().minusSeconds(600)));
        Path unrelated = Files.createDirectory(root.resolve("workspace"));
        Path recent = Files.createDirectory(root.resolve("learning-creating"));
        var manager = new WebView2TemporaryDirectories.Manager(root, path -> false);
        manager.reapStale();
        assertFalse(Files.exists(stale));
        assertFalse(Files.exists(legacy));
        assertTrue(Files.exists(unrelated));
        assertTrue(Files.exists(recent));
    }

    @Test void confirmedOwnerExitIsNotBlockedByUnrelatedInaccessibleBrowsers() throws Exception {
        var manager = new WebView2TemporaryDirectories.Manager(temporary.resolve("profiles"), path -> true);
        Path directory = manager.create("history-");
        manager.release(directory, CompletableFuture.completedFuture(null)).toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertFalse(Files.exists(directory));
    }

    @Test void startupKeepsAnotherOwnersLockedProfileAndOrphanBrowserStillRunning() throws Exception {
        Path root = Files.createDirectories(temporary.resolve("profiles"));
        var owner = new WebView2TemporaryDirectories.Manager(root, path -> false);
        Path active = owner.create("learning-");
        Path orphan = Files.createDirectory(root.resolve("editor-orphan"));
        Files.writeString(orphan.resolve(WebView2TemporaryDirectories.Manager.LEASE), "");
        var recovery = new WebView2TemporaryDirectories.Manager(root, path -> path.equals(orphan));
        recovery.reapStale();
        assertTrue(Files.exists(active));
        assertTrue(Files.exists(orphan));
        owner.release(active, CompletableFuture.completedFuture(null)).toCompletableFuture().get(5, TimeUnit.SECONDS);
    }

    @Test void startupHonorsAnExternalFileLock() throws Exception {
        Path root = Files.createDirectories(temporary.resolve("profiles"));
        Path active = Files.createDirectory(root.resolve("history-external"));
        try (var channel = FileChannel.open(active.resolve(WebView2TemporaryDirectories.Manager.LEASE),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE); var lock = channel.lock()) {
            new WebView2TemporaryDirectories.Manager(root, path -> false).reapStale();
            assertTrue(Files.exists(active));
        }
        new WebView2TemporaryDirectories.Manager(root, path -> false).reapStale();
        assertFalse(Files.exists(active));
    }

    @Test void startupDoesNotReclaimAProfileLockedByAnotherJvm() throws Exception {
        Path root = Files.createDirectories(temporary.resolve("profiles"));
        Path active = Files.createDirectory(root.resolve("learning-anotherprocess"));
        var child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                LockHolder.class.getName(), active.resolve(WebView2TemporaryDirectories.Manager.LEASE).toString())
                .redirectErrorStream(true).start();
        try {
            var output = new java.io.BufferedReader(new java.io.InputStreamReader(child.getInputStream()));
            assertEquals("locked", CompletableFuture.supplyAsync(() -> {
                try { return output.readLine(); } catch (java.io.IOException failure) { throw new CompletionException(failure); }
            }).get(5, TimeUnit.SECONDS));
            new WebView2TemporaryDirectories.Manager(root, path -> false).reapStale();
            assertTrue(Files.exists(active));
        } finally {
            child.getOutputStream().close();
            if (!child.waitFor(5, TimeUnit.SECONDS)) child.destroyForcibly().waitFor(5, TimeUnit.SECONDS);
        }
        new WebView2TemporaryDirectories.Manager(root, path -> false).reapStale();
        assertFalse(Files.exists(active));
    }

    public static final class LockHolder {
        public static void main(String[] args) throws Exception {
            try (var channel = FileChannel.open(Path.of(args[0]), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                    var lock = channel.lock()) {
                System.out.println("locked"); System.out.flush(); System.in.read();
            }
        }
    }

    @Test void failedBrowserShutdownDoesNotDeletePotentiallyLiveProfile() throws Exception {
        var manager = new WebView2TemporaryDirectories.Manager(temporary.resolve("profiles"), path -> false);
        Path directory = manager.create("editor-");
        assertThrows(ExecutionException.class, () -> manager.release(directory,
                CompletableFuture.failedFuture(new IllegalStateException("still running")))
                .toCompletableFuture().get(5, TimeUnit.SECONDS));
        assertTrue(Files.exists(directory));
        manager.reapStale();
        assertFalse(Files.exists(directory));
    }

    @Test void cleanupNeverFollowsLinksOutsideManagedRoot() throws Exception {
        Path root = Files.createDirectories(temporary.resolve("profiles"));
        Path outside = Files.createDirectories(temporary.resolve("outside"));
        Files.writeString(outside.resolve("keep"), "user data");
        var manager = new WebView2TemporaryDirectories.Manager(root, path -> false);
        Path directory = manager.create("editor-");
        try {
            Files.createSymbolicLink(directory.resolve("linked-cache"), outside);
            Files.createSymbolicLink(root.resolve("history-linked"), outside);
        } catch (FileSystemException unavailable) {
            // Windows without Developer Mode disallows unprivileged symbolic links.
        }
        manager.release(directory, CompletableFuture.completedFuture(null)).toCompletableFuture().get(5, TimeUnit.SECONDS);
        manager.reapStale();
        assertEquals("user data", Files.readString(outside.resolve("keep")));
        assertFalse(Files.exists(directory));
    }
}
