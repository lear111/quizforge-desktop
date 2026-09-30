package io.quizforge.desktop.ui;

import static org.junit.jupiter.api.Assertions.*;
import io.quizforge.core.port.QuestionResourceInput;
import io.quizforge.core.question.*;
import java.util.List;
import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
        var service=new io.quizforge.core.practice.PracticeSessionService(new io.quizforge.infrastructure.persistence.SqlitePracticeTransaction(db),java.time.Clock.systemUTC());
        var essay=new Question("q_essay","ESSAY",List.of(),new TextContent("Essay prompt"),new EssayPayload(null),
                new EssayAnswerSpec(new TextContent("Reference")),ScoreSpec.defaultScore(),null,new TextContent("Analysis"),List.of());
        var choice=Question.choice("q_choice","SINGLE_CHOICE",new TextContent("Choice prompt"),null,List.of(),
                new ChoicePayload(List.of(new ChoiceOption("opt_a",new TextContent("A")),new ChoiceOption("opt_b",new TextContent("B")))),new ChoiceAnswerSpec(List.of("opt_a")));
        var bank=new QuestionBank("qb_mixed","Mixed",List.of(),List.of(essay,choice),List.of());
        String revision=new io.quizforge.infrastructure.filesystem.QuestionBankV2Codec().contentId(bank);
        var practice=new io.quizforge.core.practice.PersistentPracticeRuntime(service,bank,revision);
        practice.saveEssayDraft(essay.id(),new io.quizforge.core.practice.EssayPracticeAnswer("Restored essay draft",null));
        var result=new CompletableFuture<Void>();
        Platform.runLater(()->{
            try{
                var view=new QuestionBankAuthoringView(bank,QuestionResourceInput.NONE,()->practice,refs->null);
                new Scene(view,1000,700);view.applyCss();view.layout();
                assertTrue(view.lookupAll(".authoring-essay-text").stream().filter(javafx.scene.control.Label.class::isInstance)
                        .map(javafx.scene.control.Label.class::cast).anyMatch(label->label.getText().equals("Restored essay draft")));
                ((Button)view.lookup("#essay-submit-answer")).fire();
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
                var reopened=new QuestionBankAuthoringView(bank,QuestionResourceInput.NONE,()->restored,refs->null);
                new Scene(reopened,1000,700);reopened.applyCss();reopened.layout();
                assertTrue(((javafx.scene.control.RadioButton)reopened.lookup("#option-0")).isSelected());
                ((Button)reopened.lookup("#previous-question")).fire();reopened.applyCss();reopened.layout();
                assertTrue(((Button)reopened.lookup("#essay-submit-answer")).isDisabled());
                ((Button)reopened.lookup("#authoring-next-question")).fire();reopened.applyCss();reopened.layout();
                ((Button)reopened.lookup("#next-question")).fire();reopened.applyCss();reopened.layout();
                assertNotNull(reopened.lookup("#summary-unscored-count"));
                String archived=restored.sessionId();restored.restart();
                var detail=new io.quizforge.core.practice.PracticeHistoryService(new io.quizforge.infrastructure.persistence.SqlitePracticeTransaction(db))
                        .loadArchivedSessionDetail(bank.assetId(),archived);
                var history=new PracticeHistoryDetailView(detail,()->{},null,null);new Scene(history,1000,700);history.applyCss();history.layout();
                assertTrue(history.lookupAll(".question-stem").stream().filter(javafx.scene.control.Label.class::isInstance)
                        .map(javafx.scene.control.Label.class::cast).anyMatch(label->label.getText().equals("Restored essay draft")));
                result.complete(null);
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
                    var view=new QuestionBankAuthoringView(bank,QuestionResourceInput.NONE);
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
