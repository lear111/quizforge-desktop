package io.quizforge.extension.document;

import java.util.Objects;

public record DocumentProcessResult(String candidateContent) {
    public DocumentProcessResult {
        Objects.requireNonNull(candidateContent);
    }
}
