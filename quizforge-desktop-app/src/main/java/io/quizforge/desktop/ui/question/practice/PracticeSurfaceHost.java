package io.quizforge.desktop.ui.question.practice;

import io.quizforge.core.practice.PersistentPracticeRuntime;
import io.quizforge.desktop.learning.SharedPracticeAdapter;
import io.quizforge.desktop.browser.javafx.SharedPracticeCanvasWebView;
import io.quizforge.desktop.learning.SharedPracticeViewModel;
import io.quizforge.desktop.ui.question.shared.QuestionCardLayout;
import io.quizforge.desktop.ui.shared.UiTheme;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

/** One tab-owned learning surface; mode changes capabilities, never the Renderer or WebView. */
public final class PracticeSurfaceHost extends StackPane {
    private final PersistentPracticeRuntime runtime;
    private final java.util.function.Supplier<io.quizforge.desktop.browser.PracticeLearningSurface> surfaceFactory;
    private final Runnable onChanged;
    private final StackPane transitional = new StackPane();
    private final javafx.scene.layout.BorderPane learningFrame = new javafx.scene.layout.BorderPane();
    // Keep the visible browser in place while a different, same-size browser prepares off screen.
    // IsVisible stays true so WebView2 can finish animation-frame/layout barriers normally.
    private final StackPane preparation = new StackPane();
    private java.util.Map<String,Boolean> preparedPreferences;
    private CompletionStage<Void> summaryReady=CompletableFuture.completedFuture(null);
    private final Label error = UiTheme.label("", "incorrect");
    private final Button toggle, previous, next;
    private io.quizforge.desktop.browser.PracticeLearningSurface shared;
    private io.quizforge.desktop.browser.HistoryLearningSurface replay;
    private String replayAttemptId;
    private final javafx.scene.layout.HBox attempts = new javafx.scene.layout.HBox(10);
    private final Label attemptPosition = UiTheme.label("", "muted");
    private final Button previousAttempt, nextAttempt, currentAttempt;
    private SharedLearningSurfaceMode mode = SharedLearningSurfaceMode.PRACTICE;
    private java.util.function.BooleanSupplier available = () -> true;
    private boolean busy, destroyed, showingTransitional, showingSummary;
    private java.util.Map<String,Boolean> uiPreferences=java.util.Map.of();
    private java.util.function.Consumer<java.util.Map<String,Boolean>> onUiChange=preferences->{ };
    public void onUiChange(java.util.function.Consumer<java.util.Map<String,Boolean>> listener){onUiChange=listener;listener.accept(uiPreferences);}

