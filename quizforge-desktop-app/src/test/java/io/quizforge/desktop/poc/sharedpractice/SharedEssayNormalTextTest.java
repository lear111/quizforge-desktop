package io.quizforge.desktop.poc.sharedpractice;
import io.quizforge.core.practice.*;import io.quizforge.core.port.QuestionResourceInput;import io.quizforge.desktop.testing.FxTestRuntime;import io.quizforge.desktop.ui.question.practice.MixedQuestionPracticeView;import io.quizforge.infrastructure.filesystem.qbank.QuestionBankV2Codec;import io.quizforge.infrastructure.persistence.SqliteDatabase;import io.quizforge.infrastructure.persistence.practice.SqlitePracticeTransaction;import java.nio.file.Path;import java.time.Clock;import java.util.concurrent.*;import javafx.application.Platform;import javafx.scene.Scene;import javafx.scene.control.*;import javafx.stage.Stage;import org.junit.jupiter.api.*;import org.junit.jupiter.api.io.TempDir;import static org.junit.jupiter.api.Assertions.*;
class SharedEssayNormalTextTest {
 @TempDir Path directory;
 @BeforeAll static void startFx() throws Exception{FxTestRuntime.start();}
 @Test void normalTextAndDraftTextUseTheSameFormalAnswerWithoutTouchingRichEditor() throws Exception {
 var bank=new SharedEssayAdapterTest().bank();var service=new PracticeSessionService(new SqlitePracticeTransaction(new SqliteDatabase(directory.resolve("practice.db"))),Clock.systemUTC());var runtime=new PersistentPracticeRuntime(service,bank,new QuestionBankV2Codec().contentId(bank));
 var view=fx(()->new MixedQuestionPracticeView(bank,QuestionResourceInput.NONE,()->runtime,refs->null));var stage=fx(()->{var s=new Stage();s.setScene(new Scene(view,1000,760));s.setOpacity(0);s.show();return s;});
 try {
   fx(()->{assertTrue(view.lookup("#essay-edit-answer").isVisible());((TextArea)view.lookup("#essay-text-answer")).setText("NORMAL partial 中文");return null;});
   assertEquals("NORMAL partial 中文",runtime.essayAnswer("q_essay").text());assertNull(runtime.essayAnswer("q_essay").document());String parent=runtime.questionState("q_essay").sessionQuestion().id();
   fx(view.surface()::enterDraft).toCompletableFuture().get(30,TimeUnit.SECONDS);
   assertEquals("NORMAL partial 中文",fx(()->view.surface().draftView().view().getEngine().executeScript("window.sharedPractice.getViewState().question.presentation.answer.text")));
   fx(()->view.surface().draftView().view().getEngine().executeScript("(()=>{const i=document.querySelector('#text-answer-0');i.value='DRAFT final before debounce';i.dispatchEvent(new Event('input',{bubbles:true}));})()"));
   fx(view.surface()::leaveDraft).toCompletableFuture().get(30,TimeUnit.SECONDS);
   assertEquals("DRAFT final before debounce",fx(()->((TextArea)view.lookup("#essay-text-answer")).getText()));assertNull(runtime.essayAnswer("q_essay").document());assertEquals(parent,runtime.questionState("q_essay").sessionQuestion().id());
   fx(view.surface()::enterDraft).toCompletableFuture().get(30,TimeUnit.SECONDS);assertEquals("DRAFT final before debounce",fx(()->view.surface().draftView().view().getEngine().executeScript("document.querySelector('#text-answer-0').value")));
 }finally{fx(()->{view.surface().destroy();stage.close();return null;});}
 }
 private static <T>T fx(Callable<T> action)throws Exception{var task=new FutureTask<T>(action);Platform.runLater(task);return task.get(20,TimeUnit.SECONDS);}
}
