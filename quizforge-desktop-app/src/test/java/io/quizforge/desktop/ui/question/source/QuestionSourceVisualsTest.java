package io.quizforge.desktop.ui.question.source;

import io.quizforge.core.question.source.QuestionBankReferenceResolver;
import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.scene.control.Button;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuestionSourceVisualsTest {
    private static void fx(Runnable action)throws Exception {
        var started=new CountDownLatch(1);
        try{Platform.startup(()->{Platform.setImplicitExit(false);started.countDown();});}
        catch(IllegalStateException running){Platform.runLater(started::countDown);}
        assertTrue(started.await(20,TimeUnit.SECONDS));
        var task=new FutureTask<Void>(()->{action.run();return null;});Platform.runLater(task);task.get(20,TimeUnit.SECONDS);
    }
    @Test void sharedSourceRowsKeepChangedRevisionNavigableAndMissingSourceWithoutAction() throws Exception {
        fx(() -> {
            var clicks = new java.util.concurrent.atomic.AtomicInteger();
            var changed = QuestionSourceVisuals.row(new QuestionSourceVisuals.Presentation("来源", "来源已修改",
                    QuestionBankReferenceResolver.Status.DIFFERENT_REVISION, true, clicks::incrementAndGet), "changed");
            Button link = (Button) changed.lookup("#changed");
            assertFalse(link.isDisabled()); link.fire();
            assertEquals(1, clicks.get());
            assertNotNull(changed.lookup(".question-source-warning"));
            var missing = QuestionSourceVisuals.row(new QuestionSourceVisuals.Presentation("来源", "来源文档不存在",
                    QuestionBankReferenceResolver.Status.MISSING_DOCUMENT, false, clicks::incrementAndGet), "missing");
            Button disabled = (Button) missing.lookup("#missing");
            assertTrue(disabled.isDisabled());
            assertNull(disabled.getOnAction()); disabled.fire();
            assertEquals(1, clicks.get());
        });
    }
}
