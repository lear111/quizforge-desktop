package io.quizforge.core.document.registered;

/** A name bound to an addressable block; occurrence is derived from source order. */
public record NamedMarkdownAnchor(String name, int occurrence, MarkdownSourceRange blockRange,
        int sourceLine) {
    public NamedMarkdownAnchor {
        name = validateName(name);
        if (occurrence < 1) throw new IllegalArgumentException("INVALID_ANCHOR_OCCURRENCE");
        if (sourceLine < 1) throw new IllegalArgumentException("INVALID_ANCHOR_SOURCE_LINE");
    }

    public boolean orphan() { return blockRange == null; }

    public static String validateName(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("EMPTY_ANCHOR_NAME");
        String name = value.trim();
        if (name.length() > 100 || name.contains("-->") || name.indexOf('\n') >= 0
                || name.indexOf('\r') >= 0 || name.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("INVALID_ANCHOR_NAME");
        }
        return name;
    }
}
