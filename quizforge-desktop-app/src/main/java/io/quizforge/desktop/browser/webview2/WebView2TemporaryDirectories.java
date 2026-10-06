package io.quizforge.desktop.browser.webview2;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Predicate;

/** Owns disposable browser profiles only; never touches workspace or installed extension data. */
public final class WebView2TemporaryDirectories {
    private static final System.Logger LOG = System.getLogger(WebView2TemporaryDirectories.class.getName());
    private static final ScheduledExecutorService CLEANER = Executors.newSingleThreadScheduledExecutor(r -> {
        var thread = new Thread(r, "qf-webview2-profile-cleanup"); thread.setDaemon(true); return thread;
    });
    private static final class Holder {
        static final Manager MANAGER = new Manager(Path.of(System.getProperty("java.io.tmpdir"), "QuizForge-webview2"),
                WebView2TemporaryDirectories::browserUses);
    }
    private WebView2TemporaryDirectories() { }

    /** Runs off the FX thread. Locked profiles belonging to another application remain untouched. */
    public static void initialize() { CLEANER.execute(() -> {
        try { Holder.MANAGER.reapStale(); }
        catch (IOException failure) { LOG.log(System.Logger.Level.WARNING, "Browser profile recovery deferred", failure); }
    }); }

    public static Path create(String prefix) throws IOException { return Holder.MANAGER.create(prefix); }

    /** The browser close acknowledgement includes native controller release and browser process exit. */
    public static CompletionStage<Void> release(Path directory, CompletionStage<Void> browserExited) {
        return Holder.MANAGER.release(directory, browserExited);
    }

    private static boolean browserUses(Path directory) {
        String profile = directory.resolve("browser-profile").toString().toLowerCase(java.util.Locale.ROOT);
        try (var processes = ProcessHandle.allProcesses()) {
            return processes.anyMatch(process -> {
                if (!process.isAlive()) return false;
                var info = process.info();
                String command = info.command().orElse("").toLowerCase(java.util.Locale.ROOT);
                String line = info.commandLine().orElse("").toLowerCase(java.util.Locale.ROOT);
                if (line.contains(profile)) return true;
                // If OS access restrictions hide a WebView2 process's arguments, defer recovery.
                return command.contains("msedgewebview2") && line.isEmpty();
            });
        } catch (SecurityException inaccessible) { return true; }
    }

    static final class Manager {
        static final String LEASE = ".quizforge-browser.lock";
        private final Path root;
        private final Predicate<Path> browserActive;
        private final Map<Path, Lease> owned = new ConcurrentHashMap<>();

        Manager(Path root, Predicate<Path> browserActive) {
            this.root = root.toAbsolutePath().normalize(); this.browserActive = browserActive;
        }

        Path create(String prefix) throws IOException {
            if (!java.util.Set.of("editor-", "learning-", "history-").contains(prefix))
                throw new IllegalArgumentException("Unknown temporary browser kind");
            checkRoot();
            Path directory = Files.createTempDirectory(root, prefix);
            try {
                Lease lease = acquire(directory);
                if (lease == null) throw new IOException("New browser profile lease is already in use");
                owned.put(directory, lease); return directory;
            }
            catch (IOException failure) { Files.deleteIfExists(directory); throw failure; }
        }

        CompletionStage<Void> release(Path directory, CompletionStage<Void> browserExited) {
            if (directory == null) return CompletableFuture.completedFuture(null);
            Path normalized = directory.toAbsolutePath().normalize();
            var result = new CompletableFuture<Void>();
            browserExited.whenComplete((unused, failure) -> CLEANER.execute(() -> {
                var lease = owned.remove(normalized);
                if (lease == null) { result.complete(null); return; }
                try { lease.close(); }
                catch (IOException closeFailure) { result.completeExceptionally(closeFailure); return; }
                if (failure != null) {
                    LOG.log(System.Logger.Level.WARNING, "Browser shutdown was not confirmed; temporary profile retained: " + normalized, failure);
                    result.completeExceptionally(failure); return;
                }
                removeAfterExit(normalized, result, 0);
            }));
            return result.minimalCompletionStage();
        }

