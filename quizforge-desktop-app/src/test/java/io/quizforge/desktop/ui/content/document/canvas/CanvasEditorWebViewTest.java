package io.quizforge.desktop.ui.content.document.canvas;

import io.quizforge.core.question.content.BlockImageNode;
import io.quizforge.core.question.content.BlockMathNode;
import io.quizforge.core.question.content.DocumentContent;
import io.quizforge.core.question.content.HeadingNode;
import io.quizforge.core.question.content.InlineTextNode;
import io.quizforge.core.question.content.ParagraphNode;
import io.quizforge.core.question.content.QuestionContentData;
import io.quizforge.core.question.content.RichContent;
import io.quizforge.core.question.content.RichDocument;
import io.quizforge.core.question.content.TextContent;
import io.quizforge.core.question.content.TextMark;
import io.quizforge.core.question.resource.ResourceKind;
import io.quizforge.desktop.ui.shared.UiTheme;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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
import static org.junit.jupiter.api.Assertions.*;

class CanvasEditorWebViewTest {
    @BeforeAll static void startFx() throws Exception {
        io.quizforge.desktop.testing.FxTestRuntime.start();
    }

    @Test void firstLineIndentSupportsParagraphRangesUndoAndPracticePreview() throws Exception {
        var loaded=new CountDownLatch(2);var finished=new CountDownLatch(1);var failure=new AtomicReference<Throwable>();
        var edit=new AtomicReference<WebView>();var preview=new AtomicReference<WebView>();var stages=new java.util.ArrayList<Stage>();
        Platform.runLater(()->{
            for(var ref:List.of(edit,preview)){
                ref.set(new WebView());var stage=new Stage();stages.add(stage);stage.setOpacity(0);
                stage.setScene(new Scene(ref.get(),1000,700));stage.show();
                ref.get().getEngine().getLoadWorker().stateProperty().addListener((o,a,b)->{
                    if(b==Worker.State.SUCCEEDED || b==Worker.State.FAILED)loaded.countDown();
                });
                var page=new CanvasEditorPageLocator(null);
                ref.get().getEngine().load(ref==edit ? page.editorUrl() : page.previewUrl());
            }
        });
        assertTrue(loaded.await(30,TimeUnit.SECONDS));
        Platform.runLater(()->{
            try {
                var engine=edit.get().getEngine();var readonly=preview.get().getEngine();
                String capture="""
                    window.painted=[];window.originalFill=CanvasRenderingContext2D.prototype.fillText;
                    CanvasRenderingContext2D.prototype.fillText=function(text,x,y,...rest){
                      const visible=String(text).replace(/^[\\s\\u200b]+/,'');
                      if(this.canvas.closest('#paper') && visible)painted.push({x:x+this.measureText(String(text).slice(0,String(text).length-visible.length)).width,y});
                      return originalFill.call(this,text,x,y,...rest);
                    };
                    window.rowStarts=()=>[...new Set(painted.map(g=>g.y))].sort((a,b)=>a-b)
                      .map(y=>Math.min(...painted.filter(g=>g.y===y).map(g=>g.x)));
                    """;
                engine.executeScript(capture);readonly.executeScript(capture);
                engine.executeScript("""
                    window.p1='开头😀 stays unchanged.';
                    window.p2='Reading opens a window to the world. '.repeat(9);
                    window.p3='Third paragraph.';
                    window.p1Length=Array.from(p1).length;
                    window.canvasEditor.load(JSON.stringify({main:[{value:p1+'\\n'},{value:p2},{value:'Emphasis',bold:true,color:'#ff0000'},{value:'\\n'+p3+'\\nTail.'}]}));
                    window.command=window.__quizforgeCanvasState.editor.command;
                    window.originalText=window.canvasEditor.text();
                    command.executeSetRange(p1Length+10,p1Length+10);
                    document.querySelector('[title="首行缩进 2 字符"]').click();
                    window.singleIndent=window.canvasEditor.value();
                    """);
                assertEquals(true,engine.executeScript("window.canvasEditor.text()===p1+'\\n\\u3000\\u3000'+p2+'Emphasis\\n'+p3+'\\nTail.'"));
                engine.executeScript("document.querySelector('[title=\"首行缩进 2 字符\"]').click()");
                assertEquals(true,engine.executeScript("singleIndent===window.canvasEditor.value()"),"Repeated clicks must not accumulate padding");
                engine.executeScript("command.executeUndo()");
                assertEquals(true,engine.executeScript("originalText===window.canvasEditor.text()"));
                engine.executeScript("command.executeRedo();command.executeSetRange(p1Length+10,p1Length+p2.length+15);document.querySelector('[title=\"首行缩进 2 字符\"]').click()");
                assertEquals(true,engine.executeScript("window.canvasEditor.text()===p1+'\\n\\u3000\\u3000'+p2+'Emphasis\\n\\u3000\\u3000'+p3+'\\nTail.'"),"Only the two selected paragraphs are indented");
                assertEquals(true,engine.executeScript("JSON.parse(window.canvasEditor.value()).main.some(e=>e.bold && e.color==='#ff0000' && e.value==='Emphasis')"));
                var saved=(String)engine.executeScript("window.canvasEditor.document()");
                ((JSObject)engine.executeScript("window.canvasEditor")).call("loadDocument",saved);
                ((JSObject)readonly.executeScript("window.canvasEditor")).call("loadDocument",saved);
                assertEquals(engine.executeScript("window.canvasEditor.value()"),readonly.executeScript("window.canvasEditor.value()"));
                engine.executeScript("painted=[];command.executeSetRange(0,0);window.starts=rowStarts()");
                readonly.executeScript("painted=[];window.__quizforgeCanvasState.editor.command.executeSetRange(0,0);window.starts=rowStarts()");
                for(var view:List.of(engine,readonly)){
                    assertEquals(32,((Number)view.executeScript("starts[1]-starts[0]")).doubleValue(),0.5,"First line is indented by two full-width characters");
                    assertEquals(0,((Number)view.executeScript("starts[2]-starts[0]")).doubleValue(),0.5,"Wrapped continuation lines stay flush left");
                }
                readonly.executeScript("window.canvasEditor.command('outdent');window.canvasEditor.command('indent')");
                assertEquals(engine.executeScript("window.canvasEditor.value()"),readonly.executeScript("window.canvasEditor.value()"),"Practice preview stays readonly");
                engine.executeScript("command.executeSelectAll();document.querySelector('[title=\"取消首行缩进\"]').click()");
                assertEquals(engine.executeScript("originalText"),engine.executeScript("window.canvasEditor.text()"),"Removing indentation restores the original text");
                engine.executeScript("command.executeUndo()");
                assertEquals(true,engine.executeScript("window.canvasEditor.text().includes('\\n\\u3000\\u3000'+p3)"),"Batch removal is one undo step");
                engine.executeScript("window.canvasEditor.load(JSON.stringify({main:[{value:''}]}));document.querySelector('[title=\"首行缩进 2 字符\"]').click()");
                assertEquals("\u3000\u3000",engine.executeScript("window.canvasEditor.text()"));
                engine.executeScript("document.querySelector('[title=\"取消首行缩进\"]').click()");
                assertEquals("",engine.executeScript("window.canvasEditor.text()"),"Empty paragraphs can be indented before typing and cleared");
            }catch(Throwable error){failure.set(error);}
            finally{stages.forEach(Stage::close);finished.countDown();}
        });
        assertTrue(finished.await(20,TimeUnit.SECONDS));
        if(failure.get()!=null)throw new AssertionError("First-line indentation failed",failure.get());
    }

