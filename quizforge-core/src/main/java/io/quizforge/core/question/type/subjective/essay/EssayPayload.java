package io.quizforge.core.question.type.subjective.essay;

import io.quizforge.core.question.model.QuestionPayload;

/** Optional writing guidance, never a user's practice answer. */
public record EssayPayload(String placeholder) implements QuestionPayload { }
