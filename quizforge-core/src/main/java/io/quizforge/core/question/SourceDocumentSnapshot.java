package io.quizforge.core.question;

import java.util.List;

/** Immutable content read from a validated document asset before calling AI. */
public record SourceDocumentSnapshot(String assetId, String contentId, String title,
        List<Chapter> chapters) {
    public SourceDocumentSnapshot { chapters = List.copyOf(chapters); }
    public record Chapter(String id, String title, List<Section> sections) {
        public Chapter { sections = List.copyOf(sections); }
    }
    public record Section(String id, String title, String markdown, List<Subsection> subsections) {
        public Section(String id, String title, String markdown) {
            this(id, title, markdown, List.of());
        }

        public Section { subsections = List.copyOf(subsections); }
        /** Selected source text; markdown() remains for the legacy reader. */
        public String content() { return markdown; }
    }
    public record Subsection(String id, String title, String content) { }
}
