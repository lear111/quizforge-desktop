package io.quizforge.core.question;

import java.util.List;

/** Immutable content read from a validated formal Markdown file before calling AI. */
public record SourceDocumentSnapshot(String assetId, String contentId, String title,
        List<Chapter> chapters) {
    public SourceDocumentSnapshot { chapters = List.copyOf(chapters); }
    public record Chapter(String id, String title, List<Section> sections) {
        public Chapter { sections = List.copyOf(sections); }
    }
    public record Section(String id, String title, String markdown) { }
}
