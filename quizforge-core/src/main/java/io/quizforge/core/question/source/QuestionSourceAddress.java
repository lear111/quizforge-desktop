package io.quizforge.core.question.source;

import io.quizforge.core.document.registered.NamedMarkdownAnchor;

/** One explicit source-address variant, keeping legacy identifiers out of anchor references. */
public record QuestionSourceAddress(Kind kind, String value, Integer occurrence) {
    public enum Kind { LEGACY_SECTION, LEGACY_NODE, ANCHOR }

    public QuestionSourceAddress {
        if (kind == null) throw new IllegalArgumentException("Source address kind is required");
        if (kind == Kind.ANCHOR) {
            value = NamedMarkdownAnchor.validateName(value);
            if (occurrence == null || occurrence < 1)
                throw new IllegalArgumentException("Invalid anchor occurrence");
        } else if (occurrence != null) {
            throw new IllegalArgumentException("Invalid legacy source address");
        }
    }

    public static QuestionSourceAddress section(String id) {
        return new QuestionSourceAddress(Kind.LEGACY_SECTION, id, null);
    }
    public static QuestionSourceAddress node(String id) {
        return new QuestionSourceAddress(Kind.LEGACY_NODE, id, null);
    }
    public static QuestionSourceAddress anchor(String name, int occurrence) {
        return new QuestionSourceAddress(Kind.ANCHOR, name, occurrence);
    }
}
