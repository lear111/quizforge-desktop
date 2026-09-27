package io.quizforge.core.document.registered;

import java.util.Objects;

public record AddressableMarkdownBlock(String nodeId, MarkdownBlockType blockType,
        String displayText, MarkdownSourceRange sourceRange) {
    public AddressableMarkdownBlock {
        if (nodeId == null || !nodeId.matches("node_[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("Invalid Markdown node ID");
        }
        Objects.requireNonNull(blockType);
        Objects.requireNonNull(displayText);
        Objects.requireNonNull(sourceRange);
    }
}
