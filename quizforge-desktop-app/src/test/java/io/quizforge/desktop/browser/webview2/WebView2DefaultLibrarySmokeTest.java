package io.quizforge.desktop.browser.webview2;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

/** Actual Chromium boot from the desktop module, without manually overriding the DLL path. */
@EnabledOnOs(OS.WINDOWS)
class WebView2DefaultLibrarySmokeTest {
    @TempDir Path directory;
    @BeforeAll static void startFx() throws Exception {io.quizforge.desktop.testing.FxTestRuntime.start();}
    private static <T> T fx(Callable<T> action) throws Exception {
        var task=new FutureTask<T>(action);Platform.runLater(task);return task.get(15,TimeUnit.SECONDS);
    }
    @ParameterizedTest @EnumSource(WebView2Browser.Page.class)
    void packagedPageBootsWithDefaultLibraryLookup(WebView2Browser.Page page) throws Exception {
        String previous=System.getProperty("quizforge.webview2.library");System.clearProperty("quizforge.webview2.library");
        var area=fx(StackPane::new);
        var stage=fx(()->{
            var window=new Stage();window.setTitle("QuizForge native path check "+UUID.randomUUID());window.setOpacity(0);
            window.setScene(new Scene(area,800,600));window.show();return window;
        });
        WebView2Browser browser=null;
        try {
            var boot=new CompletableFuture<Void>();
            browser=fx(()->new WebView2Browser(stage,area,directory,page,event->{
                if("boot".equals(event.path("kind").asText()))boot.complete(null);
                if("native-error".equals(event.path("kind").asText()))boot.completeExceptionally(new IllegalStateException(event.toString()));
            }));
            boot.get(25,TimeUnit.SECONDS);
            // The module sends boot before the final document load event in some WebView2 builds.
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
            String state;
            do {
                state=browser.evaluate("document.readyState").toCompletableFuture().get(6,TimeUnit.SECONDS).asText();
                if("complete".equals(state))break;
                Thread.sleep(100);
            }while(System.nanoTime()<deadline);
            assertEquals("complete",state);
        }finally{
            try {
                if(browser!=null){var owned=browser;fx(()->{owned.close();return null;});owned.closeCompletion().toCompletableFuture().get(35,TimeUnit.SECONDS);}
            }finally{
                try {fx(()->{stage.close();return null;});}
                finally {if(previous==null)System.clearProperty("quizforge.webview2.library");else System.setProperty("quizforge.webview2.library",previous);}
            }
        }
    }
}
