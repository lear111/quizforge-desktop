package io.quizforge.desktop.extension;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;

/** Private helper entry point. Only the host owns its stdin/stdout; there is no Java bridge in the page. */
public final class ExtensionRuleWorker {
    private final ObjectMapper json = new ObjectMapper();
    private WebView web;
    private boolean initialized;
    public static void main(String[] args) throws Exception {
        ExtensionWorkerProcess.watchParent(Long.parseLong(args[0]));
        ExtensionRuleWorker worker = new ExtensionRuleWorker();
        CompletableFuture<Void> ready = new CompletableFuture<>();
        Platform.startup(() -> { Platform.setImplicitExit(false); worker.load(ready); }); ready.join();
        try (var input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
             var output = new BufferedWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8))) {
            String line;
            while ((line = ExtensionRuleProtocol.readLine(input)) != null) {
                String request = line;
                CompletableFuture<Map<String,Object>> response = new CompletableFuture<>();
                Platform.runLater(() -> {
                    try { response.complete(Map.of("ok", true, "value", worker.execute(request))); }
                    catch (Throwable failure) { response.complete(Map.of("ok", false, "message", "题型规则执行失败：" + failure.getClass().getSimpleName())); }
                });
                String encoded = worker.json.writeValueAsString(response.join());
                if (encoded.length() > ExtensionRuleProtocol.MAX_LINE) throw new IOException("Extension worker response too large");
                output.write(encoded); output.newLine(); output.flush();
            }
        } finally { Runtime.getRuntime().halt(0); }
    }
    private void load(CompletableFuture<Void> ready) {
        try {
            web = new WebView(); web.setContextMenuEnabled(false);
            web.getEngine().setCreatePopupHandler(features -> null); web.getEngine().setConfirmHandler(message -> false);
            web.getEngine().setOnAlert(event -> {});
            web.getEngine().getLoadWorker().stateProperty().addListener((property, previous, state) -> {
                if (initialized && state == Worker.State.SCHEDULED) Runtime.getRuntime().halt(2);
                if (!initialized && state == Worker.State.SUCCEEDED) ready.complete(null);
                if (state == Worker.State.FAILED || state == Worker.State.CANCELLED) ready.completeExceptionally(new IOException("Rules page load failed"));
            });
            web.getEngine().loadContent("<!doctype html><meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none';script-src 'unsafe-inline' 'unsafe-eval';connect-src 'none';frame-src 'none';form-action 'none';base-uri 'none'\"><title>QuizForge rules</title>");
        } catch (Throwable failure) { ready.completeExceptionally(failure); }
    }
    private Object execute(String encoded) throws IOException {
        Map<String,Object> request = json.readValue(encoded, new TypeReference<>() {});
        JSObject window = (JSObject) web.getEngine().executeScript("window");
        String command = (String)request.get("command");
        if ("initialize".equals(command)) {
            if (initialized) throw new IllegalStateException("Already initialized");
            try (var resource = java.util.Objects.requireNonNull(getClass().getResourceAsStream("/editor/draft-canvas/extension-rules-runtime.js"))) {
                web.getEngine().executeScript(new String(resource.readAllBytes(), StandardCharsets.UTF_8));
            }
            web.getEngine().executeScript("window.fetch=undefined;window.XMLHttpRequest=undefined;window.WebSocket=undefined;window.Worker=undefined;window.SharedWorker=undefined;window.open=undefined;");
            initialized = true;
        } else if (!initialized) throw new IllegalStateException("Not initialized");
        window.setMember("__hostRequest", encoded);
        try {
            return switch (command) {
                case "initialize" -> {
                    web.getEngine().executeScript("(()=>{const r=JSON.parse(window.__hostRequest);Object.entries(r.templates).forEach(([type,q])=>QF.installDefaultQuestion(type,q));(0,eval)(r.source);})()"); yield true;
                }
                case "has" -> web.getEngine().executeScript("QuestionRules.has(JSON.parse(window.__hostRequest).type)");
                case "parse" -> {
                    web.getEngine().executeScript("JSON.parse(window.__hostRequest).sources.forEach(source=>new Function('QF','\"use strict\"; return (async()=>{\\n'+source+'\\n})();'))"); yield true;
                }
                case "invoke" -> {
                    Object value = web.getEngine().executeScript("(()=>{const r=JSON.parse(window.__hostRequest);return QuestionRules.invoke(r.type,r.operation,r.input);})()");
                    if (!(value instanceof String text) || text.length() > 8 * 1024 * 1024) throw new IllegalStateException("Invalid rules output");
                    yield json.readValue(text, new TypeReference<Map<String,Object>>() {});
                }
                default -> throw new IllegalArgumentException("Unknown worker command");
            };
        } finally { window.removeMember("__hostRequest"); }
    }
}
