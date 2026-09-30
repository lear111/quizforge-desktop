package io.quizforge.core.practice;

import io.quizforge.core.question.*;

import java.util.LinkedHashMap;
import java.util.Map;

/** The single mapping from current QBank content to persistence-neutral practice snapshots. */
public final class PracticeQuestionSnapshotMapper {
    public PracticeSessionQuestion.Snapshot map(Question question) {
        if (!question.stimulusRefs().isEmpty())
            throw new UnsupportedOperationException("The current practice snapshot does not support shared stimuli");
        if ("ESSAY".equals(question.type())) {
            return new PracticeSessionQuestion.Snapshot(question.type(), QuestionContentData.plainText(question.prompt()),
                    new PracticePayload(java.util.List.of()), new PracticePayload(Map.of(
                            "correctOptionIds", java.util.List.of(),
                            "prompt", QuestionContentData.encode(question.prompt()),
                            "referenceAnswer", question.essayAnswerSpec().referenceAnswer() == null ? Map.of()
                                    : QuestionContentData.encode(question.essayAnswerSpec().referenceAnswer()))),
                    QuestionContentData.plainText(question.analysis()),
                    new PracticePayload(question.sourceRefs().stream().map(this::sourceRef).toList()));
        }
        var options = question.choicePayload().options().stream()
                .map(option -> Map.of("id", option.id(), "content", QuestionText.option(option))).toList();
        // Choice answers are sets; list permutation alone does not change the correct answer.
        var correct = question.choiceAnswerSpec().correctOptionIds().stream().sorted().toList();
        var refs = question.sourceRefs().stream().map(this::sourceRef).toList();
        return new PracticeSessionQuestion.Snapshot(question.type(), QuestionText.prompt(question),
                new PracticePayload(options), new PracticePayload(Map.of("correctOptionIds", correct)),
                QuestionText.analysis(question), new PracticePayload(refs));
    }

    private Map<String, Object> sourceRef(SourceRef ref) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("documentAssetId", ref.documentAssetId());
        fields.put("documentContentId", ref.documentContentId());
        if (ref.address().kind() != QuestionSourceAddress.Kind.ANCHOR)
            throw new IllegalArgumentException("Current QBank snapshots require named source anchors");
        fields.put("anchorName", ref.anchorName());
        fields.put("occurrence", ref.occurrence());
        fields.put("documentTitle", ref.documentTitle());
        fields.put("sectionTitle", ref.sectionTitle());
        return fields;
    }
}
