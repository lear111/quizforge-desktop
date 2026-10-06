package io.quizforge.desktop.browser.webview2;

import com.fasterxml.jackson.databind.JsonNode;
import io.quizforge.core.practice.PersistentPracticeRuntime;
import io.quizforge.desktop.browser.PracticeLearningSurface;
import io.quizforge.desktop.extension.ExtensionManager;
import io.quizforge.desktop.ui.question.practice.SharedLearningSurfaceMode;
import io.quizforge.infrastructure.json.DocumentJson;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import javafx.application.Platform;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

/** Tab-owned production surface. All browser commands and Core writes are asynchronous. */
public final class WebView2LearningSurface implements PracticeLearningSurface {
    private final Pane view=new Pane();
    private final PersistentPracticeRuntime runtime;
    private final Runnable onChanged;
    private final CompletableFuture<Void> ready=new CompletableFuture<>();
    private final Map<Long,CompletableFuture<Void>> commands=new HashMap<>();
    private WebView2Browser browser;
    private Path directory;
    private WebView2PracticeSession session;
    private Runnable unregister=()->{};
    private long sequence;
    private boolean destroyed,closing,suspended,booted,wasSuspendedOnClose;
    private final List<Map<String,Object>> pendingUpdates=new ArrayList<>();
    private CompletableFuture<Void> closeBarrier;
    private Consumer<Map<String,Boolean>> onUi=ignored->{};
    private Supplier<Map<String,Object>> pageState=Map::of;
    private BiFunction<String,Object,CompletionStage<Void>> pageCommand;
    private Runnable back=()->{},forward=()->{};
    private Runnable restart=()->{};
    private Runnable attemptBack=()->{},attemptForward=()->{};
    private Map<String,Object> attemptChrome=Map.of("kind","attempt-chrome","previousAttempt",false,"nextAttempt",false,"busy",false);
    private Map<String,Object> chrome=Map.of("kind","chrome","previous",false,"next",false,"busy",true);
    private SharedLearningSurfaceMode mode=SharedLearningSurfaceMode.PRACTICE;

