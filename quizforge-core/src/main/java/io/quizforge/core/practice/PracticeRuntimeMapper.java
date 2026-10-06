package io.quizforge.core.practice;

import io.quizforge.core.question.type.QuestionTypes;
import io.quizforge.core.question.type.QuestionTarget;
import io.quizforge.core.question.type.extension.ExternalQuestionTypeDefinition;
import java.util.*;

/** Hydrates shared navigation state; question-specific answer decoding belongs to extensions. */
public final class PracticeRuntimeMapper {
    public void hydrate(QuestionBankPracticeSession runtime, ActivePracticeSnapshot snapshot) {
        if (!runtime.bank().assetId().equals(snapshot.session().questionBankAssetId())
                || snapshot.questions().size() != runtime.bank().questions().size())
            throw new IllegalStateException("Practice snapshot does not match the bank");
        Map<Integer,Boolean> submitted=new HashMap<>();
        Map<Integer,QuestionBankPracticeSession.State> states=new HashMap<>();
        Map<String,List<QuestionTarget>> targets=new HashMap<>();
        var mapper=new PracticeQuestionSnapshotMapper();
        int current=-1;
        for(int index=0;index<snapshot.questions().size();index++) {
            var row=snapshot.questions().get(index);var stored=row.sessionQuestion();var q=runtime.bank().questions().get(index);
            var fields=ExternalQuestionTypeDefinition.object(stored.snapshot().correctAnswer().value());
            var frozen=fields.containsKey("extension") ? ExternalQuestionTypeDefinition.object(fields.get("extension")) : Map.<String,Object>of();
            var expected=QuestionTypes.isExtension(q.type()) && frozen.get("version") instanceof String version
                    ? mapper.map(q,version) : mapper.map(q);
            if(!stored.questionId().equals(q.id()) || !PracticeQuestionSnapshotMapper.logical(stored.snapshot()).equals(PracticeQuestionSnapshotMapper.logical(expected)))
                throw new IllegalStateException("Practice snapshot has a different revision");
            if(stored.questionId().equals(snapshot.session().currentQuestionId()))current=index;
            var presentation=ExternalQuestionTypeDefinition.object(fields.get("extensionPresentation"));
            targets.put(q.id(),ExternalQuestionTypeDefinition.targetsFrom(presentation).stream().map(t->
                    new QuestionTarget((String)t.get("id"),((Number)t.get("number")).intValue(),
                            (Boolean)t.get("gradable"),(Boolean)t.get("locked"),(String)t.get("label"))).toList());
            if((stored.practiceState()==PracticeSessionQuestion.State.SUBMITTED || stored.practiceState()==PracticeSessionQuestion.State.RETRYING) && row.attempts().isEmpty())
                throw new IllegalStateException("Practice attempt history is missing");
            states.put(index,stored.practiceState()==PracticeSessionQuestion.State.SUBMITTED ? QuestionBankPracticeSession.State.SUBMITTED
                    : stored.draftAnswer()==null ? QuestionBankPracticeSession.State.UNANSWERED : QuestionBankPracticeSession.State.SELECTED);
            if(stored.practiceState()==PracticeSessionQuestion.State.SUBMITTED && row.attempts().getLast().result()!=QuestionAttempt.Result.UNSCORED)
                submitted.put(index,row.attempts().getLast().result()==QuestionAttempt.Result.CORRECT);
        }
        if(current<0 && snapshot.session().currentView()==PracticeSession.View.SUMMARY)current=runtime.bank().questions().size()-1;
        if(current<0)throw new IllegalStateException("Current question is missing");
        runtime.restoreTargets(targets);
        runtime.restoreState(current,submitted,snapshot.session().currentView()==PracticeSession.View.SUMMARY,states);
    }
}