    @Test void sizeDropdownKeepsSelectionStyleAndCanReturnToSixteen() throws Exception {
        var loaded=new CountDownLatch(1);var finished=new CountDownLatch(1);
        var failure=new AtomicReference<Throwable>();var web=new AtomicReference<WebView>();var stage=new AtomicReference<Stage>();
        Platform.runLater(()->{
            web.set(new WebView());stage.set(new Stage());stage.get().setOpacity(0);
            stage.get().setScene(new Scene(web.get(),1000,700));stage.get().show();
            web.get().getEngine().getLoadWorker().stateProperty().addListener((o,a,b)->{
                if(b==Worker.State.SUCCEEDED || b==Worker.State.FAILED)loaded.countDown();
            });
            web.get().getEngine().load(new CanvasEditorPageLocator(null).editorUrl());
        });
        assertTrue(loaded.await(30,TimeUnit.SECONDS));
        Platform.runLater(()->{
            try {
                var engine=web.get().getEngine();assertEquals(true,engine.executeScript("window.canvasEditor.ready()"));
                engine.executeScript("""
                    window.sizeTestError=null;window.sizeTestDone=false;
                    (async()=>{
                      const tick=()=>new Promise(resolve=>setTimeout(resolve,0));
                      const expect=(ok,message)=>{if(!ok)throw new Error(message);};
                      const sizes=document.querySelector('[data-select="size"]');
                      const command=window.__quizforgeCanvasState.editor.command;
                      const hasSize=size=>JSON.parse(window.canvasEditor.value()).main
                        .filter(e=>e.value && e.value!=='\\u200b').every(e=>e.size===size);
                      const choose=async value=>{
                        sizes.dispatchEvent(new MouseEvent('mousedown',{bubbles:true,button:0}));sizes.focus();
                        // Native selects emit change only when the chosen value differs.
                        if(sizes.value!==value){sizes.value=value;sizes.dispatchEvent(new Event('change',{bubbles:true}));}
                        await tick(); // Canvas reports range styles on the next event loop turn.
                      };
                      window.canvasEditor.load(JSON.stringify({main:[{value:'Selected text',font:'Georgia',size:24}]}));
                      command.executeSelectAll();await tick();
                      expect(sizes.value==='24','Selection should display 24, got '+sizes.value);
                      const range=JSON.stringify(command.getRange());
                      sizes.dispatchEvent(new MouseEvent('mousedown',{bubbles:true,button:0}));sizes.focus();
                      expect(sizes.value==='24','Opening dropdown reset 24 to '+sizes.value);
                      expect(document.querySelector('[data-select=font]').value==='Georgia','Font display was reset');
                      expect(range===JSON.stringify(command.getRange()),'Opening dropdown changed the selection');
                      await choose('16');expect(hasSize(16),'24 to 16 did not apply');
                      await choose('18');expect(hasSize(18),'16 to 18 did not apply');
                      await choose('16');expect(hasSize(16),'18 to 16 did not apply');
                      window.canvasEditor.loadDocument(window.canvasEditor.document());await tick();
                      expect(hasSize(16),'Size 16 did not survive native document reload');
                      window.canvasEditor.load(JSON.stringify({main:[{value:'Pasted text',size:13}]}));
                      command.executeSelectAll();await tick();
                      expect(sizes.value==='13','Non-preset pasted size displayed '+sizes.value);
                      await choose('16');expect(hasSize(16),'Pasted size 13 to 16 did not apply');
                      expect(!sizes.querySelector('[data-current-size]'),'Temporary pasted size was not removed');
                    })().catch(error=>{window.sizeTestError=String(error);}).finally(()=>{window.sizeTestDone=true;});
                    """);
                // Background WebKit can throttle timers; wait for completion instead of a fixed sleep.
                var checks=new AtomicInteger();var pending=new javafx.animation.Timeline();
                pending.getKeyFrames().add(new javafx.animation.KeyFrame(javafx.util.Duration.millis(100),event->{
                    boolean complete=Boolean.TRUE.equals(engine.executeScript("window.sizeTestDone"));
                    if(!complete && checks.incrementAndGet()<150)return;
                    pending.stop();
                    try{assertTrue(complete,"Canvas size checks did not finish");assertNull(engine.executeScript("window.sizeTestError"));}
                    catch(Throwable error){failure.set(error);}
                    finally{stage.get().close();finished.countDown();}
                }));pending.setCycleCount(javafx.animation.Animation.INDEFINITE);pending.play();
            }catch(Throwable error){failure.set(error);stage.get().close();finished.countDown();}
        });
        assertTrue(finished.await(20,TimeUnit.SECONDS));
        if(failure.get()!=null)throw new AssertionError("Font size dropdown failed",failure.get());
    }

