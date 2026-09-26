package io.quizforge.core.document;

import io.quizforge.core.asset.Asset;

public record FileDocumentView(Asset asset, String markdown) {
    /** File content; markdown() remains for legacy callers. */
    public String content() { return markdown; }
}
