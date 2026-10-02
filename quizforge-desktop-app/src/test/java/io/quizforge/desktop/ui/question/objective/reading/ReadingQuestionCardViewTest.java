package io.quizforge.desktop.ui.question.objective.reading;

import io.quizforge.core.port.QuestionResourceInput;
import io.quizforge.core.practice.*;
import io.quizforge.core.question.content.TextContent;
import io.quizforge.core.question.model.QuestionBank;
import io.quizforge.core.question.service.QuestionBankEditorModel;
import io.quizforge.core.question.type.objective.reading.*;
import io.quizforge.desktop.testing.FxTestRuntime;
import io.quizforge.desktop.ui.question.practice.MixedQuestionPracticeView;
import io.quizforge.desktop.ui.question.shared.QuestionEditorContext;
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

class ReadingQuestionCardViewTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path temp;
    @BeforeAll static void start() throws Exception { FxTestRuntime.start(); }
    private static void fx(Runnable action) throws Exception {
        var result=new CompletableFuture<Void>();Platform.runLater(()->{try{action.run();result.complete(null);}catch(Throwable error){result.completeExceptionally(error);}});
        result.get(25,TimeUnit.SECONDS);
    }

    @Test void optionsReflowFromFourColumnsToTwoToOneWithoutLosingSelection() throws Exception {
        fx(()->{
            var grid=ReadingQuestionCardView.optionGrid();var group=new ToggleGroup();
            var options=new ArrayList<RadioButton>();
            for(int i=0;i<4;i++){
                var radio=new RadioButton((char)('A'+i)+". test");radio.setToggleGroup(group);radio.setWrapText(true);
                radio.setMinWidth(0);radio.setMaxWidth(Double.MAX_VALUE);radio.getStyleClass().add("reading-answer-option");grid.add(radio,i,0);options.add(radio);
            }
            var root=new StackPane(grid);var scene=new Scene(root,720,400);io.quizforge.desktop.ui.shared.UiTheme.apply(scene);root.applyCss();root.layout();
            options.get(2).setSelected(true);assertEquals(4,grid.getColumnConstraints().size());
            for(int i=0;i<4;i++)options.get(i).setText((char)('A'+i)+". Adapted to the environment.");
            root.layout();assertEquals(2,grid.getColumnConstraints().size());
            assertEquals(options.get(0).getLayoutY(),options.get(1).getLayoutY());assertTrue(options.get(2).getLayoutY()>options.get(0).getLayoutY());
            options.get(1).setText("B. Genetic analysis offers insight into the history of donkeys.");
            root.layout();assertEquals(1,grid.getColumnConstraints().size(),"One wrapping option must switch the entire group to four rows");
            for(int i=1;i<4;i++)assertTrue(options.get(i).getLayoutY()>options.get(i-1).getLayoutY());
            options.get(1).setText("B. Adapted to the environment.");
            root.resize(340,500);root.layout();assertEquals(1,grid.getColumnConstraints().size());
            for(int i=1;i<4;i++)assertTrue(options.get(i).getLayoutY()>options.get(i-1).getLayoutY());
            root.resize(1200,500);root.layout();assertEquals(4,grid.getColumnConstraints().size());
            assertTrue(options.get(2).isSelected());assertSame(options.get(2),group.getSelectedToggle());
        });
    }

    @Test void editorAndMixedPracticeKeepChildSelectionsConfirmPartialSubmissionAndFocusOutline() throws Exception {
        var model=new QuestionBankEditorModel(new QuestionBank("qb_reading_ui","Reading",List.of(),List.of(),List.of()));model.addQuestion("READING");
        model.setStem(0,"Reading passage line.\n".repeat(65));model.setAnalysis(0,new TextContent("Shared reading explanation"));
        fx(()->{
            var body=new VBox();var navigation=new HBox();var spacer=new Region();navigation.getChildren().add(spacer);
            var c=new QuestionEditorContext(model,0,body,navigation,spacer,new HashMap<>(),new ArrayList<>(),new VBox(),QuestionResourceInput.NONE,()->null,()->{});
            ReadingEditorFields.render(c);
            assertEquals(20,body.lookupAll(".radio-button").size());assertNotNull(body.lookup("#reading-add-item"));
            ((TextField)body.lookup("#reading-option-1-1")).setText("A longer reading option");
            ((RadioButton)body.lookup("#reading-correct-1-1")).fire();
            var first=((ReadingPayload)model.bank().questions().getFirst().payload()).items().getFirst();
            assertEquals(first.options().get(1).id(),((ReadingAnswerSpec)model.bank().questions().getFirst().answerSpec()).answers().getFirst().correctOptionId());
        });
        var bank=model.bank();
        var db=new io.quizforge.infrastructure.persistence.SqliteDatabase(new io.quizforge.infrastructure.filesystem.QuizForgeDataDirectory(temp));
        var service=new PracticeSessionService(new io.quizforge.infrastructure.persistence.practice.SqlitePracticeTransaction(db),java.time.Clock.systemUTC());
        var revision=new io.quizforge.infrastructure.filesystem.qbank.QuestionBankV2Codec().contentId(bank);
        var runtime=new PersistentPracticeRuntime(service,bank,revision);
        var view=new AtomicReference<MixedQuestionPracticeView>();var stage=new AtomicReference<Stage>();
        try {
            fx(()->{
                view.set(new MixedQuestionPracticeView(bank,QuestionResourceInput.NONE,()->runtime,refs->null));
                stage.set(new Stage());stage.get().setOpacity(0);stage.get().setScene(new Scene(view.get(),1000,650));stage.get().show();
                assertTrue(MixedQuestionPracticeView.requiresMixedView(bank));assertTrue(MixedQuestionPracticeView.supportsEditing(bank));
                assertEquals(5,view.get().outline().lookupAll(".question-number-cell").size());
                ((Button)view.get().outline().lookup("#authoring-question-5")).fire();
            });
            fx(()->{
                var card=(ReadingQuestionCardView)view.get().lookup("#practice-reading-card");
                javafx.scene.Node parent=card.getParent();while(parent!=null && !(parent instanceof ScrollPane))parent=parent.getParent();
                assertInstanceOf(ScrollPane.class,parent);var scroll=(ScrollPane)parent;
                assertTrue(scroll.getVvalue()>0,"Outline must scroll to the chosen reading item");
                var chosen=(RadioButton)view.get().lookup("#practice-reading-option-1-1");chosen.fire();
                ((RadioButton)view.get().lookup("#practice-reading-option-2-3")).fire();
                assertEquals(2,runtime.session().selected().size());
                assertTrue(chosen.isSelected());assertEquals(2,runtime.questionState(bank.questions().getFirst().id()).sessionQuestion().draftAnswer().value() instanceof List<?> ids?ids.size():-1);
                var submit=(Button)view.get().lookup("#practice-reading-submit");var header=new AtomicReference<String>();
                FxTestRuntime.answerSubmission("继续作答",pane->header.set(pane.getHeaderText()));submit.fire();
                assertTrue(header.get().contains("3 道小题"));assertFalse(chosen.isDisabled());
                FxTestRuntime.acceptSubmission(submit);
                assertTrue(chosen.isDisabled());assertEquals("得分：2 / 10",((Label)view.get().lookup("#practice-reading-result")).getText());
                assertTrue(chosen.getStyleClass().contains("reading-correct"));
                assertTrue(view.get().lookup("#practice-reading-option-2-3").getStyleClass().contains("reading-incorrect"));
                assertTrue(view.get().outline().lookup("#authoring-question-1").getStyleClass().contains("correct"));
                assertTrue(view.get().outline().lookup("#authoring-question-2").getStyleClass().contains("incorrect"));
                ((Button)view.get().lookup("#practice-reading-retry")).fire();assertTrue(runtime.session().selected().isEmpty());assertFalse(chosen.isDisabled());
            });
        } finally { fx(()->{if(stage.get()!=null)stage.get().close();}); }
    }
}
