package io.quizforge.core.question;

/** Optional writing guidance, never a user's practice answer. */
public record EssayPayload(String placeholder) implements QuestionPayload { }
