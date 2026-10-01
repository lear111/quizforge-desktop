package io.quizforge.core.question.content;

import java.util.Objects;

public record RichContent(RichDocument document) implements QuestionContent {
    public RichContent { Objects.requireNonNull(document); }
}
