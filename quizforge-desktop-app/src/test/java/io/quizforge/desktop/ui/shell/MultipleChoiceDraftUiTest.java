package io.quizforge.desktop.ui.shell;

import io.quizforge.core.practice.QuestionAttempt;
import io.quizforge.core.practice.draft.DraftCanvasDocument;
import io.quizforge.desktop.ui.question.practice.PracticeSurfaceHost;
import io.quizforge.desktop.ui.question.practice.PracticeSurfaceMode;
import io.quizforge.desktop.ui.question.history.HistorySurfaceHost;
import io.quizforge.desktop.ui.question.history.HistorySurfaceMode;
import io.quizforge.infrastructure.persistence.practice.*;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.control.CheckBox;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Real formal UI -> shared renderer -> Core -> SQLite -> frozen History replay. */
class MultipleChoiceDraftUiTest extends WorkspaceUiTestSupport {
    private static final DraftCanvasJsonCodec CODEC = new DraftCanvasJsonCodec();
    @Test void multipleSelectionsReopenSubmitRetryAndTwoFrozenAttemptsReplayThroughTheSameRenderer() throws Exception {
        String path=choiceBank("MULTIPLE_CHOICE","SINGLE_CHOICE");
        fx(()->openBank(path));var host=onFx(this::host);enter(host);
        assertEquals("MULTIPLE_CHOICE",script(host,"window.sharedPractice.getViewState().question.type"));
        assertEquals("MULTIPLE",script(host,"window.sharedPractice.getViewState().question.selectionMode"));
        assertEquals(4,((Number)script(host,"document.querySelectorAll('input[type=checkbox]').length")).intValue());
        assertEquals("[]",selected(host));
        choose(host,0,true);assertEquals("[\"opt_outline_0_0\"]",selected(host));
        choose(host,1,true);assertEquals("[\"opt_outline_0_0\",\"opt_outline_0_1\"]",selected(host));
        choose(host,0,false);assertEquals("[\"opt_outline_0_1\"]",selected(host));
        choose(host,2,true);load(host,ink("reopen"));leave(host);
        fx(()->{assertTrue(((CheckBox)shell.lookup("#option-1")).isSelected());assertTrue(((CheckBox)shell.lookup("#option-2")).isSelected());});
        enter(host);assertEquals(ink("reopen"),document(host));leave(host);
        fx(()->shell.tabs().closeAll());fx(()->openBank(path));host=onFx(this::host);enter(host);
        final var reopened=host;
        assertEquals("[\"opt_outline_0_1\",\"opt_outline_0_2\"]",selected(host));assertEquals(ink("reopen"),document(host));
        choose(host,2,false);choose(host,3,true);
        // The load and immediate submit precede the autosave debounce: barrier must freeze the final document.
        load(host,ink("attempt-A"));submit(host);await(()->"SUBMITTED".equals(script(reopened,"window.sharedPractice.getViewState().question.state")));
        var state=onFx(()->reopened.draftView().snapshot());var first=state.questions().getFirst().attempts().getLast();
        assertEquals(QuestionAttempt.Mode.INITIAL,first.attemptMode());
        assertEquals(first.result().name(),script(host,"window.sharedPractice.getViewState().question.result.status"));
        assertEquals(first.score(),((Number)script(host,"window.sharedPractice.getViewState().question.result.score")).doubleValue());
        String session=state.session().id();var snapshots=new SqliteAttemptDraftSnapshotRepository(practiceDb());
        var frozen=snapshots.find(first.id()).orElseThrow();assertEquals(ink("attempt-A"),frozen.document());
        script(host,"document.querySelector('#practice-retry').click()");await(()->"RETRYING".equals(script(reopened,"window.sharedPractice.getViewState().question.state")));
        assertEquals("[]",selected(host));assertEquals(DraftCanvasDocument.createEmpty(),document(host));
        // Use the actual Core correct IDs for the second attempt, not a JS grading implementation.
        var correct=onFx(()->reopened.draftView().snapshot().questions().getFirst().sessionQuestion().snapshot().correctAnswer());
        var ids=(java.util.List<?>)((java.util.Map<?,?>)correct.value()).get("correctOptionIds");
        for(var id:ids){int index=Integer.parseInt(id.toString().substring(id.toString().lastIndexOf('_')+1));choose(host,index,true);}
        load(host,ink("attempt-B"));submit(host);await(()->"SUBMITTED".equals(script(reopened,"window.sharedPractice.getViewState().question.state")));
        var second=onFx(()->reopened.draftView().snapshot().questions().getFirst().attempts().getLast());
        assertEquals(QuestionAttempt.Mode.RETRY,second.attemptMode());assertEquals(QuestionAttempt.Result.CORRECT,second.result());
        assertEquals(ink("attempt-B"),snapshots.find(second.id()).orElseThrow().document());assertEquals(frozen,snapshots.find(first.id()).orElseThrow());
        leave(host);fx(()->shell.tabs().closeAll());
        new SqlitePracticeSessionRepository(practiceDb()).archive(session,java.time.Instant.now());
        fx(()->{openBank(path);button("qbank-history-entry").fire();shell.applyCss();shell.layout();click(shell.lookup("#history-card-"+session),1);shell.applyCss();shell.layout();});
        var history=onFx(()->(HistorySurfaceHost)shell.lookup("#history-surface-host"));
        fx(()->history.toggleButton().fire());await(()->!onFx(history::busy));
        assertEquals(HistorySurfaceMode.DRAFT,onFx(history::mode));
        assertEquals(second.id(),historyScript(history,"window.historyDraftReplay.getViewState().question.result.attemptId"));
        assertEquals(ids.size(),((Number)historyScript(history,"document.querySelectorAll('input[type=checkbox]:checked').length")).intValue());
        assertEquals(Boolean.TRUE,historyScript(history,"Array.from(document.querySelectorAll('input')).every(i=>i.disabled)"));
        assertEquals(ink("attempt-B"),historyDocument(history));
        historyScript(history,"window.multipleChanges=0;window.draftCanvas.onChange(()=>window.multipleChanges++);document.querySelector('#practice-option-0').dispatchEvent(new Event('change',{bubbles:true}));document.querySelector('#practice-form').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));window.draftCanvas.setZoom(1.5);");
        assertEquals(0,((Number)historyScript(history,"window.multipleChanges")).intValue());
        assertEquals("undefined",historyScript(history,"typeof window.practiceHost"));assertEquals("undefined",historyScript(history,"typeof window.sharedPractice"));
        assertEquals(ink("attempt-B").strokes(),historyDocument(history).strokes());assertEquals(ink("attempt-B").questionCard(),historyDocument(history).questionCard());
        fx(()->button("history-previous-attempt").fire());await(()->!onFx(history::busy));assertEquals(first.id(),historyScript(history,"window.historyDraftReplay.getViewState().question.result.attemptId"));
        assertEquals(ink("attempt-A"),historyDocument(history));assertEquals("[\"opt_outline_0_1\",\"opt_outline_0_3\"]",historyScript(history,"JSON.stringify(window.historyDraftReplay.getViewState().question.selectedOptionIds.slice().sort())"));
        fx(()->history.toggleButton().fire());assertEquals(HistorySurfaceMode.RESULT,onFx(history::mode));fx(()->assertTrue(text(shell.lookup("#history-attempt-position")).contains("1 / 2")));
        fx(()->history.toggleButton().fire());await(()->!onFx(history::busy));assertEquals(ink("attempt-A"),historyDocument(history));
        fx(()->button("history-next-attempt").fire());await(()->!onFx(history::busy));assertEquals(ink("attempt-B"),historyDocument(history));
        assertEquals(frozen,snapshots.find(first.id()).orElseThrow());
    }

