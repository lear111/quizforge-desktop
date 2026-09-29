package io.quizforge.core.question;

import java.util.Objects;
public record TextContent(String text) implements QuestionContent {
    public TextContent { Objects.requireNonNull(text, "Text content cannot be null"); }
}
