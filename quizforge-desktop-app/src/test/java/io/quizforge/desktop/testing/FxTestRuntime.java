package io.quizforge.desktop.testing;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;

/** All UI tests share the JVM toolkit; individual tests close only their own windows. */
public final class FxTestRuntime {
    private FxTestRuntime() { }
    public static void start() throws Exception {
        var started = new CountDownLatch(1);
        Runnable ready = () -> { Platform.setImplicitExit(false); started.countDown(); };
        try { Platform.startup(ready); }
        catch (IllegalStateException initialized) { Platform.runLater(ready); }
        if (!started.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("JavaFX startup timed out");
    }
}
