package io.quizforge.desktop.extension;

import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.*;

/** Recursive, debounced file watching. All callbacks are supplied by the owning window. */
public final class ExtensionDevelopmentWatcher implements AutoCloseable {
    private final WatchService watcher;
    private final Map<WatchKey, Path> directories = new java.util.concurrent.ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;
    private final Thread thread;
    private final Runnable changed;
    private ScheduledFuture<?> pending;
    private volatile boolean closed;
    public ExtensionDevelopmentWatcher(Path root, Runnable changed) throws IOException {
        this.changed = changed;
        watcher = FileSystems.getDefault().newWatchService();
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> { var worker = new Thread(r,"quizforge-extension-debounce"); worker.setDaemon(true); return worker; });
        try { registerTree(root); }
        catch (IOException failure) { scheduler.shutdownNow(); watcher.close(); throw failure; }
        thread = new Thread(this::watch,"quizforge-extension-watch"); thread.setDaemon(true); thread.start();
    }
    private void registerTree(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            for (Path directory : paths.filter(p -> Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(p)).toList())
                if (!closed) directories.put(directory.register(watcher,StandardWatchEventKinds.ENTRY_CREATE,StandardWatchEventKinds.ENTRY_DELETE,StandardWatchEventKinds.ENTRY_MODIFY),directory);
        }
    }
    private void watch() {
        while (!closed) try {
            WatchKey key = watcher.take(); Path parent = directories.get(key);
            boolean any = false;
            for (var event : key.pollEvents()) {
                any = true;
                if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE && parent != null && event.context() instanceof Path relative) {
                    Path created = parent.resolve(relative);
                    if (Files.isDirectory(created, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(created)) try { registerTree(created); } catch (IOException ignored) { }
                }
            }
            if (!key.reset()) directories.remove(key);
            if (any) schedule();
        } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return; }
          catch (ClosedWatchServiceException closedWatcher) { return; }
    }
    private synchronized void schedule() {
        if (closed) return; if (pending != null) pending.cancel(false);
        pending = scheduler.schedule(() -> { if (!closed) changed.run(); },350,TimeUnit.MILLISECONDS);
    }
    @Override public synchronized void close() {
        if (closed) return; closed = true; if (pending != null) pending.cancel(false);
        scheduler.shutdownNow(); try { watcher.close(); } catch (IOException ignored) { } thread.interrupt(); directories.clear();
    }
}
