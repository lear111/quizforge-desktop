package io.quizforge.desktop.browser.webview2;

import com.fasterxml.jackson.databind.JsonNode;
import io.quizforge.core.practice.draft.DraftCanvasDocument;
import io.quizforge.desktop.browser.HistoryLearningSurface;
import io.quizforge.desktop.extension.ExtensionManager;
import io.quizforge.desktop.learning.SharedPracticeViewModel;
import io.quizforge.desktop.ui.question.history.HistorySurfaceMode;
import io.quizforge.infrastructure.json.DocumentJson;
import io.quizforge.infrastructure.persistence.practice.DraftCanvasJsonCodec;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import javafx.application.Platform;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

/** Read-only browser: no Practice runtime, repositories, save bridge or mutation queue. */
public final class WebView2HistorySurface implements HistoryLearningSurface {
    private final Pane view=new Pane();
    private final CompletableFuture<Void> ready=new CompletableFuture<>();
    private final Map<Long,CompletableFuture<Void>> commands=new HashMap<>();
    private final List<Map<String,Object>> updates=new ArrayList<>();
    private WebView2Browser browser;
    private Path directory;
    private Runnable unregister=()->{},back=()->{},forward=()->{};
    private Runnable attemptBack=()->{},attemptForward=()->{};
    private Map<String,Object> attemptChrome=Map.of("kind","attempt-chrome","previousAttempt",false,"nextAttempt",false,"busy",false);
    private Consumer<Map<String,Boolean>> onUi=ignored->{};
    private Supplier<Map<String,Object>> state=Map::of;
    private BiFunction<String,Object,CompletionStage<Void>> pageCommand;
    private Map<String,Object> chrome=Map.of("kind","chrome","previous",false,"next",false,"busy",true);
    private String questionId;
    private HistorySurfaceMode mode=HistorySurfaceMode.RESULT;
    private boolean destroyed,booted,actionBusy;
    private long sequence;

