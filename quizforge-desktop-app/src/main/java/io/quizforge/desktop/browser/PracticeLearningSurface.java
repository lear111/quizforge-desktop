package io.quizforge.desktop.browser;

import io.quizforge.desktop.ui.question.practice.SharedLearningSurfaceMode;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.function.*;
import javafx.scene.Node;

/** Product lifecycle shared by browser backends; no synchronous JavaScript results required. */
public interface PracticeLearningSurface {
    Node view();
    CompletionStage<Void> ready();
    boolean isReady();
    void onUiChange(Consumer<Map<String,Boolean>> listener);
    void configurePageActions(Supplier<Map<String,Object>> state,BiFunction<String,Object,CompletionStage<Void>> command);
    CompletionStage<Void> flushPendingDraft();
    CompletionStage<Void> reloadCurrent();
    default CompletionStage<Void> resumeCurrent(){return reloadCurrent();}
    default CompletionStage<Void> showSummary(io.quizforge.core.practice.PracticeSummary summary,Runnable restart){return java.util.concurrent.CompletableFuture.completedFuture(null);}
    void unloadCurrent();
    void setLearningMode(SharedLearningSurfaceMode mode);
    boolean focusTarget(String targetId);
    void saveBeforeClose();
    void destroy();
    default CompletionStage<Void> prepareCloseAsync(){saveBeforeClose();return java.util.concurrent.CompletableFuture.completedFuture(null);}
    default boolean nativeSurface(){return false;}
    default void cancelClose(){}
    default void navigation(boolean previous,boolean next,boolean busy,Runnable back,Runnable forward){}
    default void attemptNavigation(boolean previous,boolean next,boolean busy,Runnable back,Runnable forward){}
}
