package io.quizforge.desktop.ui.question.subjective.translation;

import io.quizforge.core.port.QuestionResourceInput;
import io.quizforge.core.practice.*;
import io.quizforge.core.question.content.*;
import io.quizforge.core.question.model.QuestionBank;
import io.quizforge.core.question.service.QuestionBankEditorModel;
import io.quizforge.core.question.type.subjective.translation.*;
import io.quizforge.desktop.testing.FxTestRuntime;
import io.quizforge.desktop.ui.content.document.canvas.ContentEditSession;
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
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class TranslationQuestionCardViewTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path temp;
    @BeforeAll static void start() throws Exception { FxTestRuntime.start(); }
    private static <T>T fx(Callable<T> action) throws Exception {
        var result=new CompletableFuture<T>();Platform.runLater(()->{try{result.complete(action.call());}catch(Throwable error){result.completeExceptionally(error);}});
        return result.get(20,TimeUnit.SECONDS);
    }
    private static void await(Callable<Boolean> condition) throws Exception {
        for(int i=0;i<120;i++){if(fx(condition))return;Thread.sleep(50);}fail("Translation Canvas preview did not become ready");
    }
    private static QuestionBankEditorModel model(){
        var model=new QuestionBankEditorModel(new QuestionBank("qb_translation_ui","Translation",List.of(),List.of(),List.of()));model.addQuestion("TRANSLATION");return model;
    }
    @Test void textDraftsConfirmPartialSubmissionStayUnscoredAndRetry() throws Exception {
        var model=model();var items=((TranslationPayload)model.bank().questions().getFirst().payload()).items();
        model.setTranslationReference(0,items.getFirst().id(),new TextContent("阅读为我们打开通往世界的窗口。"));
        model.setAnalysis(0,"Translate faithfully and keep the meaning clear.");
        var bank=model.bank();var database=new io.quizforge.infrastructure.persistence.SqliteDatabase(new io.quizforge.infrastructure.filesystem.QuizForgeDataDirectory(temp));
        var service=new PracticeSessionService(new io.quizforge.infrastructure.persistence.practice.SqlitePracticeTransaction(database),java.time.Clock.systemUTC());
        var runtime=new PersistentPracticeRuntime(service,bank,new io.quizforge.infrastructure.filesystem.qbank.QuestionBankV2Codec().contentId(bank));
        var stage=new AtomicReference<Stage>();
        try{fx(()->{
            var view=new MixedQuestionPracticeView(bank,QuestionResourceInput.NONE,()->runtime,refs->null);
            stage.set(new Stage());stage.get().setOpacity(0);stage.get().setScene(new Scene(view,1100,750));UiTheme.apply(stage.get().getScene());stage.get().show();
            assertTrue(MixedQuestionPracticeView.requiresMixedView(bank));assertTrue(MixedQuestionPracticeView.supportsEditing(bank));
            assertEquals(5,view.outline().lookupAll(".question-number-cell").size());
            var first=(TextArea)view.lookup("#practice-translation-answer-1");first.setText("我的第一句译文");
            assertSame(first,view.lookup("#practice-translation-answer-1"));
            assertEquals("我的第一句译文",runtime.session().translationAnswers().get(items.getFirst().id()).text());
            assertTrue(view.outline().lookup("#authoring-question-1").getStyleClass().contains("draft"));
            assertTrue(view.outline().lookup("#authoring-question-2").getStyleClass().contains("unsubmitted"));
            var submit=(Button)view.lookup("#practice-translation-submit");var header=new AtomicReference<String>();
            FxTestRuntime.answerSubmission("继续作答",pane->header.set(pane.getHeaderText()));submit.fire();
            assertTrue(header.get().contains("4 道小题"));assertEquals(QuestionBankPracticeSession.State.SELECTED,runtime.session().state());
            FxTestRuntime.acceptSubmission(submit);
            assertEquals(QuestionAttempt.Result.UNSCORED,runtime.questionState(bank.questions().getFirst().id()).attempts().getFirst().result());
            assertEquals("已提交 · 待评分",((Label)view.lookup("#practice-translation-result")).getText());
            assertFalse(view.lookup("#practice-translation-answer-1") instanceof TextArea);
            assertTrue(view.lookupAll(".label").stream().map(Label.class::cast).anyMatch(label->label.getText().contains("阅读为我们打开")));
            assertTrue(view.outline().lookup("#authoring-question-1").getStyleClass().contains("unscored"));
            ((Button)view.lookup("#practice-translation-retry")).fire();
            assertTrue(runtime.session().translationAnswers().isEmpty());
            assertEquals("",((TextArea)view.lookup("#practice-translation-answer-1")).getText());
            assertNull(view.lookup("#practice-translation-result"));return null;
        });}finally{fx(()->{if(stage.get()!=null)stage.get().close();return null;});}
    }
    @Test void nativeSentencePreviewRemovesMarkersPreservesStylesAndNumbersOccurrences() throws Exception {
        var model=model();String passage="Before {{One sentence.}} between {{One sentence.}} after \\{{literal}}.";
        String nativeJson="{\"version\":\"1.0.4\",\"options\":{},\"data\":{\"main\":[{\"value\":\"Before {{\"},{\"value\":\"One \",\"bold\":true,\"color\":\"#123456\"},{\"value\":\"sentence.}} between {{One sentence.}} after \\\\{{literal}}.\"}]}}";
        var content=new ContentEditSession(new TextContent(""),List.of(),QuestionResourceInput.NONE);var document=content.stageDocument(nativeJson,passage);
        content.resources().forEach(model::addResource);model.setPrompt(0,document);
        var body=new VBox();var stage=new AtomicReference<Stage>();var web=new AtomicReference<WebView>();
        try{fx(()->{
            var navigation=new HBox();var spacer=new Region();navigation.getChildren().add(spacer);
            TranslationEditorFields.render(new QuestionEditorContext(model,0,body,navigation,spacer,new HashMap<>(),new ArrayList<>(),new VBox(),content::open,()->null,()->{}));
            assertNotNull(body.lookup("#translation-edit-prompt"));assertNotNull(body.lookup("#translation-edit-reference-1"));assertNotNull(body.lookup("#translation-edit-reference-2"));
            stage.set(new Stage());stage.get().setOpacity(0);stage.get().setScene(new Scene(UiTheme.scroll(body),760,700));UiTheme.apply(stage.get().getScene());stage.get().show();
            web.set((WebView)body.lookup("#canvas-editor-webview"));return null;
        });
        await(()->Boolean.TRUE.equals(web.get().getEngine().executeScript("!!window.canvasEditor?.ready()"))
                && ((String)web.get().getEngine().executeScript("window.canvasEditor.text()")).contains("(2)"));
        fx(()->{
            var text=(String)web.get().getEngine().executeScript("window.canvasEditor.text()");
            assertTrue(text.contains("(1) One sentence."));assertTrue(text.contains("(2) One sentence."));assertTrue(text.contains("{{literal}}"));
            assertFalse(text.contains("{{One sentence.}}"));
            assertTrue(Boolean.TRUE.equals(web.get().getEngine().executeScript("JSON.parse(window.canvasEditor.value()).main.some(e=>e.bold && e.underline && e.color==='#123456')")));
            assertEquals(passage,QuestionContentData.plainText(model.bank().questions().getFirst().prompt()));
            assertEquals(2,((TranslationPayload)model.bank().questions().getFirst().payload()).items().size());return null;
        });}finally{fx(()->{if(stage.get()!=null)stage.get().close();return null;});}
    }
}
