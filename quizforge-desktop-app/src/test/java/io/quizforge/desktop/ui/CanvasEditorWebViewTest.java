package io.quizforge.desktop.ui;

import static org.junit.jupiter.api.Assertions.*;

import io.quizforge.core.question.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.Scene;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import netscape.javascript.JSObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CanvasEditorWebViewTest {
    @BeforeAll static void startFx() throws Exception {
        var started = new CountDownLatch(1);
        Platform.startup(() -> { Platform.setImplicitExit(false); started.countDown(); });
        assertTrue(started.await(20, TimeUnit.SECONDS));
    }

    @Test void nativeKeyboardMovesCaretAndDeletesCanvasContent() throws Exception {
        var loaded=new CountDownLatch(1);var finished=new CountDownLatch(1);
        var web=new AtomicReference<WebView>();var stage=new AtomicReference<Stage>();
        var failure=new AtomicReference<Throwable>();
        Platform.runLater(()->{
            web.set(new WebView());stage.set(new Stage());stage.get().setScene(new Scene(web.get(),1000,700));
            stage.get().setOpacity(0);stage.get().show();web.get().requestFocus();
            web.get().getEngine().getLoadWorker().stateProperty().addListener((o,a,b)->{
                if(b==Worker.State.SUCCEEDED || b==Worker.State.FAILED)loaded.countDown();
            });
            web.get().getEngine().load(new CanvasEditorPageLocator(null).editorUrl());
        });
        assertTrue(loaded.await(30,TimeUnit.SECONDS));
        Platform.runLater(()->{
            try{
                var engine=web.get().getEngine();var api=(JSObject)engine.executeScript("window.canvasEditor");
                assertEquals(true,api.call("ready"));
                engine.executeScript("window.keyEvents=[];document.addEventListener('keydown',e=>window.keyEvents.push({key:e.key,code:e.code,keyCode:e.keyCode,keyIdentifier:e.keyIdentifier,target:e.target.className}),true)");
                for(var navigation:List.of(javafx.scene.input.KeyCode.UNDEFINED,javafx.scene.input.KeyCode.LEFT,
                        javafx.scene.input.KeyCode.RIGHT,javafx.scene.input.KeyCode.HOME,javafx.scene.input.KeyCode.END)){
                    api.call("load","{\"main\":[{\"value\":\"ABCDE\"}]}");
                    engine.executeScript("window.__quizforgeCanvasState.editor.command.executeSetRange(2,2);document.querySelector('.ce-inputarea').focus()");
                    if(navigation!=javafx.scene.input.KeyCode.UNDEFINED)keyboard(web.get(),navigation,false,false);
                    keyboard(web.get(),javafx.scene.input.KeyCode.BACK_SPACE,false,false);
                    String expected=switch(navigation){case LEFT->"BCDE";case RIGHT->"ABDE";case HOME->"ABCDE";case END->"ABCD";default->"ACDE";};
                    assertEquals(new TextContent(expected),CanvasEditorAdapter.fromCanvasJson((String)api.call("value"),Set.of()),
                            ()->String.valueOf(engine.executeScript("JSON.stringify(window.keyEvents)")));
                }
                api.call("load","{\"main\":[{\"value\":\"ABCDE\"}]}");
                engine.executeScript("window.__quizforgeCanvasState.editor.command.executeSetRange(2,2)");
                keyboard(web.get(),javafx.scene.input.KeyCode.DELETE,false,false);
                assertEquals("ABDE",QuestionContentData.plainText(CanvasEditorAdapter.fromCanvasJson((String)api.call("value"),Set.of())));
                api.call("load","{\"main\":[{\"value\":\"ABCDE\"}]}");
                engine.executeScript("window.__quizforgeCanvasState.editor.command.executeSetRange(2,2)");
                keyboard(web.get(),javafx.scene.input.KeyCode.RIGHT,true,false);
                keyboard(web.get(),javafx.scene.input.KeyCode.BACK_SPACE,false,false);
                assertEquals("ABDE",QuestionContentData.plainText(CanvasEditorAdapter.fromCanvasJson((String)api.call("value"),Set.of())));
                keyboard(web.get(),javafx.scene.input.KeyCode.A,false,true);
                keyboard(web.get(),javafx.scene.input.KeyCode.DELETE,false,false);
                assertEquals("",QuestionContentData.plainText(CanvasEditorAdapter.fromCanvasJson((String)api.call("value"),Set.of())));
                api.call("load","{\"main\":[{\"value\":\"ABCDE\"}]}");
                engine.executeScript("window.__quizforgeCanvasState.editor.command.executeSetRange(2,2)");
                keyboard(web.get(),javafx.scene.input.KeyCode.ENTER,false,false);
                assertEquals("AB\nCDE",QuestionContentData.plainText(CanvasEditorAdapter.fromCanvasJson((String)api.call("value"),Set.of())));
                api.call("load","{\"main\":[{\"value\":\"AB\\nCD\"}]}");
                engine.executeScript("window.__quizforgeCanvasState.editor.command.executeSetRange(2,2)");
                keyboard(web.get(),javafx.scene.input.KeyCode.DOWN,false,false);
                assertTrue(((Number)engine.executeScript("window.__quizforgeCanvasState.editor.command.getRange().startIndex")).intValue()>2);
                keyboard(web.get(),javafx.scene.input.KeyCode.UP,false,false);
                assertEquals(2,((Number)engine.executeScript("window.__quizforgeCanvasState.editor.command.getRange().startIndex")).intValue());
                assertEquals(false,engine.executeScript("document.querySelector('[data-command=bold]').dispatchEvent(new MouseEvent('mousedown',{bubbles:true,cancelable:true}))"));
                assertEquals("ce-inputarea",engine.executeScript("document.activeElement.className"));
            }catch(Throwable e){failure.set(e);}finally{stage.get().close();finished.countDown();}
        });
        assertTrue(finished.await(20,TimeUnit.SECONDS));
        if(failure.get()!=null)throw new AssertionError("Canvas keyboard failed",failure.get());
    }

    private static void keyboard(WebView web,javafx.scene.input.KeyCode code,boolean shift,boolean control){
        javafx.event.Event.fireEvent(web,new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED,
                javafx.scene.input.KeyEvent.CHAR_UNDEFINED,"",code,shift,control,false,false));
        javafx.event.Event.fireEvent(web,new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_RELEASED,
                javafx.scene.input.KeyEvent.CHAR_UNDEFINED,"",code,shift,control,false,false));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void localCanvasEditorInitializesInJavaFxWebView(boolean packagedJar) throws Exception {
        var packagedPage = getClass().getResource("canvas-editor.html");
        java.nio.file.Path jar = null;
        if (packagedJar) {
            jar = java.nio.file.Files.createTempFile("canvas-editor-offline-", ".jar");
            try (var zip = new java.util.zip.ZipOutputStream(java.nio.file.Files.newOutputStream(jar))) {
                for (var name : List.of("canvas-editor.html", "canvas-editor-bundle.js", "canvas-editor-bundle.css")) {
                    zip.putNextEntry(new java.util.zip.ZipEntry(name));
                    try (var asset = getClass().getResourceAsStream(name)) { assertNotNull(asset); asset.transferTo(zip); }
                    zip.closeEntry();
                }
            }
            packagedPage = java.net.URI.create("jar:" + jar.toUri() + "!/canvas-editor.html").toURL();
        }
        final var pageUrl = new CanvasEditorPageLocator(null, packagedPage).editorUrl();
        var loaded = new CountDownLatch(1);
        var web = new AtomicReference<WebView>();
        var stage = new AtomicReference<Stage>();
        var loadError = new AtomicReference<String>();
        Platform.runLater(() -> {
            web.set(new WebView());
            stage.set(new Stage());stage.get().setScene(new Scene(web.get(), 1100, 700));
            stage.get().setOpacity(0);stage.get().show();
            var engine = web.get().getEngine();
            engine.getLoadWorker().stateProperty().addListener((o, a, b) -> {
                if (b == Worker.State.FAILED) {
                    loadError.set(String.valueOf(engine.getLoadWorker().getException()));loaded.countDown();
                } else if (b == Worker.State.SUCCEEDED) loaded.countDown();
            });
            engine.load(pageUrl);
        });
        assertTrue(loaded.await(40, TimeUnit.SECONDS), "WebView did not finish loading");
        var result = new AtomicReference<String>();
        var threadFailure = new AtomicReference<Throwable>();
        var checked = new CountDownLatch(1);
        var imageDispatched = new CountDownLatch(1);
        var imageRequests = new AtomicInteger();
        Platform.runLater(() -> {
            try {
                var api = (JSObject) web.get().getEngine().executeScript("window.canvasEditor");
                result.set("ready=" + api.call("ready") + ";error=" + api.call("error"));
                if (Boolean.TRUE.equals(api.call("ready"))) {
                    ((JSObject) web.get().getEngine().executeScript("window"))
                            .setMember("quizforgeHost", new CanvasEditorImageHost(() -> {
                                imageRequests.incrementAndGet();imageDispatched.countDown();
                            }));
                    web.get().getEngine().executeScript("window.quizforgeHost.chooseImage()");
                    assertEquals(0, imageRequests.get());
                    var content = new RichContent(new RichDocument(List.of(
                            new HeadingNode(1,List.of(new InlineTextNode("Heading")),null),
                            new ParagraphNode(List.of(new InlineTextNode("Bold",List.of(TextMark.BOLD)))),
                            new BlockImageNode("res_image",null,null),
                            new BlockMathNode("\\frac{-b\\pm\\sqrt{b^2-4ac}}{2a}"))));
                    var images=Map.of("res_image",new CanvasEditorAdapter.ImageData(
                            "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/aKsAAAAASUVORK5CYII=",1,1));
                    api.call("load",CanvasEditorAdapter.toCanvasJson(content,images));
                    var configuration=(String)api.call("configuration");
                    assertTrue(configuration.contains("\"paperWidth\":816"));
                    assertTrue(configuration.contains("\"contentWidth\":672"));
                    assertTrue(configuration.contains("\"pageMode\":\"continuity\""));
                    assertEquals(816,((Number)web.get().getEngine().executeScript("document.querySelector('#paper').clientWidth")).intValue());
                    stage.get().setWidth(950);
                    assertEquals(816,((Number)web.get().getEngine().executeScript("document.querySelector('#paper').clientWidth")).intValue());
                    var output=(String)api.call("value");
                    assertEquals(content,CanvasEditorAdapter.fromCanvasJson(output,Set.of("res_image")));
                    assertFalse(QuestionContentData.encode(CanvasEditorAdapter.fromCanvasJson(output,Set.of("res_image")))
                            .toString().contains("base64"));
                    api.call("mode","readonly");api.call("mode","edit");
                    String longText="Paragraph\n".repeat(110);
                    api.call("load",RichContentEditorAdapter.json(Map.of("main",List.of(Map.of("value",longText)))));
                    assertEquals(1,((Number)web.get().getEngine().executeScript("document.querySelectorAll('#paper .ce-page-container > canvas').length")).intValue());
                    assertTrue(((Number)web.get().getEngine().executeScript("document.querySelector('#paper').scrollHeight")).intValue()>640);
                    api.call("load","{\"main\":[{\"value\":\"Changed\"}]}");
                    assertEquals(new TextContent("Changed"),CanvasEditorAdapter.fromCanvasJson((String)api.call("value"),Set.of()));
                    api.call("destroy");assertEquals(false,api.call("ready"));
                }
            } catch (Throwable e) { threadFailure.set(e); }
            finally { stage.get().close();checked.countDown(); }
        });
        assertTrue(checked.await(20, TimeUnit.SECONDS));
        assertTrue(imageDispatched.await(10, TimeUnit.SECONDS));
        assertEquals(1, imageRequests.get());
        assertNull(loadError.get());
        if(threadFailure.get()!=null)throw new AssertionError("Canvas round-trip failed",threadFailure.get());
        assertEquals("ready=true;error=", result.get());
        if (jar != null) java.nio.file.Files.deleteIfExists(jar);
    }
}