    @Test void justifyToolbarFillsWrappedLinesButKeepsLastLineNaturalAfterReload() throws Exception {
        var loaded=new CountDownLatch(1);var finished=new CountDownLatch(1);
        var failure=new AtomicReference<Throwable>();var web=new AtomicReference<WebView>();var stage=new AtomicReference<Stage>();
        Platform.runLater(()->{
            web.set(new WebView());stage.set(new Stage());stage.get().setOpacity(0);
            stage.get().setScene(new Scene(web.get(),1000,700));stage.get().show();
            web.get().getEngine().getLoadWorker().stateProperty().addListener((o,a,b)->{
                if(b==Worker.State.SUCCEEDED || b==Worker.State.FAILED)loaded.countDown();
            });
            web.get().getEngine().load(new CanvasEditorPageLocator(null).editorUrl());
        });
        assertTrue(loaded.await(30,TimeUnit.SECONDS));
        Platform.runLater(()->{
            try {
                var engine=web.get().getEngine();assertEquals(true,engine.executeScript("window.canvasEditor.ready()"));
                // Observe actual Canvas text positions, including the troublesome short final row.
                engine.executeScript("""
                    window.painted=[];
                    window.originalFillText=CanvasRenderingContext2D.prototype.fillText;
                    CanvasRenderingContext2D.prototype.fillText=function(text,x,y,...rest){
                      if(this.canvas.closest('#paper') && text!=='\\u200b')painted.push({text,x,y,width:this.measureText(text).width});
                      return originalFillText.call(this,text,x,y,...rest);
                    };
                    window.rowWidths=()=>{
                      const ys=[...new Set(painted.map(g=>g.y))].sort((a,b)=>a-b);
                      return ys.map(y=>{const row=painted.filter(g=>g.y===y);return Math.max(...row.map(g=>g.x+g.width))-Math.min(...row.map(g=>g.x));});
                    };
                    window.canvasEditor.load(JSON.stringify({main:[{value:'Reading opens a window to the world. '.repeat(5)+'experience beauty.'}]}));
                    window.__quizforgeCanvasState.editor.command.executeSelectAll();
                    painted=[];window.canvasEditor.command('align','left');window.leftWidths=rowWidths();
                    painted=[];document.querySelector('[title="两端对齐"]').click();window.alignedWidths=rowWidths();
                    """);
                assertTrue(((Number)engine.executeScript("alignedWidths.length")).intValue()>=2);
                double inner=((Number)engine.executeScript("(()=>{const o=window.__quizforgeCanvasState.editor.command.getOptions();return (o.width-o.margins[1]-o.margins[3])*o.scale;})()")).doubleValue();
                double first=((Number)engine.executeScript("alignedWidths[0]")).doubleValue();
                double last=((Number)engine.executeScript("alignedWidths[alignedWidths.length-1]")).doubleValue();
                double naturalLast=((Number)engine.executeScript("leftWidths[leftWidths.length-1]")).doubleValue();
                assertEquals(inner,first,2,"Wrapped lines fill the available width");
                assertEquals(naturalLast,last,0.5,"The last line keeps natural spacing");
                assertTrue(last<inner*0.8,"The short final row must not stretch across the page");
                assertTrue(Boolean.TRUE.equals(engine.executeScript("JSON.parse(window.canvasEditor.value()).main.some(e=>e.rowFlex==='alignment')")));
                engine.executeScript("window.savedAlignment=window.canvasEditor.document();painted=[];window.canvasEditor.loadDocument(savedAlignment);window.reloadedWidths=rowWidths();");
                assertEquals(last,((Number)engine.executeScript("reloadedWidths[reloadedWidths.length-1]")).doubleValue(),0.5);
            }catch(Throwable error){failure.set(error);}
            finally{stage.get().close();finished.countDown();}
        });
        assertTrue(finished.await(20,TimeUnit.SECONDS));
        if(failure.get()!=null)throw new AssertionError("Two-end alignment rendering failed",failure.get());
    }

    @Test void answerChangesAutosaveWithoutSaveClickAndBackFlushesFinalChange() throws Exception {
        var loaded=new CountDownLatch(1);var automatic=new CountDownLatch(1);var finished=new CountDownLatch(1);
        var failure=new AtomicReference<Throwable>();var window=new AtomicReference<CanvasEditorWindow>();
        var latest=new AtomicReference<String>();var saves=new AtomicInteger();
        Platform.runLater(()->{
            window.set(new CanvasEditorWindow(null,"编辑作答",new TextContent("Original"),List.of(),resource->null));
            window.get().saveDraftsTo(result->{
                latest.set(window.get().session().document((DocumentContent)result.content()));saves.incrementAndGet();automatic.countDown();
            });
            window.get().stage().setOpacity(0);window.get().stage().show();
            window.get().bridge().view().getEngine().getLoadWorker().stateProperty().addListener((o,a,b)->{
                if(b==Worker.State.SUCCEEDED || b==Worker.State.FAILED)loaded.countDown();
            });
        });
        assertTrue(loaded.await(30,TimeUnit.SECONDS));
        Platform.runLater(()->{
            try{
                assertTrue(window.get().bridge().ready());assertEquals(0,saves.get(),"Loading is not an answer edit");
                window.get().bridge().view().getEngine().executeScript("window.canvasEditor.insertElement('[{\"value\":\" Auto draft\"}]')");
            }catch(Throwable error){failure.set(error);automatic.countDown();}
        });
        assertTrue(automatic.await(10,TimeUnit.SECONDS));
        Platform.runLater(()->{
            try{
                if(failure.get()!=null)throw new AssertionError(failure.get());
                assertTrue(latest.get().contains("Auto draft"));assertTrue(window.get().stage().isShowing());
                int prior=saves.get();
                window.get().bridge().view().getEngine().executeScript("window.canvasEditor.insertElement('[{\"value\":\" Final change\"}]')");
                ((javafx.scene.control.Button)window.get().stage().getScene().lookup("#canvas-editor-back")).fire();
                assertFalse(window.get().stage().isShowing());assertTrue(saves.get()>prior);
                assertTrue(latest.get().contains("Final change"));
            }catch(Throwable error){failure.set(error);}
            finally{window.get().stage().close();finished.countDown();}
        });
        assertTrue(finished.await(20,TimeUnit.SECONDS));
        if(failure.get()!=null)throw new AssertionError("Answer autosave failed",failure.get());
    }