    public WebView2LearningSurface(PersistentPracticeRuntime runtime,Runnable onChanged){
        this.runtime=runtime;this.onChanged=onChanged;view.setMinSize(0,0);
        view.sceneProperty().addListener((ignored,before,after)->{if(after!=null){after.windowProperty().addListener((o,b,a)->attach());attach();}});
    }
    public static boolean enabled(){return System.getProperty("quizforge.learning.backend",System.getProperty("os.name","").startsWith("Windows")?"webview2":"javafx").equals("webview2");}
    private void attach(){
        if(destroyed||browser!=null||view.getScene()==null||!(view.getScene().getWindow() instanceof Stage stage))return;
        if(!stage.isShowing()){stage.showingProperty().addListener((o,b,a)->{if(a)attach();});return;}
        try{
            directory=WebView2TemporaryDirectories.create("learning-");
            var connection=new CompletableFuture<WebView2PracticeSession>();
            browser=new WebView2Browser(stage,view,directory,message->{
                if(message.path("kind").asText().equals("boot"))Platform.runLater(()->{if(!destroyed){booted=true;browser.post(chrome);browser.post(attemptChrome);publishState();for(var update:pendingUpdates)browser.post(update);pendingUpdates.clear();}});
                if(List.of("command-complete","page-request","chrome-action").contains(message.path("kind").asText()))Platform.runLater(()->receive(message));
                else connection.thenAccept(current->current.receive(message));
            });
            List<Map<String,Object>> packages=DocumentJson.mapper().readValue(ExtensionManager.getDefault().currentPackagesJson(),new com.fasterxml.jackson.core.type.TypeReference<>(){});
            session=new WebView2PracticeSession(runtime,packages,browser::post,message->Platform.runLater(()->receive(message)));
            connection.complete(session);
            unregister=ExtensionManager.getDefault().registerMessagePage(message->{
                if(destroyed)return;
                if(message.get("kind").equals("stop")){release();return;}
                if(!booted){pendingUpdates.add(message);return;}
                try{browser.post(message);}catch(RuntimeException failure){release();throw failure;}
            });
        }catch(Exception failure){ready.completeExceptionally(failure);release();}
    }
    private void receive(JsonNode message){
        if(destroyed)return;
        switch(message.path("kind").asText()){
            case "rendered" -> {publishState();ready.complete(null);}
            case "command-complete" -> {var future=commands.remove(message.path("id").asLong());if(future!=null){if(message.path("ok").asBoolean())future.complete(null);else future.completeExceptionally(new IllegalStateException(message.path("message").asText()));}}
            case "preferences" -> {var preferences=DocumentJson.mapper().convertValue(message.path("value"),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});onUi.accept(io.quizforge.desktop.ui.question.shared.QuestionUiPreferences.validate(preferences,false));}
            case "state-changed" -> {publishState();onChanged.run();}
            case "mode-ready" -> publishState();
            case "page-request" -> pageRequest(message);
            case "chrome-action" -> {if(!closing&&(!suspended||runtime.session().finished())&&identity().equals(message.path("questionId").asText())&&!Boolean.TRUE.equals(chrome.get("busy"))&&!Boolean.TRUE.equals(attemptChrome.get("busy"))){
                switch(message.path("action").asText()){
                    case "previous" -> {if(Boolean.TRUE.equals(chrome.get("previous")))back.run();}
                    case "next" -> {if(Boolean.TRUE.equals(chrome.get("next")))forward.run();}
                    case "previousAttempt" -> {if(Boolean.TRUE.equals(attemptChrome.get("previousAttempt")))attemptBack.run();}
                    case "nextAttempt" -> {if(Boolean.TRUE.equals(attemptChrome.get("nextAttempt")))attemptForward.run();}
                    case "restart" -> {if(runtime.session().finished())restart.run();}
                    default -> { }
                }
            }}
            case "page-error","native-error","host-error" -> {var failure=new IllegalStateException(message.toString());ready.completeExceptionally(failure);commands.values().forEach(f->f.completeExceptionally(failure));commands.clear();}
            default -> { }
        }
    }
    private String identity(){return runtime.session().finished()?"__summary":runtime.snapshot().questions().stream().filter(q->q.sessionQuestion().questionId().equals(runtime.session().current().id())).findFirst().orElseThrow().sessionQuestion().id();}
    private void publishState(){if(browser!=null&&booted&&!destroyed&&!runtime.session().finished())browser.post(Map.of("kind","page-state","questionId",identity(),"state",pageState.get()));}
    private void pageRequest(JsonNode message){
        String id=message.path("id").asText();if(!id.matches("[1-9][0-9]{0,15}"))return;
        try{
            if(closing||suspended||pageCommand==null||!identity().equals(message.path("questionId").asText()))throw new IllegalStateException("题目已切换或页面已关闭");
            String action=message.path("action").asText();if(!Set.of("navigate","learning.mode","source.open").contains(action))throw new IllegalArgumentException("不支持的页面操作");
            Object argument=DocumentJson.mapper().convertValue(message.path("argument"),Object.class);
            pageCommand.apply(action,argument).whenComplete((ignored,error)->Platform.runLater(()->reply(id,error)));
        }catch(Exception failure){reply(id,failure);}
    }
    private void reply(String id,Throwable failure){
        if(destroyed||browser==null)return;
        Object reply=failure==null?Map.of("ok",true,"data",Map.of()):Map.of("ok",false,"error",Map.of("code","HOST_ACTION_FAILED","message",Objects.toString(failure.getMessage(),"页面操作失败"),"retryable",false));
        browser.post(Map.of("kind","page-reply","reply",Map.of("id",id,"reply",reply)));publishState();
    }
    private CompletionStage<Void> command(String kind,Map<String,Object> arguments){
        if(destroyed||browser==null)return CompletableFuture.failedFuture(new IllegalStateException("学习页面未就绪"));
        long id=++sequence;var result=new CompletableFuture<Void>();commands.put(id,result);
        var message=new LinkedHashMap<String,Object>(arguments);message.put("kind",kind);message.put("commandId",id);
        try{browser.post(message);}catch(Exception failure){commands.remove(id);result.completeExceptionally(failure);}
        result.orTimeout(20,TimeUnit.SECONDS).whenComplete((v,e)->Platform.runLater(()->commands.remove(id,result)));return result.minimalCompletionStage();
    }
    @Override public Pane view(){return view;}
    @Override public CompletionStage<Void> ready(){return ready.minimalCompletionStage();}
    @Override public boolean isReady(){return ready.isDone()&&!ready.isCompletedExceptionally();}
    @Override public boolean nativeSurface(){return true;}
    @Override public void onUiChange(Consumer<Map<String,Boolean>> listener){onUi=listener;}
    @Override public void configurePageActions(Supplier<Map<String,Object>> state,BiFunction<String,Object,CompletionStage<Void>> command){pageState=state;pageCommand=command;publishState();}
    @Override public void navigation(boolean previous,boolean next,boolean busy,Runnable back,Runnable forward){this.back=back;this.forward=forward;chrome=Map.of("kind","chrome","previous",previous,"next",next,"busy",busy);if(browser!=null&&booted&&!destroyed){browser.post(chrome);publishState();}}
    @Override public void attemptNavigation(boolean previous,boolean next,boolean busy,Runnable back,Runnable forward){attemptBack=back;attemptForward=forward;attemptChrome=Map.of("kind","attempt-chrome","previousAttempt",previous,"nextAttempt",next,"busy",busy);if(browser!=null&&booted&&!destroyed)browser.post(attemptChrome);}
    @Override public CompletionStage<Void> flushPendingDraft(){return command("flush",Map.of());}
    @Override public CompletionStage<Void> resumeCurrent(){
        suspended=false;
        session.resume();return command("resume",Map.of("mode",mode.name()));
    }
    @Override public CompletionStage<Void> showSummary(io.quizforge.core.practice.PracticeSummary summary,Runnable restart){
        this.restart=restart;
        return command("summary",Map.of("summary",Map.of("total",summary.totalCount(),"correct",summary.correctCount(),"incorrect",summary.incorrectCount(),"unfinished",summary.unfinishedCount(),"unscored",summary.unscoredCount(),
            "score",summary.score().map(n->n.stripTrailingZeros().toPlainString()).orElse("未评分"),"maximum",summary.maxScore().map(n->n.stripTrailingZeros().toPlainString()).orElse("未设置"))));
    }
    @Override public CompletionStage<Void> reloadCurrent(){
        if(destroyed||session==null)return CompletableFuture.failedFuture(new IllegalStateException("学习页面未就绪"));
        long id=++sequence;var result=new CompletableFuture<Void>();commands.put(id,result);
        suspended=false;session.reload(id).whenComplete((v,e)->{if(e!=null)result.completeExceptionally(e);});
        result.orTimeout(20,TimeUnit.SECONDS).whenComplete((v,e)->Platform.runLater(()->commands.remove(id,result)));return result.minimalCompletionStage();
    }
    @Override public void unloadCurrent(){suspended=true;if(session!=null)session.suspend();if(browser!=null)browser.post(Map.of("kind","suspend"));}
    @Override public void setLearningMode(SharedLearningSurfaceMode mode){this.mode=mode;if(session!=null)session.setMode(mode.name());}
    @Override public void cancelClose(){if(!closing)return;closing=false;closeBarrier=null;if(!wasSuspendedOnClose){suspended=false;if(session!=null)session.resume();setLearningMode(mode);}}
    @Override public boolean focusTarget(String targetId){if(browser==null)return false;browser.post(Map.of("kind","focus","targetId",targetId));return true;}
    @Override public CompletionStage<Void> prepareCloseAsync(){if(closeBarrier!=null)return closeBarrier.minimalCompletionStage();wasSuspendedOnClose=suspended;closing=true;closeBarrier=(!isReady()||suspended?CompletableFuture.<Void>completedFuture(null):flushPendingDraft().thenRun(this::unloadCurrent).toCompletableFuture());closeBarrier.whenComplete((v,e)->{if(e!=null)Platform.runLater(()->{closing=false;closeBarrier=null;});});return closeBarrier.minimalCompletionStage();}
    @Override public void saveBeforeClose(){if(!isReady()||suspended)return;if(closeBarrier==null||!closeBarrier.isDone()||closeBarrier.isCompletedExceptionally())throw new IllegalStateException("请等待草稿保存完成");}
    @Override public void destroy(){if(destroyed)return;saveBeforeClose();release();}
    void abortVerification(){release();}
    private void release(){if(destroyed)return;destroyed=true;unregister.run();if(session!=null)session.close();if(browser!=null)browser.close();WebView2TemporaryDirectories.release(directory,browser==null?CompletableFuture.completedFuture(null):browser.closeCompletion());var failure=new IllegalStateException("学习页面已关闭");ready.completeExceptionally(failure);commands.values().forEach(f->f.completeExceptionally(failure));commands.clear();}
    WebView2Browser browser(){return browser;}
    WebView2PracticeSession session(){return session;}
}
