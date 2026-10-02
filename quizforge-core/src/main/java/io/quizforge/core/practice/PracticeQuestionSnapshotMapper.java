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
        if (QuestionTypes.isTranslation(question.type())) {
            var fields = new LinkedHashMap<String,Object>();
            QuestionContentData.map(snapshot.correctAnswer().value()).forEach((key,value) -> fields.put((String) key, value));
            fields.put("translationPresentation", TranslationQuestionSnapshot.capture(question, resources, input).payload().value());
            return new PracticeSessionQuestion.Snapshot(snapshot.questionType(), snapshot.stem(), snapshot.options(), new PracticePayload(fields), snapshot.analysis(), snapshot.sourceRefs());
        }
        if (QuestionTypes.isMatching(question.type())) {
            var fields = new LinkedHashMap<String,Object>();
            QuestionContentData.map(snapshot.correctAnswer().value()).forEach((key,value) -> fields.put((String) key, value));
            fields.put("matchingPresentation", MatchingQuestionSnapshot.capture(question, resources, input).payload().value());
            return new PracticeSessionQuestion.Snapshot(snapshot.questionType(), snapshot.stem(), snapshot.options(), new PracticePayload(fields), snapshot.analysis(), snapshot.sourceRefs());
        }
        if (QuestionTypes.isReading(question.type())) {
            var fields = new LinkedHashMap<String,Object>();
            QuestionContentData.map(snapshot.correctAnswer().value()).forEach((key,value) -> fields.put((String) key, value));
            fields.put("readingPresentation", ReadingQuestionSnapshot.capture(question, resources, input).payload().value());
            return new PracticeSessionQuestion.Snapshot(snapshot.questionType(), snapshot.stem(), snapshot.options(), new PracticePayload(fields), snapshot.analysis(), snapshot.sourceRefs());
        }
        if(QuestionTypes.isCloze(question.type())){
            var fields=new LinkedHashMap<String,Object>();
            QuestionContentData.map(snapshot.correctAnswer().value()).forEach((key,value)->fields.put((String)key,value));
            fields.put("clozePresentation",ClozeQuestionSnapshot.capture(question,resources,input).payload().value());
            return new PracticeSessionQuestion.Snapshot(snapshot.questionType(),snapshot.stem(),snapshot.options(),new PracticePayload(fields),snapshot.analysis(),snapshot.sourceRefs());
        }
        if(!QuestionTypes.isEssay(question.type()))return snapshot;
        var fields=new LinkedHashMap<String,Object>();
        QuestionContentData.map(snapshot.correctAnswer().value()).forEach((key,value)->fields.put((String)key,value));
        fields.put("essayPresentation",EssayQuestionSnapshot.capture(question,resources,input).payload().value());
        return new PracticeSessionQuestion.Snapshot(snapshot.questionType(),snapshot.stem(),snapshot.options(),
                new PracticePayload(fields),snapshot.analysis(),snapshot.sourceRefs());
    }

    public static PracticeSessionQuestion.Snapshot logical(PracticeSessionQuestion.Snapshot snapshot){
        var fields=new LinkedHashMap<String,Object>();
        // Score metadata added to an existing snapshot must not clear its answers.
        QuestionContentData.map(snapshot.correctAnswer().value()).forEach((key,value)->{if(!"maxScore".equals(key) && !"essayPresentation".equals(key) && !"clozePresentation".equals(key) && !"readingPresentation".equals(key) && !"matchingPresentation".equals(key) && !"translationPresentation".equals(key))fields.put((String)key,value);});
        return new PracticeSessionQuestion.Snapshot(snapshot.questionType(),snapshot.stem(),snapshot.options(),
                new PracticePayload(fields),snapshot.analysis(),snapshot.sourceRefs());
    }
    public PracticeSessionQuestion.Snapshot map(Question question) {
        var snapshot=mapContent(question);
        var fields=new LinkedHashMap<String,Object>();
        QuestionContentData.map(snapshot.correctAnswer().value()).forEach((key,value)->fields.put((String)key,value));
        int count=1;
        if(QuestionTypes.isCloze(question.type()))count=((io.quizforge.core.question.type.objective.cloze.ClozePayload)question.payload()).blanks().size();
        else if(QuestionTypes.isReading(question.type()))count=((io.quizforge.core.question.type.objective.reading.ReadingPayload)question.payload()).items().size();
        else if(QuestionTypes.isMatching(question.type()))count=io.quizforge.core.question.type.objective.matching.MatchingQuestionType.gradableCount(question);
        else if(QuestionTypes.isTranslation(question.type()))count=((io.quizforge.core.question.type.subjective.translation.TranslationPayload)question.payload()).items().size();
        fields.put("maxScore",question.scoreSpec().defaultMaxScore().multiply(java.math.BigDecimal.valueOf(count)));
        return new PracticeSessionQuestion.Snapshot(snapshot.questionType(),snapshot.stem(),snapshot.options(),new PracticePayload(fields),snapshot.analysis(),snapshot.sourceRefs());
    }
    private PracticeSessionQuestion.Snapshot mapContent(Question question) {
        if (!question.stimulusRefs().isEmpty())
            throw new UnsupportedOperationException("The current practice snapshot does not support shared stimuli");
        if (QuestionTypes.isTranslation(question.type())) {
            return new PracticeSessionQuestion.Snapshot(question.type(), QuestionContentData.plainText(question.prompt()),
                    new PracticePayload(java.util.List.of()), new PracticePayload(Map.of("correctOptionIds", java.util.List.of(),
                    "translation", TranslationQuestionSnapshot.logical(question))), QuestionContentData.plainText(question.analysis()),
                    new PracticePayload(question.sourceRefs().stream().map(this::sourceRef).toList()));
        }
        if (QuestionTypes.isMatching(question.type())) {
            var options = ((io.quizforge.core.question.type.objective.matching.MatchingPayload) question.payload()).options().stream()
                    .map(option -> Map.of("id", option.id(), "label", option.label(), "content", option.label())).toList();
            return new PracticeSessionQuestion.Snapshot(question.type(), QuestionContentData.plainText(question.prompt()), new PracticePayload(options),
                    new PracticePayload(Map.of("correctOptionIds", ((io.quizforge.core.question.type.objective.matching.MatchingAnswerSpec) question.answerSpec()).assignments().values().stream().sorted().toList(),
                            "matching", MatchingQuestionSnapshot.logical(question))), QuestionContentData.plainText(question.analysis()),
                    new PracticePayload(question.sourceRefs().stream().map(this::sourceRef).toList()));
        }
        if (QuestionTypes.isReading(question.type())) {
            var items = ((io.quizforge.core.question.type.objective.reading.ReadingPayload) question.payload()).items();
            var options = items.stream().flatMap(item -> item.options().stream().map(option -> Map.of("id", option.id(),
                    "content", QuestionContentData.plainText(option.content()), "itemId", item.id()))).toList();
            return new PracticeSessionQuestion.Snapshot(question.type(), QuestionContentData.plainText(question.prompt()), new PracticePayload(options),
                    new PracticePayload(Map.of("correctOptionIds", ((io.quizforge.core.question.type.objective.reading.ReadingAnswerSpec) question.answerSpec()).correctOptionIds().stream().sorted().toList(),
                            "reading", ReadingQuestionSnapshot.logical(question))), QuestionContentData.plainText(question.analysis()),
                    new PracticePayload(question.sourceRefs().stream().map(this::sourceRef).toList()));
        }
        if(QuestionTypes.isCloze(question.type())){
            var blanks=((io.quizforge.core.question.type.objective.cloze.ClozePayload)question.payload()).blanks();
            var options=blanks.stream().flatMap(b->b.options().stream().map(o->Map.of("id",o.id(),"content",QuestionContentData.plainText(o.content()),"blankId",b.id()))).toList();
            return new PracticeSessionQuestion.Snapshot(question.type(),QuestionContentData.plainText(question.prompt()),new PracticePayload(options),
                    new PracticePayload(Map.of("correctOptionIds",((io.quizforge.core.question.type.objective.cloze.ClozeAnswerSpec)question.answerSpec()).correctOptionIds().stream().sorted().toList(),
                            "cloze",ClozeQuestionSnapshot.logical(question))),QuestionContentData.plainText(question.analysis()),
                    new PracticePayload(question.sourceRefs().stream().map(this::sourceRef).toList()));
        }
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