    @Test void zoomKeepsPaperAndTextTogetherAndBothEdgesReachable() throws Exception {
        var loaded = new CountDownLatch(1);var finished = new CountDownLatch(1);
        var web = new AtomicReference<WebView>();var stage = new AtomicReference<Stage>();
        var failure = new AtomicReference<Throwable>();
        Platform.runLater(() -> {
            web.set(new WebView());stage.set(new Stage());stage.get().setOpacity(0);
            stage.get().setScene(new Scene(web.get(),1000,700));stage.get().show();
            web.get().getEngine().getLoadWorker().stateProperty().addListener((o,a,b) -> {
                if (b == Worker.State.SUCCEEDED || b == Worker.State.FAILED) loaded.countDown();
            });
            web.get().getEngine().load(new CanvasEditorPageLocator(null).editorUrl());
        });
        assertTrue(loaded.await(30,TimeUnit.SECONDS));
        Platform.runLater(() -> {
            try {
                var engine = web.get().getEngine();var api = (JSObject)engine.executeScript("window.canvasEditor");
                assertEquals(true,api.call("ready"));
                api.call("load","{\"main\":[{\"value\":\"Zoom keeps the document intact.\"}]}");
                engine.executeScript("window.zoomCommand=window.__quizforgeCanvasState.editor.command;zoomCommand.executeSetRange(5,5);window.originalCaret=zoomCommand.getCursorPosition().coordinate.rightTop[0];window.originalHeight=document.querySelector('#paper canvas').getBoundingClientRect().height;window.originalData=JSON.stringify(zoomCommand.getValue().data)");
                for (double scale : new double[]{0.75,1,1.25,1.5}) {
                    engine.executeScript("zoomCommand.executePageScale("+scale+")");
                    assertEquals(816*scale,((Number)engine.executeScript("document.getElementById('paper').getBoundingClientRect().width")).doubleValue(),1);
                    assertEquals(816*scale,((Number)engine.executeScript("document.querySelector('#paper canvas').getBoundingClientRect().width")).doubleValue(),1);
                    assertEquals(scale,((Number)engine.executeScript("document.querySelector('#paper canvas').getBoundingClientRect().height/originalHeight")).doubleValue(),0.02);
                    assertEquals(scale,((Number)engine.executeScript("zoomCommand.getCursorPosition().coordinate.rightTop[0]/originalCaret")).doubleValue(),0.02);
                    assertEquals(true,engine.executeScript("originalData===JSON.stringify(zoomCommand.getValue().data)"));
                    assertEquals(0,((Number)engine.executeScript("(()=>{const p=document.getElementById('paper').getBoundingClientRect(),w=document.getElementById('workspace');return p.left+p.width/2-w.getBoundingClientRect().left-w.clientWidth/2})()")).doubleValue(),1);
                    if (scale > 1) {
                        assertEquals(true,engine.executeScript("document.getElementById('workspace').scrollWidth>document.getElementById('workspace').clientWidth"));
                        assertEquals("auto",engine.executeScript("getComputedStyle(document.getElementById('workspace')).overflowX"));
                        assertEquals(24,((Number)engine.executeScript("(()=>{const w=document.getElementById('workspace');w.scrollLeft=0;return document.getElementById('paper').getBoundingClientRect().left-w.getBoundingClientRect().left})()")).doubleValue(),1);
                        assertEquals(24,((Number)engine.executeScript("(()=>{const w=document.getElementById('workspace');w.scrollLeft=w.scrollWidth;return w.getBoundingClientRect().left+w.clientWidth-document.getElementById('paper').getBoundingClientRect().right})()")).doubleValue(),1);
                    }
                }
                assertEquals("none",engine.executeScript("getComputedStyle(document.getElementById('workspace')).backgroundImage"));
                assertEquals("rgb(242, 244, 247)",engine.executeScript("getComputedStyle(document.getElementById('workspace')).backgroundColor"));
                assertEquals("none",engine.executeScript("getComputedStyle(document.getElementById('workspace'),'::after').content"));
                assertEquals("none",engine.executeScript("getComputedStyle(document.querySelector('.ce-magnifier')).display"));
                engine.executeScript("zoomCommand.executePageScale(1);document.getElementById('workspace').dispatchEvent(new WheelEvent('wheel',{ctrlKey:true,deltaY:-100,bubbles:true,cancelable:true}))");
                assertEquals(1.05,((Number)engine.executeScript("zoomCommand.getOptions().scale")).doubleValue(),0.001);
                engine.executeScript("for(let i=0;i<30;i++)document.getElementById('workspace').dispatchEvent(new WheelEvent('wheel',{ctrlKey:true,deltaY:-100,bubbles:true,cancelable:true}))");
                assertEquals(1.5,((Number)engine.executeScript("zoomCommand.getOptions().scale")).doubleValue(),0.001);
                engine.executeScript("for(let i=0;i<30;i++)document.getElementById('workspace').dispatchEvent(new WheelEvent('wheel',{ctrlKey:true,deltaY:100,bubbles:true,cancelable:true}))");
                assertEquals(0.75,((Number)engine.executeScript("zoomCommand.getOptions().scale")).doubleValue(),0.001);
            } catch (Throwable e) { failure.set(e); }
            finally { stage.get().close();finished.countDown(); }
        });
        assertTrue(finished.await(20,TimeUnit.SECONDS));
        if (failure.get()!=null) throw new AssertionError("Canvas zoom layout failed",failure.get());
    }

