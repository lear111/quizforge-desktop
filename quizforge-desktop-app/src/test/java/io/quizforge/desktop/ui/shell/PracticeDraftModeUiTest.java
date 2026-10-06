package io.quizforge.desktop.ui.shell;

import io.quizforge.core.practice.draft.DraftCanvasDocument;
import io.quizforge.desktop.ui.question.practice.PracticeSurfaceHost;
import io.quizforge.desktop.ui.question.practice.PracticeSurfaceMode;
import io.quizforge.infrastructure.persistence.practice.DraftCanvasJsonCodec;
import io.quizforge.infrastructure.persistence.practice.SqliteActiveDraftCanvasRepository;
import io.quizforge.infrastructure.persistence.practice.SqliteAttemptDraftSnapshotRepository;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.control.RadioButton;
import javafx.stage.Window;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Formal FilePane -> SurfaceHost -> WebKit -> Core -> SQLite, with existing outline/navigation. */
class PracticeDraftModeUiTest extends WorkspaceUiTestSupport {
    private static final DraftCanvasJsonCodec CODEC = new DraftCanvasJsonCodec();

    @Test void normalAndDraftShareStateAndRepeatedTogglesReuseOneBridgeThenCloseAndRestore() throws Exception {
        String path=outlineBank("SINGLE_CHOICE","SINGLE_CHOICE");
        fx(()->{openBank(path);((RadioButton)shell.lookup("#option-1")).fire();});
        var host=onFx(this::host); int windows=onFx(()->Window.getWindows().size());
        enter(host); var web=host.draftView();
        String session=onFx(()->web.snapshot().session().id()), question=question(host);
        assertEquals(windows,onFx(()->Window.getWindows().size()));
        assertEquals("opt_outline_0_1",script(host,"window.sharedPractice.getViewState().question.selectedOptionIds[0]"));
        choose(host,2); load(host,ink("A")); leave(host);
        fx(()->{assertEquals(PracticeSurfaceMode.NORMAL,host.mode());assertTrue(((RadioButton)shell.lookup("#option-2")).isSelected());});
        assertEquals(question,question(host));assertEquals(ink("A"),active(question));
        for(int i=0;i<3;i++){enter(host);assertSame(web,host.draftView());assertEquals(ink("A"),document(host));leave(host);}
        enter(host); int sent=((Number)script(host,"window.sharedPractice.diagnostics().sent")).intValue();
        choose(host,1);
        assertEquals(sent+1,((Number)script(host,"window.sharedPractice.diagnostics().sent")).intValue(),"One answer event after repeated toggles");
        fx(()->shell.tabs().closeAll());assertTrue(web.isDestroyed());
        fx(()->openBank(path));var reopened=onFx(this::host);enter(reopened);
        assertEquals(session,onFx(()->reopened.draftView().snapshot().session().id()));
        assertEquals(question,question(reopened));assertEquals(ink("A"),document(reopened));
    }

    @Test void outlineAndArrowNavigationFlushEachQuestionAndMultipleChoiceUsesSameSurface() throws Exception {
        String path=outlineBank("SINGLE_CHOICE","SINGLE_CHOICE","MULTIPLE_CHOICE");
        fx(()->openBank(path));var host=onFx(this::host);enter(host);
        var web=host.draftView();String q1=question(host);load(host,ink("A"));
        fx(()->button("question-number-2").fire());await(()->!onFx(host::busy)&&!q1.equals(question(host)));
        String q2=question(host);assertSame(web,host.draftView());assertTrue(document(host).strokes().isEmpty());
        assertEquals(ink("A"),active(q1));load(host,ink("B"));
        fx(()->button("draft-previous-question").fire());await(()->!onFx(host::busy)&&q1.equals(question(host)));
        assertEquals(ink("A"),document(host));assertEquals(ink("B"),active(q2));
        fx(()->button("draft-next-question").fire());await(()->!onFx(host::busy)&&q2.equals(question(host)));
        assertEquals(ink("B"),document(host));leave(host);
        assertEquals(q2,question(host));enter(host);assertEquals(ink("B"),document(host));
        fx(()->button("question-number-3").fire());await(()->!onFx(host::busy)&&"MULTIPLE_CHOICE".equals(script(host,"window.sharedPractice.getViewState().question.type")));
        assertEquals(PracticeSurfaceMode.DRAFT,onFx(host::mode));
        assertEquals(3,((Number)script(host,"document.querySelectorAll('input[type=checkbox]').length")).intValue());
    }

