package io.quizforge.core.question.source;

/** Shared recorded source identity. Legacy addresses are used only by historical snapshots. */
public record SourceRef(String documentAssetId, String documentContentId,
        QuestionSourceAddress address, String documentTitle, String sectionTitle) {
    public SourceRef(String documentAssetId, String documentContentId, String nodeId,
            String documentTitle, String sectionTitle) {
        this(documentAssetId, documentContentId, QuestionSourceAddress.node(nodeId), documentTitle, sectionTitle);
    }
    public static SourceRef anchor(String assetId, String contentId, String name, int occurrence,
            String documentTitle, String sectionTitle) {
        return new SourceRef(assetId, contentId, QuestionSourceAddress.anchor(name, occurrence), documentTitle, sectionTitle);
    }
    public String nodeId() { return address.kind() == QuestionSourceAddress.Kind.ANCHOR ? null : address.value(); }
    public String anchorName() { return address.kind() == QuestionSourceAddress.Kind.ANCHOR ? address.value() : null; }
    public Integer occurrence() { return address.occurrence(); }
}