        private void removeAfterExit(Path directory, CompletableFuture<Void> result, int attempt) {
            try {
                // This path follows the owner's explicit native/process exit acknowledgement. Unrelated
                // WebView2 processes with inaccessible arguments must not prevent normal tab cleanup.
                deleteOwned(directory); result.complete(null);
            } catch (IOException failure) {
                if (attempt < 10) CLEANER.schedule(() -> removeAfterExit(directory, result, attempt + 1), 1, TimeUnit.SECONDS);
                else {
                    LOG.log(System.Logger.Level.WARNING, "Temporary browser profile retained for startup recovery: " + directory, failure);
                    result.completeExceptionally(failure);
                }
            }
        }

        void reapStale() throws IOException {
            checkRoot();
            try (var directories = Files.list(root)) {
                for (var directory : directories.toList()) { try {
                    if (!isOwnedPath(directory) || owned.containsKey(directory) || browserActive.test(directory)) continue;
                    // Older versions had no lease. Avoid racing a directory whose owner is still creating its lease.
                    if (!Files.exists(directory.resolve(LEASE), LinkOption.NOFOLLOW_LINKS) &&
                            Files.getLastModifiedTime(directory, LinkOption.NOFOLLOW_LINKS).toInstant()
                                    .isAfter(Instant.now().minus(Duration.ofMinutes(5)))) continue;
                    Lease lease;
                    try { lease = acquire(directory); }
                    catch (IOException | OverlappingFileLockException inUse) { continue; }
                    if (lease == null) continue;
                    lease.close();
                    // Recheck immediately before deleting, including a browser surviving its owner JVM.
                    if (!browserActive.test(directory)) {
                        try { deleteOwned(directory); }
                        catch (IOException locked) { LOG.log(System.Logger.Level.DEBUG, "Temporary browser cleanup deferred: " + directory); }
                    }
                } catch (NoSuchFileException alreadyReclaimed) { /* Another process finished recovery first. */ } }
            }
        }

        private void checkRoot() throws IOException {
            Files.createDirectories(root);
            if (Files.isSymbolicLink(root) || !root.toRealPath().equals(root))
                throw new IOException("Temporary browser root must not contain symbolic links");
        }

        private Lease acquire(Path directory) throws IOException {
            Path marker = directory.resolve(LEASE);
            if (Files.isSymbolicLink(marker)) throw new IOException("Unsafe browser lease");
            var channel = FileChannel.open(marker, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            try {
                FileLock lock = channel.tryLock();
                if (lock == null) { channel.close(); return null; }
                return new Lease(channel, lock);
            } catch (IOException | RuntimeException failure) { channel.close(); throw failure; }
        }

        private boolean isOwnedPath(Path directory) {
            return directory.toAbsolutePath().normalize().getParent().equals(root) &&
                    directory.getFileName().toString().matches("(editor|learning|history)-[A-Za-z0-9-]+") &&
                    Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(directory);
        }

        private void deleteOwned(Path directory) throws IOException {
            checkRoot();
            if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) return;
            if (!isOwnedPath(directory) || !directory.toRealPath().getParent().equals(root))
                throw new IOException("Refusing cleanup outside temporary browser root");
            // walkFileTree does not follow links, including links placed inside a browser cache.
            Path resolved = directory.toRealPath();
            Files.walkFileTree(directory, new SimpleFileVisitor<>() {
                @Override public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes attrs) throws IOException {
                    // Also reject Windows junctions/reparse points that are not reported as symbolic links.
                    if (!path.toRealPath().startsWith(resolved)) throw new IOException("Browser cache directory escapes its owner");
                    return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file); return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult postVisitDirectory(Path path, IOException failure) throws IOException {
                    if (failure != null) throw failure;
                    Files.delete(path); return FileVisitResult.CONTINUE;
                }
            });
        }

        private record Lease(FileChannel channel, FileLock lock) implements AutoCloseable {
            @Override public void close() throws IOException { try { lock.release(); } finally { channel.close(); } }
        }
    }
}
