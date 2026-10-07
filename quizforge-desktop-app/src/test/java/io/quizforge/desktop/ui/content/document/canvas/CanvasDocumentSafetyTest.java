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
            var original=fx(()->(WebView)view.getChildren().getFirst());
            fx(()->{original.getEngine().executeScript("window.previewToken='tab-retained'");return null;});
            fx(()->{root.setCenter(new Label("other tab"));return null;});
            fx(()->null);
            fx(()->{root.setCenter(view);return null;});
            awaitReady(view);
            assertSame(original,fx(()->view.getChildren().getFirst()),"Returning to a recent tab must reuse its loaded WebView");
            assertEquals("tab-retained",fx(()->original.getEngine().executeScript("window.previewToken")));
            String restored=fx(()->(String)((WebView)view.getChildren().getFirst()).getEngine().executeScript("window.canvasEditor.document()"));
            assertTrue(restored.contains("retained content"));
        } finally {fx(()->{stage.close();return null;});}
    }
    @Test void questionNavigationCacheIsBoundedAndClearedWhenWindowCloses() throws Exception {
        var root=fx(BorderPane::new);
        var stage=fx(()->{var owned=new Stage();owned.setOpacity(0);owned.setScene(new Scene(root,850,500));owned.show();return owned;});
        var webs=new java.util.ArrayList<WebView>();
        var loads=new java.util.ArrayList<java.util.concurrent.atomic.AtomicInteger>();
        try {
            for(int i=0;i<8;i++){
                final int number=i;
                var current=fx(()->{
                    var view=new CanvasDocumentView(new TextContent("Article "+number),List.of(),QuestionResourceInput.NONE,"navigation-");
                    assertTrue(view.getChildren().isEmpty(),"Unattached previews must not allocate a WebView");
                    root.setCenter(view);
                    assertEquals(0,((WebView)view.getChildren().getFirst()).getOpacity(),"Uninitialized preview must hide the raw editor toolbar");
                    return view;
                });
                awaitReady(current);
                var web=fx(()->(WebView)current.getChildren().getFirst());webs.add(web);
                var count=new java.util.concurrent.atomic.AtomicInteger();loads.add(count);
                fx(()->{
                    assertEquals(1,web.getOpacity());
                    web.getEngine().executeScript("window.previewToken='article-"+number+"'");
                    web.getEngine().getLoadWorker().stateProperty().addListener((o,a,b)->{
                        if(b==javafx.concurrent.Worker.State.SCHEDULED)count.incrementAndGet();
                    });return null;
                });
                fx(()->null);
            }
            assertTrue(loads.get(0).get()>0,"The seventh detached renderer evicts the oldest of six cached previews");
            assertEquals(0,loads.get(1).get());
            var recent=fx(()->{
                var view=new CanvasDocumentView(new TextContent("Article 1"),List.of(),QuestionResourceInput.NONE,"navigation-");
                root.setCenter(view);return view;
            });
            fx(()->null);
            assertSame(webs.get(1),fx(()->recent.getChildren().getFirst()));
            assertEquals("article-1",fx(()->webs.get(1).getEngine().executeScript("window.previewToken")));
            assertEquals(0,loads.get(1).get());
            var evicted=fx(()->{
                var view=new CanvasDocumentView(new TextContent("Article 0"),List.of(),QuestionResourceInput.NONE,"navigation-");
                root.setCenter(view);return view;
            });
            assertNotSame(webs.get(0),fx(()->evicted.getChildren().getFirst()));
            awaitReady(evicted);fx(()->null);
            assertEquals(0,loads.get(3).get());
            fx(()->{stage.close();return null;});
            assertTrue(loads.get(3).get()>0,"Closing the window must release the detached preview cache");
        } finally {fx(()->{root.setCenter(null);stage.close();return null;});}
    }
    @Test void leavingBeforeLoadReleasesRendererAndReturningStillLoadsArticle() throws Exception {
        var view=fx(()->new CanvasDocumentView(new TextContent("Article after early navigation"),List.of(),QuestionResourceInput.NONE,"early-"));
        var root=fx(()->new BorderPane(view));
        var stage=fx(()->{
            var owned=new Stage();owned.setOpacity(0);owned.setScene(new Scene(root,850,500));owned.show();
            root.setCenter(new Label("another question"));return owned;
        });
        try {
            fx(()->null);
            assertTrue(fx(()->view.getChildren().isEmpty()),"An unloaded detached preview must drop its WebView instead of retaining it in an inactive tab");
            fx(()->{root.setCenter(view);return null;});
            awaitReady(view);
            assertTrue(fx(()->(String)((WebView)view.getChildren().getFirst()).getEngine().executeScript("window.canvasEditor.document()")).contains("Article after early navigation"));
        } finally {fx(()->{root.setCenter(null);stage.close();return null;});}
    }
    @Test void synchronousPageRebuildReusesLoadedPreviewButChangedDocumentLoadsFresh() throws Exception {
        var session=new ContentEditSession(new TextContent(""),List.of(),QuestionResourceInput.NONE);
        var content=session.stageDocument("{\"version\":\"1.0.4\",\"data\":{\"main\":[{\"value\":\"retained content\"}]},\"options\":{}}","retained content");
        var view=fx(()->new CanvasDocumentView(content,session.resources(),session::open,"refresh-"));
        var root=fx(()->new BorderPane(view));
        var stage=fx(()->{var owned=new Stage();owned.setOpacity(0);owned.setScene(new Scene(root,850,500));owned.show();return owned;});
        try {
            awaitReady(view);
            var web=fx(()->(WebView)view.getChildren().getFirst());
            var loads=new java.util.concurrent.atomic.AtomicInteger();
            fx(()->{
                web.getEngine().executeScript("window.previewToken='loaded-once'");
                web.getEngine().getLoadWorker().stateProperty().addListener((o,a,b)->{
                    if(b==javafx.concurrent.Worker.State.SCHEDULED)loads.incrementAndGet();
                });return null;
            });
            // Adding unrelated answer/analysis resources must not invalidate the article.
            var changed=session.stageDocument("{\"version\":\"1.0.4\",\"data\":{\"main\":[{\"value\":\"updated article\"}]},\"options\":{}}","updated article");
            var replacement=fx(()->{
                CanvasDocumentView current=null;
                for(int i=0;i<3;i++){
                    current=new CanvasDocumentView(content,session.resources(),session::open,"refresh-");
                    assertTrue(current.getChildren().isEmpty(),"A replacement must try reuse before allocating a WebView");
                    root.setCenter(current);assertSame(web,current.getChildren().getFirst());
                }return current;
            });
            fx(()->null); // Drain the detached previews' deferred cleanup.
            assertEquals("loaded-once",fx(()->web.getEngine().executeScript("window.previewToken")));
            assertEquals(0,loads.get(),"Unchanged previews must not navigate/reload their WebEngine");
            assertSame(web,fx(()->replacement.getChildren().getFirst()));
            var updated=fx(()->{var current=new CanvasDocumentView(changed,session.resources(),session::open,"refresh-");root.setCenter(current);return current;});
            assertNotSame(web,fx(()->updated.getChildren().getFirst()));
            awaitReady(updated);
            assertTrue(fx(()->(String)((WebView)updated.getChildren().getFirst()).getEngine().executeScript("window.canvasEditor.document()")).contains("updated article"));
        } finally {fx(()->{root.setCenter(null);stage.close();return null;});}
    }
    @Test void failedPreviewCanRetryWhenItsResourceBecomesAvailable() throws Exception {
        var session=new ContentEditSession(new TextContent(""),List.of(),QuestionResourceInput.NONE);
        var content=session.stageDocument("{\"version\":\"1.0.4\",\"data\":{\"main\":[{\"value\":\"recovered article\"}]},\"options\":{}}","recovered article");
        var missing=fx(()->new CanvasDocumentView(content,session.resources(),QuestionResourceInput.NONE,"recover-"));
        var root=fx(()->new BorderPane(missing));
        var stage=fx(()->{var owned=new Stage();owned.setOpacity(0);owned.setScene(new Scene(root,850,500));owned.show();return owned;});
        var web=fx(()->(WebView)missing.getChildren().getFirst());
        try {
            boolean failed=false;
            for(int i=0;i<200;i++){
                if(fx(()->missing.getChildren().getFirst() instanceof Label)){failed=true;break;}
                Thread.sleep(100);
            }
            assertTrue(failed,"The missing resource must report an error before retrying");
            var restored=fx(()->{var current=new CanvasDocumentView(content,session.resources(),session::open,"recover-");root.setCenter(current);return current;});
            assertNotSame(web,fx(()->restored.getChildren().getFirst()),"Failed renderers must not be adopted");
            awaitReady(restored);
            assertTrue(fx(()->(String)((WebView)restored.getChildren().getFirst()).getEngine().executeScript("window.canvasEditor.document()")).contains("recovered article"));
        } finally {fx(()->{root.setCenter(null);stage.close();return null;});}
    }
    @Test void previewLoadsOnlyReferencedImagesAndPlainTextDoesNotReadBankImages() throws Exception {
        var used=new io.quizforge.core.question.resource.QBankResource("used",io.quizforge.core.question.resource.ResourceKind.IMAGE,"image/png","used.png","hash");
        var unused=new io.quizforge.core.question.resource.QBankResource("unused",io.quizforge.core.question.resource.ResourceKind.IMAGE,"image/png","unused.png","hash");
        var resources=List.of(used,unused);
        var reads=new java.util.ArrayList<String>();
        byte[] png=java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/aKsAAAAASUVORK5CYII=");
        QuestionResourceInput input=resource->{reads.add(resource.id());return new java.io.ByteArrayInputStream(png);};
        var rich=new io.quizforge.core.question.content.RichContent(new io.quizforge.core.question.content.RichDocument(List.of(
                new io.quizforge.core.question.content.BlockImageNode("used",null,null))));
        var picture=fx(()->new CanvasDocumentView(rich,resources,input,"images-"));
        var root=fx(()->new BorderPane(picture));
        var stage=fx(()->{var owned=new Stage();owned.setOpacity(0);owned.setScene(new Scene(root,850,500));owned.show();return owned;});
        try {
            awaitReady(picture);
            assertEquals(List.of("used"),fx(()->List.copyOf(reads)),"Unrelated images must not be read or decoded");
            assertTrue(fx(()->(String)((WebView)picture.getChildren().getFirst()).getEngine().executeScript("window.canvasEditor.document()")).contains("data:image/png;base64,"));
            var plain=fx(()->{var view=new CanvasDocumentView(new TextContent("Plain article"),resources,input,"images-");root.setCenter(view);return view;});
            awaitReady(plain);
            assertEquals(List.of("used"),fx(()->List.copyOf(reads)),"Plain text must not read any image from the bank");
        } finally {fx(()->{root.setCenter(null);stage.close();return null;});}
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