    @Test void singleAndMultipleHaveIndependentDraftsAndRendererLifecycleReleasesOldControls() throws Exception {
        String path=choiceBank("SINGLE_CHOICE","MULTIPLE_CHOICE");fx(()->openBank(path));var host=onFx(this::host);enter(host);load(host,ink("single"));
        String first=onFx(()->host.draftView().snapshot().questions().getFirst().sessionQuestion().id());
        fx(()->button("question-number-2").fire());await(()->!onFx(host::busy)&&"MULTIPLE_CHOICE".equals(script(host,"window.sharedPractice.getViewState().question.type")));
        assertTrue(document(host).strokes().isEmpty());assertEquals(ink("single"),new SqliteActiveDraftCanvasRepository(practiceDb()).find(first).orElseThrow().document());
        choose(host,0,true);choose(host,2,true);load(host,ink("multiple"));
        fx(()->button("draft-previous-question").fire());await(()->!onFx(host::busy)&&"SINGLE_CHOICE".equals(script(host,"window.sharedPractice.getViewState().question.type")));
        assertEquals(ink("single"),document(host));assertEquals(4,((Number)script(host,"document.querySelectorAll('input[type=radio]').length")).intValue());
        fx(()->button("draft-next-question").fire());await(()->!onFx(host::busy)&&"MULTIPLE_CHOICE".equals(script(host,"window.sharedPractice.getViewState().question.type")));
        assertEquals(ink("multiple"),document(host));assertEquals("[\"opt_outline_1_0\",\"opt_outline_1_2\"]",selected(host));
        script(host,"window.oldChoiceInput=document.querySelector('#practice-option-0');window.beforeDestroyEvents=window.sharedPractice.diagnostics().sent;window.sharedPractice.refreshPractice(window.sharedPractice.getViewState());window.oldChoiceInput.dispatchEvent(new Event('change',{bubbles:true}));");
        assertEquals(Boolean.TRUE,script(host,"window.oldChoiceInput.disabled"));
        assertEquals(Boolean.TRUE,script(host,"window.beforeDestroyEvents===window.sharedPractice.diagnostics().sent"));
        var web=onFx(host::draftView);fx(()->shell.tabs().closeAll());assertTrue(web.isDestroyed());
    }

