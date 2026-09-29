package io.quizforge.desktop.ui;

import java.util.Objects;
import java.util.Set;

/** One submitted answer. Final session state and attempt navigation belong to the caller. */
record QuestionResultPresentation(QuestionPresentation question, Set<String> userAnswer, Result result,
        boolean showCorrectAnswer, boolean showAnalysis, boolean showSources) {
    enum Result { CORRECT, INCORRECT, UNSCORED }

    QuestionResultPresentation {
        Objects.requireNonNull(question);
        Objects.requireNonNull(result);
        userAnswer = Set.copyOf(userAnswer);
    }
}
