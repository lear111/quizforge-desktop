package io.quizforge.extension.document;

import java.util.List;

public record StandardDocumentStructure(String title, String markdown, List<Chapter> chapters) {
    public StandardDocumentStructure {
        chapters = List.copyOf(chapters);
    }

    public record Chapter(String id, String title, String markdown, List<Section> sections) {
        public Chapter {
            sections = List.copyOf(sections);
        }
    }

    public record Section(String id, String title, String markdown) {
    }
}
