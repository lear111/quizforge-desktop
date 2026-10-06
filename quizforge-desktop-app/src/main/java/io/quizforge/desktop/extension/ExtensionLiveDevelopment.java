package io.quizforge.desktop.extension;

import io.quizforge.infrastructure.extension.ExtensionDevelopmentSource;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import javafx.application.Platform;

/** One session-scoped source watcher, shared by all browser pages. */
final class ExtensionLiveDevelopment implements AutoCloseable {
    private final ExtensionDevelopmentSource source;
    private final ExtensionDevelopmentWatcher watcher;
    private final Consumer<ExtensionDevelopmentSource.Candidate> publish;
    private final Consumer<String> status;
    private boolean closed;
    private boolean again;
    private String revision;
    private CompletableFuture<Void> loading;

    ExtensionLiveDevelopment(Path directory, Consumer<ExtensionDevelopmentSource.Candidate> publish,
            Consumer<String> status) throws java.io.IOException {
        source = new ExtensionDevelopmentSource(directory); this.publish = publish; this.status = status;
        watcher = new ExtensionDevelopmentWatcher(source.directory(), () -> Platform.runLater(() -> {
            if (!closed) refresh().exceptionally(failure -> null);
        }));
    }
    CompletableFuture<Void> refresh() {
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException("开发目录已关闭"));
        if (loading != null) { again = true; return loading; }
        var result = new CompletableFuture<Void>(); loading = result;
        CompletableFuture.supplyAsync(() -> {
            try { return source.load(); }
            catch (Exception failure) { throw new java.util.concurrent.CompletionException(failure); }
        }).whenComplete((candidate, failure) -> Platform.runLater(() -> {
            try {
                if (closed) throw new IllegalStateException("开发目录已关闭");
                if (failure != null) throw new java.util.concurrent.CompletionException(failure);
                if (!candidate.revision().equals(revision)) {
                    publish.accept(candidate); revision = candidate.revision();
                }
                status.accept("主浏览区实时预览已启用 · " + source.directory());
                result.complete(null);
            } catch (Exception problem) {
                Throwable cause = problem; while (cause.getCause() != null) cause = cause.getCause();
                if (!closed) status.accept("实时预览更新失败，保留上一次可用页面：" + cause.getMessage());
                result.completeExceptionally(cause);
            } finally {
                loading = null;
                if (again && !closed) { again = false; refresh().exceptionally(problem -> null); }
            }
        }));
        return result;
    }
    @Override public void close() {
        if (closed) return; closed = true; watcher.close();
        if (loading != null) loading.completeExceptionally(new IllegalStateException("开发目录已关闭"));
    }
}
