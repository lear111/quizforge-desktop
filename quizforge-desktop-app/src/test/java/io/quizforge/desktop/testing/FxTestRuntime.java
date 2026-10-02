package io.quizforge.desktop.testing;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;

/** All UI tests share the JVM toolkit; individual tests close only their own windows. */
public final class FxTestRuntime {
    private FxTestRuntime() { }
    public static void acceptSubmission(javafx.scene.control.Button submit){
        if(submit.isDisabled())return;
        answerSubmission("提交",ignored->{});
        submit.fire();
    }
    public static void answerSubmission(String label,java.util.function.Consumer<javafx.scene.control.DialogPane> inspect){
        Platform.runLater(()->{
            var pane=javafx.stage.Window.getWindows().stream().filter(javafx.stage.Window::isShowing)
                    .map(window->window.getScene().getRoot()).filter(javafx.scene.control.DialogPane.class::isInstance)
                    .map(javafx.scene.control.DialogPane.class::cast).filter(dialog->"question-submit-confirmation".equals(dialog.getId()))
                    .findFirst().orElseThrow();
            var answer=pane.getButtonTypes().stream().filter(type->label.equals(type.getText())).findFirst().orElseThrow();
            inspect.accept(pane);
            ((javafx.scene.control.Button)pane.lookupButton(answer)).fire();
        });
    }
    public static void start() throws Exception {
        var started = new CountDownLatch(1);
        Runnable ready = () -> { Platform.setImplicitExit(false); started.countDown(); };
        try { Platform.startup(ready); }
        catch (IllegalStateException initialized) { Platform.runLater(ready); }
        if (!started.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("JavaFX startup timed out");
    }
}
