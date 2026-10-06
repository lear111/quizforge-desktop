package io.quizforge.desktop.ui.shell;

import io.quizforge.core.practice.*;
import io.quizforge.core.practice.draft.DraftCanvasDocument;
import io.quizforge.desktop.ui.question.history.*;
import io.quizforge.infrastructure.filesystem.qbank.QuestionBankV2Codec;
import io.quizforge.infrastructure.persistence.practice.*;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.stage.Window;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Existing FilePane History entry -> JavaFX detail -> native WebKit, no new user workspace. */
class HistoryDraftReplayUiTest extends WorkspaceUiTestSupport {
    record Prepared(String path,String session,String first,String second){}
    private Prepared prepare(boolean firstInk,boolean secondQuestionInk) throws Exception {
        String path=outlineBank("SINGLE_CHOICE","SINGLE_CHOICE");
        var codec=new QuestionBankV2Codec();
        var template=codec.parse(io.quizforge.infrastructure.testing.QBankTestPackageBuilder.read(fixture.alphaRoot.resolve(path)));
        var bank=new io.quizforge.core.question.model.QuestionBank("qb_history_"+UUID.randomUUID(),template.title(),"2.0",template.stimuli(),template.questions(),template.resources());
        fixture.write(path,codec.write(bank));
        var service=new PracticeSessionService(new SqlitePracticeTransaction(practiceDb()),Clock.systemUTC());
        String revision=codec.contentId(bank);var state=service.openOrCreateActiveSession(bank,revision);String session=state.session().id();
        var q1=bank.questions().getFirst();var q2=bank.questions().getLast();
        if(firstInk)service.saveActiveDraftCanvas(session,revision,q1.id(),ink("A",3));
        service.saveDraft(session,revision,q1.id(),Set.of(q1.choicePayload().options().get(1).id()));
        var first=service.submitAnswer(session,revision,q1.id()).questions().getFirst().attempts().getLast();
        service.retryQuestion(session,revision,q1.id());
        if(firstInk)service.saveActiveDraftCanvas(session,revision,q1.id(),ink("B",1));
        service.saveDraft(session,revision,q1.id(),Set.of(q1.choicePayload().options().get(0).id()));
        var second=service.submitAnswer(session,revision,q1.id()).questions().getFirst().attempts().getLast();
        service.updateCurrentQuestion(session,revision,q2.id());
        if(secondQuestionInk)service.saveActiveDraftCanvas(session,revision,q2.id(),ink("C",2));
        service.saveDraft(session,revision,q2.id(),Set.of(q2.choicePayload().options().get(0).id()));service.submitAnswer(session,revision,q2.id());
        new SqlitePracticeSessionRepository(practiceDb()).archive(session,Instant.now());
        var active=service.openOrCreateActiveSession(bank,revision);service.saveActiveDraftCanvas(active.session().id(),revision,q1.id(),ink("active",1));
        return new Prepared(path,session,first.id(),second.id());
    }
    private void openHistory(Prepared prepared){
        shell.refresh();open(prepared.path());button("qbank-history-entry").fire();shell.applyCss();shell.layout();
        click(shell.lookup("#history-card-"+prepared.session()),1);shell.applyCss();shell.layout();assertNotNull(shell.lookup("#practice-history-detail"));
    }
    @Test void selectedAttemptOwnsFrozenAnswerInkAndGeometryAcrossModesQuestionsAndRepeatedToggles() throws Exception {
        var prepared=prepare(true,true);
        var codec=new QuestionBankV2Codec();var bank=codec.parse(io.quizforge.infrastructure.testing.QBankTestPackageBuilder.read(fixture.alphaRoot.resolve(prepared.path())));
        var old=bank.questions().getFirst();String frozenStem=io.quizforge.core.question.content.QuestionText.prompt(old);
        var changed=io.quizforge.core.question.model.Question.choice(old.id(),old.type(),new io.quizforge.core.question.content.TextContent("CURRENT BANK TEXT MUST NOT APPEAR"),old.analysis(),old.sourceRefs(),old.choicePayload(),old.choiceAnswerSpec());
        fixture.write(prepared.path(),codec.write(new io.quizforge.core.question.model.QuestionBank(bank.assetId(),bank.title(),"2.0",List.of(),List.of(changed,bank.questions().getLast()),List.of())));
        fx(()->openHistory(prepared));var host=onFx(this::host);
        assertNotNull(onFx(host::draftView),"Default fixed view uses the shared WebView");await(()->!onFx(host::busy));
        assertEquals(HistorySurfaceMode.RESULT,onFx(host::mode));
        assertEquals(1,((Number)script(host,"document.querySelector('#strokes').children.length")).intValue());
        script(host,"window.historyCardRef=document.querySelector('#question-card')");int windows=onFx(()->Window.getWindows().size());
        enter(host);var web=onFx(host::draftView);
        assertEquals(Boolean.TRUE,script(host,"window.historyCardRef===document.querySelector('#question-card')"));
        assertEquals(prepared.second(),attempt(host));assertEquals(ink("B",1),document(host));
        assertEquals(frozenStem,script(host,"window.historyDraftReplay.getViewState().question.prompt.text"));
        assertEquals("opt_outline_0_0",script(host,"window.historyDraftReplay.getViewState().question.selectedOptionIds[0]"));
        fx(()->button("history-previous-attempt").fire());await(()->prepared.first().equals(attempt(host)));
        assertEquals(HistorySurfaceMode.DRAFT,onFx(host::mode));assertEquals(ink("A",3),document(host));
        assertEquals("opt_outline_0_1",script(host,"window.historyDraftReplay.getViewState().question.selectedOptionIds[0]"));
        assertEquals(3,((Number)script(host,"document.querySelector('#strokes').children.length")).intValue());
        fx(()->host.toggleButton().fire());assertEquals(HistorySurfaceMode.RESULT,onFx(host::mode));
        fx(()->assertTrue(text(shell.lookup("#history-attempt-controls")).contains("第 1 / 2 次作答")));
        assertEquals(3,((Number)script(host,"document.querySelector('#strokes').children.length")).intValue());
        for(int i=0;i<3;i++){enter(host);assertSame(web,onFx(host::draftView));assertEquals(prepared.first(),attempt(host));fx(()->host.toggleButton().fire());}
        enter(host);fx(()->button("history-next-attempt").fire());await(()->prepared.second().equals(attempt(host)));
        assertEquals(ink("B",1),document(host));
        fx(()->button("history-draft-next-question").fire());await(()->"q_outline_1".equals(script(host,"window.historyDraftReplay.getViewState().question.questionId")));
        assertEquals(ink("C",2),document(host));
        fx(()->button("history-draft-previous-question").fire());await(()->prepared.second().equals(attempt(host)));assertEquals(ink("B",1),document(host));
        assertSame(web,onFx(host::draftView));assertEquals(windows,onFx(()->Window.getWindows().size()));
        fx(()->button("history-detail-back").fire());assertTrue(web.isDestroyed());fx(()->assertNull(shell.lookup("#practice-history-detail")));
    }
    @Test void readOnlyBridgeDisallowsMutationButPanZoomAndResizeNeverWriteActiveOrFrozenRows() throws Exception {
        var prepared=prepare(true,true);fx(()->openHistory(prepared));var host=onFx(this::host);enter(host);var before=rows();
        assertEquals("READ_ONLY",script(host,"window.historyDraftReplay.diagnostics().accessMode"));
        assertEquals("undefined",script(host,"typeof window.practiceHost"));assertEquals("undefined",script(host,"typeof window.sharedPractice"));
        assertEquals("undefined",script(host,"typeof window.historyHost.saveActiveDraftCanvas"));
        assertEquals("undefined",script(host,"typeof window.historyHost.submit"));
        assertEquals(0,((Number)script(host,"document.querySelectorAll('#practice-submit,#practice-retry,#practice-confirm-submit').length")).intValue());
        assertEquals(Boolean.TRUE,script(host,"Array.from(document.querySelectorAll('input[type=radio]')).every(i=>i.disabled)"));
        script(host,"window.historyChanges=0;window.draftCanvas.onChange(()=>window.historyChanges++);");
        assertEquals(Boolean.TRUE,script(host,"['PEN','ERASER','INTERACT'].every(m=>{try{window.draftCanvas.setMode(m);return false;}catch(e){return true;}})"));
        script(host,"['undo','redo','clear'].forEach(id=>document.getElementById(id).click());document.dispatchEvent(new KeyboardEvent('keydown',{key:'z',ctrlKey:true,bubbles:true}));var input=document.querySelector('#practice-option-2');input.checked=true;input.dispatchEvent(new Event('change',{bubbles:true}));document.querySelector('#practice-form').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));");
        assertEquals(ink("B",1),document(host));assertEquals(prepared.second(),attempt(host));
        assertEquals("opt_outline_0_0",script(host,"window.historyDraftReplay.getViewState().question.selectedOptionIds[0]"));
        script(host,"window.draftCanvas.setZoom(1.7);document.querySelector('#zoom-fit').click();");
        var viewing=document(host);assertNotEquals(ink("B",1).viewport(),viewing.viewport());
        assertEquals(ink("B",1).strokes(),viewing.strokes());assertEquals(ink("B",1).questionCard(),viewing.questionCard());
        fx(()->{stage.setWidth(780);stage.setHeight(600);shell.applyCss();shell.layout();});
        assertEquals(viewing,document(host));assertEquals("720px",script(host,"document.querySelector('#question-card').style.width"));
        assertEquals(0,((Number)script(host,"window.historyChanges")).intValue(),"No DRAFT_CHANGED callbacks, including local navigation");
        for(int i=0;i<3;i++){fx(()->host.toggleButton().fire());enter(host);}
        assertEquals(0,((Number)script(host,"window.historyChanges")).intValue());assertEquals(before,rows());
        var web=onFx(host::draftView);fx(()->shell.tabs().closeAll());assertTrue(web.isDestroyed());assertEquals(before,rows());
    }
    @Test void absentAnnotationsUseNewCardAndCorruptSnapshotsDoNotFallBackToOldCards() throws Exception {
        var prepared=prepare(true,false);fx(()->openHistory(prepared));var host=onFx(this::host);enter(host);var web=onFx(host::draftView);
        fx(()->button("history-draft-next-question").fire());
        await(()->!onFx(host::busy));assertEquals(HistorySurfaceMode.DRAFT,onFx(host::mode));fx(()->assertTrue(host.toggleButton().isVisible()));
        assertEquals(0,((Number)script(host,"document.querySelector('#strokes').children.length")).intValue());
        assertNotNull(script(host,"window.historyDraftReplay.getViewState().question.result"));
        fx(()->button("history-draft-previous-question").fire());enter(host);assertSame(web,onFx(host::draftView));assertEquals(ink("B",1),document(host));
        fx(()->button("history-detail-back").fire());assertTrue(web.isDestroyed());
        // A separate archived attempt with no snapshot is corrupt only after an INSERT; frozen UPDATE remains forbidden.
        fx(()->shell.tabs().closeAll());
        var legacy=prepare(false,false);
        try(var c=practiceDb().openConnection();var s=c.prepareStatement("INSERT INTO attempt_draft_snapshot(attempt_id,document_json,created_at) VALUES(?,?,?)")){
            s.setString(1,legacy.second());s.setString(2,"{");s.setString(3,Instant.now().toString());s.executeUpdate();
        }
        fx(()->openHistory(legacy));var invalid=onFx(this::host);var before=rows();
        fx(()->{assertFalse(invalid.toggleButton().isVisible());assertTrue(text(shell.lookup("#history-draft-error")).contains("格式"));assertNull(shell.lookup("#history-attempt-result"));});
        assertNull(onFx(invalid::draftView),"Corrupt snapshot does not create a replacement document");
        fx(()->button("history-previous-attempt").fire());enter(invalid);
        assertTrue(document(invalid).strokes().isEmpty());assertNotNull(attempt(invalid));assertEquals(before,rows());
    }
    private HistorySurfaceHost host(){return (HistorySurfaceHost)shell.lookup("#history-surface-host");}
    private static DraftCanvasDocument ink(String name,int count){
        var strokes=new ArrayList<DraftCanvasDocument.Stroke>();for(int i=0;i<count;i++)strokes.add(new DraftCanvasDocument.Stroke(name+i,"PEN","#7054a5",2.4,List.of(new DraftCanvasDocument.Point(210,170+i*20,.5),new DraftCanvasDocument.Point(340,185+i*20,.8))));
        return new DraftCanvasDocument("1.0","1",new DraftCanvasDocument.Viewport(-31,47,1.25),new DraftCanvasDocument.QuestionCard(140,95,720),strokes);
    }
    private List<String> rows() throws Exception {
        var values=new ArrayList<String>();try(var c=practiceDb().openConnection();var s=c.createStatement()){
            for(String table:List.of("practice_draft_canvas","attempt_draft_snapshot"))try(var r=s.executeQuery("SELECT * FROM "+table+" ORDER BY 1")){while(r.next()){var row=new StringBuilder(table);for(int i=1;i<=r.getMetaData().getColumnCount();i++)row.append('|').append(r.getString(i));values.add(row.toString());}}
        }return values;
    }
    private static Object script(HistorySurfaceHost host,String code)throws Exception{return onFx(()->host.draftView().view().getEngine().executeScript(code));}
    private static String attempt(HistorySurfaceHost host)throws Exception{return (String)script(host,"window.historyDraftReplay.getViewState().question.result.attemptId");}
    private static DraftCanvasDocument document(HistorySurfaceHost host)throws Exception{return DraftCanvasJsonCodec.decode((String)script(host,"window.draftCanvas.getDraft()"));}
    private static void enter(HistorySurfaceHost host)throws Exception{await(()->!onFx(host::busy));if(onFx(host::mode)!=HistorySurfaceMode.DRAFT)fx(()->host.toggleButton().fire());await(()->!onFx(host::busy));assertEquals(HistorySurfaceMode.DRAFT,onFx(host::mode));fx(()->assertFalse(host.lookup("#history-draft-error").isVisible()));}
    private static <T>T onFx(Callable<T> action)throws Exception{var task=new FutureTask<>(action);Platform.runLater(task);return task.get(20,TimeUnit.SECONDS);}
    private static void await(Callable<Boolean> condition)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);while(System.nanoTime()<end){if(condition.call())return;Thread.sleep(50);}fail("Timed out waiting for History replay");}
}
