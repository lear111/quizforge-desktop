package io.quizforge.core.practice;
import java.util.*;
import java.time.Instant;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PracticeSummaryTest {
 private ActivePracticeSnapshot.Question row(String id,double maximum,Double score,QuestionAttempt.Result result,PracticeSessionQuestion.State state){
  var snapshot=new PracticeSessionQuestion.Snapshot("test.question","Question",new PracticePayload(List.of()),new PracticePayload(Map.of("maxScore",maximum)),"",new PracticePayload(List.of()));
  var question=new PracticeSessionQuestion(id,"session",id,0,snapshot,state,null,Instant.EPOCH,Instant.EPOCH);
  var attempts=result==null?List.<QuestionAttempt>of():List.of(new QuestionAttempt("a_"+id,id,1,QuestionAttempt.Mode.INITIAL,new PracticePayload(Map.of()),result,score,maximum,Instant.EPOCH));
  return new ActivePracticeSnapshot.Question(question,attempts);
 }
 @Test void scoringKeepsPartialAndUnscoredAnswersSeparate(){
  var result=PracticeSummary.fromQuestions(List.of(row("one",3.5,2.5,QuestionAttempt.Result.INCORRECT,PracticeSessionQuestion.State.SUBMITTED),row("two",5,null,QuestionAttempt.Result.UNSCORED,PracticeSessionQuestion.State.SUBMITTED),row("three",2,2.0,QuestionAttempt.Result.CORRECT,PracticeSessionQuestion.State.SUBMITTED)));
  assertEquals(new BigDecimal("4.5"),result.score().orElseThrow());assertEquals(new BigDecimal("10.5"),result.maxScore().orElseThrow());assertEquals(1,result.correctCount());assertEquals(1,result.incorrectCount());assertEquals(1,result.unscoredCount());
 }
 @Test void retryDoesNotCountPreviousScore(){
  var result=PracticeSummary.fromQuestions(List.of(row("one",3,3.0,QuestionAttempt.Result.CORRECT,PracticeSessionQuestion.State.RETRYING)));
  assertEquals(BigDecimal.ZERO,result.score().orElseThrow());assertEquals(1,result.unfinishedCount());
 }
}
