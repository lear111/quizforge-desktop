package io.quizforge.desktop.ui.question.shared;

import io.quizforge.core.practice.PracticeSummary;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Optional;

/** Shared points display for active rounds, history cards and confirmation text. */
public final class PracticeScoreText {
    private PracticeScoreText(){}
    public static String points(Optional<BigDecimal> value){
        // Hide floating point artifacts from the persisted Double score columns.
        return value.map(score->score.round(new MathContext(12)).stripTrailingZeros().toPlainString()).orElse("—");
    }
    public static String summary(PracticeSummary summary){
        return "得分 "+points(summary.score())+" / "+points(summary.maxScore());
    }
}
