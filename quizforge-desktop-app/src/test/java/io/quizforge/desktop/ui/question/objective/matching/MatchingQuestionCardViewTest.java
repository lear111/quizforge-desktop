package io.quizforge.desktop.ui.question.objective.matching;

import io.quizforge.core.port.QuestionResourceInput;
import io.quizforge.core.practice.*;
import io.quizforge.core.question.content.TextContent;
import io.quizforge.core.question.model.QuestionBank;
import io.quizforge.core.question.service.QuestionBankEditorModel;
import io.quizforge.core.question.type.objective.matching.*;
import io.quizforge.desktop.testing.FxTestRuntime;
import io.quizforge.desktop.ui.question.practice.MixedQuestionPracticeView;
import io.quizforge.desktop.ui.question.shared.QuestionEditorContext;
import io.quizforge.desktop.ui.shared.UiTheme;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class MatchingQuestionCardViewTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path temp;
    @BeforeAll static void start() throws Exception { FxTestRuntime.start(); }
    private static void fx(Runnable action) throws Exception {
        var result=new CompletableFuture<Void>();Platform.runLater(()->{try{action.run();result.complete(null);}catch(Throwable error){result.completeExceptionally(error);}});
        result.get(20,TimeUnit.SECONDS);
    }
    @Test void ordinaryPromptEightSlotsAndPositionLocksReplaceInlineAndOptionEditors() throws Exception {
        var model=new QuestionBankEditorModel(new QuestionBank("qb_matching_ui","Matching",List.of(),List.of(),List.of()));
        model.addQuestion("MATCHING");model.setStem(0,"Arrange A–G in the correct order. All eight paragraphs are written in this prompt.");
        model.setAnalysis(0,new TextContent("Matching explanation"));
        var body=new VBox();
        fx(()->{
            var navigation=new HBox();var spacer=new Region();navigation.getChildren().add(spacer);
            var context=new QuestionEditorContext(model,0,body,navigation,spacer,new HashMap<>(),new ArrayList<>(),new VBox(),QuestionResourceInput.NONE,()->null,()->{});
            MatchingEditorFields.render(context);
            assertEquals(8,body.lookupAll(".menu-button").size());
            assertNotNull(body.lookup("#matching-edit-prompt"));assertNull(body.lookup("#canvas-editor-webview"));
            assertNull(body.lookup("#matching-edit-option-A"));assertNull(body.lookup("#matching-add-blank"));assertNull(body.lookup("#matching-add-option"));
            ((Button)body.lookup("#matching-edit-matching-lock-1")).fire();
            ((MenuButton)body.lookup("#matching-edit-matching-blank-1")).getItems().get(7).fire();
            assertEquals("1. H",((MenuButton)body.lookup("#matching-edit-matching-blank-1")).getText());
            assertEquals("8. A",((MenuButton)body.lookup("#matching-edit-matching-blank-8")).getText());
            ((Button)body.lookup("#matching-edit-matching-lock-1")).fire();
            assertTrue(((MenuButton)body.lookup("#matching-edit-matching-blank-1")).isDisabled());
            assertTrue(((MenuButton)body.lookup("#matching-edit-matching-blank-2")).getItems().get(7).isDisable());
            ((Button)body.lookup("#matching-edit-matching-lock-1")).fire();
            assertFalse(((MenuButton)body.lookup("#matching-edit-matching-blank-1")).isDisabled());
            ((Button)body.lookup("#matching-edit-matching-lock-1")).fire();
            assertEquals("已给出：3 / 3 · 待答：5",((Label)body.lookup("#matching-edit-hint-status")).getText());
        });
        var bank=model.bank();var payload=(MatchingPayload)bank.questions().getFirst().payload();
        var db=new io.quizforge.infrastructure.persistence.SqliteDatabase(new io.quizforge.infrastructure.filesystem.QuizForgeDataDirectory(temp));
        var service=new PracticeSessionService(new io.quizforge.infrastructure.persistence.practice.SqlitePracticeTransaction(db),java.time.Clock.systemUTC());
        var revision=new io.quizforge.infrastructure.filesystem.qbank.QuestionBankV2Codec().contentId(bank);
        var runtime=new PersistentPracticeRuntime(service,bank,revision);var stage=new AtomicReference<Stage>();
        try {
            fx(()->{
                var view=new MixedQuestionPracticeView(bank,QuestionResourceInput.NONE,()->runtime,refs->null);
                stage.set(new Stage());stage.get().setOpacity(0);stage.get().setScene(new Scene(view,1100,650));UiTheme.apply(stage.get().getScene());stage.get().show();
                assertTrue(MixedQuestionPracticeView.supportsEditing(bank));assertEquals(5,view.outline().lookupAll(".question-number-cell").size());
                assertTrue(view.outline().lookupAll(".question-number-cell.hint").isEmpty());
                assertTrue(runtime.session().matchingAnswers().isEmpty());assertNull(view.lookup("#canvas-editor-webview"));
                assertNull(view.lookup("#practice-matching-option-A"));
                var hint=(MenuButton)view.lookup("#practice-matching-blank-1");assertEquals("1. H",hint.getText());assertTrue(hint.isDisabled());
                assertTrue(view.lookupAll(".matching-answer-slot .icon-lock").isEmpty());
                assertTrue(view.lookupAll(".matching-answer-slot .icon-unlock").isEmpty());
                var second=(MenuButton)view.lookup("#practice-matching-blank-2");assertTrue(second.getItems().get(7).isDisable());assertTrue(second.getItems().get(3).isDisable());assertTrue(second.getItems().get(5).isDisable());
                second.getItems().get(1).fire();
                var third=(MenuButton)view.lookup("#practice-matching-blank-3");
                assertFalse(third.getItems().get(1).isDisable());
                third.getItems().get(1).fire();
                assertEquals("3. B",((MenuButton)view.lookup("#practice-matching-blank-3")).getText());
                assertNull(view.lookup("#practice-matching-clear-3"));
                assertTrue(view.lookupAll(".matching-answer-slot .icon-close").isEmpty());
                ((MenuButton)view.lookup("#practice-matching-blank-3")).getItems().get(2).fire();
                assertEquals("3. C",((MenuButton)view.lookup("#practice-matching-blank-3")).getText());
                ((MenuButton)view.lookup("#practice-matching-blank-3")).getItems().get(1).fire();
                assertEquals(2,runtime.session().matchingAnswers().size());
                var submit=(Button)view.lookup("#practice-matching-submit");var header=new AtomicReference<String>();
                FxTestRuntime.answerSubmission("继续作答",pane->header.set(pane.getHeaderText()));submit.fire();
                assertTrue(header.get().contains("3 道小题"));assertFalse(((MenuButton)view.lookup("#practice-matching-blank-2")).isDisabled());
                FxTestRuntime.acceptSubmission(submit);
                assertEquals("得分：2 / 10",((Label)view.lookup("#practice-matching-result")).getText());
                assertTrue(((MenuButton)view.lookup("#practice-matching-blank-2")).isDisabled());
                assertTrue(view.outline().lookupAll(".question-number-cell.hint").isEmpty());
                assertTrue(view.outline().lookup("#authoring-question-1").getStyleClass().contains("correct"));
                assertTrue(view.outline().lookup("#authoring-question-2").getStyleClass().contains("incorrect"));
                ((Button)view.lookup("#practice-matching-retry")).fire();assertTrue(runtime.session().matchingAnswers().isEmpty());
                assertEquals("1. H",((MenuButton)view.lookup("#practice-matching-blank-1")).getText());
                ((MenuButton)view.lookup("#practice-matching-blank-2")).getItems().get(1).fire();
                ((MenuButton)view.lookup("#practice-matching-blank-2")).getItems().get(0).fire();
                assertEquals("2. A",((MenuButton)view.lookup("#practice-matching-blank-2")).getText());
                assertFalse(((MenuButton)view.lookup("#practice-matching-blank-2")).isDisabled());
            });
        }finally{fx(()->{if(stage.get()!=null)stage.get().close();});}
    }
    @Test void outlineSkipsHintPositionsAndRebuildsWhenTheirLocationsChange() throws Exception {
        fx(()->{
            var model=new QuestionBankEditorModel(new QuestionBank("qb_outline_matching","Outline",List.of(),List.of(),List.of()));
            model.addQuestion("ESSAY");model.addQuestion("MATCHING");model.addQuestion("ESSAY");
            var outline=new io.quizforge.desktop.ui.question.shared.QuestionOutlineView(new QuestionBankPracticeSession(model.bank()),index->{});
            var scene=new Scene(outline,260,650);UiTheme.apply(scene);outline.applyCss();outline.layout();
            var targets=new ArrayList<List<Integer>>();
            outline.setItemJump((index,position)->targets.add(List.of(index,position)));
            assertEquals(7,outline.lookupAll(".question-number-cell").size());
            for(int number=1;number<=7;number++)assertEquals(Integer.toString(number),((Button)outline.lookup("#question-number-"+number)).getText());
            for(int number=2;number<=6;number++)((Button)outline.lookup("#question-number-"+number)).fire();
            assertEquals(List.of(List.of(1,2),List.of(1,3),List.of(1,5),List.of(1,7),List.of(1,8)),targets);
            assertTrue(outline.lookupAll(".question-number-cell.hint").isEmpty());
            var payload=(MatchingPayload)model.bank().questions().get(1).payload();
            model.setMatchingLocked(1,payload.blanks().get(0).id(),false);
            model.setMatchingLocked(1,payload.blanks().get(1).id(),true);
            outline.showEditor(model.bank(),1,index->{});
            targets.clear();((Button)outline.lookup("#question-number-2")).fire();
            assertEquals(List.of(List.of(1,1)),targets);
            assertEquals(7,outline.lookupAll(".question-number-cell").size());
        });
    }

}
