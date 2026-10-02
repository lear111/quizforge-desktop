package io.quizforge.core.practice;

import io.quizforge.core.question.content.*;
import io.quizforge.core.question.model.*;
import io.quizforge.core.question.type.QuestionTypes;
import io.quizforge.core.question.type.objective.cloze.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PracticeSummaryTest {
    private final AtomicInteger ids=new AtomicInteger();
    private Question draft(String type,String points){
        var q=QuestionTypes.require(type).createDraft(prefix->prefix+ids.incrementAndGet(),List.of());
        return new Question(q.id(),q.type(),q.stimulusRefs(),q.prompt(),q.payload(),q.answerSpec(),new ScoreSpec(new BigDecimal(points)),q.evaluationSpec(),q.analysis(),q.sourceRefs());
    }
    private ActivePracticeSnapshot.Question row(Question q,PracticeSessionQuestion.State state,QuestionAttempt.Result result,Double score){
        var snapshot=new PracticeQuestionSnapshotMapper().map(q);
        var row=new PracticeSessionQuestion("sq_"+q.id(),"session",q.id(),0,snapshot,state,null,Instant.EPOCH,Instant.EPOCH);
        var maximum=((Number)((Map<?,?>)snapshot.correctAnswer().value()).get("maxScore")).doubleValue();
        var attempts=result==null?List.<QuestionAttempt>of():List.of(new QuestionAttempt("attempt",row.id(),1,QuestionAttempt.Mode.INITIAL,new PracticePayload(List.of()),result,score,maximum,Instant.EPOCH));
        return new ActivePracticeSnapshot.Question(row,attempts);
    }
    @Test void weightedPartialScoresIncludeTwentyClozeItemsAndExcludeLockedHintsAndPendingSubjectiveAnswers(){
        var cloze=draft("CLOZE","0.5");
        var blanks=new ArrayList<ClozeBlank>();var answers=new ArrayList<ClozeAnswerSpec.Answer>();
        for(int i=1;i<=20;i++){
            var blank=ClozeQuestionType.newBlank(i,prefix->prefix+ids.incrementAndGet());blanks.add(blank);
            answers.add(new ClozeAnswerSpec.Answer(blank.id(),blank.options().getFirst().id()));
        }
        cloze=new Question(cloze.id(),cloze.type(),List.of(),new TextContent("Passage"),new ClozePayload(blanks),new ClozeAnswerSpec(answers),cloze.scoreSpec(),null,null,List.of());
        var summary=PracticeSummary.fromQuestions(List.of(
                row(cloze,PracticeSessionQuestion.State.SUBMITTED,QuestionAttempt.Result.INCORRECT,8.5),
                row(draft("READING","2"),PracticeSessionQuestion.State.SUBMITTED,QuestionAttempt.Result.INCORRECT,6.0),
                row(draft("MATCHING","2"),PracticeSessionQuestion.State.SUBMITTED,QuestionAttempt.Result.INCORRECT,8.0),
                row(draft("TRANSLATION","2"),PracticeSessionQuestion.State.SUBMITTED,QuestionAttempt.Result.UNSCORED,null),
                row(draft("ESSAY","20.25"),PracticeSessionQuestion.State.SUBMITTED,QuestionAttempt.Result.UNSCORED,null),
                row(draft("SINGLE_CHOICE","2.5"),PracticeSessionQuestion.State.SUBMITTED,QuestionAttempt.Result.INCORRECT,0.0)));
        assertEquals(new BigDecimal("22.5"),summary.score().orElseThrow());
        assertEquals(new BigDecimal("62.75"),summary.maxScore().orElseThrow());
        assertEquals(0,summary.correctCount());assertEquals(4,summary.incorrectCount());assertEquals(2,summary.unscoredCount());
    }
    @Test void retryingAndDraftAnswersContributeNoPointsButKeepFullMaximum(){
        var summary=PracticeSummary.fromQuestions(List.of(
                row(draft("SINGLE_CHOICE","2.5"),PracticeSessionQuestion.State.RETRYING,QuestionAttempt.Result.CORRECT,2.5),
                row(draft("MULTIPLE_CHOICE","3.25"),PracticeSessionQuestion.State.DRAFT,null,null)));
        assertEquals(BigDecimal.ZERO,summary.score().orElseThrow());
        assertEquals(new BigDecimal("5.75"),summary.maxScore().orElseThrow());assertEquals(2,summary.unfinishedCount());
    }
    @Test void onlyLatestSubmittedAttemptContributesToTheCurrentRound(){
        var row=row(draft("SINGLE_CHOICE","2.5"),PracticeSessionQuestion.State.SUBMITTED,QuestionAttempt.Result.CORRECT,2.5);
        var next=new QuestionAttempt("retry",row.sessionQuestion().id(),2,QuestionAttempt.Mode.RETRY,new PracticePayload(List.of()),QuestionAttempt.Result.INCORRECT,0.0,2.5,Instant.EPOCH);
        var summary=PracticeSummary.fromQuestions(List.of(new ActivePracticeSnapshot.Question(row.sessionQuestion(),List.of(row.attempts().getFirst(),next))));
        assertEquals(BigDecimal.ZERO,summary.score().orElseThrow());assertEquals(1,summary.incorrectCount());
    }
    @Test void legacyMissingScoresRemainUnknownAndMetadataUpgradeKeepsLogicalQuestionIdentity(){
        var row=row(draft("SINGLE_CHOICE","7.5"),PracticeSessionQuestion.State.SUBMITTED,QuestionAttempt.Result.CORRECT,null);
        var original=row.sessionQuestion();var full=original.snapshot();
        var data=new LinkedHashMap<>((Map<String,Object>)full.correctAnswer().value());data.remove("maxScore");
        var legacy=new PracticeSessionQuestion.Snapshot(full.questionType(),full.stem(),full.options(),new PracticePayload(data),full.analysis(),full.sourceRefs());
        assertEquals(PracticeQuestionSnapshotMapper.logical(full),PracticeQuestionSnapshotMapper.logical(legacy));
        var oldRow=new PracticeSessionQuestion(original.id(),original.sessionId(),original.questionId(),0,legacy,original.practiceState(),null,Instant.EPOCH,Instant.EPOCH);
        var oldAttempt=new QuestionAttempt("old",oldRow.id(),1,QuestionAttempt.Mode.INITIAL,new PracticePayload(List.of()),QuestionAttempt.Result.CORRECT,null,null,Instant.EPOCH);
        var summary=PracticeSummary.fromQuestions(List.of(new ActivePracticeSnapshot.Question(oldRow,List.of(oldAttempt))));
        assertTrue(summary.score().isEmpty());assertTrue(summary.maxScore().isEmpty());
    }
}