    @Test void saveUsesCurrentEditorApiAndRetainsRepeatedChineseText() throws Exception {
        var loaded=new CountDownLatch(1);var finished=new CountDownLatch(1);
        var failure=new AtomicReference<Throwable>();var window=new AtomicReference<CanvasEditorWindow>();
        Platform.runLater(()->{
            window.set(new CanvasEditorWindow(null,"参考答案与解析",new TextContent("测试\n".repeat(35)),List.of(),resource->null));
            window.get().stage().setOpacity(0);window.get().stage().show();
            window.get().bridge().view().getEngine().getLoadWorker().stateProperty().addListener((o,a,b)->{
                if(b==Worker.State.SUCCEEDED || b==Worker.State.FAILED)loaded.countDown();
            });
        });
        assertTrue(loaded.await(30,TimeUnit.SECONDS));
        Platform.runLater(()->{
            try {
                var bridge=window.get().bridge();assertTrue(bridge.ready());var engine=bridge.view().getEngine();
                String text=(String)engine.executeScript("window.canvasEditor.text()");
                engine.executeScript("window.oldApi=window.canvasEditor;window.canvasEditor=Object.assign({},oldApi);oldApi.empty=oldApi.document=oldApi.text=()=>{throw new Error('Invalid function reference')}");
                var content=(DocumentContent)bridge.getContent();
                assertEquals(text,content.text());assertTrue(text.lines().count()>=35);
                var json=new com.fasterxml.jackson.databind.ObjectMapper();
                assertEquals(json.readTree((String)engine.executeScript("window.canvasEditor.document()")),json.readTree(window.get().session().document(content)));
                ((javafx.scene.control.Button)window.get().stage().getScene().lookup("#canvas-editor-save")).fire();
                assertFalse(window.get().stage().isShowing(),"Save must succeed and close the editor");
            }catch(Throwable e){failure.set(e);}finally{window.get().stage().close();finished.countDown();}
        });
        assertTrue(finished.await(20,TimeUnit.SECONDS));
        if(failure.get()!=null)throw new AssertionError("Save after editor API replacement failed",failure.get());
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
    void pastedImagePreservesExistingTextAndPunctuationCaretMatchesDrawing(boolean httpPage) throws Exception {
        var loaded=new CountDownLatch(1);var finished=new CountDownLatch(1);
        var web=new AtomicReference<WebView>();var stage=new AtomicReference<Stage>();
        var failure=new AtomicReference<Throwable>();
        byte[] png=io.quizforge.infrastructure.testing.EssayTestBanks.image("png");
        var payload=ContentJson.write(Map.of("text","","html","","image",Map.of(
                "value","data:image/png;base64,"+java.util.Base64.getEncoder().encodeToString(png),"width",1024,"height",768)));
        var host=new CanvasEditorImageHost(()->{},()->{},message->{throw new AssertionError(message);},()->payload,(text,html)->true);
        var server=httpPage?com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0):null;
        if(server!=null) {
            server.createContext("/",exchange->{
                String name=exchange.getRequestURI().getPath().substring(1);
                if(name.isEmpty())name="canvas-editor.html";
                try(var resource=getClass().getResourceAsStream("/editor/canvas/" + name)) {
                    if(resource==null) {exchange.sendResponseHeaders(404,-1);return;}
                    byte[] bytes=resource.readAllBytes();
                    exchange.getResponseHeaders().set("Content-Type",name.endsWith(".js")?"text/javascript":name.endsWith(".css")?"text/css":"text/html");
                    exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);
                }finally{exchange.close();}
            });server.start();
        }
        String pageUrl=server==null?new CanvasEditorPageLocator(null).editorUrl():"http://127.0.0.1:"+server.getAddress().getPort()+"/?contentWidth=672";
        try {
        Platform.runLater(()->{
            web.set(new WebView());stage.set(new Stage());stage.get().setOpacity(0);
            stage.get().setScene(new Scene(web.get(),1000,700));stage.get().show();web.get().requestFocus();
            web.get().getEngine().getLoadWorker().stateProperty().addListener((o,a,b)->{
                if(b==Worker.State.SUCCEEDED || b==Worker.State.FAILED)loaded.countDown();
            });
            web.get().getEngine().load(pageUrl);
        });
        assertTrue(loaded.await(30,TimeUnit.SECONDS));
        Platform.runLater(()->{
            try {
                var engine=web.get().getEngine();var api=(JSObject)engine.executeScript("window.canvasEditor");
                ((JSObject)engine.executeScript("window")).setMember("quizforgeHost",host);
                assertEquals(true,engine.executeScript("(()=>{const c=document.createElement('canvas').getContext('2d');c.font='bold 16px Arial';c.save();c.font='16px Arial';c.restore();return c.font.includes('bold')})()"));
                engine.executeScript("window.drawCalls=[];window.pageErrors=[];window.addEventListener('error',e=>pageErrors.push(e.message));window.originalFill=CanvasRenderingContext2D.prototype.fillText;CanvasRenderingContext2D.prototype.fillText=function(text,x,y){drawCalls.push({text,x,y,font:this.font,width:this.measureText(text).width});return originalFill.apply(this,arguments)}");
                String text="Write an essay based on the charts below. In your essay, you should";
                var rich=new RichContent(new RichDocument(List.of(
                        new ParagraphNode(List.of(new InlineTextNode("Part B",List.of(TextMark.BOLD)))),
                        new ParagraphNode(List.of(new InlineTextNode("52. Directions:",List.of(TextMark.BOLD)))),
                        new ParagraphNode(List.of(new InlineTextNode(text))),
                        new ParagraphNode(List.of(new InlineTextNode("1) describe the charts briefly,"))))));
                api.call("load",CanvasEditorAdapter.toCanvasJson(rich,Map.of()));
                assertEquals(true,engine.executeScript("drawCalls.find(c=>c.text==='Part B').font.includes('bold')"));
                assertEquals(0,((Number)engine.executeScript("(()=>{const a=drawCalls.find(c=>c.text.includes('charts below.'));const b=drawCalls.find(c=>c.text===' In your essay,');return b.x-a.x-a.width})()")).doubleValue(),1);
                engine.executeScript("window.__quizforgeCanvasState.editor.command.executeSetRange(64,64)");
                keyboard(web.get(),javafx.scene.input.KeyCode.BACK_SPACE,false,false);
                assertTrue(((String)api.call("text")).contains("below In your essay,"));
                api.call("load",ContentJson.write(Map.of("main",List.of(Map.of("value","Write an essay based on the charts below.")))));
                engine.executeScript("window.__quizforgeCanvasState.editor.command.executeSetRange(41,41);window.inkRight=()=>{const canvas=document.querySelector('#paper canvas'),r=canvas.width/816,ctx=canvas.getContext('2d'),p=ctx.getImageData(0,70*r,canvas.width,38*r);let right=0;for(let y=0;y<p.height;y++)for(let x=0;x<p.width;x++){let i=(y*p.width+x)*4;if(p.data[i+3]>128&&p.data[i]<80&&p.data[i+1]<80&&p.data[i+2]<80)right=Math.max(right,x)}return right/r}");
                assertEquals(0,((Number)engine.executeScript("window.__quizforgeCanvasState.editor.command.getCursorPosition().coordinate.rightTop[0]-inkRight()")).doubleValue(),3);
                keyboard(web.get(),javafx.scene.input.KeyCode.BACK_SPACE,false,false);
                assertEquals(0,((Number)engine.executeScript("window.__quizforgeCanvasState.editor.command.getCursorPosition().coordinate.rightTop[0]-inkRight()")).doubleValue(),3);
                api.call("load",ContentJson.write(Map.of("main",List.of(Map.of("value",text)))));
                engine.executeScript("window.__quizforgeCanvasState.editor.command.executeSetRange("+text.length()+","+text.length()+");document.querySelector('.ce-inputarea').focus()");
                // A selection saved by an earlier/cancelled image chooser must not be reused.
                engine.executeScript("window.__quizforgeCanvasState.imageRange={startIndex:0,endIndex:"+text.length()+"}");
                keyboard(web.get(),javafx.scene.input.KeyCode.V,false,true);
                assertEquals(text,api.call("text"));
                var delay=new javafx.animation.PauseTransition(javafx.util.Duration.millis(500));
                delay.setOnFinished(event->{
                    try {
                        assertEquals(text,api.call("text"));
                        assertEquals("[]",engine.executeScript("JSON.stringify(pageErrors)"));
                        assertEquals(1,((Number)engine.executeScript("window.__quizforgeCanvasState.editor.command.getValue().data.main.filter(e=>e.type==='image').length")).intValue());
                        assertEquals(true,engine.executeScript("(()=>{const c=document.querySelector('#paper canvas'),p=c.getContext('2d').getImageData(0,0,c.width,c.height).data;for(let i=0;i<p.length;i+=4)if(p[i]<40&&p[i+1]<40&&p[i+2]>200&&p[i+3]>200)return true;return false})()"),"Pasted picture must be painted, not just present in JSON");
                    }catch(Throwable e){failure.set(e);}finally{stage.get().close();finished.countDown();}
                });delay.play();
            }catch(Throwable e){failure.set(e);stage.get().close();finished.countDown();}
        });
        assertTrue(finished.await(20,TimeUnit.SECONDS));
        if(failure.get()!=null)throw new AssertionError("Canvas paste/layout regression",failure.get());
        }finally{if(server!=null)server.stop(0);}
    }

    @Test void nativeClipboardShortcutsPasteTextAndPreserveRichSelection() throws Exception {
        var loaded=new CountDownLatch(1);var finished=new CountDownLatch(1);
        var web=new AtomicReference<WebView>();var stage=new AtomicReference<Stage>();
        var failure=new AtomicReference<Throwable>();
        var clipboard=new AtomicReference<>(Map.of("text","Line 1\r\n中文第二行","html",""));
        var host=new CanvasEditorImageHost(()->{},()->{},message->{throw new AssertionError(message);},
                ()->ContentJson.write(clipboard.get()),(text,html)->{clipboard.set(Map.of("text",text,"html",html));return true;});
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
                ((JSObject)engine.executeScript("window")).setMember("quizforgeHost",host);
                api.call("load","{\"main\":[{\"value\":\"\"}]}");
                engine.executeScript("window.__quizforgeCanvasState.editor.command.executeSetRange(0,0);document.querySelector('.ce-inputarea').focus()");
                keyboard(web.get(),javafx.scene.input.KeyCode.V,false,true);
                assertEquals("Line 1\n中文第二行",QuestionContentData.plainText(CanvasEditorAdapter.fromCanvasJson((String)api.call("value"),Set.of())));
                var bold=new RichContent(new RichDocument(List.of(new ParagraphNode(List.of(new InlineTextNode("Bold",List.of(TextMark.BOLD)))))));
                api.call("load",CanvasEditorAdapter.toCanvasJson(bold,Map.of()));
                keyboard(web.get(),javafx.scene.input.KeyCode.A,false,true);keyboard(web.get(),javafx.scene.input.KeyCode.C,false,true);
                assertEquals("Bold",clipboard.get().get("text"));assertTrue(clipboard.get().get("html").contains("Bold"));
                api.call("load","{\"main\":[{\"value\":\"\"}]}");
                engine.executeScript("window.__quizforgeCanvasState.editor.command.executeSetRange(0,0)");
                keyboard(web.get(),javafx.scene.input.KeyCode.V,false,true);
                assertEquals(bold,CanvasEditorAdapter.fromCanvasJson((String)api.call("value"),Set.of()));
                keyboard(web.get(),javafx.scene.input.KeyCode.A,false,true);keyboard(web.get(),javafx.scene.input.KeyCode.X,false,true);
                assertEquals("",QuestionContentData.plainText(CanvasEditorAdapter.fromCanvasJson((String)api.call("value"),Set.of())));
                assertEquals("Bold",clipboard.get().get("text"));
                api.call("mode","readonly");keyboard(web.get(),javafx.scene.input.KeyCode.V,false,true);
                assertEquals("",QuestionContentData.plainText(CanvasEditorAdapter.fromCanvasJson((String)api.call("value"),Set.of())));
                api.call("mode","edit");
                engine.executeScript("window.__quizforgeCanvasState.editor.command.executeSetRange(0,0);document.querySelector('.ce-inputarea').focus()");
                clipboard.set(Map.of("text","External text","html","<p><strong>External</strong> text</p>"));
                keyboard(web.get(),javafx.scene.input.KeyCode.V,false,true);
                assertEquals("External text",api.call("text"));
                assertTrue((Boolean)engine.executeScript("window.__quizforgeCanvasState.editor.command.getValue().data.main.some(e=>e.bold)"));
                api.call("load","{\"main\":[{\"value\":\"\"}]}");
                engine.executeScript("window.__quizforgeCanvasState.editor.command.executeSetRange(0,0)");
                keyboard(web.get(),javafx.scene.input.KeyCode.V,true,true);
                assertEquals(new TextContent("External text"),CanvasEditorAdapter.fromCanvasJson((String)api.call("value"),Set.of()));
            }catch(Throwable e){failure.set(e);}finally{stage.get().close();finished.countDown();}
        });
        assertTrue(finished.await(20,TimeUnit.SECONDS));
        if(failure.get()!=null)throw new AssertionError("Canvas clipboard failed",failure.get());
    }

    @Test void nativeFilePreservesBreaksFormattingPastedImagesAndDraggedDimensions() throws Exception {
        var loaded=new CountDownLatch(1);var finished=new CountDownLatch(1);
        var failure=new AtomicReference<Throwable>();var window=new AtomicReference<CanvasEditorWindow>();
        byte[] png=io.quizforge.infrastructure.testing.EssayTestBanks.image("png");
        var imageFile=java.nio.file.Files.createTempFile("canvas-native-picture-",".png");java.nio.file.Files.write(imageFile,png);
        var clipboard=new AtomicReference<>("{}");
        var host=new CanvasEditorImageHost(()->{},()->{},message->{throw new AssertionError(message);},clipboard::get,(text,html)->true);
        Platform.runLater(()->{
            window.set(new CanvasEditorWindow(null,"Native",new TextContent("First\n\nSecond\nThird"),List.of(),resource->null));
            window.get().stage().setOpacity(0);window.get().stage().show();
            window.get().bridge().view().getEngine().getLoadWorker().stateProperty().addListener((o,a,b)->{
                if(b==Worker.State.SUCCEEDED || b==Worker.State.FAILED)loaded.countDown();
            });
        });
        assertTrue(loaded.await(30,TimeUnit.SECONDS));
        Platform.runLater(()->{
            try {
                var bridge=window.get().bridge();assertTrue(bridge.ready());var engine=bridge.view().getEngine();
                var api=(JSObject)engine.executeScript("window.canvasEditor");
                assertEquals("First\n\nSecond\nThird",api.call("text"));
                engine.executeScript("window.__quizforgeCanvasState.editor.command.executeSelectAll()");
                api.call("command","font","Georgia");api.call("command","size","24");api.call("command","color","#ff0000");
                var imported=window.get().session().stage(imageFile);bridge.insertImage(imported);
                engine.executeScript("var canvas=document.querySelector('#paper canvas');var rect=canvas.getBoundingClientRect();"
                        +"for(var y=74;y<180;y+=4){canvas.dispatchEvent(new MouseEvent('mousedown',{bubbles:true,clientX:rect.left+80,clientY:rect.top+y}));"
                        +"canvas.dispatchEvent(new MouseEvent('mouseup',{bubbles:true,clientX:rect.left+80,clientY:rect.top+y}));"
                        +"if(document.querySelector('.ce-resizer-selection').style.display==='block')break;}"
                        +"document.querySelector('.resizer-handle.handle-4').dispatchEvent(new MouseEvent('mousedown',{bubbles:true,clientX:20,clientY:20}));"
                        +"document.dispatchEvent(new MouseEvent('mousemove',{bubbles:true,clientX:50,clientY:50}));document.dispatchEvent(new MouseEvent('mouseup',{bubbles:true}));");
                String saved=(String)api.call("document");var json=new com.fasterxml.jackson.databind.ObjectMapper();
                var tree=json.readTree(saved);var image=java.util.stream.StreamSupport.stream(tree.path("data").path("main").spliterator(),false)
                        .filter(e->"image".equals(e.path("type").asText())).findFirst().orElseThrow();
                assertTrue(image.path("width").asDouble()>24);assertTrue(image.path("height").asDouble()>16);
                var content=(DocumentContent)bridge.getContent();var result=window.get().session().save(content);
                assertEquals(1,result.addedResources().size());assertEquals(ResourceKind.DOCUMENT,result.addedResources().getFirst().resource().kind());
                bridge.loadContent(content);assertEquals(tree.path("data"),json.readTree((String)api.call("document")).path("data"));
                api.call("load","{\"main\":[{\"value\":\"First\\n\\nSecond\\nThird\",\"font\":\"Georgia\",\"size\":24,\"color\":\"#ff0000\"}]}");
                saved=(String)api.call("document");
                for(int i=0;i<3;i++)api.call("loadDocument",saved);
                assertEquals("First\n\nSecond\nThird",api.call("text"));
                assertEquals(json.readTree(saved).path("data"),json.readTree((String)api.call("document")).path("data"));
                api.call("load","{\"main\":[{\"value\":\"\"}]}");
                ((JSObject)engine.executeScript("window")).setMember("quizforgeHost",host);
                clipboard.set(ContentJson.write(Map.of("text","","html","","image",Map.of("value","data:image/png;base64,"+java.util.Base64.getEncoder().encodeToString(png),"width",24,"height",16))));
                keyboard(bridge.view(),javafx.scene.input.KeyCode.V,false,true);
                assertTrue(((String)api.call("document")).contains("data:image/png;base64,"));
            }catch(Throwable e){failure.set(e);}finally{window.get().stage().close();finished.countDown();}
        });
        assertTrue(finished.await(25,TimeUnit.SECONDS));java.nio.file.Files.deleteIfExists(imageFile);
        if(failure.get()!=null)throw new AssertionError("Native Canvas document failed",failure.get());
    }

    @Test void nativePreviewUsesReadonlyCanvasWithNaturalContentHeight() throws Exception {
        var loaded=new CountDownLatch(1);var checked=new CountDownLatch(1);var failure=new AtomicReference<Throwable>();
        var web=new AtomicReference<WebView>();var stage=new AtomicReference<Stage>();
        String source=ContentJson.write(Map.of("version","1.0.4","options",Map.of("width",816,"height",640,
                "margins",List.of(72,72,72,72),"pageMode","continuity","defaultFont","Arial","defaultSize",16),
                "data",Map.of("main",List.of(Map.of("value","First\n\nSecond\n".repeat(8),"font","Georgia","size",24,"color","#ff0000"),
                        Map.of("type","table","value","\n","colgroup",java.util.stream.IntStream.range(0,5).mapToObj(i->Map.of("width",134.4)).toList(),
                                "trList",List.of(Map.of("height",42,"tdList",java.util.stream.IntStream.range(0,5).mapToObj(i->Map.of("colspan",1,"rowspan",1,"value",List.of(Map.of("value",(i+1)+". A")))).toList()))),
                        Map.of("type","image","value","data:image/png;base64,"+java.util.Base64.getEncoder().encodeToString(io.quizforge.infrastructure.testing.EssayTestBanks.image("png")),
                                "width",672,"height",420,"imgDisplay","block")))));
        var session=new ContentEditSession(new TextContent(""),List.of(),resource->null);
        var content=session.stageDocument(source,"First\n\nSecond");
        Platform.runLater(()->{
            var view=new CanvasDocumentView(content,session.resources(),session::open,"test-");
            stage.set(new Stage());stage.get().setOpacity(0);
            var body=new javafx.scene.layout.VBox(12,view,new javafx.scene.control.Label("Below the expanded preview"));
            stage.get().setScene(new Scene(UiTheme.scroll(body),600,360));
            // ScrollPane attaches its content when its skin is created on showing.
            stage.get().show();
            web.set((WebView)view.getChildren().getFirst());
            web.get().getEngine().getLoadWorker().stateProperty().addListener((o,a,b)->{
                if(b==Worker.State.SUCCEEDED || b==Worker.State.FAILED)loaded.countDown();
            });
        });
        assertTrue(loaded.await(30,TimeUnit.SECONDS));
        Platform.runLater(()->{
            var delay=new javafx.animation.PauseTransition(javafx.util.Duration.millis(300));
            delay.setOnFinished(event->{
            try {
                var engine=web.get().getEngine();var api=(JSObject)engine.executeScript("window.canvasEditor");
                assertEquals(true,api.call("ready"));
                assertEquals("none",engine.executeScript("getComputedStyle(document.getElementById('toolbar')).display"));
                assertEquals("readonly",engine.executeScript("window.__quizforgeCanvasState.editor.command.getOptions().mode"));
                assertEquals(true,engine.executeScript("window.__quizforgeCanvasState.editor.command.getOptions().scale<1"));
                assertEquals(true,engine.executeScript("document.getElementById('paper').getBoundingClientRect().right<=document.documentElement.clientWidth+1"),"Full-width table and image must fit a narrow preview");
                assertEquals(674,((Number)engine.executeScript("window.__quizforgeCanvasState.editor.command.getOptions().width")).intValue());
                assertEquals(1,((Number)engine.executeScript("window.__quizforgeCanvasState.editor.command.getOptions().margins[1]")).intValue(),"Table border needs space inside the canvas");
                var json=new com.fasterxml.jackson.databind.ObjectMapper();
                var original=json.readTree(source).path("data").path("main");
                var rendered=json.readTree((String)api.call("value")).path("main");
                assertEquals(original.get(0),rendered.get(0));
                assertEquals(original.get(1).path("colgroup"),rendered.get(1).path("colgroup"));
                assertEquals(original.get(1).path("trList").get(0).path("tdList"),rendered.get(1).path("trList").get(0).path("tdList"));
                assertEquals(original.get(2).path("value"),rendered.get(2).path("value"));
                assertEquals(672,rendered.get(2).path("width").asDouble(),0.000001);
                assertEquals(420,rendered.get(2).path("height").asDouble(),0.000001);
                assertTrue(((Number)engine.executeScript("document.getElementById('paper').scrollHeight")).doubleValue()>200);
                double contentHeight=((Number)engine.executeScript("document.getElementById('paper').scrollHeight")).doubleValue();
                assertTrue(contentHeight>700,"Long text and image must expand beyond the outer viewport");
                assertTrue(web.get().getHeight()>=contentHeight,"JavaFX must not squeeze the preview into an internal scrolling viewport");
                var previewOverflow=engine.executeScript("CSS.supports('overflow','clip')?'clip':'hidden'");
                assertEquals(previewOverflow,engine.executeScript("getComputedStyle(document.documentElement).overflowY"));
                assertEquals(previewOverflow,engine.executeScript("getComputedStyle(document.body).overflowY"));
                assertEquals(true,engine.executeScript("document.getElementById('paper').getBoundingClientRect().bottom<=window.innerHeight+1"),
                        ()->String.valueOf(engine.executeScript("JSON.stringify({paper:document.getElementById('paper').getBoundingClientRect().bottom,viewport:window.innerHeight,documentHeight:document.documentElement.scrollHeight})")));
                var outer=(javafx.scene.control.ScrollPane)stage.get().getScene().getRoot();
                assertTrue(outer.getContent().getBoundsInLocal().getHeight()>outer.getViewportBounds().getHeight());
                assertEquals(0,((Number)engine.executeScript("window.__quizforgeCanvasState.editor.command.getOptions().margins[0]")).intValue());
            }catch(Throwable e){failure.set(e);}finally{stage.get().close();checked.countDown();}
            });delay.play();
        });
        assertTrue(checked.await(20,TimeUnit.SECONDS));if(failure.get()!=null)throw new AssertionError("Native preview failed",failure.get());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void localCanvasEditorInitializesInJavaFxWebView(boolean packagedJar) throws Exception {
        var packagedPage = getClass().getResource("/editor/canvas/canvas-editor.html");
        java.nio.file.Path jar = null;
        if (packagedJar) {
            jar = java.nio.file.Files.createTempFile("canvas-editor-offline-", ".jar");
            try (var zip = new java.util.zip.ZipOutputStream(java.nio.file.Files.newOutputStream(jar))) {
                for (var name : List.of("canvas-editor.html", "canvas-editor-bundle.js", "canvas-editor-bundle.css")) {
                    zip.putNextEntry(new java.util.zip.ZipEntry(name));
                    try (var asset = getClass().getResourceAsStream("/editor/canvas/" + name)) { assertNotNull(asset); asset.transferTo(zip); }
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
                    api.call("load",ContentJson.write(Map.of("main",List.of(Map.of("value",longText)))));
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
