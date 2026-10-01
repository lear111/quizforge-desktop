package io.quizforge.core.question.model;

import io.quizforge.core.question.resource.QBankResource;
import io.quizforge.core.question.source.QuestionSourceDocument;
import java.util.List;

/** Portable QBank v2 logical content; identity is separate from revision. */
public record QuestionBank(String assetId, String title, String schemaVersion,
        List<Stimulus> stimuli, List<Question> questions, List<QBankResource> resources) {
    public static final String SCHEMA_VERSION = "2.0";
    public QuestionBank {
        stimuli = List.copyOf(stimuli);
        questions = List.copyOf(questions);
        resources = List.copyOf(resources);
    }
    public QuestionBank(String assetId, String title, List<Stimulus> stimuli,
            List<Question> questions, List<QBankResource> resources) {
        this(assetId, title, SCHEMA_VERSION, stimuli, questions, resources);
    }
    /** Runtime source summaries are derived from references, never another JSON schema field. */
    public List<QuestionSourceDocument> sourceDocuments() {
        return questions.stream().flatMap(question -> question.sourceRefs().stream())
                .map(ref -> new QuestionSourceDocument(ref.documentAssetId(), ref.documentContentId(),
                        ref.documentTitle())).distinct().toList();
    }
}
