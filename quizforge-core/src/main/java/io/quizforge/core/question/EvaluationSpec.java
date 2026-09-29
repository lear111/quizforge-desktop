package io.quizforge.core.question;

import java.util.List;
public record EvaluationSpec(List<EvaluationCriterion> criteria, String evaluatorGuidance) {
    public EvaluationSpec { criteria = List.copyOf(criteria); }
}
