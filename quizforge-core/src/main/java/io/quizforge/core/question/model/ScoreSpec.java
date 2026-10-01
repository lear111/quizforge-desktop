package io.quizforge.core.question.model;

import java.math.BigDecimal;

public record ScoreSpec(BigDecimal defaultMaxScore) {
    public static ScoreSpec defaultScore() { return new ScoreSpec(BigDecimal.ONE); }
}
