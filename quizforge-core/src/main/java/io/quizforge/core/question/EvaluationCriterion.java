package io.quizforge.core.question;

import java.math.BigDecimal;
public record EvaluationCriterion(String id, String description, BigDecimal weight) { }
