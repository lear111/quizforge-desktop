package io.quizforge.desktop.ui.question.history;

import io.quizforge.core.practice.draft.DraftCanvasDocument;
import io.quizforge.desktop.learning.SharedPracticeViewModel;
import io.quizforge.infrastructure.persistence.practice.DraftCanvasJsonCodec;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;

/** One local read-only page. Its bridge cannot save, submit, retry or change answers. */
public final class HistoryDraftWebView implements io.quizforge.desktop.browser.HistoryLearningSurface {
    private final WebView view=new WebView();
    private final CompletableFuture<Void> ready=new CompletableFuture<>();
    private final ReadyHost host=new ReadyHost();
    private JSObject replay;
    private boolean destroyed;
    private String displayedQuestionId;
    private final io.quizforge.desktop.ui.question.shared.QuestionPageActions pageActions=new io.quizforge.desktop.ui.question.shared.QuestionPageActions(view,()->displayedQuestionId,()->destroyed);
    public void configurePageActions(java.util.function.Supplier<java.util.Map<String,Object>> state,java.util.function.BiFunction<String,Object,CompletionStage<Void>> command){pageActions.configure(state,command);}
    private java.util.Map<String,Boolean> uiPreferences=java.util.Map.of();
    private java.util.function.Consumer<java.util.Map<String,Boolean>> onUiChange=preferences->{ };
    public void onUiChange(java.util.function.Consumer<java.util.Map<String,Boolean>> listener){onUiChange=listener;listener.accept(uiPreferences);}
    public HistoryDraftWebView() {
        requireFx();view.setContextMenuEnabled(false);
        var page=Objects.requireNonNull(getClass().getResource("/editor/draft-canvas/history-replay.html"),"Build History replay frontend first");
        view.getEngine().getLoadWorker().stateProperty().addListener((observable,before,after)->{
            if(destroyed)return;
            if(after==Worker.State.SUCCEEDED){
                try {
                    var window=(JSObject)view.getEngine().executeScript("window");
                    if(!(window.getMember("historyDraftReplay") instanceof JSObject bridge))
                        throw new IllegalStateException("History replay bridge is missing");
                    io.quizforge.desktop.extension.ExtensionManager.getDefault().installScripts(view);
                    replay=bridge;window.setMember("historyHost",host);
                    window.setMember("pageHost",pageActions);
                    view.getEngine().executeScript("window.historyDraftReplay.bindHost()");
                }catch(RuntimeException failure){ready.completeExceptionally(failure);}
            }else if(after==Worker.State.FAILED || after==Worker.State.CANCELLED)
                ready.completeExceptionally(new IllegalStateException("History Draft page could not load"));
        });
        view.getEngine().load(page.toExternalForm());
    }
    public boolean focusTarget(String targetId){requireFx();if(destroyed || replay==null)return false;return Boolean.TRUE.equals(replay.call("focusTarget",Objects.requireNonNull(targetId)));}
    public WebView view(){return view;}
    public CompletionStage<Void> ready(){return ready.minimalCompletionStage();}
    public boolean isDestroyed(){return destroyed;}
    public CompletionStage<Void> load(SharedPracticeViewModel card,DraftCanvasDocument document){
        requireFx();if(destroyed || replay==null)throw new IllegalStateException("History Draft is not ready");
        displayedQuestionId=card.question().sessionQuestionId();
        // Resolve functions from their strong window globals, avoiding stale WebKit function wrappers.
        var window=(JSObject)view.getEngine().executeScript("window");
        window.setMember("__quizforgeHistoryCard",card.toJson());
        window.setMember("__quizforgeHistoryDocument",new DraftCanvasJsonCodec().encode(document));
        try{view.getEngine().executeScript("window.historyDraftReplay.loadHistoryDraft(window.__quizforgeHistoryCard,window.__quizforgeHistoryDocument)");}
        finally{window.removeMember("__quizforgeHistoryCard");window.removeMember("__quizforgeHistoryDocument");}
        return CompletableFuture.completedFuture(null);
    }
    /** Clear the old Attempt immediately while the next projection is being selected. */
    public void setLearningMode(HistorySurfaceMode mode){
        requireFx();if(destroyed || replay==null)throw new IllegalStateException("History is not ready");
        view.getEngine().executeScript("window.historyDraftReplay.setLearningMode('"+(mode==HistorySurfaceMode.DRAFT?"DRAFT":"PRACTICE")+"')");
    }
    public void clear(){
        requireFx();if(destroyed || replay==null)return;
        displayedQuestionId=null;uiPreferences=java.util.Map.of();onUiChange.accept(uiPreferences);
        view.getEngine().executeScript("window.historyDraftReplay.clear()");
    }
    public void destroy(){
        requireFx();if(destroyed)return;
        destroyed=true;
        try{if(replay!=null)view.getEngine().executeScript("if(window.historyDraftReplay)window.historyDraftReplay.destroy();window.historyHost=null;");}
        catch(netscape.javascript.JSException releasedPage){ /* The engine may already have released its local page. */ }
        finally{replay=null;ready.completeExceptionally(new IllegalStateException("History Draft closed"));view.getEngine().load(null);}
    }
    public final class ReadyHost {
        public void uiPreferences(String questionId,String encoded){
            if(destroyed)return;
            try{
                var json=io.quizforge.infrastructure.json.DocumentJson.mapper();
                var preferences=io.quizforge.desktop.ui.question.shared.QuestionUiPreferences.validate(
                        json.readValue(encoded,new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String,Object>>() { }),false);
                Platform.runLater(()->{if(destroyed || !questionId.equals(displayedQuestionId))return;uiPreferences=preferences;onUiChange.accept(preferences);});
            }catch(Exception ignored){ /* Read-only presentation cannot alter host capabilities. */ }
        }
        public void ready(){requireFx();if(!destroyed)ready.complete(null);}
    }
    private static void requireFx(){if(!Platform.isFxApplicationThread())throw new IllegalStateException("Use the JavaFX thread");}
}
