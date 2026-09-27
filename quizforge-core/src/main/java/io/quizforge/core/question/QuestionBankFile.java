package io.quizforge.core.question;

import java.util.List;

/** Portable QuestionBank v1 content. Workspace paths intentionally do not occur here. */
public record QuestionBankFile(String format, String schemaVersion, String id, String title,
        List<SourceDocument> sourceDocuments, List<Entry> questions) {
    public QuestionBankFile {
        sourceDocuments = List.copyOf(sourceDocuments);
        questions = List.copyOf(questions);
    }

    public record SourceDocument(String assetId, String contentId, String title) { }
    public record SourceRef(String documentAssetId, String documentContentId,
            QuestionSourceAddress address, String documentTitle, String sectionTitle) {
        public SourceRef(String documentAssetId, String documentContentId, String nodeId,
                String documentTitle, String sectionTitle) {
            this(documentAssetId, documentContentId, QuestionSourceAddress.node(nodeId),
                    documentTitle, sectionTitle);
        }
        public static SourceRef anchor(String assetId, String contentId, String name,
                int occurrence, String documentTitle, String sectionTitle) {
            return new SourceRef(assetId, contentId, QuestionSourceAddress.anchor(name, occurrence),
                    documentTitle, sectionTitle);
        }
        public String nodeId() { return address.kind() == QuestionSourceAddress.Kind.ANCHOR
                ? null : address.value(); }
        public String anchorName() { return address.kind() == QuestionSourceAddress.Kind.ANCHOR
                ? address.value() : null; }
        public Integer occurrence() { return address.occurrence(); }
    }
    public record Option(String id, String content) { }
    public record Data(List<Option> options, List<String> correctOptionIds) {
        public Data {
            options = List.copyOf(options);
            correctOptionIds = List.copyOf(correctOptionIds);
        }
    }
    public record Entry(String id, String type, String stem, String analysis,
            List<SourceRef> sourceRefs, Data data) {
        public Entry { sourceRefs = List.copyOf(sourceRefs); }
    }
}