    @Test void failedFlushBlocksExitNavigationAndClosingWithoutLosingMemoryInk() throws Exception {
        String path=outlineBank("SINGLE_CHOICE","SINGLE_CHOICE");
        fx(()->openBank(path));var host=onFx(this::host);enter(host);String q1=question(host);
        sql("CREATE TRIGGER reject_draft BEFORE INSERT ON practice_draft_canvas BEGIN SELECT RAISE(ABORT, 'injected draft save failure'); END");
        load(host,ink("unsaved"));
        var leave=onFx(host::leaveDraft);assertThrows(java.util.concurrent.ExecutionException.class,()->leave.toCompletableFuture().get(20,TimeUnit.SECONDS));
        assertEquals(PracticeSurfaceMode.DRAFT,onFx(host::mode));assertEquals(ink("unsaved"),document(host));
        fx(()->button("question-number-2").fire());await(()->!onFx(host::busy));
        assertEquals(q1,question(host));assertEquals(ink("unsaved"),document(host));
        fx(()->{assertFalse(host.prepareClose());assertFalse(shell.tabs().closeAll());assertNotNull(shell.lookup("#practice-surface-host"));});
        sql("DROP TRIGGER reject_draft");leave(host);assertEquals(ink("unsaved"),active(q1));
    }

    @Test void embeddedSubmitFreezesImmediateLastStrokeAndRetryKeepsOldSnapshotImmutable() throws Exception {
        String path=outlineBank("SINGLE_CHOICE","SINGLE_CHOICE");
        fx(()->openBank(path));var host=onFx(this::host);enter(host);choose(host,0);
        load(host,ink("final-A"));submit(host);await(()->"SUBMITTED".equals(script(host,"window.sharedPractice.getViewState().question.state")));
        var first=onFx(()->host.draftView().snapshot().questions().getFirst().attempts().getFirst());
        var snapshots=new SqliteAttemptDraftSnapshotRepository(practiceDb());var frozen=snapshots.find(first.id()).orElseThrow();
        assertEquals(ink("final-A"),frozen.document());assertTrue(new SqliteActiveDraftCanvasRepository(practiceDb()).find(question(host)).isEmpty());
        leave(host);fx(()->assertNotNull(shell.lookup("#practice-retry")));enter(host);
        script(host,"document.querySelector('#practice-retry').click()");await(()->"RETRYING".equals(script(host,"window.sharedPractice.getViewState().question.state")));
        assertEquals(DraftCanvasDocument.createEmpty(),document(host));assertEquals(frozen,snapshots.find(first.id()).orElseThrow());
        choose(host,1);load(host,ink("final-B"));submit(host);await(()->"SUBMITTED".equals(script(host,"window.sharedPractice.getViewState().question.state")));
        var second=onFx(()->host.draftView().snapshot().questions().getFirst().attempts().getLast());
        assertNotEquals(first.id(),second.id());assertEquals(ink("final-B"),snapshots.find(second.id()).orElseThrow().document());
        assertEquals(frozen,snapshots.find(first.id()).orElseThrow());
    }

    @Test void mixedBankUsesTheSameSurfaceForEssayAndCanReturnToNormalWithoutLosingChoiceDrafts() throws Exception {
        String choices=outlineBank("SINGLE_CHOICE","SINGLE_CHOICE");
        var codec=new io.quizforge.infrastructure.filesystem.qbank.QuestionBankV2Codec();
        var original=codec.parse(io.quizforge.infrastructure.testing.QBankTestPackageBuilder.read(fixture.alphaRoot.resolve(choices)));
        var bank=new io.quizforge.core.question.model.QuestionBank("qb_mixed_draft","Mixed draft","2.0",List.of(),List.of(
                original.questions().getFirst(),io.quizforge.infrastructure.testing.EssayTestBanks.bank().questions().getFirst(),original.questions().getLast()),List.of());
        String path="题库/MixedDraft.qbank";fixture.write(path,codec.write(bank));
        fx(()->openBank(path));var host=onFx(this::host);enter(host);choose(host,2);load(host,ink("mixed-A"));String q1=question(host);
        fx(()->button("authoring-question-2").fire());await(()->!onFx(host::busy)&&"ESSAY".equals(script(host,"window.sharedPractice.getViewState().question.type")));
        assertEquals(PracticeSurfaceMode.DRAFT,onFx(host::mode));
        assertTrue(document(host).strokes().isEmpty());
        leave(host);
        fx(()->{assertTrue(host.toggleButton().isVisible());assertNotNull(shell.lookup("#essay-answer-pane"));});
        fx(()->button("authoring-question-3").fire());enter(host);assertTrue(document(host).strokes().isEmpty());
        load(host,ink("mixed-B"));leave(host);fx(()->button("authoring-question-1").fire());enter(host);
        assertEquals(q1,question(host));assertEquals(ink("mixed-A"),document(host));
        assertEquals("opt_outline_0_2",script(host,"window.sharedPractice.getViewState().question.selectedOptionIds[0]"));
    }