    @Test void unsupportedTypesAndContentShowExplicitErrorsInsteadOfSingleFallbackAndDrawingBlocksSelection() throws Exception {
        String path=choiceBank("MULTIPLE_CHOICE");fx(()->openBank(path));var host=onFx(this::host);enter(host);
        script(host,"window.validRendererView=window.sharedPractice.getViewState();window.unsupportedView=JSON.parse(JSON.stringify(window.validRendererView));window.unsupportedView.question.type='UNKNOWN';try{window.sharedPractice.refreshPractice(window.unsupportedView)}catch(e){}");
        assertTrue(((String)script(host,"document.querySelector('#practice-unsupported').textContent")).contains("Unsupported question type"));
        assertEquals(0,((Number)script(host,"document.querySelectorAll('input').length")).intValue());
        script(host,"window.sharedPractice.refreshPractice(window.validRendererView);window.unsupportedView=JSON.parse(JSON.stringify(window.validRendererView));window.unsupportedView.question.prompt={kind:'RICH',document:{}};try{window.sharedPractice.refreshPractice(window.unsupportedView)}catch(e){}");
        assertTrue(((String)script(host,"document.querySelector('#practice-unsupported').textContent")).contains("Unsupported content"));
        script(host,"window.sharedPractice.refreshPractice(window.validRendererView);window.draftCanvas.setMode('PEN');window.beforeModeEvents=window.sharedPractice.diagnostics().sent;var input=document.querySelector('#practice-option-0');input.checked=true;input.dispatchEvent(new Event('change',{bubbles:true}));");
        assertEquals("[]",selected(host));assertEquals(Boolean.TRUE,script(host,"window.beforeModeEvents===window.sharedPractice.diagnostics().sent"));
        assertEquals(Boolean.FALSE,script(host,"document.querySelector('#practice-option-0').checked"));script(host,"window.draftCanvas.setMode('INTERACT')");choose(host,0,true);
    }

