package io.quizforge.desktop.ui.question.extension;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quizforge.core.question.content.QuestionContentData;
import io.quizforge.core.question.codec.QuestionDataCodec;
import io.quizforge.desktop.extension.ExtensionManager;
import io.quizforge.desktop.ui.content.document.canvas.CanvasEditorWindow;
import io.quizforge.desktop.ui.question.shared.QuestionEditorContext;
import io.quizforge.desktop.ui.shared.UiTheme;
import javafx.concurrent.Worker;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;

/** One generic authoring host; type-specific controls are supplied by the installed package. */
public final class ExtensionEditorFields {
    private static final ObjectMapper JSON = io.quizforge.infrastructure.json.DocumentJson.mapper();
    private QuestionEditorContext context;
    /** Portable page commands. The desktop owner supplies storage and navigation implementations. */
    public interface Shell {
        java.util.Map<String,Object> state();
        void execute(String action, Object argument);
        default void configureUi(java.util.Map<String,Boolean> preferences) { }
    }
    private Shell shell;
    private boolean commandBusy;
    private Runnable validator;
    private boolean nativeBackend = io.quizforge.desktop.browser.webview2.WebView2LearningSurface.enabled();
    private WebView web;
    private io.quizforge.desktop.browser.webview2.WebView2EditorSurface nativeEditor;
    private boolean preparedClose;
    private java.util.concurrent.CompletionStage<Void> pendingApply;
    private final Host host = new Host();
    private boolean ready;
    private boolean closed;
    private long flushSequence;
    private String pendingFlushId;
    private java.util.concurrent.CompletableFuture<String> pendingFlush;
    private ExtensionEditorFields(QuestionEditorContext context) { this.context = context; }
    public static void render(QuestionEditorContext context) { new ExtensionEditorFields(context).mount(); }
    public static ExtensionEditorFields renderShell(QuestionEditorContext context, Shell shell) {
        var fields = new ExtensionEditorFields(context); fields.shell = shell; fields.mount(); return fields;
    }
    public void showShell(QuestionEditorContext next) {
        if (validator != null) context.validators().remove(validator);
        context = next;
        registerValidator();
        if (ready && !closed) publishShell();
    }
    public void showError(String message) {
        if (shell != null && ready && !closed) callShell("errorFromJson", java.util.Map.of("message", message == null ? "编辑失败" : message));
        else context.errors().getChildren().setAll(UiTheme.label(message == null ? "编辑失败" : message, "editor-error"));
    }
    private boolean editable() {
        return !context.model().bank().questions().isEmpty()
                && io.quizforge.core.question.type.QuestionTypes.find(context.model().bank().questions().get(context.index()).type()).isPresent();
    }
    private void publishShell() { callShell("updateFromJson", shell.state()); }
    private void callShell(String method, Object value) {
        if(nativeBackend){if(nativeEditor!=null)nativeEditor.shell(method,value);return;}
        try {
            var window = (JSObject) web.getEngine().executeScript("window");
            window.setMember("__editorShellData", JSON.writeValueAsString(value));
            try { web.getEngine().executeScript("window.qfEditorShell." + method + "(window.__editorShellData)"); }
            finally { window.removeMember("__editorShellData"); }
        } catch (Exception failure) { context.errors().getChildren().setAll(UiTheme.label("编辑页通信失败", "editor-error")); }
    }
    private void registerValidator() {
        if (shell != null && !editable()) return;
        validator = () -> {
            if (!ready || closed) throw new IllegalStateException("题型编辑器尚未就绪");
            flushEditor();
        };
        context.validators().add(validator);
    }
    private void mount() {
        if(nativeBackend && shell != null){mountNative();return;}
        nativeBackend=false;web=new WebView();
        web.setId("extension-question-editor");web.setContextMenuEnabled(false);
        web.setMinHeight(24);web.setPrefHeight(24);web.setMaxHeight(24);
        web.addEventFilter(javafx.scene.input.ScrollEvent.SCROLL, event -> {
            if (!event.isControlDown() && !event.isMetaDown() && web.getParent() != null)
                javafx.event.Event.fireEvent(web.getParent(), event.copyFor(web.getParent(), web.getParent()));
            event.consume();
        });
        web.getEngine().getLoadWorker().stateProperty().addListener((o,before,after) -> {
            if (closed) return;
            if (after == Worker.State.SUCCEEDED) {
                try {
                    ExtensionManager.getDefault().installScripts(web);
                    var window = (JSObject)web.getEngine().executeScript("window");
                    window.setMember("editorHost", host);window.setMember("extensionEditorHost", host);
                    if (shell != null) {
                        window.setMember("bankEditorHost", host);
                        ready = true; publishShell(); return;
                    }
                    var question = context.model().bank().questions().get(context.index());
                    window.setMember("__editorType", question.type());
                    var data = QuestionDataCodec.encodePersisted(question);
                    window.setMember("__editorQuestion", JSON.writeValueAsString(data));
                    try { web.getEngine().executeScript("window.questionExtensions.mountEditorFromJson(window.__editorType,window.__editorQuestion)"); }
                    finally { window.removeMember("__editorType");window.removeMember("__editorQuestion"); }
                    ready = true;
                } catch (Exception failure) { error(failure); }
            } else if (after == Worker.State.FAILED) error(new IllegalStateException("题型编辑器加载失败"));
        });
        registerValidator();
        web.sceneProperty().addListener((o,before,after) -> {
            if (before != null && after == null && !closed) {
                closed = true;
                if (pendingFlush != null) pendingFlush.completeExceptionally(new IllegalStateException("题型编辑器已关闭"));
                if (ready) web.getEngine().executeScript("window.extensionEditorInstance && window.extensionEditorInstance.destroy && window.extensionEditorInstance.destroy()");
                web.getEngine().load(null);
            }
        });
        context.body().getChildren().add(web);
        var page = ExtensionEditorFields.class.getResource("/editor/draft-canvas/extension-editor.html");
        if (page == null) throw new IllegalStateException("Build the extension editor frontend first");
        web.getEngine().load(page.toExternalForm());
    }
    private void error(Exception failure) {
        showError(failure.getMessage() == null ? "题型扩展编辑失败" : failure.getMessage());
    }
    /** The existing save API is synchronous; a bounded nested FX loop keeps Promise flushes responsive. */
    private void flushEditor() {
        if(nativeBackend){
            if(preparedClose)return;
            var result=flushAsync().toCompletableFuture();
            if(!result.isDone()) {
                Object loopKey=new Object();
                result.whenComplete((v,e)->javafx.application.Platform.runLater(()->javafx.application.Platform.exitNestedEventLoop(loopKey,null)));
                javafx.application.Platform.enterNestedEventLoop(loopKey);
            }
            try{result.join();}catch(java.util.concurrent.CompletionException failure){throw new IllegalStateException(failure.getCause().getMessage(),failure.getCause());}
            return;
        }
        if (pendingFlush != null) throw new IllegalStateException("正在保存题型编辑内容");
        var result = new java.util.concurrent.CompletableFuture<String>();
        pendingFlush = result; pendingFlushId = Long.toString(++flushSequence);
        Object loopKey = new Object();
        var timeout = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(15));
        context.body().setDisable(true);
        try {
            web.getEngine().executeScript("window.extensionEditorFlushToHost('" + pendingFlushId + "')");
            if (!result.isDone()) {
                timeout.setOnFinished(event -> result.completeExceptionally(new IllegalStateException("题型编辑保存超时，请重试")));
                result.whenComplete((draft, failure) -> javafx.application.Platform.exitNestedEventLoop(loopKey, null));
                timeout.play();
                javafx.application.Platform.enterNestedEventLoop(loopKey);
            }
            host.apply(result.join());
        } catch (java.util.concurrent.CompletionException failure) {
            throw new IllegalStateException(failure.getCause().getMessage(), failure.getCause());
        } finally {
            timeout.stop(); pendingFlush = null; pendingFlushId = null; context.body().setDisable(false);
        }
    }
    public boolean nativeBackend(){return nativeBackend;}
    public java.util.concurrent.CompletionStage<Void> ready(){return nativeBackend?nativeEditor.ready():java.util.concurrent.CompletableFuture.completedFuture(null);}
    public java.util.concurrent.CompletionStage<Void> prepareCloseAsync(){
        if(!nativeBackend||closed||!ready)return java.util.concurrent.CompletableFuture.completedFuture(null);
        if(preparedClose)return java.util.concurrent.CompletableFuture.completedFuture(null);
        return flushAsync().thenRun(()->{preparedClose=true;nativeEditor.post(java.util.Map.of("kind","closing","value",true));});
    }
    public void cancelClose(){preparedClose=false;if(nativeBackend&&ready&&!closed)nativeEditor.post(java.util.Map.of("kind","closing","value",false));}
    public void close(){
        if(closed)return;closed=true;
        if(pendingFlush!=null)pendingFlush.completeExceptionally(new IllegalStateException("编辑页面已关闭"));
        if(nativeEditor!=null)nativeEditor.close();else web.getEngine().load(null);
    }
    private java.util.concurrent.CompletionStage<Void> flushAsync(){
        if(!editable())return java.util.concurrent.CompletableFuture.completedFuture(null);
        if(!ready||closed)return java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException("题型编辑器尚未就绪"));
        if(pendingApply!=null)return pendingApply;
        var result=new java.util.concurrent.CompletableFuture<String>();
        pendingFlush=result;pendingFlushId=Long.toString(++flushSequence);
        var timer=new javafx.animation.PauseTransition(javafx.util.Duration.seconds(15));
        timer.setOnFinished(event->result.completeExceptionally(new IllegalStateException("题型编辑保存超时，请重试")));
        context.body().setDisable(true);
        var applied=result.thenAccept(host::apply);
        pendingApply=applied.whenComplete((value,failure)->{
            timer.stop();pendingApply=null;pendingFlush=null;pendingFlushId=null;context.body().setDisable(false);
            if(!closed)nativeEditor.post(java.util.Map.of("kind","closing","value",false));
        });
        var returned=pendingApply;
        try{nativeEditor.post(java.util.Map.of("kind","closing","value",true));nativeEditor.flush(pendingFlushId);timer.play();}
        catch(RuntimeException failure){result.completeExceptionally(failure);}
        return returned;
    }
    private void mountNative(){
        nativeEditor=new io.quizforge.desktop.browser.webview2.WebView2EditorSurface(shell::state,this::receiveNative);
        nativeEditor.view().setId("extension-question-editor");
        javafx.scene.layout.VBox.setVgrow(nativeEditor.view(),javafx.scene.layout.Priority.ALWAYS);
        context.body().getChildren().add(nativeEditor.view());registerValidator();
    }
    public io.quizforge.desktop.browser.webview2.WebView2EditorSurface nativeSurface(){return nativeEditor;}
    private void receiveNative(com.fasterxml.jackson.databind.JsonNode message){
        if(closed)return;
        try {
            switch(message.path("kind").asText()){
                case "editor-ready" -> ready=true;
                case "bank-request" -> {
                    if(preparedClose)throw new IllegalStateException("编辑页面正在关闭");
                    String id=message.path("requestId").asText();if(!id.matches("[1-9][0-9]{0,15}"))throw new IllegalArgumentException("无效请求编号");
                    host.request(id,message.path("encoded").asText());
                }
                case "editor-call" -> nativeCall(message);
                case "configure-ui" -> host.configureUi(message.path("questionId").asText(),message.path("encoded").asText());
                case "flushed" -> host.flushed(message.path("id").asText(),message.path("draft").asText());
                case "flush-failed" -> host.flushFailed(message.path("id").asText(),message.path("message").asText());
                case "reload-failed","native-error","page-error" -> {
                    var failure=new IllegalStateException(message.path("message").asText("编辑页面操作失败"));
                    if(pendingFlush!=null)pendingFlush.completeExceptionally(failure);error(failure);
                }
                default -> throw new IllegalArgumentException("不支持的编辑消息");
            }
        }catch(Exception failure){error(failure);}
    }
    private void nativeCall(com.fasterxml.jackson.databind.JsonNode message){
        String id=message.path("id").asText();if(!id.matches("[1-9][0-9]{0,15}"))return;
        java.util.Map<String,Object> reply;
        try {
            if(preparedClose&&!"resolveContent".equals(message.path("method").asText())||!editable()||!context.model().bank().questions().get(context.index()).id().equals(message.path("questionId").asText()))
                throw new IllegalArgumentException("题目已切换或编辑页面正在关闭");
            String encoded=message.path("encoded").asText();
            String value=switch(message.path("method").asText()){
                case "changed" -> {host.changed(encoded);yield "";}
                case "editContent" -> host.editContent(encoded);
                case "resolveContent" -> host.resolveContent(encoded);
                default -> throw new IllegalArgumentException("不支持的编辑接口");
            };
            reply=java.util.Map.of("ok",true,"data",value);
        }catch(Exception failure){
            Throwable cause=failure;while(cause.getCause()!=null)cause=cause.getCause();
            var details=new java.util.LinkedHashMap<String,Object>();details.put("code","EDITOR_COMMAND_FAILED");details.put("message",cause.getMessage()==null?"编辑操作失败":cause.getMessage());details.put("retryable",false);
            if(cause instanceof io.quizforge.core.question.type.extension.ExtensionDataValidationException invalid){details.put("code","DATA_VALIDATION_FAILED");details.put("issues",invalid.issues());}
            if(cause instanceof io.quizforge.core.question.type.extension.ExtensionExecutionException execution)details.put("code",execution.code());
            reply=java.util.Map.of("ok",false,"error",details);
        }
        if(!closed)nativeEditor.post(java.util.Map.of("kind","editor-reply","id",id,"reply",reply));
    }
    public final class Host {
        /** Read-only visible area; the HTML editor still reports its natural height for outer scrolling. */
        public double viewportHeight() {
            if(nativeBackend)return nativeEditor.view().getHeight();
            for (javafx.scene.Parent parent=web.getParent();parent!=null;parent=parent.getParent())
                if(parent instanceof javafx.scene.control.ScrollPane scroll && scroll.getViewportBounds().getHeight()>0)
                    return scroll.getViewportBounds().getHeight();
            return web.getScene()==null?web.getHeight():web.getScene().getHeight();
        }
        public void configureUi(String questionId,String encoded) {
            if(shell==null || closed)return;
            try {
                var preferences=io.quizforge.desktop.ui.question.shared.QuestionUiPreferences.validate(
                        JSON.readValue(encoded,new TypeReference<java.util.Map<String,Object>>() { }),true);
                javafx.application.Platform.runLater(()->{
                    if(closed || !editable() || !questionId.equals(context.model().bank().questions().get(context.index()).id()))return;
                    shell.configureUi(preferences);publishShell();
                });
            }catch(Exception failure){error(failure);}
        }
        /** Dispatch after the JS callback returns; no native nested loop is entered from a page command. */
        public void request(String requestId, String encoded) {
            javafx.application.Platform.runLater(() -> {
                if (closed || shell == null) return;
                java.util.Map<String,Object> reply;
                if (commandBusy || pendingFlush != null) reply = failed("BUSY", "正在处理编辑操作，请稍后重试");
                else {
                    commandBusy = true;
                    try {
                        var request = JSON.readValue(encoded, new TypeReference<java.util.Map<String,Object>>() { });
                        if (!(request.get("action") instanceof String action) || !java.util.Set.of("save","navigate","add","duplicate","delete","source.add","source.remove","source.open").contains(action))
                            throw new IllegalArgumentException("不支持的编辑操作");
                        String questionId = context.model().bank().questions().isEmpty() ? "" : context.model().bank().questions().get(context.index()).id();
                        if (!questionId.equals(request.get("questionId"))) throw new IllegalArgumentException("题目已切换，请重新操作");
                        if (editable() && request.get("draft") != null) {
                            var draft = io.quizforge.core.question.type.extension.ExternalQuestionTypeDefinition.object(request.get("draft"));
                            if (!questionId.equals(draft.get("id"))) throw new IllegalArgumentException("编辑草稿不属于当前题目");
                            if (!context.model().bank().questions().get(context.index()).type().equals(draft.get("type")))
                                throw new IllegalArgumentException("不能更改题目身份和题型");
                            apply(JSON.writeValueAsString(draft));
                        }
                        shell.execute(action, request.get("argument"));
                        reply = java.util.Map.of("ok",true,"data",shell.state());
                    } catch (Exception failure) {
                        if (failure instanceof io.quizforge.core.question.type.extension.ExtensionDataValidationException invalid) {
                            reply = java.util.Map.of("ok",false,"error",java.util.Map.of("code","DATA_VALIDATION_FAILED",
                                    "message",invalid.getMessage(),"retryable",false,"issues",invalid.issues()));
                        } else if (failure instanceof io.quizforge.core.question.type.extension.ExtensionExecutionException execution) {
                            reply = failed(execution.code(), execution.getMessage());
                        } else reply = failed("EDITOR_COMMAND_FAILED", failure.getMessage() == null ? "编辑操作失败" : failure.getMessage());
                    } finally { commandBusy = false; }
                }
                if (!closed) callShell("replyFromJson", java.util.Map.of("requestId",requestId,"reply",reply));
            });
        }
        private java.util.Map<String,Object> failed(String code, String message) {
            return java.util.Map.of("ok",false,"error",java.util.Map.of("code",code,"message",message,"retryable",false));
        }
        public void contentHeight(double height) {
            if(nativeBackend)return;
            if (closed || !Double.isFinite(height) || height <= 0) return;
            double size = Math.max(24, Math.ceil(height));
            if (Math.abs(web.getPrefHeight() - size) < 1) return;
            web.setMinHeight(size);web.setPrefHeight(size);web.setMaxHeight(size);
        }
        public void reloadFailed(String message) { error(new IllegalStateException(message)); }
        public String newId(String prefix) {
            if (prefix == null || !prefix.matches("[A-Za-z][A-Za-z0-9_]{0,31}"))
                throw new IllegalArgumentException("Invalid editor identity prefix");
            return prefix + java.util.UUID.randomUUID();
        }
        public void flushed(String id, String draft) {
            if (id.equals(pendingFlushId) && pendingFlush != null) pendingFlush.complete(draft);
        }
        public void flushFailed(String id, String message) {
            if (id.equals(pendingFlushId) && pendingFlush != null) pendingFlush.completeExceptionally(new IllegalStateException(message));
        }
        public String resolveContent(String encoded) {
            try {
                var raw = JSON.readValue(encoded, Object.class);
                var content = QuestionContentData.decode(raw);
                var ids = QuestionContentData.resourceIds(content);
                var bytes = new java.util.LinkedHashMap<String,String>();
                var resources = new java.util.ArrayList<java.util.Map<String,String>>();
                for (var resource : context.model().bank().resources()) {
                    if (!ids.contains(resource.id())) continue;
                    var pending = context.imported().get(resource.id());
                    try (var stream = pending == null ? context.resources().open(resource) : pending.open()) {
                        if (stream == null) throw new IllegalStateException("富文本资源缺失：" + resource.id());
                        bytes.put(resource.id(), java.util.Base64.getEncoder().encodeToString(stream.readAllBytes()));
                    }
                    resources.add(java.util.Map.of("id",resource.id(),"mediaType",resource.mediaType()));
                }
                return JSON.writeValueAsString(io.quizforge.desktop.learning.SharedContent.read(raw,
                        java.util.Map.of("resourceData",bytes,"resources",resources)));
            } catch (Exception failure) { error(failure);throw new IllegalArgumentException("无法显示扩展富文本",failure); }
        }
        public void changed(String encoded) {
            try {
                // Delayed callbacks from a destroyed page cannot overwrite the newly selected question.
                var fields = JSON.readValue(encoded, new TypeReference<java.util.Map<String,Object>>() { });
                if (!editable() || !context.model().bank().questions().get(context.index()).id().equals(fields.get("id"))) return;
                apply(encoded);context.errors().getChildren().clear();
            } catch (Exception failure) { error(failure);throw new IllegalArgumentException(failure.getMessage(),failure); }
        }
        private void apply(String encoded) {
            try {
                var fields = JSON.readValue(encoded, new TypeReference<java.util.Map<String,Object>>() { });
                context.model().setEditorQuestion(context.index(), fields);
            } catch (java.io.IOException failure) { throw new IllegalArgumentException("扩展编辑器返回了无效数据", failure); }
        }
        public String editContent(String encoded) {
            try {
                var content = QuestionContentData.decode(JSON.readValue(encoded, Object.class));
                var result = CanvasEditorWindow.openEditor(context.owner().get(), "编辑富文本", content,
                        context.model().bank().resources(), context.resources());
                if (!result.saved()) return encoded;
                for (var added : result.addedResources()) {
                    if (context.model().bank().resources().stream().noneMatch(resource -> resource.id().equals(added.resource().id())))
                        context.model().addResource(added.resource());
                    context.imported().put(added.resource().id(), added);
                }
                return JSON.writeValueAsString(QuestionContentData.encode(result.content()));
            } catch (Exception failure) { error(failure);return encoded; }
        }
    }
}
