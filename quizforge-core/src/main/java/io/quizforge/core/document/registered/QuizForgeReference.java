package io.quizforge.core.document.registered;

/** Portable document or named-anchor reference, independent of a Workspace path. */
public record QuizForgeReference(String documentAssetId, String anchorName,
        Integer occurrence, String documentContentId) {
    public QuizForgeReference {
        if (documentAssetId == null || !documentAssetId.matches("doc_[A-Za-z0-9_-]+"))
            throw new IllegalArgumentException("Invalid document assetId");
        if (anchorName == null) {
            if (occurrence != null || documentContentId != null)
                throw new IllegalArgumentException("Incomplete document reference");
        } else {
            anchorName = NamedMarkdownAnchor.validateName(anchorName);
            if (occurrence == null || occurrence < 1)
                throw new IllegalArgumentException("Invalid anchor occurrence");
            if (documentContentId == null
                    || !documentContentId.matches("qfd:v[12]:[0-9a-f]{64}"))
                throw new IllegalArgumentException("Invalid document contentId");
        }
    }

    public static QuizForgeReference document(String assetId) {
        return new QuizForgeReference(assetId, null, null, null);
    }

    public static QuizForgeReference anchor(String assetId, String contentId,
            String anchorName, int occurrence) {
        return new QuizForgeReference(assetId, anchorName, occurrence, contentId);
    }

    public boolean isAnchor() { return anchorName != null; }
}