    private String choiceBank(String... types)throws Exception {
        String path=outlineBank(types);var codec=new io.quizforge.infrastructure.filesystem.qbank.QuestionBankV2Codec();
        var original=codec.parse(io.quizforge.infrastructure.testing.QBankTestPackageBuilder.read(fixture.alphaRoot.resolve(path)));
        var questions=new java.util.ArrayList<io.quizforge.core.question.model.Question>();
        for(int i=0;i<types.length;i++){
            var old=original.questions().get(i);var options=new java.util.ArrayList<io.quizforge.core.question.type.objective.choice.ChoiceOption>();
            for(int j=0;j<4;j++)options.add(new io.quizforge.core.question.type.objective.choice.ChoiceOption("opt_outline_"+i+"_"+j,new io.quizforge.core.question.content.TextContent("Option "+j)));
            var correct=types[i].equals("MULTIPLE_CHOICE")?List.of(options.get(0).id(),options.get(2).id()):List.of(options.get(0).id());
            questions.add(io.quizforge.core.question.model.Question.choice(old.id(),old.type(),old.prompt(),old.analysis(),old.sourceRefs(),new io.quizforge.core.question.type.objective.choice.ChoicePayload(options),new io.quizforge.core.question.type.objective.choice.ChoiceAnswerSpec(correct)));
        }
        fixture.write(path,codec.write(new io.quizforge.core.question.model.QuestionBank(original.assetId(),original.title(),"2.0",List.of(),questions,List.of())));return path;
    }
    private void openBank(String path){shell.refresh();open(path);shell.applyCss();shell.layout();assertNotNull(shell.lookup("#practice-surface-host"));}
    private PracticeSurfaceHost host(){return (PracticeSurfaceHost)shell.lookup("#practice-surface-host");}
    private static DraftCanvasDocument ink(String id){return new DraftCanvasDocument("1.0","1",new DraftCanvasDocument.Viewport(-31,47,1.25),new DraftCanvasDocument.QuestionCard(140,95,720),List.of(new DraftCanvasDocument.Stroke(id,"PEN","#7054a5",2.4,List.of(new DraftCanvasDocument.Point(190,140,.5),new DraftCanvasDocument.Point(310,180,.8)))));}
    private static void enter(PracticeSurfaceHost host)throws Exception{onFx(host::enterDraft).toCompletableFuture().get(30,TimeUnit.SECONDS);}
    private static void leave(PracticeSurfaceHost host)throws Exception{onFx(host::leaveDraft).toCompletableFuture().get(20,TimeUnit.SECONDS);}
    private static Object script(PracticeSurfaceHost host,String code)throws Exception{return onFx(()->host.draftView().view().getEngine().executeScript(code));}
    private static String selected(PracticeSurfaceHost host)throws Exception{return (String)script(host,"JSON.stringify(window.sharedPractice.getViewState().question.selectedOptionIds.slice().sort())");}
    private static void choose(PracticeSurfaceHost host,int index,boolean checked)throws Exception{script(host,"var input=document.querySelector('#practice-option-"+index+"');input.checked="+checked+";input.dispatchEvent(new Event('change',{bubbles:true}));");await(()->!Boolean.TRUE.equals(script(host,"document.querySelector('#practice-form').closest('#question-card').getAttribute('aria-busy')==='true'")));}
    private static void submit(PracticeSurfaceHost host)throws Exception{script(host,"document.querySelector('#practice-form').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));document.querySelector('#practice-confirm-submit').click();");}
    private static void load(PracticeSurfaceHost host,DraftCanvasDocument doc)throws Exception{onFx(()->{host.draftView().loadDraft(CODEC.encode(doc));return null;});}
    private static DraftCanvasDocument document(PracticeSurfaceHost host)throws Exception{return DraftCanvasJsonCodec.decode(onFx(()->host.draftView().getDraft()));}
    private static Object historyScript(HistorySurfaceHost host,String code)throws Exception{return onFx(()->host.draftView().view().getEngine().executeScript(code));}
    private static DraftCanvasDocument historyDocument(HistorySurfaceHost host)throws Exception{return DraftCanvasJsonCodec.decode((String)historyScript(host,"window.draftCanvas.getDraft()"));}
    private static <T>T onFx(Callable<T> action)throws Exception{var task=new FutureTask<>(action);Platform.runLater(task);return task.get(20,TimeUnit.SECONDS);}
    private static void await(Callable<Boolean> condition)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);while(System.nanoTime()<end){if(condition.call())return;Thread.sleep(50);}fail("Timed out waiting for multiple renderer/Core state");}
}
