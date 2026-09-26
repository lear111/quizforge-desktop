package io.quizforge.core.document.qdoc;

import java.util.List;

public record DocumentNode(String id, DocumentNodeType type, String title,
        List<DocumentElement> children) implements DocumentElement {
    public DocumentNode { children = children == null ? List.of() : List.copyOf(children); }
}
