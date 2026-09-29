package io.quizforge.core.question;

import java.util.Objects;
public record RichContent(RichDocument document) implements QuestionContent {
    public RichContent { Objects.requireNonNull(document); }
}
