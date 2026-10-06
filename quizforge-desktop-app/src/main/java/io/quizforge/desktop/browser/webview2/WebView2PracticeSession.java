package io.quizforge.desktop.browser.webview2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quizforge.core.practice.PersistentPracticeRuntime;
import io.quizforge.core.practice.draft.DraftCanvasDocument;
import io.quizforge.desktop.learning.SharedPracticeAdapter;
import io.quizforge.infrastructure.json.DocumentJson;
import io.quizforge.infrastructure.persistence.practice.DraftCanvasJsonCodec;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Learning message adapter. FIFO persistence and grading run off both native and JavaFX UI threads. */
public final class WebView2PracticeSession implements AutoCloseable {
    private final PersistentPracticeRuntime runtime;
    private final SharedPracticeAdapter adapter;
    private final List<Map<String,Object>> packages;
    private final Consumer<Object> send;
    private final Consumer<JsonNode> status;
    private final ThreadPoolExecutor business = new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(128),r->{var thread=new Thread(r,"qf-webview2-practice");thread.setDaemon(true);return thread;});
    private final DraftCanvasJsonCodec drafts=new DraftCanvasJsonCodec();
    private volatile boolean closed, suspended;
    private long lastOperation;
    private String mode="PRACTICE";

    public WebView2PracticeSession(PersistentPracticeRuntime runtime,List<Map<String,Object>> packages,Consumer<Object> send,Consumer<JsonNode> status) {
        this.runtime=runtime;adapter=new SharedPracticeAdapter(runtime);this.packages=List.copyOf(packages);this.send=send;this.status=status;
    }
    public void receive(JsonNode message) {
        if(closed || !message.isObject())return;
        try {business.execute(()->{
            if(closed || suspended)return;
            try {
                switch(message.path("kind").asText()) {
                    case "boot" -> send.accept(Map.of("kind","bootstrap","packages",packages,"viewModel",model(),"draft",draft()));
                    case "practice-event" -> event(message.path("event"));
                    case "navigate-ready" -> {
                        var index=message.path("index");if(!index.isInt()||index.asInt()<0||index.asInt()>=runtime.snapshot().questions().size())throw new IllegalArgumentException("Invalid question index");
                        runtime.goTo(index.asInt());lastOperation=0;
                        send.accept(Map.of("kind","replace","viewModel",model(),"draft",draft()));
                    }
                    case "mode-ready" -> {
                        var value=message.path("mode").asText();if(!List.of("PRACTICE","DRAFT").contains(value))throw new IllegalArgumentException("Invalid mode");mode=value;status.accept(message);
                    }
                    case "preferences" -> { // Validated chrome preferences; no new capability is granted.
                        if(message.path("questionId").asText().equals(adapter.viewModel().question().sessionQuestionId()))
                            {io.quizforge.desktop.ui.question.shared.QuestionUiPreferences.validate(DocumentJson.mapper().convertValue(message.path("value"),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){}),false);status.accept(message);}
                    }
                    case "rendered","close-ready","page-error","native-error" -> status.accept(message);
                    default -> throw new IllegalArgumentException("Unsupported browser message");
                }
            } catch(Exception failure){status.accept(DocumentJson.mapper().valueToTree(Map.of("kind","host-error","message",failure.getMessage()==null?"操作失败":failure.getMessage())));}
        });}catch(RejectedExecutionException overloaded){status.accept(DocumentJson.mapper().valueToTree(Map.of("kind","host-error","message","操作队列已满")));}
    }
    private JsonNode model() throws Exception {return DocumentJson.mapper().readTree(adapter.viewModelJson());}
    private ObjectNode draft() {
        var saved=adapter.findDisplayedDraft();var payload=DocumentJson.mapper().createObjectNode().put("hasSavedDraft",saved.isPresent());
        try {payload.set("document",DocumentJson.mapper().readTree(drafts.encode(saved.orElseGet(DraftCanvasDocument::createEmpty))));return payload;}
        catch(Exception failure){throw new IllegalStateException(failure);}
    }
    private void event(JsonNode event) throws Exception {
        var seq=event.path("operationSeq");
        if(!seq.isIntegralNumber()||!seq.canConvertToLong()||seq.asLong()<=lastOperation||seq.asLong()>9_007_199_254_740_991L)return;
        var before=model();
        for(var field:List.of("sessionId","sessionQuestionId")) {
            var expected=field.equals("sessionId")?before.path("session").path(field):before.path("question").path(field);
            if(!event.path(field).isTextual()||!event.path(field).equals(expected))return;
        }
        lastOperation=seq.asLong();
        var response=DocumentJson.mapper().createObjectNode().put("operationSeq",lastOperation).put("operationType",event.path("type").asText());
        try {
            switch(event.path("type").asText()) {
                case "ANSWER_CHANGED" -> {
                    if(!event.path("answer").isObject())throw new IllegalArgumentException("Answer must be an object");
                    adapter.extensionAnswerChanged(DocumentJson.mapper().convertValue(event.path("answer"),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){}));
                }
                case "DRAFT_CHANGED" -> {
                    var document=DraftCanvasJsonCodec.decode(event.path("document").toString());
                    if(!mode.equals("DRAFT")&&!(event.path("initialLayout").asBoolean()&&initialLayoutOnly(document)))throw new IllegalStateException("练习模式不能修改笔迹");
                    adapter.saveDraft(document);
                }
                case "SUBMIT" -> adapter.submit();
                case "RETRY" -> adapter.retry();
                default -> throw new IllegalArgumentException("Unsupported practice event");
            }
            response.put("status","SUCCESS").putNull("error");response.set("viewModel",model());
            if(List.of("SUBMIT","RETRY").contains(event.path("type").asText()))response.set("draftDocument",draft().get("document"));
        } catch(Exception failure) {
            response.put("status","ERROR");response.set("viewModel",before);
            var error=response.putObject("error").put("message",failure.getMessage()==null?"操作失败":failure.getMessage());
            if(failure instanceof io.quizforge.core.question.type.extension.ExtensionExecutionException execution)error.put("code",execution.code());
            if(failure instanceof io.quizforge.core.question.type.extension.ExtensionDataValidationException invalid){error.put("code","DATA_VALIDATION_FAILED");error.set("issues",DocumentJson.mapper().valueToTree(invalid.issues()));}
        }
        send.accept(Map.of("kind","response","response",response));
        if(response.path("status").asText().equals("SUCCESS")&&!event.path("type").asText().equals("DRAFT_CHANGED"))status.accept(DocumentJson.mapper().createObjectNode().put("kind","state-changed"));
    }
    void suspend(){suspended=true;}
    void resume(){suspended=false;}
    CompletionStage<Void> reload(long commandId){return queued(()->{lastOperation=0;suspended=false;send.accept(Map.of("kind","replace","commandId",commandId,"viewModel",model(),"draft",draft()));});}
    CompletionStage<Void> setMode(String value){return queued(()->{if(!List.of("PRACTICE","DRAFT").contains(value))throw new IllegalArgumentException("Invalid mode");mode=value;send.accept(Map.of("kind","mode","mode",value));});}
    private interface Work{void run()throws Exception;}
    private CompletionStage<Void> queued(Work work){
        var result=new CompletableFuture<Void>();
        if(closed)return CompletableFuture.failedFuture(new IllegalStateException("Practice page closed"));
        try{business.execute(()->{try{if(closed)throw new IllegalStateException("Practice page closed");work.run();result.complete(null);}catch(Exception failure){result.completeExceptionally(failure);}});}catch(RejectedExecutionException failure){result.completeExceptionally(failure);}
        return result.minimalCompletionStage();
    }
    private boolean initialLayoutOnly(DraftCanvasDocument document) {
        var saved=adapter.findDisplayedDraft();if(saved.isPresent())return saved.get().equals(document);
        var empty=DraftCanvasDocument.createEmpty();var width=document.questionCard().width();var viewport=document.viewport();
        if(width<64||width>8192||viewport.zoom()<.1||viewport.zoom()>1||Math.abs(viewport.x())>1_000_000||Math.abs(viewport.y())>1_000_000)return false;
        return new DraftCanvasDocument(empty.schemaVersion(),empty.layoutVersion(),viewport,
            new DraftCanvasDocument.QuestionCard(empty.questionCard().x(),empty.questionCard().y(),width),empty.strokes()).equals(document);
    }
    /** All native/browser diagnostics consume an immutable projection through the same queue. */
    public CompletionStage<JsonNode> state() {
        var result=new CompletableFuture<JsonNode>();
        try {business.execute(()->{try{result.complete(model());}catch(Exception failure){result.completeExceptionally(failure);}});}
        catch(RejectedExecutionException failure){result.completeExceptionally(failure);}return result.minimalCompletionStage();
    }
    @Override public void close(){closed=true;business.shutdown();}
}
