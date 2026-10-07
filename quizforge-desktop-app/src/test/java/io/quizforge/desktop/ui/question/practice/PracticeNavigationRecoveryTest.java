package io.quizforge.desktop.ui.question.practice;

import io.quizforge.core.practice.*;
import io.quizforge.core.question.model.QuestionBank;
import io.quizforge.core.question.type.*;
import io.quizforge.core.question.type.extension.ExternalQuestionTypeDefinition;
import io.quizforge.desktop.browser.PracticeLearningSurface;
import io.quizforge.desktop.testing.FxTestRuntime;
import io.quizforge.infrastructure.persistence.SqliteDatabase;
import io.quizforge.infrastructure.persistence.practice.SqlitePracticeTransaction;
import java.nio.file.Path;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class PracticeNavigationRecoveryTest {
    private static final String TYPE = "test.NAVIGATION";
    private final java.util.concurrent.atomic.AtomicInteger targetCalls = new java.util.concurrent.atomic.AtomicInteger();
    @TempDir Path directory;
    @BeforeAll static void startup() throws Exception { FxTestRuntime.start(); }
    @AfterEach void cleanup() { QuestionTypes.unregister(TYPE); }
    private static <T> T fx(Callable<T> operation) throws Exception {
        var task = new FutureTask<T>(operation); Platform.runLater(task); return task.get(15, TimeUnit.SECONDS);
    }
    private PersistentPracticeRuntime runtime() {
        var type = new ExternalQuestionTypeDefinition(TYPE, "Test", QuestionTypeDefinition.Family.OBJECTIVE, "1.0.0", (operation, input) -> switch(operation) {
            case "createDraft" -> Map.of("prompt", Map.of("kind", "TEXT", "text", "Question"), "payload", Map.of(), "answerSpec", Map.of(), "maxScore", 1);
            case "validate" -> Map.of("errors", List.of());
            case "snapshot", "targets" -> {
                if(operation.equals("targets"))targetCalls.incrementAndGet();
                yield Map.of("targets", List.of(Map.of("id", "one", "number", 1, "gradable", true)));
            }
            default -> throw new IllegalArgumentException(operation);
        });
        QuestionTypes.register(type);
        var bank = new QuestionBank("qb_navigation", "Navigation", List.of(),
                List.of(type.createDraft(prefix -> prefix + "one", List.of()), type.createDraft(prefix -> prefix + "two", List.of())), List.of());
        return new PersistentPracticeRuntime(new PracticeSessionService(new SqlitePracticeTransaction(new SqliteDatabase(directory.resolve("practice.db"))), Clock.systemUTC()), bank,
                new io.quizforge.infrastructure.filesystem.qbank.QuestionBankV2Codec().contentId(bank));
    }
    @Test void failedActionResumesSavesAndAllowsTheNextNavigation() throws Exception {
        var runtime = runtime(); var browser = fx(FakeSurface::new);
        var host = fx(() -> new PracticeSurfaceHost(runtime, () -> {}, () -> {}, () -> {}, () -> browser));
        try {
            var failure = fx(() -> host.navigate(() -> { throw new IllegalStateException("Failure after suspension"); }));
            assertThrows(ExecutionException.class, () -> failure.toCompletableFuture().get(5, TimeUnit.SECONDS));
            assertFalse(fx(host::busy)); assertFalse(browser.suspended); assertEquals(1, browser.resumes);
            fx(() -> host.navigate(() -> runtime.goTo(1))).toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertEquals(1, runtime.session().index()); assertFalse(fx(host::busy));
        } finally { fx(() -> { host.destroy(); return null; }); }
    }
    @Test void practiceOutlineRefreshUsesFrozenTargetsInsteadOfEditorRules() throws Exception {
        var runtime = runtime(); targetCalls.set(0);
        fx(() -> {
            var outline = new io.quizforge.desktop.ui.question.shared.QuestionOutlineView(runtime.session(), index -> {});
            for(int i=0;i<10;i++)outline.showPractice(index -> {});
            return null;
        });
        assertEquals(0,targetCalls.get());
    }
    @Test void failedReloadCanBeRetriedWithoutSuspendingTheSaveQueue() throws Exception {
        var runtime = runtime(); var browser = fx(FakeSurface::new);
        var host = fx(() -> new PracticeSurfaceHost(runtime, () -> {}, () -> {}, () -> {}, () -> browser));
        try {
            browser.failReload = true;
            var failure = fx(() -> host.navigate(() -> runtime.goTo(1)));
            assertThrows(ExecutionException.class, () -> failure.toCompletableFuture().get(5, TimeUnit.SECONDS));
            assertFalse(browser.suspended); assertFalse(fx(host::busy));
            fx(() -> host.navigate(() -> runtime.goTo(0))).toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertEquals(0, runtime.session().index());
        } finally { fx(() -> { host.destroy(); return null; }); }
    }
    private static final class FakeSurface implements PracticeLearningSurface {
        private final StackPane view = new StackPane();
        boolean suspended, failReload; int resumes;
        public Node view() { return view; }
        public CompletionStage<Void> ready() { return CompletableFuture.completedFuture(null); }
        public boolean isReady() { return true; }
        public void onUiChange(Consumer<Map<String,Boolean>> listener) { }
        public void configurePageActions(Supplier<Map<String,Object>> state, BiFunction<String,Object,CompletionStage<Void>> command) { }
        public CompletionStage<Void> flushPendingDraft() { return suspended ? CompletableFuture.failedFuture(new IllegalStateException("Save queue suspended")) : ready(); }
        public CompletionStage<Void> reloadCurrent() { if(failReload){failReload=false;return CompletableFuture.failedFuture(new IllegalStateException("Reload failed"));}suspended=false;return ready(); }
        public CompletionStage<Void> resumeCurrent() { resumes++; suspended=false;return ready(); }
        public void unloadCurrent() { suspended=true; }
        public void setLearningMode(SharedLearningSurfaceMode mode) { }
        public boolean focusTarget(String targetId) { return true; }
        public void saveBeforeClose() { }
        public void destroy() { }
    }
}
