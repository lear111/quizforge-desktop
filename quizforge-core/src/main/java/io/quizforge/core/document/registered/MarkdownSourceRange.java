package io.quizforge.core.document.registered;

/** One-based source positions in the Markdown body, excluding Front Matter. */
public record MarkdownSourceRange(int startLine, int startColumn, int endLine, int endColumn) {
    public MarkdownSourceRange {
        if (startLine < 1 || startColumn < 1 || endLine < startLine || endColumn < 1) {
            throw new IllegalArgumentException("Invalid Markdown source range");
        }
    }
}
