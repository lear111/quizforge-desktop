package io.quizforge.core.question;

public record QuestionGenerationOutcome(QuestionBank bank, int requested, int generated,
        int accepted, int rejected) { }
