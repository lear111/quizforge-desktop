package io.quizforge.desktop.ui.question.objective.cloze;

import io.quizforge.core.question.content.*;
import io.quizforge.core.question.model.*;
import io.quizforge.core.question.type.objective.cloze.*;
import io.quizforge.desktop.ui.content.document.canvas.ContentEditSession;
import io.quizforge.desktop.ui.shared.UiTheme;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.RadioButton;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class ClozeQuestionCardViewTest {
    @BeforeAll static void start()throws Exception{io.quizforge.desktop.testing.FxTestRuntime.start();}
    private static <T>T fx(Callable<T> action)throws Exception{
        var result=new CompletableFuture<T>();Platform.runLater(()->{try{result.complete(action.call());}catch(Throwable error){result.completeExceptionally(error);}});return result.get(15,TimeUnit.SECONDS);
    }
    private static void await(Callable<Boolean> condition)throws Exception{
        for(int i=0;i<100;i++){if(fx(condition))return;Thread.sleep(50);}fail("Canvas preview did not become ready");
    }
    private Question question(QuestionContent prompt){
        var counter=new AtomicInteger();var draft=new ClozeQuestionType().createDraft(prefix->prefix+counter.incrementAndGet(),List.of());
        var blank=((ClozePayload)draft.payload()).blanks().getFirst();
        var options=new ArrayList<io.quizforge.core.question.type.objective.choice.ChoiceOption>();
        for(int i=0;i<4;i++)options.add(new io.quizforge.core.question.type.objective.choice.ChoiceOption(blank.options().get(i).id(),new TextContent("选项 "+(char)('A'+i))));
        return new Question(draft.id(),"CLOZE",List.of(),prompt,new ClozePayload(List.of(new ClozeBlank(blank.id(),blank.number(),options))),draft.answerSpec(),draft.scoreSpec(),null,null,List.of());
    }
    @Test void outlineExpandsClozeBlanksInOrderAndJumpsToTheirParent() throws Exception {
        fx(()->{
            var model=new io.quizforge.core.question.service.QuestionBankEditorModel(new QuestionBank("qb_outline","Outline",List.of(),List.of(),List.of()));
            model.addQuestion("ESSAY");model.addQuestion("CLOZE");
            model.setStem(1,java.util.stream.IntStream.rangeClosed(1,20).mapToObj(n->"{{"+n+"}}").collect(java.util.stream.Collectors.joining(" "))+" {{1}}");
            model.addQuestion("SINGLE_CHOICE");model.addQuestion("CLOZE");
            var session=new io.quizforge.core.practice.QuestionBankPracticeSession(model.bank());
            var jump=new AtomicInteger(-1);
            var outline=new io.quizforge.desktop.ui.question.shared.QuestionOutlineView(session,jump::set,"authoring-question-");
            var stage=new Stage();stage.setOpacity(0);stage.setScene(new Scene(outline,280,650));stage.show();
            try {
            outline.refresh();assertEquals(23,outline.lookupAll(".question-number-cell").size());
            var group=(javafx.scene.layout.VBox)outline.lookup("#question-outline-cloze");
            assertEquals(20,((javafx.scene.layout.FlowPane)group.getChildren().get(1)).getChildren().size());
            for(int n=2;n<=21;n++){
                ((javafx.scene.control.Button)outline.lookup("#authoring-question-"+n)).fire();assertEquals(1,jump.get());
            }
            ((javafx.scene.control.Button)outline.lookup("#authoring-question-22")).fire();assertEquals(2,jump.get());
            ((javafx.scene.control.Button)outline.lookup("#authoring-question-23")).fire();assertEquals(3,jump.get());
            var blanks=((ClozePayload)model.bank().questions().get(1).payload()).blanks();
            session.next();session.select(blanks.get(0).options().get(0).id());session.select(blanks.get(1).options().get(1).id());outline.refresh();
            assertTrue(outline.lookup("#authoring-question-2").getStyleClass().contains("draft"));
            assertTrue(outline.lookup("#authoring-question-4").getStyleClass().contains("unsubmitted"));
            session.submit();session.next();outline.refresh();
            assertTrue(outline.lookup("#authoring-question-2").getStyleClass().contains("correct"));
            assertTrue(outline.lookup("#authoring-question-3").getStyleClass().contains("incorrect"));
            assertTrue(outline.lookup("#authoring-question-4").getStyleClass().contains("unsubmitted"));
            model.addClozeBlank(1);outline.showEditor(model.bank(),1,jump::set);
            outline.applyCss();outline.layout();
            assertEquals(24,outline.lookupAll(".question-number-cell").size());
            ((javafx.scene.control.Button)outline.lookup("#authoring-question-23")).fire();assertEquals(2,jump.get());
            } finally { stage.close(); }
            return null;
        });
    }
    @Test void editorFieldsBuildAndExposeFourEditableOptions()throws Exception{
        var stage=new AtomicReference<Stage>();var web=new AtomicReference<WebView>();var body=new javafx.scene.layout.VBox();
        var model=new io.quizforge.core.question.service.QuestionBankEditorModel(new QuestionBank("qb_editor","Cloze",List.of(),List.of(),List.of()));model.addQuestion("CLOZE");
        try{fx(()->{
            var navigation=new javafx.scene.layout.HBox();var spacer=new javafx.scene.layout.Region();navigation.getChildren().add(spacer);
            var context=new io.quizforge.desktop.ui.question.shared.QuestionEditorContext(model,0,body,navigation,spacer,new HashMap<>(),new ArrayList<>(),new javafx.scene.layout.VBox(),r->null,()->null,()->{});
            ClozeEditorFields.render(context);assertNotNull(body.lookup("#cloze-edit-prompt"));
            assertNotNull(body.lookup("#cloze-add-blank"));
            for(int i=0;i<4;i++)assertNotNull(body.lookup("#cloze-option-1-"+i));
            assertTrue(((RadioButton)body.lookup("#cloze-correct-1-0")).isSelected());
            ((javafx.scene.control.TextField)body.lookup("#cloze-option-1-0")).setText("went");
            assertEquals("went",QuestionContentData.plainText(((ClozePayload)model.bank().questions().getFirst().payload()).options().getFirst().content()));
            stage.set(new Stage());stage.get().setOpacity(0);stage.get().setScene(new Scene(UiTheme.scroll(body),760,650));stage.get().show();web.set((WebView)body.lookup("#canvas-editor-webview"));return null;
        });
        await(()->Boolean.TRUE.equals(web.get().getEngine().executeScript("document.querySelectorAll('.cloze-hit').length===1")));
        fx(()->{
            web.get().getEngine().executeScript("document.querySelector('.cloze-hit').click(); document.querySelectorAll('.cloze-popup-option')[2].click()");return null;
        });
        await(()->((RadioButton)body.lookup("#cloze-correct-1-2")).isSelected());
        fx(()->{
            var blank=((ClozePayload)model.bank().questions().getFirst().payload()).blanks().getFirst();
            assertEquals(blank.options().get(2).id(),((ClozeAnswerSpec)model.bank().questions().getFirst().answerSpec()).answers().getFirst().correctOptionId());
            var a=body.lookup("#cloze-option-1-0").localToScene(0,0);var d=body.lookup("#cloze-option-1-3").localToScene(0,0);
            assertEquals(a.getY(),d.getY(),0.1);assertTrue(d.getX()>a.getX());
            ((RadioButton)body.lookup("#cloze-correct-1-1")).fire();
            assertEquals(blank.options().get(1).id(),((ClozeAnswerSpec)model.bank().questions().getFirst().answerSpec()).answers().getFirst().correctOptionId());return null;
        });
        }finally{fx(()->{if(stage.get()!=null)stage.get().close();return null;});}
    }
    @Test void partialSubmissionRequiresConfirmationAndCancelPreservesSelections()throws Exception{
        fx(()->{
            var model=new io.quizforge.core.question.service.QuestionBankEditorModel(new QuestionBank("qb_submit","Cloze",List.of(),List.of(),List.of()));
            model.addQuestion("CLOZE");model.setStem(0,"{{1}} then {{2}}");
            var q=model.bank().questions().getFirst();var blanks=((ClozePayload)q.payload()).blanks();
            var selected=new AtomicReference<Set<String>>(Set.of(blanks.getFirst().options().getFirst().id()));
            var submitted=new AtomicBoolean();var header=new AtomicReference<String>();var cancelDefault=new AtomicBoolean();
            var card=new ClozeQuestionCardView(q,0,1,List.of(),r->null,"confirm-",selected::get,submitted::get,id->{},()->submitted.set(true),()->{},null);
            var stage=new Stage();stage.setOpacity(0);stage.setScene(new Scene(card,760,650));stage.show();
            try{
                var submit=(javafx.scene.control.Button)card.lookup("#confirm-cloze-submit");
                io.quizforge.desktop.testing.FxTestRuntime.answerSubmission("继续作答",pane->{
                    header.set(pane.getHeaderText());
                    var cancel=pane.getButtonTypes().stream().filter(type->"继续作答".equals(type.getText())).findFirst().orElseThrow();
                    cancelDefault.set(((javafx.scene.control.Button)pane.lookupButton(cancel)).isDefaultButton());
                });
                submit.fire();assertTrue(header.get().contains("1 道小题未作答"));assertTrue(cancelDefault.get());
                assertFalse(submitted.get());assertEquals(Set.of(blanks.getFirst().options().getFirst().id()),selected.get());assertFalse(submit.isDisabled());
                io.quizforge.desktop.testing.FxTestRuntime.acceptSubmission(submit);assertTrue(submitted.get());
                assertTrue(((RadioButton)card.lookup("#confirm-cloze-option-1-0")).isDisabled());
            }finally{stage.close();}
            return null;
        });
    }
    @Test void inlinePopupAndBottomOptionsSynchronizeRepeatedMarkersAndLockAfterSubmit()throws Exception{
        var selected=new AtomicReference<Set<String>>(Set.of());var submitted=new AtomicBoolean();var stage=new AtomicReference<Stage>();var web=new AtomicReference<WebView>();var card=new AtomicReference<ClozeQuestionCardView>();
        var q=question(new TextContent("Yesterday {{1}}. Repeat {{1}}. Literal \\{{1}}."));
        try{
            fx(()->{
                card.set(new ClozeQuestionCardView(q,0,1,List.of(),r->null,"test-",selected::get,submitted::get,id->selected.set(Set.of(id)),()->submitted.set(true),()->{submitted.set(false);selected.set(Set.of());},null));
                stage.set(new Stage());stage.get().setOpacity(0);var scene=new Scene(UiTheme.scroll(card.get()),760,650);UiTheme.apply(scene);stage.get().setScene(scene);stage.get().show();web.set((WebView)card.get().lookup("#canvas-editor-webview"));return null;
            });
            await(()->Boolean.TRUE.equals(web.get().getEngine().executeScript("!!window.canvasEditor && document.querySelectorAll('.cloze-hit').length===2")));
            fx(()->{
                var engine=web.get().getEngine();assertEquals(javafx.scene.paint.Color.TRANSPARENT,web.get().getPageFill());
                assertEquals("continuity",engine.executeScript("window.__quizforgeCanvasState.editor.command.getOptions().pageMode"));
                assertEquals("[0,1,0,1]",engine.executeScript("JSON.stringify(window.__quizforgeCanvasState.editor.command.getOptions().margins)"));
                assertEquals(true,engine.executeScript("window.__quizforgeCanvasState.editor.command.getOptions().pageNumber.disabled"));
                assertTrue(((String)engine.executeScript("window.__quizforgeCanvasState.editor.command.getText().main")).contains("1._______"));
                assertEquals("rgba(0, 0, 0, 0)",engine.executeScript("getComputedStyle(document.querySelector('canvas')).backgroundColor"));
                engine.executeScript("document.querySelector('.cloze-hit').click()");
                assertEquals(4,((Number)engine.executeScript("document.querySelectorAll('.cloze-popup-option').length")).intValue());
                engine.executeScript("document.querySelectorAll('.cloze-popup-option')[0].click()");return null;
            });
            await(()->!selected.get().isEmpty());
            fx(()->{
                var text=(String)web.get().getEngine().executeScript("window.__quizforgeCanvasState.editor.command.getText().main");
                assertEquals(2,text.split("选项 A",-1).length-1);assertTrue(text.contains("Literal {{1}}"));
                assertTrue(((RadioButton)card.get().lookup("#test-cloze-option-1-0")).isSelected());
                ((RadioButton)card.get().lookup("#test-cloze-option-1-1")).fire();return null;
            });
            fx(()->{
                var engine=web.get().getEngine();assertEquals(2,((String)engine.executeScript("window.__quizforgeCanvasState.editor.command.getText().main")).split("选项 B",-1).length-1);
                assertTrue(((String)engine.executeScript("JSON.stringify(window.__quizforgeCanvasState.clozeOriginal)")).contains("{{1}}"));
                io.quizforge.desktop.testing.FxTestRuntime.acceptSubmission((javafx.scene.control.Button)card.get().lookup("#test-cloze-submit"));
                assertTrue(((RadioButton)card.get().lookup("#test-cloze-option-1-1")).isDisabled());
                assertEquals(true,engine.executeScript("Array.from(document.querySelectorAll('.cloze-hit')).every(button=>button.disabled)"));
                ((javafx.scene.control.Button)card.get().lookup("#test-cloze-retry")).fire();assertTrue(selected.get().isEmpty());return null;
            });
        }finally{fx(()->{if(stage.get()!=null)stage.get().close();return null;});}
    }
    @Test void nativeDocumentMarkersAcrossFormattingRunsAreClickableWithoutChangingOriginal()throws Exception{
        var stage=new AtomicReference<Stage>();var web=new AtomicReference<WebView>();
        try{
            fx(()->{
                var session=new ContentEditSession(new TextContent(""),List.of(),r->null);
                var content=session.stageDocument("{\"version\":\"1.0.4\",\"options\":{\"defaultFont\":\"Arial\",\"defaultSize\":16,\"pageMode\":\"paging\",\"margins\":[100,100,100,100],\"marginIndicatorSize\":35,\"pageNumber\":{\"disabled\":false}},\"data\":{\"main\":[{\"value\":\"Read {{\"},{\"value\":\"1\",\"bold\":true},{\"value\":\"}}.\"}"+",{\"value\":\"\\nLong passage line.\"}".repeat(80)+",{\"value\":\"\\nEnd {{1}}.\"}]}}","Read {{1}} and {{1}}.");
                var q=question(content);var selected=new AtomicReference<Set<String>>(Set.of());
                var card=new ClozeQuestionCardView(q,0,1,session.resources(),session::open,"native-",selected::get,()->false,id->selected.set(Set.of(id)),()->{},()->{},null);
                stage.set(new Stage());stage.get().setOpacity(0);stage.get().setScene(new Scene(card,760,650));stage.get().show();web.set((WebView)card.lookup("#canvas-editor-webview"));return null;
            });
            await(()->web.get().getPrefHeight()>1000 && Boolean.TRUE.equals(web.get().getEngine().executeScript("document.querySelectorAll('.cloze-hit').length===2")));
            fx(()->{
                var engine=web.get().getEngine();assertEquals(2,((Number)engine.executeScript("window.__quizforgeCanvasState.clozeGroups.length")).intValue());
                assertEquals(1,((Number)engine.executeScript("document.querySelectorAll('.ce-page-container canvas').length")).intValue());
                assertEquals("continuity",engine.executeScript("window.__quizforgeCanvasState.editor.command.getOptions().pageMode"));
                assertEquals("[0,1,0,1]",engine.executeScript("JSON.stringify(window.__quizforgeCanvasState.editor.command.getOptions().margins)"));
                assertEquals(0,((Number)engine.executeScript("window.__quizforgeCanvasState.editor.command.getOptions().marginIndicatorSize")).intValue());
                double height=web.get().getPrefHeight();
                engine.executeScript("document.querySelectorAll('.cloze-hit')[1].click()");
                assertEquals(4,((Number)engine.executeScript("document.querySelectorAll('.cloze-popup-option').length")).intValue());
                assertEquals(true,engine.executeScript("document.getElementById('cloze-options-popup').getBoundingClientRect().bottom <= document.getElementById('paper').getBoundingClientRect().bottom"));
                assertEquals(true,engine.executeScript("document.getElementById('cloze-options-popup').getBoundingClientRect().top < document.querySelectorAll('.cloze-hit')[1].getBoundingClientRect().top"));
                assertEquals(height,web.get().getPrefHeight());
                return null;
            });
        }finally{fx(()->{if(stage.get()!=null)stage.get().close();return null;});}
    }
}
