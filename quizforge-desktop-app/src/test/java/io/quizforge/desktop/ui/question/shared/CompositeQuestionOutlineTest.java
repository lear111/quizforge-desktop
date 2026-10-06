package io.quizforge.desktop.ui.question.shared;

import io.quizforge.desktop.testing.FxTestRuntime;
import java.util.*;
import java.util.concurrent.*;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import static org.junit.jupiter.api.Assertions.*;

/** Outline numbering is independent of the removed native question cards. */
class CompositeQuestionOutlineTest {
    @BeforeAll static void start()throws Exception{FxTestRuntime.start();}
    private static <T>T fx(Callable<T> work)throws Exception{var task=new FutureTask<T>(work);Platform.runLater(task);return task.get(20,TimeUnit.SECONDS);}
}
