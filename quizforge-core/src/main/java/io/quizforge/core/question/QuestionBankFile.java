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
    public record SourceRef(String documentAssetId, String documentContentId, String sectionId,
            String documentTitle, String sectionTitle) { }
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
