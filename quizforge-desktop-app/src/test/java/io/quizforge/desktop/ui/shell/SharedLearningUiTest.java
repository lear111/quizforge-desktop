package io.quizforge.desktop.ui.shell;

import io.quizforge.desktop.browser.javafx.SharedPracticeCanvasWebView;
import io.quizforge.desktop.ui.question.practice.*;
import java.util.concurrent.*;
import javafx.application.Platform;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** FilePane/Router production default, rather than a directly constructed Surface fixture. */
class SharedLearningUiTest extends WorkspaceUiTestSupport {
    @Override protected boolean useSharedLearningUi() { return true; }
    @Test void formalDefaultReusesOneWebViewAcrossModesQuestionsAndTabClose() throws Exception {
        int before=SharedPracticeCanvasWebView.liveViewCount();
        fx(()->{shell.refresh();open("题库/Java集合.qbank");});
        var host=onFx(()->(PracticeSurfaceHost)shell.lookup("#practice-surface-host"));
        assertTrue(host.unified());host.ready().toCompletableFuture().get(30,TimeUnit.SECONDS);
        var web=host.draftView();assertEquals(before+1,SharedPracticeCanvasWebView.liveViewCount());
        fx(()->{assertNull(shell.lookup("#practice-question-card"));assertNull(shell.lookup("#option-0"));assertNotNull(shell.lookup("#shared-learning-webview"));});
        assertEquals(SharedLearningSurfaceMode.PRACTICE,onFx(host::learningMode));
        onFx(host::enterDraft).toCompletableFuture().get(20,TimeUnit.SECONDS);
        onFx(host::leaveDraft).toCompletableFuture().get(20,TimeUnit.SECONDS);
        assertSame(web,host.draftView());
        fx(()->button("authoring-question-2").fire());
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
        while(onFx(host::busy)&&System.nanoTime()<end)Thread.sleep(30);
        assertFalse(onFx(host::busy));assertSame(web,host.draftView());
        assertEquals("MULTIPLE_CHOICE",onFx(()->web.view().getEngine().executeScript("window.sharedPractice.getViewState().question.type")));
        fx(()->shell.tabs().closeAll());assertTrue(web.isDestroyed());assertEquals(before,SharedPracticeCanvasWebView.liveViewCount());
    }
    private static <T>T onFx(Callable<T> action)throws Exception {var task=new FutureTask<T>(action);Platform.runLater(task);return task.get(20,TimeUnit.SECONDS);}
    @Test void initialPageLoadCanBeClosedAndReleased() throws Exception {
        int before=SharedPracticeCanvasWebView.liveViewCount();
        fx(()->{shell.refresh();open("题库/Java集合.qbank");
            var host=(PracticeSurfaceHost)shell.lookup("#practice-surface-host");
            var web=host.draftView();assertTrue(host.prepareClose());
            shell.tabs().closeAll();assertTrue(web.isDestroyed());
            assertEquals(before,SharedPracticeCanvasWebView.liveViewCount());});
    }
    @Test void workspaceSwitchReleasesSharedSurface() throws Exception {
        int before=SharedPracticeCanvasWebView.liveViewCount();
        fx(()->{shell.refresh();open("题库/Java集合.qbank");});
        var host=onFx(()->(PracticeSurfaceHost)shell.lookup("#practice-surface-host"));
        host.ready().toCompletableFuture().get(30,TimeUnit.SECONDS);var web=host.draftView();
        fx(()->shell.switchWorkspace(fixture.beta));
        assertTrue(web.isDestroyed());assertEquals(before,SharedPracticeCanvasWebView.liveViewCount());
    }
    @Test void applicationExitReleasesSharedSurface() throws Exception {
        int before=SharedPracticeCanvasWebView.liveViewCount();
        fx(()->{shell.refresh();open("题库/Java集合.qbank");});
        var host=onFx(()->(PracticeSurfaceHost)shell.lookup("#practice-surface-host"));
        host.ready().toCompletableFuture().get(30,TimeUnit.SECONDS);var web=host.draftView();
        fx(()->{DesktopView.installCloseLifecycle(stage,shell,()->{});button("window-close").fire();assertFalse(stage.isShowing());});
        assertTrue(web.isDestroyed());assertEquals(before,SharedPracticeCanvasWebView.liveViewCount());
    }
}
