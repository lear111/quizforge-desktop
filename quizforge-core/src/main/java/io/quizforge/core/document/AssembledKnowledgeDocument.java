package io.quizforge.core.document;

public record AssembledKnowledgeDocument(String assetId, String contentId, String title,
        String schemaVersion, String markdown) {
    /** Serialized asset content; markdown() remains for the legacy v1 call sites. */
    public String content() { return markdown; }
}
