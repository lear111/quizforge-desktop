package io.quizforge.core.practice;

import io.quizforge.core.question.content.QuestionContentData;
import io.quizforge.core.question.model.Question;
import io.quizforge.core.question.resource.QBankResource;
import io.quizforge.core.question.source.QuestionSourceAddress;
import io.quizforge.core.question.source.SourceRef;
import io.quizforge.core.question.type.QuestionTypes;
import io.quizforge.core.question.type.objective.choice.QuestionText;
import java.util.LinkedHashMap;
import java.util.Map;

/** The single mapping from current QBank content to persistence-neutral practice snapshots. */
public final class PracticeQuestionSnapshotMapper {
    public PracticeSessionQuestion.Snapshot map(Question question,java.util.List<QBankResource> resources,
            io.quizforge.core.port.QuestionResourceInput input){
        var snapshot=map(question);
        if(!QuestionTypes.isEssay(question.type()))return snapshot;
        var fields=new LinkedHashMap<String,Object>();
        QuestionContentData.map(snapshot.correctAnswer().value()).forEach((key,value)->fields.put((String)key,value));
        fields.put("essayPresentation",EssayQuestionSnapshot.capture(question,resources,input).payload().value());
        return new PracticeSessionQuestion.Snapshot(snapshot.questionType(),snapshot.stem(),snapshot.options(),
                new PracticePayload(fields),snapshot.analysis(),snapshot.sourceRefs());
    }

    public static PracticeSessionQuestion.Snapshot logical(PracticeSessionQuestion.Snapshot snapshot){
        if(!QuestionTypes.isEssay(snapshot.questionType()))return snapshot;
        var fields=new LinkedHashMap<String,Object>();
        QuestionContentData.map(snapshot.correctAnswer().value()).forEach((key,value)->{if(!"essayPresentation".equals(key))fields.put((String)key,value);});
        return new PracticeSessionQuestion.Snapshot(snapshot.questionType(),snapshot.stem(),snapshot.options(),
                new PracticePayload(fields),snapshot.analysis(),snapshot.sourceRefs());
    }
    public PracticeSessionQuestion.Snapshot map(Question question) {
        if (!question.stimulusRefs().isEmpty())
            throw new UnsupportedOperationException("The current practice snapshot does not support shared stimuli");
        if (QuestionTypes.isEssay(question.type())) {
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
