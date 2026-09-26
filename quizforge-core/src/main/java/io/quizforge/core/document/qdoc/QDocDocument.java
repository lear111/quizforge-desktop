package io.quizforge.core.document.qdoc;

import java.util.List;

/** Formal file asset; no path, UI state or serialized JSON is part of the model. */
public record QDocDocument(String format, String schemaVersion, String id,
        TemplateRef template, String title, String language, List<DocumentNode> content) {
    public QDocDocument { content = content == null ? List.of() : List.copyOf(content); }

    public record TemplateRef(String id, String version) { }
}
