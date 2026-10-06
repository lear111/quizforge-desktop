package io.quizforge.desktop.extension;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quizforge.core.question.type.extension.ExtensionExecutionException;
import io.quizforge.core.question.type.extension.QuestionExtensionRules;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** One pinned package/version per killable process; no extension code executes in the application's WebKit. */
public final class ExtensionRuleRuntime implements AutoCloseable {
    static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(45);
    static final Duration CALL_TIMEOUT = Duration.ofSeconds(3);
    private final ObjectMapper json = new ObjectMapper();
    private final ExecutorService io = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "qf-extension-rules-ipc"); thread.setDaemon(true); return thread;
    });
    private final AtomicReference<Worker> worker = new AtomicReference<>();
    private final AtomicLong generation = new AtomicLong();
    private final CompletableFuture<Void> ready = new CompletableFuture<>();
    private final String initialization;
    private final Duration startupTimeout, callTimeout;
    private volatile boolean closed;

    public ExtensionRuleRuntime(String source) { this(source, Map.of()); }
    public ExtensionRuleRuntime(String source, Map<String,Map<String,Object>> templates) {
        this(source, templates, STARTUP_TIMEOUT, CALL_TIMEOUT);
    }
    ExtensionRuleRuntime(String source, Map<String,Map<String,Object>> templates, Duration startupTimeout, Duration callTimeout) {
        this.startupTimeout = startupTimeout; this.callTimeout = callTimeout;
        try { initialization = json.writeValueAsString(Map.of("command", "initialize", "source", source, "templates", templates)); }
        catch (IOException failure) { throw new IllegalArgumentException("Invalid extension initialization", failure); }
        if (initialization.length() > ExtensionRuleProtocol.MAX_LINE) throw new IllegalArgumentException("Extension rules input is too large");
        CompletableFuture.runAsync(() -> exchange(Map.of("command", "has", "type", "__startup_probe__"), callTimeout),
                CompletableFuture.delayedExecutor(0, TimeUnit.MILLISECONDS)).whenComplete((value, failure) -> {
            if (failure == null) ready.complete(null); else ready.completeExceptionally(failure);
        });
    }
    public CompletionStage<Void> ready() { return ready.minimalCompletionStage(); }
    /** Syntax checks are performed in the worker, without evaluating the candidate page scripts. */
    public void validatePageScripts(List<String> sources) { requireReady(); exchange(Map.of("command", "parse", "sources", sources), callTimeout); }
    public boolean hasRules(String type) { requireReady(); return Boolean.TRUE.equals(exchange(Map.of("command", "has", "type", type), callTimeout).get("value")); }
    public QuestionExtensionRules rules(String type) { return (operation, input) -> invoke(type, operation, input); }
    public Map<String,Object> invoke(String type, String operation, Map<String,Object> input) {
        requireReady();
        Map<String,Object> reply = exchange(Map.of("command", "invoke", "type", type, "operation", operation, "input", input), callTimeout);
        if (!(reply.get("value") instanceof Map<?,?> value)) { stopWorker(); throw failed("Invalid extension rule response", null); }
        @SuppressWarnings("unchecked") Map<String,Object> result = (Map<String,Object>) value;
        return result;
    }
    private void requireReady() {
        if (closed || !ready.isDone() || ready.isCompletedExceptionally())
            throw new ExtensionExecutionException("EXTENSION_UNAVAILABLE", "题型规则尚未就绪或已关闭，请重新打开题型扩展。");
    }
    private synchronized Map<String,Object> exchange(Map<String,Object> request, Duration timeout) {
        if (closed) throw new ExtensionExecutionException("EXTENSION_UNAVAILABLE", "题型规则已关闭。");
        long expected = generation.get();
        Future<Map<String,Object>> pending;
        try {
            if (worker.get() == null) await(io.submit(() -> startWorker(expected)), startupTimeout);
            pending = io.submit(() -> {
            if (closed || expected != generation.get()) throw failed("Extension operation was cancelled", null);
            Worker current = worker.get();
            if (current == null) throw failed("Extension operation was cancelled", null);
            try {
                String encoded = json.writeValueAsString(request);
                if (encoded.length() > ExtensionRuleProtocol.MAX_LINE) throw failed("Extension rules input is too large", null);
                current.output.write(encoded); current.output.newLine(); current.output.flush();
                return readReply(current);
            } catch (IOException failure) { throw failed("规则进程已退出，请重试。", failure); }
        }); } catch (RejectedExecutionException failure) { throw new ExtensionExecutionException("EXTENSION_UNAVAILABLE", "题型规则已关闭。", failure); }
        return await(pending, timeout);
    }
    private <T> T await(Future<T> pending, Duration timeout) {
        try { return pending.get(timeout.toMillis(), TimeUnit.MILLISECONDS); }
        catch (TimeoutException failure) {
            stopWorker(); pending.cancel(true);
            throw new ExtensionExecutionException("EXTENSION_TIMEOUT", "题型规则执行超时，已停止运行；已保存的答案和草稿保留，可以再次尝试。", failure);
        } catch (InterruptedException failure) {
            stopWorker(); pending.cancel(true); Thread.currentThread().interrupt();
            throw failed("题型规则调用已取消。", failure);
        } catch (ExecutionException failure) {
            stopWorker();
            if (failure.getCause() instanceof ExtensionExecutionException execution) throw execution;
            throw failed("题型规则执行失败，请重试。", failure.getCause());
        }
    }
    private Worker startWorker(long expected) throws IOException {
        try {
            Worker next = new Worker(ExtensionWorkerProcess.start(ExtensionRuleWorker.class));
            if (closed || expected != generation.get() || !worker.compareAndSet(null, next)) { next.close(); throw failed("Extension operation was cancelled", null); }
            next.output.write(initialization); next.output.newLine(); next.output.flush(); readReply(next);
            return next;
        } catch (IOException failure) {
            throw new ExtensionExecutionException("EXTENSION_UNAVAILABLE", "题型规则隔离环境无法启动，已停止加载。", failure);
        }
    }
    private Map<String,Object> readReply(Worker current) throws IOException {
        String line = ExtensionRuleProtocol.readLine(current.input);
        if (line == null) throw new EOFException("Extension worker exited");
        Map<String,Object> reply = json.readValue(line, new TypeReference<>() {});
        if (!Boolean.TRUE.equals(reply.get("ok"))) {
            String message = reply.get("message") instanceof String text ? text : "题型规则执行失败。";
            throw failed(message.substring(0, Math.min(message.length(), 1024)), null);
        }
        return reply;
    }
    private static ExtensionExecutionException failed(String message, Throwable cause) { return new ExtensionExecutionException("EXTENSION_FAILED", message, cause); }
    private void stopWorker() { generation.incrementAndGet(); Worker previous = worker.getAndSet(null); if (previous != null) previous.close(); }
    @Override public void close() {
        if (closed) return; closed = true; stopWorker(); io.shutdownNow();
        ready.completeExceptionally(new ExtensionExecutionException("EXTENSION_UNAVAILABLE", "题型规则已关闭。"));
    }
    // Exposes only lifecycle facts for tests, never a bridge to the extension page.
    long workerPid() { Worker current = worker.get(); return current == null ? -1 : current.process.pid(); }
    private static final class Worker implements AutoCloseable {
        final Process process; final BufferedReader input; final BufferedWriter output;
        Worker(Process process) {
            this.process = process;
            input = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            output = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        }
        @Override public void close() {
            // Kill first: closing a reader held by a blocked read would itself block the caller.
            process.destroyForcibly();
        }
    }
}
