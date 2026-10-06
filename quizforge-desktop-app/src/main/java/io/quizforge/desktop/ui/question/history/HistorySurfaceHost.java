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
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;

/** One read-only World for both the fixed practice view and the movable draft view. */
public final class HistorySurfaceHost extends StackPane {
    private final Button toggle=UiTheme.button("草稿","book-pen","",this::toggle);
    private final Label error=UiTheme.label("","muted");
    private final Button previous,next,previousAttempt,nextAttempt;
    private boolean hasPreviousAttempt,hasNextAttempt;
    private Runnable attemptBack=()->{},attemptForward=()->{};
    private final BorderPane frame=new BorderPane();
    private final BooleanSupplier hasPrevious,hasNext;
    private HistorySurfaceMode mode=HistorySurfaceMode.RESULT;
    private HistoryDraftAdapter.Replay selected;
    private io.quizforge.desktop.browser.HistoryLearningSurface draft;
    private final Runnable previousQuestion,nextQuestion;
    private long selectionVersion;
    private boolean busy,destroyed,nativeSummary;
    private String pendingTarget;
    private java.util.Map<String,Boolean> uiPreferences=java.util.Map.of();
    private java.util.function.Consumer<java.util.Map<String,Boolean>> onUiChange=preferences->{ };
    private java.util.function.Supplier<java.util.Map<String,Object>> pageState;
    private java.util.function.BiFunction<String,Object,java.util.concurrent.CompletionStage<Void>> pageCommand;
    public void configurePageActions(java.util.function.Supplier<java.util.Map<String,Object>> state,java.util.function.BiFunction<String,Object,java.util.concurrent.CompletionStage<Void>> command){pageState=state;pageCommand=command;}
    public void setMode(HistorySurfaceMode next){if(destroyed||busy||!available())throw new IllegalStateException("历史题卡暂不可操作");draft.setLearningMode(next);mode=next;update();}
    public void onUiChange(java.util.function.Consumer<java.util.Map<String,Boolean>> listener){onUiChange=listener;listener.accept(uiPreferences);}
    public HistorySurfaceHost(Runnable previousQuestion,Runnable nextQuestion,BooleanSupplier hasPrevious,BooleanSupplier hasNext){
        this.previousQuestion=previousQuestion;this.nextQuestion=nextQuestion;
        this.hasPrevious=hasPrevious;this.hasNext=hasNext;
        setId("history-surface-host");setMinSize(0,0);getStyleClass().add("practice-surface-host");setStyle("-fx-background-color: #f5f4f7;");
        toggle.setId("history-draft-toggle");toggle.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        error.setId("history-draft-error");error.setWrapText(true);StackPane.setAlignment(error,Pos.CENTER);
        previous=QuestionCardLayout.navigation("arrow-left","上一题",previousQuestion);next=QuestionCardLayout.navigation("arrow","下一题",nextQuestion);
        previous.visibleProperty().unbind();next.visibleProperty().unbind();
        previous.setId("history-draft-previous-question");next.setId("history-draft-next-question");
        StackPane.setAlignment(previous,Pos.CENTER_LEFT);StackPane.setAlignment(next,Pos.CENTER_RIGHT);
        previousAttempt=QuestionCardLayout.navigation("arrow-left","上一次尝试",()->attemptBack.run());
        nextAttempt=QuestionCardLayout.navigation("arrow","下一次尝试",()->attemptForward.run());
        previousAttempt.getGraphic().setRotate(90);nextAttempt.getGraphic().setRotate(90);
        previousAttempt.visibleProperty().unbind();nextAttempt.visibleProperty().unbind();
        previousAttempt.setId("history-previous-attempt");nextAttempt.setId("history-next-attempt");
        StackPane.setAlignment(previousAttempt,Pos.TOP_CENTER);StackPane.setAlignment(nextAttempt,Pos.BOTTOM_CENTER);
        for(var button:java.util.List.of(previous,next,previousAttempt,nextAttempt))StackPane.setMargin(button,new javafx.geometry.Insets(12));
        getChildren().addAll(frame,previous,next,previousAttempt,nextAttempt,error);update();
    }
    public void attemptNavigation(boolean previous,boolean next,Runnable back,Runnable forward){hasPreviousAttempt=previous;hasNextAttempt=next;attemptBack=back;attemptForward=forward;update();}
    public Button toggleButton(){return toggle;}
    public HistorySurfaceMode mode(){return mode;}
    public HistoryDraftWebView draftView(){return draft instanceof HistoryDraftWebView legacy?legacy:null;}
    public io.quizforge.desktop.browser.HistoryLearningSurface learningSurface(){return draft;}
    public boolean busy(){return busy;}
    public void setSourceContent(Node content){frame.setBottom(content);applySourceUi();}
    public void refreshChrome(){applySourceUi();update();}
    private void applySourceUi(){var content=frame.getBottom();if(content!=null){boolean visible=uiPreferences.getOrDefault("sources",true);content.setVisible(visible);content.setManaged(visible);}}
    public void showSummary(Node content){
        selectionVersion++;pendingTarget=null;selected=null;busy=false;nativeSummary=false;error.setText("");
        if(draft!=null)draft.clear();frame.setBottom(null);frame.setCenter(content);update();
    }
    public void showSummary(Node content,io.quizforge.core.practice.PracticeSummary summary){
        if(draft==null||!draft.nativeSurface()){showSummary(content);return;}
        long version=++selectionVersion;pendingTarget=null;selected=null;busy=true;nativeSummary=true;error.setText("");
        frame.setBottom(null);frame.setCenter(draft.view());update();
        draft.ready().thenCompose(v->draft.showSummary(summary)).whenComplete((v,e)->onFx(()->{
            if(destroyed||version!=selectionVersion)return;busy=false;
            if(e!=null)error.setText("统计页面暂时无法载入："+e.getMessage());update();
        }));
    }
    public void select(HistoryDraftAdapter.Replay replay){
        selectionVersion++;pendingTarget=null;selected=replay;busy=false;nativeSummary=false;error.setText("");
        if(!available()){if(draft!=null)draft.clear();frame.setCenter(null);error.setText(replay==null?"本轮没有题目":replay.draft().message()==null?"此记录没有新版题卡快照":replay.draft().message());update();return;}
        if(draft==null){draft=io.quizforge.desktop.browser.webview2.WebView2LearningSurface.enabled()?new io.quizforge.desktop.browser.webview2.WebView2HistorySurface():new HistoryDraftWebView();if(pageState!=null)draft.configurePageActions(pageState,pageCommand);draft.onUiChange(preferences->{uiPreferences=preferences;applySourceUi();update();onUiChange.accept(preferences);});draft.view().setId("history-learning-surface");frame.setCenter(draft.view());}
        else frame.setCenter(draft.view());
        busy=true;update();long version=selectionVersion;
        draft.ready().whenComplete((ignored,failure)->{
            Runnable apply=()->{
                if(destroyed || version!=selectionVersion)return;
                try{
                    if(failure!=null)throw new IllegalStateException("历史题卡暂时无法载入",failure);
                    draft.setLearningMode(mode);
                    draft.load(selected.card(),selected.draft().document()).whenComplete((v,loadFailure)->onFx(()->{
                        if(destroyed || version!=selectionVersion)return;busy=false;
                        if(loadFailure!=null)error.setText("历史题卡暂时无法载入："+loadFailure.getMessage());
                        else if(pendingTarget!=null){draft.focusTarget(pendingTarget);pendingTarget=null;}
                        update();
                    }));
                }catch(RuntimeException invalid){busy=false;error.setText("历史题卡暂时无法载入："+invalid.getMessage());update();}
            };
            if(Platform.isFxApplicationThread())apply.run();else Platform.runLater(apply);
        });
    }
    private static void onFx(Runnable action){if(Platform.isFxApplicationThread())action.run();else Platform.runLater(action);}
    private boolean available(){return selected!=null && selected.card()!=null && selected.draft().status()==HistoryDraftReplay.Status.READY;}
    public void focusTarget(String targetId){if(destroyed || targetId==null || !available())return;if(busy){pendingTarget=targetId;return;}if(draft!=null)draft.focusTarget(targetId);}
    public void toggle(){
        if(destroyed || busy || !available())return;
        setMode(mode==HistorySurfaceMode.DRAFT?HistorySurfaceMode.RESULT:HistorySurfaceMode.DRAFT);
    }
    private void update(){
        toggle.setText(mode==HistorySurfaceMode.RESULT?"草稿":"返回练习");toggle.setAccessibleText(toggle.getText());toggle.setVisible(available() && uiPreferences.getOrDefault("draftToggle",true));toggle.setManaged(toggle.isVisible());toggle.setDisable(busy || destroyed);
        if(draft!=null){boolean show=(available()||nativeSummary) && error.getText().isEmpty();draft.view().setVisible(show);draft.view().setManaged(show);}
        error.setVisible(!error.getText().isEmpty());error.setManaged(error.isVisible());
        boolean nativeCard=draft!=null&&draft.nativeSurface()&&draft.view().isVisible();
        if(draft!=null)draft.navigation(hasPrevious.getAsBoolean(),hasNext.getAsBoolean(),busy,previousQuestion,nextQuestion);
        previous.setVisible(hasPrevious.getAsBoolean()&&!nativeCard);previous.setManaged(previous.isVisible());previous.setDisable(busy);
        next.setVisible(hasNext.getAsBoolean()&&!nativeCard);next.setManaged(next.isVisible());next.setDisable(busy);
        boolean canPrevious=available()&&hasPreviousAttempt,canNext=available()&&hasNextAttempt;
        previousAttempt.setVisible(canPrevious&&!nativeCard);previousAttempt.setManaged(previousAttempt.isVisible());previousAttempt.setDisable(busy||destroyed||!canPrevious);
        nextAttempt.setVisible(canNext&&!nativeCard);nextAttempt.setManaged(nextAttempt.isVisible());nextAttempt.setDisable(busy||destroyed||!canNext);
        if(draft!=null)draft.attemptNavigation(canPrevious,canNext,busy||destroyed,previousAttempt::fire,nextAttempt::fire);
    }
    public void destroy(){if(destroyed)return;destroyed=true;selectionVersion++;if(draft!=null)draft.destroy();draft=null;selected=null;frame.setCenter(null);frame.setBottom(null);getChildren().clear();}
}