    public PracticeSurfaceHost(PersistentPracticeRuntime runtime, Runnable onChanged, Runnable previousAction, Runnable nextAction) {
        this(runtime,onChanged,previousAction,nextAction,null);
    }
    PracticeSurfaceHost(PersistentPracticeRuntime runtime, Runnable onChanged, Runnable previousAction, Runnable nextAction,
            java.util.function.Supplier<io.quizforge.desktop.browser.PracticeLearningSurface> surfaceFactory) {
        this.runtime = runtime; this.onChanged = onChanged;
        this.surfaceFactory = surfaceFactory;
        setId("practice-surface-host"); setMinSize(0, 0);
        getStyleClass().add("practice-surface-host");
toggle = UiTheme.button("草稿", "book-pen", "", this::toggle); toggle.setId("practice-draft-toggle");
        previous = QuestionCardLayout.navigation("arrow-left", "上一题", () -> {
            if (showingSummary) navigate(() -> { runtime.previous(); onChanged.run(); });
            else previousAction.run();
        });
        next = QuestionCardLayout.navigation("arrow", "下一题", nextAction);
        previousAttempt=QuestionCardLayout.navigation("arrow-left","上一次尝试",()->selectAttempt(displayedAttemptIndex()-1));
        nextAttempt=QuestionCardLayout.navigation("arrow","下一次尝试",()->selectAttempt(displayedAttemptIndex()+1));
        previousAttempt.getGraphic().setRotate(90);nextAttempt.getGraphic().setRotate(90);
        previousAttempt.visibleProperty().unbind();nextAttempt.visibleProperty().unbind();
        currentAttempt=UiTheme.button("返回当前作答","arrow", "",()->selectAttempt(currentAttemptIndex()));
        previousAttempt.setId("practice-previous-attempt");nextAttempt.setId("practice-next-attempt");
        currentAttempt.setId("practice-current-attempt");attemptPosition.setId("practice-attempt-position");
        attempts.setId("practice-attempt-controls");attempts.setAlignment(Pos.CENTER_LEFT);
        attempts.setStyle("-fx-padding: 8 20 8 20; -fx-background-color: #f5f4f7;");
        attempts.getChildren().addAll(attemptPosition,currentAttempt);
        attempts.setVisible(false);attempts.setManaged(false);
        previous.visibleProperty().unbind(); next.visibleProperty().unbind();
        previous.setId("draft-previous-question"); next.setId("draft-next-question");
        StackPane.setAlignment(previous, Pos.CENTER_LEFT); StackPane.setAlignment(next, Pos.CENTER_RIGHT);
        StackPane.setAlignment(previousAttempt,Pos.TOP_CENTER);StackPane.setAlignment(nextAttempt,Pos.BOTTOM_CENTER);
        for(var button:java.util.List.of(previous,next,previousAttempt,nextAttempt))StackPane.setMargin(button,new javafx.geometry.Insets(12));
        error.setId("practice-surface-error"); error.setWrapText(true); StackPane.setAlignment(error, Pos.BOTTOM_CENTER);
        preparation.setTranslateX(100000);preparation.setMouseTransparent(true);preparation.setVisible(false);preparation.setManaged(false);
        getChildren().addAll(preparation,learningFrame, transitional, previous, next,previousAttempt,nextAttempt,attempts, error); ensureShared();
        updateChrome();
    }
    public boolean unified() { return true; }
    public SharedLearningSurfaceMode learningMode() { return mode; }
    /** Transitional source compatibility; formal product mode uses learningMode(). */
    public PracticeSurfaceMode mode() { return learningMode() == SharedLearningSurfaceMode.DRAFT ? PracticeSurfaceMode.DRAFT : PracticeSurfaceMode.NORMAL; }
    public Button toggleButton() { return toggle; }
    public boolean busy() { return busy; }
    public SharedPracticeCanvasWebView draftView() { return shared instanceof SharedPracticeCanvasWebView legacy?legacy:null; }
    public io.quizforge.desktop.browser.PracticeLearningSurface learningSurface(){return shared;}
    public io.quizforge.desktop.browser.HistoryLearningSurface attemptSurface(){return replay;}
    public boolean reviewingAttempt(){return replayAttemptId!=null;}
    public CompletionStage<Void> ready() { return shared == null ? CompletableFuture.completedFuture(null) : shared.ready(); }
    public boolean supportsCurrent() {
        if (runtime.session().finished() || !available.getAsBoolean()) return false;
        try { SharedPracticeViewModel.from(runtime.snapshot()); return true; }
        catch (IllegalArgumentException unsupported) { return false; }
    }
    public void setDraftAvailable(java.util.function.BooleanSupplier value) {
        available = value; updateChrome();
    }
    public void setNormalContent(Node content) {
        showingSummary = false; showingTransitional = true; transitional.getChildren().setAll(content); updateChrome();
    }
    public void setSummaryContent(Node content) {
        uiPreferences=java.util.Map.of();onUiChange.accept(uiPreferences);
        if(shared!=null&&shared.nativeSurface()&&shared.isReady()){
            showingSummary=true;showingTransitional=false;replayAttemptId=null;
            learningFrame.setCenter(shared.view());learningFrame.setBottom(null);
            // Retain the action owner in the Scene for the existing restart confirmation dialog.
            transitional.getChildren().setAll(content);
            var restart=content.lookup("#practice-restart");
            summaryReady=shared.showSummary(runtime.summary(),()->{if(restart instanceof Button button)button.fire();});
            updateChrome();return;
        }
        showingSummary = true;showingTransitional=true;
        transitional.getChildren().setAll(content);updateChrome();
    }
    public void showQuestion() { showingSummary = false; showingTransitional = false; transitional.getChildren().clear(); ensureShared(); updateChrome(); }
    public void setSourceContent(Node content) { learningFrame.setBottom(content);applySourceUi(); }
    private void applySourceUi(){var source=learningFrame.getBottom();if(source!=null){boolean visible=uiPreferences.getOrDefault("sources",true);source.setVisible(visible);source.setManaged(visible);}}
    private void ensureShared() {
        if (shared != null || !supportsCurrent() || destroyed) return;
        shared = surfaceFactory != null ? surfaceFactory.get() : io.quizforge.desktop.browser.webview2.WebView2LearningSurface.enabled()
            ? new io.quizforge.desktop.browser.webview2.WebView2LearningSurface(runtime,onChanged)
            : new SharedPracticeCanvasWebView(new SharedPracticeAdapter(runtime), onChanged);
        shared.configurePageActions(this::pageState,this::pageCommand);
        shared.onUiChange(preferences->{if(shared.view().getParent()==preparation){preparedPreferences=preferences;return;}if(!reviewingAttempt()){uiPreferences=preferences;applySourceUi();updateChrome();onUiChange.accept(preferences);}});
        shared.view().setId("shared-learning-webview"); learningFrame.setCenter(shared.view());
        boolean ownsBusy=!busy;busy=true;
        if(ownsBusy)finish(shared.ready(), () -> shared.setLearningMode(mode));
        else shared.ready().thenRun(()->shared.setLearningMode(mode));
    }
    public void refreshChrome() { applySourceUi();updateChrome(); }
    private void ensureReplay(){
        if(replay!=null)return;
        replay=io.quizforge.desktop.browser.webview2.WebView2LearningSurface.enabled()
            ?new io.quizforge.desktop.browser.webview2.WebView2HistorySurface()
            :new io.quizforge.desktop.ui.question.history.HistoryDraftWebView();
        replay.configurePageActions(this::pageState,this::pageCommand);
        replay.onUiChange(preferences->{if(replay.view().getParent()==preparation){preparedPreferences=preferences;return;}if(reviewingAttempt()){uiPreferences=preferences;applySourceUi();updateChrome();onUiChange.accept(preferences);}});
    }
    private void updateChrome() {
        updateAttempts();
        toggle.setText(mode == SharedLearningSurfaceMode.DRAFT ? "返回练习" : "草稿"); toggle.setAccessibleText(toggle.getText());
        toggle.setVisible(supportsCurrent() && !showingTransitional && uiPreferences.getOrDefault("draftToggle",true)); toggle.setManaged(toggle.isVisible()); toggle.setDisable(busy || destroyed);
        transitional.setVisible(showingTransitional); transitional.setManaged(showingTransitional);
        learningFrame.setVisible(!showingTransitional); learningFrame.setManaged(!showingTransitional);
        if (shared != null) { boolean show=shared.view().getParent()==preparation||!showingTransitional&&!reviewingAttempt();shared.view().setVisible(show);shared.view().setManaged(show); }
        if (replay != null) { boolean show=replay.view().getParent()==preparation||!showingTransitional&&reviewingAttempt();replay.view().setVisible(show);replay.view().setManaged(show); }
        previous.setVisible(showingSummary || !showingTransitional && runtime.session().index() > 0); previous.setManaged(previous.isVisible());
        next.setVisible(!showingTransitional&&!showingSummary); next.setManaged(next.isVisible()); previous.setDisable(busy); next.setDisable(busy);
        next.setAccessibleText(runtime.session().index() == runtime.session().bank().questions().size() - 1 ? "查看本次练习" : "下一题");
        if(reviewingAttempt()&&replay!=null&&replay.nativeSurface()){
            replay.navigation(!showingTransitional&&runtime.session().index()>0,!showingTransitional,busy,previous::fire,next::fire);
            previous.setVisible(false);previous.setManaged(false);next.setVisible(false);next.setManaged(false);
        }else if(shared!=null&&shared.nativeSurface()){
            shared.navigation(showingSummary||!showingTransitional&&runtime.session().index()>0,!showingTransitional&&!showingSummary,busy,previous::fire,next::fire);
            previous.setVisible(showingSummary&&showingTransitional);previous.setManaged(previous.isVisible());next.setVisible(false);next.setManaged(false);
        }
        error.setVisible(!error.getText().isEmpty()); error.setManaged(error.isVisible());
    }
    public void toggle() { if (busy() || destroyed) return; if (learningMode() == SharedLearningSurfaceMode.PRACTICE) enterDraft(); else leaveDraft(); }
    public CompletionStage<Void> enterDraft() { return changeMode(SharedLearningSurfaceMode.DRAFT); }
    public CompletionStage<Void> leaveDraft() { return changeMode(SharedLearningSurfaceMode.PRACTICE); }
    public CompletionStage<Void> flushBeforeLeave() {
        if (busy || destroyed) return CompletableFuture.failedFuture(new IllegalStateException("练习操作进行中"));
        if (shared == null || showingTransitional || reviewingAttempt()) return CompletableFuture.completedFuture(null);
        busy = true; updateChrome();
        return finish(shared.flushPendingDraft(), () -> shared.setLearningMode(mode));
    }
    private CompletionStage<Void> changeMode(SharedLearningSurfaceMode target) {
        if (busy || destroyed || shared == null || !supportsCurrent()) return CompletableFuture.failedFuture(new IllegalStateException("练习操作进行中"));
        busy = true; error.setText(""); updateChrome();
        if(reviewingAttempt())return finish(CompletableFuture.completedFuture(null),()->{mode=target;replay.setLearningMode(replayMode());});
        return finish(shared.flushPendingDraft(), () -> { shared.setLearningMode(target); mode = target; });
    }
    public void focusTarget(String targetId) {
        if (!busy && !destroyed && !showingTransitional && shared != null) {
            if(reviewingAttempt())replay.focusTarget(targetId);else shared.focusTarget(targetId);
        }
    }
    public CompletionStage<Void> navigate(Runnable action) { return navigate(action, null); }
    public CompletionStage<Void> navigate(Runnable action, String targetId) {
        if (busy || destroyed) return CompletableFuture.failedFuture(new IllegalStateException("练习操作进行中"));
        busy = true; error.setText(""); updateChrome();
        var unloaded = new java.util.concurrent.atomic.AtomicBoolean();
        var replacing = new java.util.concurrent.atomic.AtomicBoolean();
        String previousQuestionId = runtime.session().current().id();
        var flush = shared == null || showingTransitional || reviewingAttempt() ? CompletableFuture.<Void>completedFuture(null) : shared.flushPendingDraft();
        var reloaded=flush.thenCompose(ignored -> {
            if (shared != null) { shared.unloadCurrent(); unloaded.set(true); }
            replayAttemptId=null;if(replay!=null)replay.clear();
            if(shared!=null)learningFrame.setCenter(shared.view());
            action.run();
            if (supportsCurrent()) {
                showingTransitional = false; ensureShared();
                return shared.ready().thenCompose(v -> { replacing.set(true); return shared.reloadCurrent(); });
            }
            return showingSummary?summaryReady:CompletableFuture.<Void>completedFuture(null);
        });
        // A failed navigation must not leave the business queue suspended: it would
        // drop every later save intent while the next flush waited for its reply.
        var recoverable = reloaded.exceptionallyCompose(failure -> {
            if (!unloaded.get() || destroyed || shared == null || !supportsCurrent())
                return CompletableFuture.failedFuture(failure);
            // If Core advanced before the callback failed, restore that authoritative
            // question instead of unlocking the retained page for a different identity.
            var recovery = !replacing.get() && !previousQuestionId.equals(runtime.session().current().id())
                    ? shared.reloadCurrent() : shared.resumeCurrent();
            return recovery.handle((ignored, recoveryFailure) -> {
                if (recoveryFailure != null) failure.addSuppressed(recoveryFailure);
                throw new java.util.concurrent.CompletionException(failure);
            });
        });
        return finish(recoverable,()->{
            if(supportsCurrent()){shared.setLearningMode(mode);if(targetId!=null)shared.focusTarget(targetId);}
        });
    }
    private io.quizforge.core.practice.ActivePracticeSnapshot.Question currentRow(){
        return runtime.questionState(runtime.session().current().id());
    }
    private int currentAttemptIndex(){
        var row=currentRow();return row.attempts().size()-(row.sessionQuestion().practiceState()==io.quizforge.core.practice.PracticeSessionQuestion.State.SUBMITTED?1:0);
    }
    private int displayedAttemptIndex(){
        if(!reviewingAttempt())return currentAttemptIndex();
        var values=currentRow().attempts();
        for(int i=0;i<values.size();i++)if(values.get(i).id().equals(replayAttemptId))return i;
        throw new IllegalStateException("尝试记录已变化");
    }
    private void updateAttempts(){
        boolean visible=!showingTransitional&&!runtime.session().finished()&&supportsCurrent()&&!currentRow().attempts().isEmpty();
        var active=reviewingAttempt()?replay:shared;
        boolean nativeSurface=active!=null&&(reviewingAttempt()?replay.nativeSurface():shared.nativeSurface());
        int selected=visible?displayedAttemptIndex():0,last=visible?currentAttemptIndex():0;
        boolean hasPrevious=visible&&selected>0,hasNext=visible&&selected<last;
        previousAttempt.setVisible(hasPrevious&&!nativeSurface);previousAttempt.setManaged(previousAttempt.isVisible());
        nextAttempt.setVisible(hasNext&&!nativeSurface);nextAttempt.setManaged(nextAttempt.isVisible());
        if(reviewingAttempt()&&replay!=null)replay.attemptNavigation(hasPrevious,hasNext,busy||destroyed,previousAttempt::fire,nextAttempt::fire);
        else if(shared!=null)shared.attemptNavigation(hasPrevious,hasNext,busy||destroyed,previousAttempt::fire,nextAttempt::fire);
        previousAttempt.setDisable(busy||destroyed||!hasPrevious);nextAttempt.setDisable(busy||destroyed||!hasNext);
        if(!visible)return;
        int count=currentRow().attempts().size();
        attemptPosition.setText(selected<count?"第 "+(selected+1)+" / "+count+" 次尝试"+(reviewingAttempt()?" · 只读":" · 已提交")
            :"第 "+(count+1)+" 次尝试 · 未提交");
        previousAttempt.setDisable(busy||destroyed||selected<=0);nextAttempt.setDisable(busy||destroyed||selected>=last);
        currentAttempt.setVisible(reviewingAttempt());currentAttempt.setManaged(reviewingAttempt());currentAttempt.setDisable(busy||destroyed);
    }
    private io.quizforge.desktop.ui.question.history.HistorySurfaceMode replayMode(){
        return mode==SharedLearningSurfaceMode.DRAFT?io.quizforge.desktop.ui.question.history.HistorySurfaceMode.DRAFT:io.quizforge.desktop.ui.question.history.HistorySurfaceMode.RESULT;
    }
    /** Host-owned attempt navigation. Extensions keep the same practice page and read-only contract. */
    public CompletionStage<Void> selectAttempt(int index){
        if(busy||destroyed||showingTransitional||shared==null)return CompletableFuture.failedFuture(new IllegalStateException("练习操作进行中"));
        int last=currentAttemptIndex();
        if(index<0||index>last)return CompletableFuture.failedFuture(new IllegalArgumentException("尝试序号无效"));
        if(index==displayedAttemptIndex())return CompletableFuture.completedFuture(null);
        boolean wasReplay=reviewingAttempt();busy=true;error.setText("");updateChrome();
        var flush=wasReplay?CompletableFuture.<Void>completedFuture(null):shared.flushPendingDraft();
        var loaded=flush.thenCompose(ignored->{
            if(destroyed)throw new IllegalStateException("Practice page closed");
            if(index==last){
                prepareSurface(shared.view());
                shared.setLearningMode(mode);
                return shared.resumeCurrent();
            }else{
                ensureReplay();
                prepareSurface(replay.view());
                var attemptId=currentRow().attempts().get(index).id();
                var card=SharedPracticeViewModel.from(runtime.snapshot(),attemptId);
                var draft=runtime.attemptDraft(attemptId);
                return replay.ready().thenCompose(v->{replay.setLearningMode(replayMode());return replay.load(card,draft);});
            }
        });
        return finish(loaded,()->{
            if(index==last){
                replayAttemptId=null;showPreparedSurface(shared.view());shared.setLearningMode(mode);
                if(replay!=null)replay.clear();
            }else{
                // Suspend Core writes while retaining the flushed card for a fast return.
                if(!wasReplay)shared.unloadCurrent();
                replayAttemptId=currentRow().attempts().get(index).id();showPreparedSurface(replay.view());
            }
        }).whenComplete((v,failure)->{
            if(failure!=null){
                Runnable restore=()->{clearPreparation();if(!destroyed&&!wasReplay)shared.setLearningMode(mode);updateChrome();};
                if(Platform.isFxApplicationThread())restore.run();else Platform.runLater(restore);
            }
        });
    }
    private void prepareSurface(Node view){
        if(learningFrame.getCenter()==view)return;
        preparedPreferences=null;preparation.getChildren().setAll(view);view.setVisible(true);view.setManaged(true);
        preparation.setVisible(true);preparation.setManaged(true);requestLayout();
    }
    private void showPreparedSurface(Node view){
        preparation.getChildren().remove(view);learningFrame.setCenter(view);
        if(preparedPreferences!=null){uiPreferences=preparedPreferences;applySourceUi();onUiChange.accept(uiPreferences);}
        clearPreparation();
    }
    private void clearPreparation(){
        preparation.getChildren().clear();preparation.setVisible(false);preparation.setManaged(false);preparedPreferences=null;
    }
    private java.util.Map<String,Object> pageState(){
        var questions=runtime.snapshot().questions().stream().map(entry->{var q=entry.sessionQuestion();return java.util.Map.<String,Object>of(
                "index",q.questionOrder(),"id",q.questionId(),"type",q.snapshot().questionType(),"state",q.practiceState().name());}).toList();
        var sources=learningFrame.getBottom() instanceof io.quizforge.desktop.ui.question.source.QuestionSourceListView list?list.pageSources():java.util.List.of();
        return java.util.Map.of("index",runtime.session().index(),"count",questions.size(),"questions",questions,"sources",sources,"learningMode",mode.name(),
                "attemptNavigation",java.util.Map.of("canPrevious",displayedAttemptIndex()>0,"canNext",displayedAttemptIndex()<currentAttemptIndex(),"isCurrent",!reviewingAttempt()),
                "targets",io.quizforge.desktop.ui.question.shared.QuestionTargetNumbers.current(runtime.session().bank().questions().stream().map(runtime.session()::outlineTargets).toList(),runtime.session().index()));
    }
    private CompletionStage<Void> pageCommand(String action,Object argument){
        if(busy||destroyed)throw new IllegalStateException("练习操作进行中");
        switch(action){
            case "navigate" -> {
                int target=io.quizforge.desktop.ui.question.shared.QuestionPageActions.index(argument,runtime.session().bank().questions().size()+1);
                if(target==runtime.session().index())return CompletableFuture.completedFuture(null);
                return navigate(()->{if(target==runtime.session().bank().questions().size())runtime.next();else runtime.goTo(target);onChanged.run();});
            }
            case "attempt.previous" -> { return selectAttempt(displayedAttemptIndex()-1); }
            case "attempt.next" -> { return selectAttempt(displayedAttemptIndex()+1); }
            case "attempt.current" -> { return selectAttempt(currentAttemptIndex()); }
            case "learning.mode" -> {
                if(!(argument instanceof String value))throw new IllegalArgumentException("模式无效");
                return changeMode(SharedLearningSurfaceMode.valueOf(value));
            }
            case "source.open" -> {
                if(!(learningFrame.getBottom() instanceof io.quizforge.desktop.ui.question.source.QuestionSourceListView list))throw new IllegalArgumentException("当前题目没有来源");
                list.openSource(io.quizforge.desktop.ui.question.shared.QuestionPageActions.index(argument,list.pageSources().size()));
                return CompletableFuture.completedFuture(null);
            }
            default -> throw new IllegalArgumentException("不支持的页面操作");
        }
    }
    private CompletionStage<Void> finish(CompletionStage<Void> operation, Runnable success) {
        var result = new CompletableFuture<Void>();
        operation.whenComplete((ignored, failure) -> {
            Runnable complete = () -> {
                try { if (destroyed) throw new IllegalStateException("Practice page closed"); if (failure != null) throw new java.util.concurrent.CompletionException(failure); success.run();busy=false; updateChrome(); result.complete(null); }
                catch (RuntimeException problem) { busy=false;Throwable cause = problem; while (cause.getCause() != null) cause = cause.getCause(); error.setText("练习操作未完成：" + cause.getMessage()); updateChrome(); result.completeExceptionally(cause); }
            };
            if (Platform.isFxApplicationThread()) complete.run(); else Platform.runLater(complete);
        });
        return result.minimalCompletionStage();
    }
    public boolean prepareClose() {
        try { if (busy && shared != null && shared.isReady()) throw new IllegalStateException("练习保存进行中，请稍后关闭"); if (shared != null) shared.saveBeforeClose(); return true; }
        catch (RuntimeException failure) { error.setText(failure.getMessage()); updateChrome(); return false; }
    }
    public CompletionStage<Boolean> prepareCloseAsync(){
        if(shared==null||!shared.nativeSurface())return CompletableFuture.completedFuture(prepareClose());
        if(busy)return CompletableFuture.completedFuture(false);
        busy=true;updateChrome();
        var result=new CompletableFuture<Boolean>();
        shared.prepareCloseAsync().whenComplete((v,failure)->Platform.runLater(()->{
            busy=false;if(failure!=null)error.setText("草稿保存失败："+failure.getMessage());updateChrome();result.complete(failure==null);
        }));return result.minimalCompletionStage();
    }
    public void cancelClose(){if(shared!=null)shared.cancelClose();}
    public void destroy() {
        if (destroyed) return;
        if (busy && shared != null && shared.isReady()) throw new IllegalStateException("练习保存进行中，请稍后关闭");
        if (shared != null) shared.destroy(); shared = null;
        if(replay!=null)replay.destroy();replay=null;
        destroyed = true; clearPreparation();learningFrame.setCenter(null); learningFrame.setBottom(null); transitional.getChildren().clear(); getChildren().clear();
    }
}
