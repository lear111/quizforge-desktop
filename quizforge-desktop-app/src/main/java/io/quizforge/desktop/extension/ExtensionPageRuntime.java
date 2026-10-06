package io.quizforge.desktop.extension;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;

/** Killable page process. All pipe traffic and supervision run outside the application's FX thread. */
public final class ExtensionPageRuntime implements AutoCloseable {
    static final Duration STARTUP = Duration.ofSeconds(45), STALL = Duration.ofSeconds(5);
    private static final ScheduledExecutorService WATCH = Executors.newSingleThreadScheduledExecutor(r -> daemon(r, "qf-page-watchdog"));
    private final ObjectMapper json = new ObjectMapper();
    private final ExecutorService writer = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(128), r -> daemon(r, "qf-page-writer"), new ThreadPoolExecutor.AbortPolicy());
    private final AtomicReference<Process> process = new AtomicReference<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Consumer<Map<String,Object>> events;
    private final long started = System.nanoTime();
    private final Duration startup, stall;
    private volatile long lastPulse;
    private volatile BufferedWriter output;
    private ScheduledFuture<?> watch;
    public ExtensionPageRuntime(String document, String session, int width, Consumer<Map<String,Object>> events) {
        this(document, session, width, events, STARTUP, STALL);
    }
    ExtensionPageRuntime(String document, String session, int width, Consumer<Map<String,Object>> events, Duration startup, Duration stall) {
        this.events = events; this.startup = startup; this.stall = stall;
        watch = WATCH.scheduleAtFixedRate(this::check, 200, 200, TimeUnit.MILLISECONDS);
        writer.execute(() -> {
            try {
                Process next = ExtensionWorkerProcess.start(ExtensionPageWorker.class);
                process.set(next);
                if (closed.get()) { next.destroyForcibly(); return; }
                output = new BufferedWriter(new OutputStreamWriter(next.getOutputStream(), StandardCharsets.UTF_8));
                Thread reader = daemon(() -> read(next), "qf-page-reader"); reader.start();
                write(Map.of("command", "initialize", "document", document, "session", session, "width", width));
            } catch (Exception failure) { fail("EXTENSION_UNAVAILABLE", "题型页面隔离环境无法启动，已停止加载，请重新加载。"); }
        });
    }
    public void send(Map<String,Object> command) {
        if (closed.get()) return;
        try { writer.execute(() -> { if (!closed.get()) try { write(command); } catch (Exception failure) { fail("EXTENSION_FAILED", "题型页面进程已停止，请重新加载。"); } }); }
        catch (RejectedExecutionException overload) { if (!closed.get()) fail("EXTENSION_FAILED", "题型页面输入队列已满，已停止页面。"); }
    }
    private void write(Map<String,Object> command) throws IOException {
        String line = json.writeValueAsString(command);
        if (line.length() > ExtensionRuleProtocol.MAX_LINE) throw new IOException("Page command too large");
        output.write(line); output.newLine(); output.flush();
    }
    private void read(Process child) {
        long windowStart=System.nanoTime();int messages=0;
        try (var reader = new BufferedReader(new InputStreamReader(child.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while (!closed.get() && (line = ExtensionRuleProtocol.readLine(reader)) != null) {
                Map<String,Object> value = json.readValue(line, new TypeReference<>() {});
                // Only a trusted worker FX task can generate a pulse, never extension postMessage traffic.
                if ("pulse".equals(value.get("kind"))) {
                    if (lastPulse == 0 && Boolean.getBoolean("quizforge.sandbox.timings")) System.err.println("Sandbox page first pulse ms=" + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
                    lastPulse = System.nanoTime();
                }
                else {
                    long now=System.nanoTime();if(now-windowStart>TimeUnit.SECONDS.toNanos(1)){windowStart=now;messages=0;}
                    if(++messages>256){fail("EXTENSION_FAILED","题型页面发送请求过于频繁，已停止页面。");return;}
                    if(!Set.of("page","image","failure").contains(value.get("kind")))throw new IOException("Unknown page response");
                    if("failure".equals(value.get("kind"))){fail("EXTENSION_FAILED","题型页面渲染失败，请重新加载。");return;}
                    events.accept(value);
                }
            }
            if (!closed.get()) fail(lastPulse == 0 ? "EXTENSION_UNAVAILABLE" : "EXTENSION_FAILED", lastPulse == 0 ? "题型页面隔离环境无法启动，已停止加载。" : "题型页面进程意外退出，请重新加载。");
        } catch (Exception failure) { if (!closed.get()) fail(lastPulse == 0 ? "EXTENSION_UNAVAILABLE" : "EXTENSION_FAILED", lastPulse == 0 ? "题型页面隔离环境无法启动，已停止加载。" : "题型页面响应无效，已停止页面。"); }
    }
    private void check() {
        if (closed.get()) return;
        long now = System.nanoTime(), pulse = lastPulse;
        if (now - (pulse == 0 ? started : pulse) > (pulse == 0 ? startup : stall).toNanos())
            fail("EXTENSION_TIMEOUT", pulse == 0 ? "题型页面启动超时，已停止运行；已保存的答案和白板保留，请重新加载题卡。" : "题型页面执行超时，已停止运行；已保存的答案和白板保留，请重新加载题卡。");
    }
    private void fail(String code, String message) {
        if (!stop()) return;
        events.accept(Map.of("kind", "failure", "code", code, "message", message));
    }
    private boolean stop() {
        if (!closed.compareAndSet(false, true)) return false;
        if (watch != null) watch.cancel(false);
        Process child = process.getAndSet(null); if (child != null) child.destroyForcibly();
        writer.shutdownNow(); return true;
    }
    @Override public void close() { stop(); }
    public boolean isClosed() { return closed.get(); }
    long workerPid() { Process p = process.get(); return p == null ? -1 : p.pid(); }
    private static Thread daemon(Runnable action, String name) { Thread thread = new Thread(action, name); thread.setDaemon(true); return thread; }
}
