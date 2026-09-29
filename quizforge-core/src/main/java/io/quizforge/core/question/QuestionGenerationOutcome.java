package io.quizforge.core.question;

public record QuestionGenerationOutcome(StoredQuestionBank bank, int requested, int generated,
        int accepted, int rejected) { }
