package io.quizforge.desktop.ui.question.history;

import io.quizforge.core.practice.draft.DraftCanvasDocument;
import io.quizforge.desktop.poc.sharedpractice.SharedPracticeViewModel;
import io.quizforge.infrastructure.persistence.practice.DraftCanvasJsonCodec;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;

/** One local read-only page. Its bridge cannot save, submit, retry or change answers. */
public final class HistoryDraftWebView {
    private final WebView view=new WebView();
    private final CompletableFuture<Void> ready=new CompletableFuture<>();
    private final ReadyHost host=new ReadyHost();
    private JSObject replay;
    private boolean destroyed;
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
                    replay=bridge;window.setMember("historyHost",host);
                    view.getEngine().executeScript("window.historyDraftReplay.bindHost()");
                }catch(RuntimeException failure){ready.completeExceptionally(failure);}
            }else if(after==Worker.State.FAILED || after==Worker.State.CANCELLED)
                ready.completeExceptionally(new IllegalStateException("History Draft page could not load"));
        });
        view.getEngine().load(page.toExternalForm());
    }
    public WebView view(){return view;}
    public CompletionStage<Void> ready(){return ready.minimalCompletionStage();}
    public boolean isDestroyed(){return destroyed;}
    public void load(SharedPracticeViewModel card,DraftCanvasDocument document){
        requireFx();if(destroyed || replay==null)throw new IllegalStateException("History Draft is not ready");
        // Resolve functions from their strong window globals, avoiding stale WebKit function wrappers.
        var window=(JSObject)view.getEngine().executeScript("window");
        window.setMember("__quizforgeHistoryCard",card.toJson());
        window.setMember("__quizforgeHistoryDocument",new DraftCanvasJsonCodec().encode(document));
        try{view.getEngine().executeScript("window.historyDraftReplay.loadHistoryDraft(window.__quizforgeHistoryCard,window.__quizforgeHistoryDocument)");}
        finally{window.removeMember("__quizforgeHistoryCard");window.removeMember("__quizforgeHistoryDocument");}
    }
    /** Clear the old Attempt immediately while the next projection is being selected. */
    public void clear(){
        requireFx();if(destroyed || replay==null)return;
        view.getEngine().executeScript("document.querySelector('#question-card').replaceChildren();document.querySelector('#strokes').replaceChildren();document.querySelector('#active-stroke').replaceChildren();");
    }
    public void destroy(){
        requireFx();if(destroyed)return;
        destroyed=true;
        try{if(replay!=null)view.getEngine().executeScript("if(window.historyDraftReplay)window.historyDraftReplay.destroy();window.historyHost=null;");}
        catch(netscape.javascript.JSException releasedPage){ /* The engine may already have released its local page. */ }
        finally{replay=null;ready.completeExceptionally(new IllegalStateException("History Draft closed"));view.getEngine().load(null);}
    }
    public final class ReadyHost {
        public void ready(){requireFx();if(!destroyed)ready.complete(null);}
    }
    private static void requireFx(){if(!Platform.isFxApplicationThread())throw new IllegalStateException("Use the JavaFX thread");}
}
