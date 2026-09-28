package io.quizforge.core.practice;

import io.quizforge.core.question.QuestionBankFile;
import java.util.LinkedHashMap;
import java.util.Map;

/** The single mapping from current QBank content to persistence-neutral practice snapshots. */
public final class PracticeQuestionSnapshotMapper {
    public PracticeSessionQuestion.Snapshot map(QuestionBankFile.Entry question) {
        var options = question.data().options().stream()
                .map(option -> Map.of("id", option.id(), "content", option.content())).toList();
        // Choice answers are sets; list permutation alone does not change the correct answer.
        var correct = question.data().correctOptionIds().stream().sorted().toList();
        var refs = question.sourceRefs().stream().map(this::sourceRef).toList();
        return new PracticeSessionQuestion.Snapshot(question.type(), question.stem(),
                new PracticePayload(options), new PracticePayload(Map.of("correctOptionIds", correct)),
                question.analysis(), new PracticePayload(refs));
    }

    private Map<String, Object> sourceRef(QuestionBankFile.SourceRef ref) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("documentAssetId", ref.documentAssetId());
        fields.put("documentContentId", ref.documentContentId());
        switch (ref.address().kind()) {
            case ANCHOR -> {
                fields.put("anchorName", ref.anchorName());
                fields.put("occurrence", ref.occurrence());
            }
            case LEGACY_SECTION -> fields.put("sectionId", ref.nodeId());
            case LEGACY_NODE -> fields.put("nodeId", ref.nodeId());
        }
        fields.put("documentTitle", ref.documentTitle());
        fields.put("sectionTitle", ref.sectionTitle());
        return fields;
    }
}
