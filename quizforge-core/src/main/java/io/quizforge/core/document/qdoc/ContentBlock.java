package io.quizforge.core.document.qdoc;

import java.util.List;

/** Lists use items; other blocks use text. Code blocks may also specify a language. */
public record ContentBlock(ContentBlockType type, String text, List<String> items,
        String language) implements DocumentElement {
    public ContentBlock {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public static ContentBlock text(ContentBlockType type, String value) {
        return new ContentBlock(type, value, List.of(), null);
    }

    public static ContentBlock list(ContentBlockType type, List<String> values) {
        return new ContentBlock(type, null, values, null);
    }
}
