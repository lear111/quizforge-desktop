package io.quizforge.desktop.ui.question.practice;

import io.quizforge.core.practice.PersistentPracticeRuntime;
import io.quizforge.core.question.type.QuestionTypes;
import io.quizforge.desktop.poc.sharedpractice.SharedPracticeAdapter;
import io.quizforge.desktop.poc.sharedpractice.SharedPracticeCanvasWebView;
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

/** Tab-owned reading surface. No Stage, new session, question index or answer state lives here. */
public final class PracticeSurfaceHost extends StackPane {
    private final PersistentPracticeRuntime runtime;
    private final Runnable refreshNormal;
    private final Runnable previous;
    private final Runnable next;
    private final StackPane normal = new StackPane();
    private final Label error = UiTheme.label("", "incorrect");
    private final Button toggle;
    private final Button previousDraft;
    private final Button nextDraft;
    private SharedPracticeCanvasWebView draft;
    private PracticeSurfaceMode mode = PracticeSurfaceMode.NORMAL;
    private boolean busy, destroyed, loaded;
    private java.util.function.BooleanSupplier draftAvailable = () -> true;

    public PracticeSurfaceHost(PersistentPracticeRuntime runtime, Runnable refreshNormal, Runnable previous, Runnable next) {
        this.runtime=runtime; this.refreshNormal=refreshNormal; this.previous=previous; this.next=next;
        setId("practice-surface-host"); setMinSize(0,0);
        normal.setId("normal-practice-surface");
        toggle=UiTheme.button("草稿", "book-pen", "", this::toggle);
        toggle.setId("practice-draft-toggle");
        error.setId("practice-surface-error"); error.setWrapText(true);
        StackPane.setAlignment(error,Pos.BOTTOM_CENTER);
        previousDraft=QuestionCardLayout.navigation("arrow-left","上一题",previous);
        nextDraft=QuestionCardLayout.navigation("arrow","下一题",next);
        // This host owns mode/boundary visibility; shared navigation normally binds it to disabled.
        previousDraft.visibleProperty().unbind(); nextDraft.visibleProperty().unbind();
        previousDraft.setId("draft-previous-question"); nextDraft.setId("draft-next-question");
        StackPane.setAlignment(previousDraft,Pos.CENTER_LEFT); StackPane.setAlignment(nextDraft,Pos.CENTER_RIGHT);
        getChildren().addAll(normal,previousDraft,nextDraft,error); updateChrome();
    }
    public Button toggleButton() { return toggle; }
    public PracticeSurfaceMode mode() { return mode; }
    public boolean busy() { return busy; }
    public SharedPracticeCanvasWebView draftView() { return draft; }
    public void refreshChrome(){updateChrome();}
    public void setDraftAvailable(java.util.function.BooleanSupplier available){draftAvailable=java.util.Objects.requireNonNull(available);updateChrome();}
    public void setNormalContent(Node content) {
        normal.getChildren().setAll(content); updateChrome();
    }
    private boolean supported() {
        return !runtime.session().finished() && draftAvailable.getAsBoolean()
                && io.quizforge.desktop.poc.sharedpractice.SharedPracticeViewModel.supportsType(runtime.session().current().type());
    }
    private void updateChrome() {
        toggle.setText(mode==PracticeSurfaceMode.DRAFT?"退出草稿":"草稿");
        toggle.setAccessibleText(toggle.getText());
        toggle.setVisible(supported()); toggle.setManaged(supported()); toggle.setDisable(busy || destroyed);
        normal.setVisible(mode==PracticeSurfaceMode.NORMAL); normal.setManaged(mode==PracticeSurfaceMode.NORMAL);
        if(draft!=null){draft.view().setVisible(mode==PracticeSurfaceMode.DRAFT);draft.view().setManaged(mode==PracticeSurfaceMode.DRAFT);}
        previousDraft.setVisible(mode==PracticeSurfaceMode.DRAFT && runtime.session().index()>0);
        nextDraft.setVisible(mode==PracticeSurfaceMode.DRAFT);
        previousDraft.setManaged(previousDraft.isVisible());nextDraft.setManaged(nextDraft.isVisible());
        previousDraft.setDisable(busy);nextDraft.setDisable(busy);
        error.setVisible(!error.getText().isEmpty());error.setManaged(error.isVisible());
    }
    private void showError(Throwable failure) {
        error.setText("草稿操作未完成："+rootCause(failure).getMessage());updateChrome();
    }
    private static Throwable rootCause(Throwable t){while(t.getCause()!=null)t=t.getCause();return t;}
    public void toggle(){if(busy || destroyed)return;if(mode==PracticeSurfaceMode.NORMAL)enterDraft();else leaveDraft();}
    public CompletionStage<Void> enterDraft() {
        if(busy || destroyed || !supported())return CompletableFuture.failedFuture(new IllegalStateException("当前题目暂不支持草稿"));
        busy=true;error.setText("");updateChrome();
        try {
            runtime.refresh();
            CompletionStage<Void> ready;
            if(draft==null){
                draft=new SharedPracticeCanvasWebView(new SharedPracticeAdapter(runtime),refreshNormal);
                draft.view().setId("draft-practice-surface");getChildren().add(1,draft.view());updateChrome();ready=draft.ready();
            }else if(loaded)ready=draft.refreshCurrent();
            else ready=draft.reloadCurrent();
            return finish(ready,()->{loaded=true;mode=PracticeSurfaceMode.DRAFT;});
        }catch(RuntimeException failure){busy=false;showError(failure);return CompletableFuture.failedFuture(failure);}
    }
    public CompletionStage<Void> leaveDraft() {
        if(busy || destroyed)return CompletableFuture.failedFuture(new IllegalStateException("草稿操作进行中"));
        busy=true;error.setText("");updateChrome();
        return finish(draft.flushPendingDraft(),()->{runtime.refresh();mode=PracticeSurfaceMode.NORMAL;refreshNormal.run();});
    }
    /** Existing normal/outline/navigation commands all pass through this barrier before changing Core position. */
    public void focusTarget(String targetId) {
        if(busy || destroyed || mode!=PracticeSurfaceMode.DRAFT || !loaded)return;
        try { draft.focusTarget(targetId); } catch(RuntimeException failure) {showError(failure);}
    }
    public void navigate(Runnable action) { navigate(action,null); }
    public void navigate(Runnable action,String targetId) {
        if(busy || destroyed)return;
        if(mode==PracticeSurfaceMode.NORMAL){
            try {if(draft!=null && loaded){draft.unloadCurrent();loaded=false;}action.run();updateChrome();}
            catch(RuntimeException failure){showError(failure);}return;
        }
        busy=true;error.setText("");updateChrome();
        finish(draft.flushPendingDraft(),()->{
            draft.unloadCurrent();loaded=false;
            action.run();
            if(!supported()){mode=PracticeSurfaceMode.NORMAL;updateChrome();return;}
            busy=true;
            finish(draft.reloadCurrent(),()->{loaded=true;if(targetId!=null)draft.focusTarget(targetId);});
        });
    }
    private CompletionStage<Void> finish(CompletionStage<Void> operation,Runnable success) {
        var result=new CompletableFuture<Void>();
        operation.whenComplete((ignored,failure)->{
            Runnable completed=()->{
                busy=false;
                if(destroyed){result.completeExceptionally(new IllegalStateException("Practice page closed"));return;}
                try {if(failure!=null)throw new java.util.concurrent.CompletionException(failure);success.run();updateChrome();result.complete(null);}
                catch(RuntimeException error){showError(error);result.completeExceptionally(rootCause(error));}
            };
            if(Platform.isFxApplicationThread())completed.run();else Platform.runLater(completed);
        });return result.minimalCompletionStage();
    }
    /** Host destruction uses the existing synchronous Core close-save guard before releasing the bridge. */
    public void destroy() {
        if(destroyed)return;
        if(busy)throw new IllegalStateException("草稿保存进行中，请稍后关闭");
        if(draft!=null)draft.destroy();
        destroyed=true;getChildren().clear();updateChrome();
    }
    public boolean prepareClose() {
        if(destroyed)return true;
        try {if(busy)throw new IllegalStateException("草稿保存进行中，请稍后关闭");if(draft!=null)draft.saveBeforeClose();return true;}
        catch(RuntimeException failure){showError(failure);return false;}
    }
}
