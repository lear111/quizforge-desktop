package io.quizforge.desktop.ui.question.objective.choice;

import java.util.Objects;
import java.util.Set;

/** One submitted answer. Final session state and attempt navigation belong to the caller. */
public record ChoiceResultPresentation(ChoicePresentation question, Set<String> userAnswer, Result result,
        boolean showCorrectAnswer, boolean showAnalysis, boolean showSources) {
    public enum Result { CORRECT, INCORRECT, UNSCORED }

    public ChoiceResultPresentation {
        Objects.requireNonNull(question);
        Objects.requireNonNull(result);
        userAnswer = Set.copyOf(userAnswer);
    }
}
