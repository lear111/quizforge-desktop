package io.quizforge.desktop.ui.question.history;

import io.quizforge.core.practice.HistoryDraftReplay;
import io.quizforge.desktop.ui.question.shared.QuestionCardLayout;
import io.quizforge.desktop.ui.shared.UiTheme;
import java.util.function.BooleanSupplier;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

/** Detail-owned projection host. Attempt selection stays in PracticeHistoryDetailView. */
public final class HistorySurfaceHost extends StackPane {
    private final Node result;
    private final Button toggle=UiTheme.button("草稿","book-pen","",this::toggle);
    private final Label error=UiTheme.label("","incorrect");
    private final Button previous,next;
    private final BooleanSupplier hasPrevious,hasNext;
    private HistorySurfaceMode mode=HistorySurfaceMode.RESULT;
    private HistoryDraftAdapter.Replay selected;
    private HistoryDraftWebView draft;
    private long selectionVersion;
    private boolean busy,destroyed;
    private Runnable onModeChanged=()->{};
    public HistorySurfaceHost(Node result,Runnable previousQuestion,Runnable nextQuestion,
            BooleanSupplier hasPrevious,BooleanSupplier hasNext){
        this.result=result;this.hasPrevious=hasPrevious;this.hasNext=hasNext;
        setId("history-surface-host");setMinSize(0,0);toggle.setId("history-draft-toggle");
        toggle.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        error.setId("history-draft-error");error.setWrapText(true);StackPane.setAlignment(error,Pos.CENTER);
        previous=QuestionCardLayout.navigation("arrow-left","上一题",previousQuestion);
        next=QuestionCardLayout.navigation("arrow","下一题",nextQuestion);
        previous.visibleProperty().unbind();next.visibleProperty().unbind();
        previous.setId("history-draft-previous-question");next.setId("history-draft-next-question");
        StackPane.setAlignment(previous,Pos.CENTER_LEFT);StackPane.setAlignment(next,Pos.CENTER_RIGHT);
        getChildren().addAll(result,previous,next,error);update();
    }
    public Button toggleButton(){return toggle;}
    public HistorySurfaceMode mode(){return mode;}
    public HistoryDraftWebView draftView(){return draft;}
    public boolean busy(){return busy;}
    public void onModeChanged(Runnable action){onModeChanged=java.util.Objects.requireNonNull(action);}
    public void select(HistoryDraftAdapter.Replay replay){
        selectionVersion++;selected=replay;busy=false;error.setText("");
        if(draft!=null)draft.clear();
        if(!available())mode=HistorySurfaceMode.RESULT;
        if(mode==HistorySurfaceMode.DRAFT)loadSelected();else update();
    }
    private boolean available(){return selected!=null && selected.draft().status()!=HistoryDraftReplay.Status.MISSING;}
    public void toggle(){
        if(destroyed || busy || !available())return;
        selectionVersion++;error.setText("");
        if(mode==HistorySurfaceMode.DRAFT){mode=HistorySurfaceMode.RESULT;onModeChanged.run();update();}
        else{mode=HistorySurfaceMode.DRAFT;onModeChanged.run();loadSelected();}
    }
    private void loadSelected(){
        if(selected.draft().status()==HistoryDraftReplay.Status.UNAVAILABLE){error.setText(selected.draft().message());update();return;}
        if(draft==null){draft=new HistoryDraftWebView();draft.view().setId("history-draft-surface");getChildren().add(1,draft.view());}
        busy=true;update();long version=selectionVersion;
        draft.ready().whenComplete((ignored,failure)->{
            Runnable apply=()->{
                if(destroyed || version!=selectionVersion || mode!=HistorySurfaceMode.DRAFT)return;
                busy=false;
                try{if(failure!=null)throw new IllegalStateException("历史草稿暂时无法载入",failure);
                    draft.load(selected.card(),selected.draft().document());
                }catch(RuntimeException invalid){error.setText("此草稿使用当前版本暂不支持的格式，或暂时无法载入。");}
                update();
            };
            if(Platform.isFxApplicationThread())apply.run();else Platform.runLater(apply);
        });
    }
    private void update(){
        toggle.setText(mode==HistorySurfaceMode.RESULT?"草稿":"返回结果");toggle.setAccessibleText(toggle.getText());
        toggle.setVisible(available());toggle.setManaged(available());toggle.setDisable(busy || destroyed);
        result.setVisible(mode==HistorySurfaceMode.RESULT);result.setManaged(mode==HistorySurfaceMode.RESULT);
        if(draft!=null){boolean show=mode==HistorySurfaceMode.DRAFT && !busy && error.getText().isEmpty();draft.view().setVisible(show);draft.view().setManaged(show);}
        error.setVisible(mode==HistorySurfaceMode.DRAFT && !error.getText().isEmpty());error.setManaged(error.isVisible());
        previous.setVisible(mode==HistorySurfaceMode.DRAFT && hasPrevious.getAsBoolean());previous.setManaged(previous.isVisible());
        next.setVisible(mode==HistorySurfaceMode.DRAFT && hasNext.getAsBoolean());next.setManaged(next.isVisible());
    }
    public void destroy(){
        if(destroyed)return;destroyed=true;selectionVersion++;
        if(draft!=null)draft.destroy();draft=null;selected=null;getChildren().clear();
    }
}
