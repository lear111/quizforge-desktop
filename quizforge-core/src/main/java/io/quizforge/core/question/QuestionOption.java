package io.quizforge.core.question;

import java.util.UUID;

public record QuestionOption(UUID id, QuestionId questionId, String key, String content,
        boolean correct, int sortOrder) { }
