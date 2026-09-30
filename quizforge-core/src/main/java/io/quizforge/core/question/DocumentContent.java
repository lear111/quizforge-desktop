package io.quizforge.core.question;

import java.util.Objects;

/** An opaque document resource; text is a derived search/accessibility summary. */
public record DocumentContent(String resourceId, String text) implements QuestionContent {
    public DocumentContent { Objects.requireNonNull(resourceId); Objects.requireNonNull(text); }
}
