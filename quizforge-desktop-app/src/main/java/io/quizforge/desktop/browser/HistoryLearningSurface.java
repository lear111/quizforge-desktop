package io.quizforge.desktop.browser;

import io.quizforge.core.practice.draft.DraftCanvasDocument;
import io.quizforge.desktop.learning.SharedPracticeViewModel;
import io.quizforge.desktop.ui.question.history.HistorySurfaceMode;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.function.*;
import javafx.scene.Node;

/** Frozen replay only. There are no answer, scoring, persistence or editing operations. */
public interface HistoryLearningSurface {
    Node view();
    CompletionStage<Void> ready();
    CompletionStage<Void> load(SharedPracticeViewModel card,DraftCanvasDocument document);
    void setLearningMode(HistorySurfaceMode mode);
    void clear();
    boolean focusTarget(String targetId);
    void configurePageActions(Supplier<Map<String,Object>> state,BiFunction<String,Object,CompletionStage<Void>> command);
    void onUiChange(Consumer<Map<String,Boolean>> listener);
    default boolean nativeSurface(){return false;}
    default CompletionStage<Void> showSummary(io.quizforge.core.practice.PracticeSummary summary){return java.util.concurrent.CompletableFuture.completedFuture(null);}
    default void navigation(boolean previous,boolean next,boolean busy,Runnable back,Runnable forward){}
    default void attemptNavigation(boolean previous,boolean next,boolean busy,Runnable back,Runnable forward){}
    void destroy();
}