    public WebView2HistorySurface(){
        view.setMinSize(0,0);
        view.sceneProperty().addListener((o,b,a)->{if(a!=null){a.windowProperty().addListener((ignored,before,after)->attach());attach();}});
    }
    private void attach(){
        if(destroyed||browser!=null||view.getScene()==null||!(view.getScene().getWindow() instanceof Stage stage))return;
        if(!stage.isShowing()){stage.showingProperty().addListener((o,b,a)->{if(a)attach();});return;}
        try{
            directory=WebView2TemporaryDirectories.create("history-");
            browser=new WebView2Browser(stage,view,directory,WebView2Browser.Page.HISTORY,message->Platform.runLater(()->receive(message)));
            unregister=ExtensionManager.getDefault().registerMessagePage(message->{
                if(destroyed)return;
                if("stop".equals(message.get("kind"))){destroy();return;}
                if(!booted)updates.add(message);else browser.post(message);
            });
        }catch(Exception failure){fail(failure);destroy();}
    }
    private void receive(JsonNode message){
        if(destroyed)return;
        try{
            switch(message.path("kind").asText()){
                case "boot" -> {
                    booted=true;
                    browser.post(Map.of("kind","bootstrap","packages",DocumentJson.mapper().readTree(ExtensionManager.getDefault().currentPackagesJson())));
                    browser.post(chrome);browser.post(attemptChrome);for(var update:updates)browser.post(update);updates.clear();
                }
                case "history-ready" -> ready.complete(null);
                case "command-complete" -> {
                    var pending=commands.remove(message.path("id").asLong());
                    if(pending!=null){if(message.path("ok").asBoolean())pending.complete(null);else pending.completeExceptionally(new IllegalStateException(message.path("message").asText()));}
                }
                case "preferences" -> {
                    if(!Objects.equals(questionId,message.path("questionId").asText()))return;
                    var value=DocumentJson.mapper().convertValue(message.path("value"),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});
                    onUi.accept(io.quizforge.desktop.ui.question.shared.QuestionUiPreferences.validate(value,false));
                }
                case "page-request" -> pageRequest(message);
                case "chrome-action" -> {
                    if(!actionBusy&&questionId!=null&&Objects.equals(questionId,message.path("questionId").asText())&&!Boolean.TRUE.equals(chrome.get("busy"))&&!Boolean.TRUE.equals(attemptChrome.get("busy"))){
                        if("previous".equals(message.path("action").asText())&&Boolean.TRUE.equals(chrome.get("previous")))back.run();
                        else if("next".equals(message.path("action").asText())&&Boolean.TRUE.equals(chrome.get("next")))forward.run();
                        else if("previousAttempt".equals(message.path("action").asText())&&Boolean.TRUE.equals(attemptChrome.get("previousAttempt")))attemptBack.run();
                        else if("nextAttempt".equals(message.path("action").asText())&&Boolean.TRUE.equals(attemptChrome.get("nextAttempt")))attemptForward.run();
                    }
                }
                case "page-error","native-error" -> fail(new IllegalStateException(message.path("message").asText(message.toString())));
                default -> throw new IllegalArgumentException("不支持的历史消息");
            }
        }catch(Exception failure){fail(failure);}
    }
    private void fail(Throwable failure){ready.completeExceptionally(failure);commands.values().forEach(p->p.completeExceptionally(failure));commands.clear();}
    private void pageRequest(JsonNode message){
        String id=message.path("id").asText();if(!id.matches("[1-9][0-9]{0,15}"))return;
        try{
            if(actionBusy)throw new IllegalStateException("历史操作正在完成");
            if(questionId==null||pageCommand==null||!questionId.equals(message.path("questionId").asText()))throw new IllegalStateException("历史题目已切换");
            String action=message.path("action").asText();
            if(!Set.of("navigate","learning.mode","source.open").contains(action))throw new IllegalArgumentException("历史页不支持此操作");
            actionBusy=true;
            pageCommand.apply(action,DocumentJson.mapper().convertValue(message.path("argument"),Object.class)).whenComplete((v,e)->Platform.runLater(()->{actionBusy=false;reply(id,e);}));
        }catch(Exception failure){actionBusy=false;reply(id,failure);}
    }
    private void reply(String id,Throwable failure){
        if(destroyed)return;
        var reply=failure==null?Map.of("ok",true,"data",Map.of()):Map.of("ok",false,"error",Map.of("code","HOST_ACTION_FAILED","message",Objects.toString(failure.getMessage(),"历史操作失败"),"retryable",false));
        browser.post(Map.of("kind","page-reply","reply",Map.of("id",id,"reply",reply)));publishState();
    }
    private void publishState(){if(booted&&!destroyed&&questionId!=null)browser.post(Map.of("kind","page-state","questionId",questionId,"state",state.get()));}
    private CompletionStage<Void> command(String kind,Map<String,Object> value){
        if(destroyed||!booted)return CompletableFuture.failedFuture(new IllegalStateException("历史浏览器尚未就绪"));
        long id=++sequence;var result=new CompletableFuture<Void>();commands.put(id,result);
        var message=new LinkedHashMap<String,Object>(value);message.put("kind",kind);message.put("commandId",id);
        try{browser.post(message);}catch(Exception failure){commands.remove(id);result.completeExceptionally(failure);}
        result.orTimeout(20,TimeUnit.SECONDS).whenComplete((v,e)->Platform.runLater(()->commands.remove(id,result)));
        return result.minimalCompletionStage();
    }
    public Pane view(){return view;}
    public CompletionStage<Void> ready(){return ready.minimalCompletionStage();}
    public boolean nativeSurface(){return true;}
    public CompletionStage<Void> showSummary(io.quizforge.core.practice.PracticeSummary summary){
        questionId="__summary";
        return command("summary",Map.of("summary",Map.of("total",summary.totalCount(),"correct",summary.correctCount(),"incorrect",summary.incorrectCount(),"unfinished",summary.unfinishedCount(),"unscored",summary.unscoredCount(),
            "score",summary.score().map(n->n.stripTrailingZeros().toPlainString()).orElse("未评分"),"maximum",summary.maxScore().map(n->n.stripTrailingZeros().toPlainString()).orElse("未设置"))));
    }
    public CompletionStage<Void> load(SharedPracticeViewModel card,DraftCanvasDocument document){
        questionId=card.question().sessionQuestionId();
        try{return command("load",Map.of("questionId",questionId,"card",DocumentJson.mapper().readTree(card.toJson()),"document",DocumentJson.mapper().readTree(new DraftCanvasJsonCodec().encode(document)),"state",state.get(),"mode",mode==HistorySurfaceMode.DRAFT?"DRAFT":"PRACTICE"));}
        catch(Exception failure){return CompletableFuture.failedFuture(failure);}
    }
    public void setLearningMode(HistorySurfaceMode mode){this.mode=mode;if(booted&&!destroyed){browser.post(Map.of("kind","mode","mode",mode==HistorySurfaceMode.DRAFT?"DRAFT":"PRACTICE"));publishState();}}
    public void clear(){questionId=null;onUi.accept(Map.of());if(booted&&!destroyed)browser.post(Map.of("kind","clear"));}
    public boolean focusTarget(String targetId){if(destroyed||!booted||questionId==null)return false;browser.post(Map.of("kind","focus","targetId",Objects.requireNonNull(targetId)));return true;}
    public void configurePageActions(Supplier<Map<String,Object>> state,BiFunction<String,Object,CompletionStage<Void>> command){this.state=state;pageCommand=command;publishState();}
    public void onUiChange(Consumer<Map<String,Boolean>> listener){onUi=listener;listener.accept(Map.of());}
    public void navigation(boolean previous,boolean next,boolean busy,Runnable back,Runnable forward){
        this.back=back;this.forward=forward;chrome=Map.of("kind","chrome","previous",previous,"next",next,"busy",busy);
        if(booted&&!destroyed){browser.post(chrome);publishState();}
    }
    public void destroy(){if(destroyed)return;destroyed=true;unregister.run();if(browser!=null)browser.close();WebView2TemporaryDirectories.release(directory,browser==null?CompletableFuture.completedFuture(null):browser.closeCompletion());fail(new IllegalStateException("历史浏览器已关闭"));updates.clear();}
    public void attemptNavigation(boolean previous,boolean next,boolean busy,Runnable back,Runnable forward){attemptBack=back;attemptForward=forward;attemptChrome=Map.of("kind","attempt-chrome","previousAttempt",previous,"nextAttempt",next,"busy",busy);if(booted&&!destroyed)browser.post(attemptChrome);}
    WebView2Browser browser(){return browser;}
}
