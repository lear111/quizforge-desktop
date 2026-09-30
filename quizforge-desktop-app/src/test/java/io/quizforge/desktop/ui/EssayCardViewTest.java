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
    @BeforeAll static void startFx() throws Exception {
        var started=new CountDownLatch(1);
        try{Platform.startup(started::countDown);}catch(IllegalStateException alreadyStarted){started.countDown();}
        assertTrue(started.await(20,TimeUnit.SECONDS));
        Platform.setImplicitExit(false);
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
