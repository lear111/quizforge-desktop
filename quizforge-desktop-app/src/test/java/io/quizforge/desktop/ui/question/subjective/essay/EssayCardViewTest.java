package io.quizforge.desktop.ui.question.subjective.essay;

import io.quizforge.core.port.QuestionResourceInput;
import io.quizforge.core.question.content.DocumentContent;
import io.quizforge.core.question.content.TextContent;
import io.quizforge.core.question.model.Question;
import io.quizforge.core.question.model.QuestionBank;
import io.quizforge.core.question.model.ScoreSpec;
import io.quizforge.core.question.resource.QBankResource;
import io.quizforge.core.question.resource.ResourceKind;
import io.quizforge.core.question.type.objective.choice.ChoiceAnswerSpec;
import io.quizforge.core.question.type.objective.choice.ChoiceOption;
import io.quizforge.core.question.type.objective.choice.ChoicePayload;
import io.quizforge.core.question.type.subjective.essay.EssayAnswerSpec;
import io.quizforge.core.question.type.subjective.essay.EssayPayload;
import io.quizforge.desktop.ui.question.history.PracticeHistoryDetailView;
import io.quizforge.desktop.ui.question.practice.MixedQuestionPracticeView;
import java.util.List;
import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EssayCardViewTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path temp;
    @BeforeAll static void startFx() throws Exception {
        var started=new CountDownLatch(1);
        try{Platform.startup(started::countDown);}catch(IllegalStateException alreadyStarted){started.countDown();}
        assertTrue(started.await(20,TimeUnit.SECONDS));
        Platform.setImplicitExit(false);
    }

    @Test void activeAnswersRestoreThroughMixedCardsSummaryAndArchivedEssay() throws Exception {
        var db=new io.quizforge.infrastructure.persistence.SqliteDatabase(new io.quizforge.infrastructure.filesystem.QuizForgeDataDirectory(temp));
        var service=new io.quizforge.core.practice.PracticeSessionService(new io.quizforge.infrastructure.persistence.practice.SqlitePracticeTransaction(db),java.time.Clock.systemUTC());
        var essay=new Question("q_essay","ESSAY",List.of(),new TextContent("Essay prompt"),new EssayPayload(null),
                new EssayAnswerSpec(new TextContent("Reference")),ScoreSpec.defaultScore(),null,new TextContent("Analysis"),List.of());
        var choice=Question.choice("q_choice","SINGLE_CHOICE",new TextContent("Choice prompt"),null,List.of(),
                new ChoicePayload(List.of(new ChoiceOption("opt_a",new TextContent("A")),new ChoiceOption("opt_b",new TextContent("B")))),new ChoiceAnswerSpec(List.of("opt_a")));
        var bank=new QuestionBank("qb_mixed","Mixed",List.of(),List.of(essay,choice),List.of());
        String revision=new io.quizforge.infrastructure.filesystem.qbank.QuestionBankV2Codec().contentId(bank);
        var practice=new io.quizforge.core.practice.PersistentPracticeRuntime(service,bank,revision);
        practice.saveEssayDraft(essay.id(),new io.quizforge.core.practice.EssayPracticeAnswer("Restored essay draft",null));
        var result=new CompletableFuture<Void>();
        Platform.runLater(()->{
            try{
                var view=new MixedQuestionPracticeView(bank,QuestionResourceInput.NONE,()->practice,refs->null);
                new Scene(view,1000,700);view.applyCss();view.layout();
                assertTrue(view.lookupAll(".authoring-essay-text").stream().filter(javafx.scene.control.Label.class::isInstance)
                        .map(javafx.scene.control.Label.class::cast).anyMatch(label->label.getText().equals("Restored essay draft")));
                io.quizforge.desktop.testing.FxTestRuntime.acceptSubmission((Button)view.lookup("#essay-submit-answer"));
                assertEquals(1,practice.summary().unscoredCount());
                assertFalse(view.lookup("#essay-edit-answer").isVisible());
                assertTrue(view.lookup("#essay-retry").isVisible());
                assertEquals(1,view.lookupAll(".essay-section-title").stream().filter(javafx.scene.control.Label.class::isInstance)
                        .map(javafx.scene.control.Label.class::cast).filter(label->label.getText().equals("参考答案与解析")).count());
                ((Button)view.lookup("#essay-retry")).fire();
                assertTrue(view.lookup("#essay-edit-answer").isVisible());assertFalse(view.lookup("#essay-retry").isVisible());
                assertEquals(io.quizforge.core.practice.PracticeSessionQuestion.State.RETRYING,practice.questionState(essay.id()).sessionQuestion().practiceState());
                assertEquals(1,practice.questionState(essay.id()).attempts().size());
                practice.saveEssayDraft(essay.id(),new io.quizforge.core.practice.EssayPracticeAnswer("Restored essay draft",null));
                practice.submit();
                ((Button)view.lookup("#authoring-next-question")).fire();view.applyCss();view.layout();
                ((javafx.scene.control.RadioButton)view.lookup("#option-0")).fire();
                assertTrue(practice.questionState(choice.id()).attempts().isEmpty());
                var restored=new io.quizforge.core.practice.PersistentPracticeRuntime(service,bank,revision);
                var reopened=new MixedQuestionPracticeView(bank,QuestionResourceInput.NONE,()->restored,refs->null);
                new Scene(reopened,1000,700);reopened.applyCss();reopened.layout();
                assertTrue(((javafx.scene.control.RadioButton)reopened.lookup("#option-0")).isSelected());
                ((Button)reopened.lookup("#previous-question")).fire();reopened.applyCss();reopened.layout();
                assertTrue(((Button)reopened.lookup("#essay-submit-answer")).isDisabled());
                ((Button)reopened.lookup("#authoring-next-question")).fire();reopened.applyCss();reopened.layout();
                ((Button)reopened.lookup("#next-question")).fire();reopened.applyCss();reopened.layout();
                assertNotNull(reopened.lookup("#summary-unscored-count"));
                String archived=restored.sessionId();restored.restart();
                var detail=new io.quizforge.core.practice.PracticeHistoryService(new io.quizforge.infrastructure.persistence.practice.SqlitePracticeTransaction(db))
                        .loadArchivedSessionDetail(bank.assetId(),archived);
                var history=new PracticeHistoryDetailView(detail,()->{},null,null);new Scene(history,1000,700);history.applyCss();history.layout();
                assertEquals("分值：1",((javafx.scene.control.Label)history.lookup("#history-essay-metadata")).getText());
                assertNotNull(history.lookup("#history-essay-answer-box"));assertNull(history.lookup("#essay-edit-answer"));
                assertTrue(history.lookupAll(".essay-section-title").stream().filter(javafx.scene.control.Label.class::isInstance)
                        .map(javafx.scene.control.Label.class::cast).anyMatch(label->label.getText().equals("参考答案与解析")));
                assertTrue(history.lookupAll(".question-stem").stream().filter(javafx.scene.control.Label.class::isInstance)
                        .map(javafx.scene.control.Label.class::cast).anyMatch(label->label.getText().equals("Restored essay draft")));
                result.complete(null);
            }catch(Throwable failure){result.completeExceptionally(failure);}
        });
        result.get(25,TimeUnit.SECONDS);
    }

    @Test void olderHistoryUsesRichPromptOnlyFromIdenticalBankRevision() throws Exception {
        byte[] bytes="{\"version\":\"1.0.4\",\"options\":{},\"data\":{\"main\":[{\"value\":\"Directions\",\"bold\":true}]}}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        var resource=new QBankResource("res_canvas_"+hash,ResourceKind.DOCUMENT,"application/vnd.quizforge.canvas+json","resources/prompt.canvas.json",hash);
        var essay=new Question("q_old_essay","ESSAY",List.of(),new DocumentContent(resource.id(),"Directions"),new EssayPayload(null),
                new EssayAnswerSpec(new TextContent("Reference")),new ScoreSpec(new java.math.BigDecimal("20")),null,new TextContent("Analysis"),List.of());
        var bank=new QuestionBank("qb_old_history","Old",List.of(),List.of(essay),List.of(resource));
        String revision=new io.quizforge.infrastructure.filesystem.qbank.QuestionBankV2Codec().contentId(bank);
        var snapshot=new io.quizforge.core.practice.PracticeQuestionSnapshotMapper().map(essay);
        var answer=new io.quizforge.core.practice.EssayPracticeAnswer("Submitted answer",null).payload();
        var now=java.time.Instant.now();
        var attempt=new io.quizforge.core.practice.PracticeHistoryDetail.Attempt(1,io.quizforge.core.practice.QuestionAttempt.Mode.INITIAL,
                answer,io.quizforge.core.practice.QuestionAttempt.Result.UNSCORED,null,null,now);
        var row=new io.quizforge.core.practice.PracticeHistoryDetail.Question("psq_old","q_old_essay",0,"ESSAY",snapshot.stem(),List.of(),List.of(),
                snapshot.analysis(),snapshot.sourceRefs(),io.quizforge.core.practice.PracticeSessionQuestion.State.SUBMITTED,null,List.of(attempt),snapshot.correctAnswer());
        var summary=new io.quizforge.core.practice.PracticeSummary(1,1,0,0,0,java.util.Optional.of(java.math.BigDecimal.ZERO),java.util.Optional.of(new java.math.BigDecimal("20.25")));
        var detail=new io.quizforge.core.practice.PracticeHistoryDetail("ps_old","Old",now,now,summary,List.of(row),revision);
        var result=new CompletableFuture<Void>();
        Platform.runLater(()->{
            try{
                var view=new PracticeHistoryDetailView(detail,()->{},null,null,bank,revision,r->new java.io.ByteArrayInputStream(bytes));
                new Scene(view,1000,700);view.applyCss();view.layout();
                assertNotNull(view.lookup("#history-prompt-native-document"));
                assertEquals("分值：20",((javafx.scene.control.Label)view.lookup("#history-essay-metadata")).getText());
                assertNotNull(view.lookup("#history-essay-answer-box"));
                assertTrue(view.lookupAll(".essay-section-title").stream().filter(javafx.scene.control.Label.class::isInstance)
                        .map(javafx.scene.control.Label.class::cast).anyMatch(label->label.getText().equals("参考答案与解析")));
                var changed=new PracticeHistoryDetailView(detail,()->{},null,null,bank,"qfb:v2:"+"f".repeat(64),r->{throw new AssertionError("Later resources must not be used");});
                new Scene(changed,1000,700);changed.applyCss();changed.layout();
                assertNull(changed.lookup("#history-prompt-native-document"));
                assertEquals("分值：未记录",((javafx.scene.control.Label)changed.lookup("#history-essay-metadata")).getText());
                view.getScene().setRoot(new javafx.scene.layout.VBox());result.complete(null);
            }catch(Throwable failure){result.completeExceptionally(failure);}
        });
        result.get(25,TimeUnit.SECONDS);
    }

    @Test void essayCardsInitializeForDifferentBanksUsingTheSameView() throws Exception {
        var result=new CompletableFuture<Void>();
        Platform.runLater(()->{
            try{
                for(String title:List.of("English Essay Acceptance","另一份作文题库")){
                    var question=new Question("q_essay","ESSAY",List.of(),new TextContent("Write an essay."),
                            new EssayPayload(null),new EssayAnswerSpec(null),ScoreSpec.defaultScore(),null,null,List.of());
                    var bank=new QuestionBank("qb_"+Integer.toUnsignedString(title.hashCode()),title,List.of(),List.of(question),List.of());
                    var view=new MixedQuestionPracticeView(bank,QuestionResourceInput.NONE);
                    new Scene(view,1000,700);view.applyCss();view.layout();
                    assertNotNull(view.lookup("#essay-answer-box"));
                    assertNotNull(view.lookup("#essay-edit-answer .icon-edit"));
                    assertTrue(((Button)view.lookup("#essay-submit-answer")).isDisabled());
                    assertFalse(((javafx.scene.control.Label)view.lookup("#authoring-essay-metadata")).getText().contains("字数"));
                }
                result.complete(null);
            }catch(Throwable failure){result.completeExceptionally(failure);}
        });
        result.get(25,TimeUnit.SECONDS);
    }
}
