package io.quizforge.core.question.model;

import java.math.BigDecimal;

public record EvaluationCriterion(String id, String description, BigDecimal weight) { }
