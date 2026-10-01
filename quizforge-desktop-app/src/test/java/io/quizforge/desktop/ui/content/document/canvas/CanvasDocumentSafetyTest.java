package io.quizforge.desktop.ui.content.document.canvas;

import io.quizforge.core.port.QuestionResourceInput;
import io.quizforge.core.question.content.TextContent;
import java.util.List;
import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class CanvasDocumentSafetyTest {
    @BeforeAll static void startFx() throws Exception {
        io.quizforge.desktop.testing.FxTestRuntime.start();
    }
    private static <T> T fx(Callable<T> action) throws Exception {
        var result=new CompletableFuture<T>();
        Platform.runLater(()->{try{result.complete(action.call());}catch(Throwable failure){result.completeExceptionally(failure);}});
        return result.get(20,TimeUnit.SECONDS);
    }
    private static void awaitReady(CanvasDocumentView view) throws Exception {
        for(int i=0;i<200;i++) {
            if(fx(()->view.getChildren().getFirst() instanceof WebView web && Boolean.TRUE.equals(
                    web.getEngine().executeScript("typeof window.canvasEditor !== 'undefined' && window.canvasEditor.ready()"))))return;
            Thread.sleep(100);
        }
        fail("Canvas preview did not recover");
    }
    @Test void detachedPreviewCanRenderAgainAfterReturningToTab() throws Exception {
        var session=new ContentEditSession(new TextContent(""),List.of(),QuestionResourceInput.NONE);
        var content=session.stageDocument("{\"version\":\"1.0.4\",\"data\":{\"main\":[{\"value\":\"retained content\"}]},\"options\":{}}","retained content");
        var view=fx(()->new CanvasDocumentView(content,session.resources(),session::open,"review-"));
        var root=fx(()->new BorderPane(view));
        var stage=fx(()->{var owned=new Stage();owned.setOpacity(0);owned.setScene(new Scene(root,850,500));owned.show();return owned;});
        try {
            awaitReady(view);
            fx(()->{root.setCenter(new Label("other tab"));return null;});
            fx(()->null);
            fx(()->{root.setCenter(view);return null;});
            awaitReady(view);
            String restored=fx(()->(String)((WebView)view.getChildren().getFirst()).getEngine().executeScript("window.canvasEditor.document()"));
            assertTrue(restored.contains("retained content"));
        } finally {fx(()->{stage.close();return null;});}
    }
    @ParameterizedTest @ValueSource(strings={"http://127.0.0.1/review.png","https://example.invalid/review.png","file:///C:/review.png","../outside.png"})
    void nativePicturesCannotLoadExternalResources(String value) {
        String document="{\"version\":\"1.0.4\",\"data\":{\"main\":[{\"type\":\"image\",\"value\":\""+value+"\"}]},\"options\":{}}";
        assertThrows(IllegalArgumentException.class,()->CanvasNativeDocument.bytes(document));
    }
    @Test void validNativeStringAboveDefaultJacksonLimitIsAccepted() {
        String document="{\"version\":\"1.0.4\",\"data\":{\"main\":[{\"value\":\""+"x".repeat(21_000_000)+"\"}]},\"options\":{}}";
        assertDoesNotThrow(()->CanvasNativeDocument.bytes(document));
        assertThrows(IllegalArgumentException.class,()->CanvasNativeDocument.bytes(document+"{}"));
    }
}