    @Test void draftNavigationToRichPreviewPreservesItsRendererAndDoesNotOpenAnotherQuestionsDraft() throws Exception {
        String choices=outlineBank("SINGLE_CHOICE","SINGLE_CHOICE");
        var codec=new io.quizforge.infrastructure.filesystem.qbank.QuestionBankV2Codec();
        var original=codec.parse(io.quizforge.infrastructure.testing.QBankTestPackageBuilder.read(fixture.alphaRoot.resolve(choices)));
        var second=original.questions().getLast();
        var rich=new io.quizforge.core.question.content.RichContent(new io.quizforge.core.question.content.RichDocument(List.of(
                new io.quizforge.core.question.content.ParagraphNode(List.of(new io.quizforge.core.question.content.InlineTextNode("Rich preview",List.of()))))));
        var options=new java.util.ArrayList<>(second.choicePayload().options());
        options.set(0,new io.quizforge.core.question.model.choice.ChoiceOption(options.getFirst().id(),rich));
        var preview=io.quizforge.core.question.model.Question.choice(second.id(),second.type(),second.prompt(),second.analysis(),second.sourceRefs(),
                new io.quizforge.core.question.model.choice.ChoicePayload(options),second.choiceAnswerSpec());
        var bank=new io.quizforge.core.question.model.QuestionBank("qb_draft_rich_preview","Draft rich preview",List.of(),List.of(
                original.questions().getFirst(),preview,io.quizforge.infrastructure.testing.EssayTestBanks.bank().questions().getFirst()),List.of());
        String path="题库/DraftRichPreview.qbank";fixture.write(path,codec.write(bank));
        fx(()->openBank(path));var host=onFx(this::host);enter(host);String q1=question(host);load(host,ink("before-preview"));
        fx(()->button("draft-next-question").fire());await(()->!onFx(host::busy)&&onFx(host::mode)==PracticeSurfaceMode.NORMAL);
        fx(()->{assertNotNull(shell.lookup("#authoring-option-0-rich-content"));assertFalse(host.toggleButton().isVisible());});
        assertEquals(q1,question(host));assertEquals(ink("before-preview"),active(q1));
        fx(()->button("authoring-next-question").fire());fx(()->assertNotNull(shell.lookup("#essay-answer-pane")));
        fx(()->button("authoring-question-1").fire());enter(host);leave(host);
        fx(()->assertNotNull(shell.lookup("#option-0")));enter(host);assertEquals(ink("before-preview"),document(host));
    }

    private void openBank(String path){shell.refresh();open(path);assertNotNull(shell.lookup("#practice-surface-host"),text(shell.tabs().activePane()));}
    private PracticeSurfaceHost host(){shell.applyCss();shell.layout();return (PracticeSurfaceHost)shell.lookup("#practice-surface-host");}
    private String question(PracticeSurfaceHost host)throws Exception{return onFx(()->{var snapshot=host.draftView().snapshot();return snapshot.questions().stream().filter(q->q.sessionQuestion().questionId().equals(snapshot.session().currentQuestionId())).findFirst().orElseThrow().sessionQuestion().id();});}
    private DraftCanvasDocument active(String id){return new SqliteActiveDraftCanvasRepository(practiceDb()).find(id).orElseThrow().document();}
    private static DraftCanvasDocument ink(String id){return new DraftCanvasDocument("1.0","1",new DraftCanvasDocument.Viewport(-31,47,1.25),new DraftCanvasDocument.QuestionCard(140,95,720),List.of(new DraftCanvasDocument.Stroke(id,"PEN","#7054a5",2.4,List.of(new DraftCanvasDocument.Point(190,140,.5),new DraftCanvasDocument.Point(310,180,.8)))));}
    private static void enter(PracticeSurfaceHost host)throws Exception{CompletionStage<Void> ready=onFx(host::enterDraft);ready.toCompletableFuture().get(30,TimeUnit.SECONDS);assertEquals(PracticeSurfaceMode.DRAFT,onFx(host::mode));}
    private static void leave(PracticeSurfaceHost host)throws Exception{onFx(host::leaveDraft).toCompletableFuture().get(20,TimeUnit.SECONDS);}
    private static Object script(PracticeSurfaceHost host,String code)throws Exception{return onFx(()->host.draftView().view().getEngine().executeScript(code));}
    private static void load(PracticeSurfaceHost host,DraftCanvasDocument doc)throws Exception{onFx(()->{host.draftView().loadDraft(CODEC.encode(doc));return null;});}
    private static DraftCanvasDocument document(PracticeSurfaceHost host)throws Exception{return DraftCanvasJsonCodec.decode(onFx(()->host.draftView().getDraft()));}
    private static void choose(PracticeSurfaceHost host,int index)throws Exception{script(host,"var input=document.querySelector('#practice-option-"+index+"');input.checked=true;input.dispatchEvent(new Event('change',{bubbles:true}));");await(()->!Boolean.TRUE.equals(script(host,"document.querySelector('#practice-submit').disabled")));}
    private static void submit(PracticeSurfaceHost host)throws Exception{script(host,"document.querySelector('#practice-form').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));document.querySelector('#practice-confirm-submit').click();");}
    private void sql(String value)throws Exception{try(var c=practiceDb().openConnection();var s=c.createStatement()){s.execute(value);}}
    private static <T>T onFx(Callable<T> action)throws Exception{var task=new FutureTask<>(action);Platform.runLater(task);return task.get(20,TimeUnit.SECONDS);}
    private static void await(Callable<Boolean> condition)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);while(System.nanoTime()<end){if(condition.call())return;Thread.sleep(50);}fail("Timed out waiting for formal Practice state");}
}
