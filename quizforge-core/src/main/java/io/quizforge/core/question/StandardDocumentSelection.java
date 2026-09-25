package io.quizforge.core.question;

public record StandardDocumentSelection(String documentAssetId, GenerationScopeType scope,
        String chapterId, String sectionId) {
    public StandardDocumentSelection {
        if (documentAssetId == null || documentAssetId.isBlank() || scope == null) {
            throw new IllegalArgumentException("Document and scope are required");
        }
    }

    public static StandardDocumentSelection whole(String assetId) {
        return new StandardDocumentSelection(assetId, GenerationScopeType.DOCUMENT, null, null);
    }
}
