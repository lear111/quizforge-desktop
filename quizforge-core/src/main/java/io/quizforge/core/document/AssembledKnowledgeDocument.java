package io.quizforge.core.document;

public record AssembledKnowledgeDocument(String assetId, String contentId, String title,
        String schemaVersion, String markdown) { }
