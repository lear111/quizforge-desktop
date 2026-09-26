package io.quizforge.core.question;

public record StandardDocumentSelection(String documentAssetId, GenerationScopeType scope,
        String chapterId, String sectionId, String subsectionId) {
    public StandardDocumentSelection(String documentAssetId, GenerationScopeType scope,
            String chapterId, String sectionId) {
        this(documentAssetId, scope, chapterId, sectionId, null);
    }

    public StandardDocumentSelection {
        if (documentAssetId == null || documentAssetId.isBlank() || scope == null) {
            throw new IllegalArgumentException("Document and scope are required");
        }
        if (scope == GenerationScopeType.SUBSECTION
                && (sectionId == null || sectionId.isBlank() || subsectionId == null || subsectionId.isBlank())) {
            throw new IllegalArgumentException("Subsection selection requires section and subsection IDs");
        }
    }

    public static StandardDocumentSelection whole(String assetId) {
        return new StandardDocumentSelection(assetId, GenerationScopeType.DOCUMENT, null, null, null);
    }
}
