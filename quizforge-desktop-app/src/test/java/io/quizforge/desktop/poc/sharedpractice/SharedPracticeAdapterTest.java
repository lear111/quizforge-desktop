package io.quizforge.desktop.poc.sharedpractice;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quizforge.core.port.PracticeTransaction;
import io.quizforge.core.practice.ActivePracticeSnapshot;
import io.quizforge.core.practice.PracticeSessionService;
import io.quizforge.core.practice.QuestionAttempt;
import io.quizforge.infrastructure.filesystem.qbank.QuestionBankV2Codec;
import io.quizforge.infrastructure.persistence.SqliteDatabase;
import io.quizforge.infrastructure.persistence.practice.SqlitePracticeTransaction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Real packaged QBank and existing SQLite transactions, without a Workspace or service mock. */
class SharedPracticeAdapterTest {
    private static final String CORRECT = "opt_demo_01_a";
    private static final String INCORRECT = "opt_demo_01_b";
    private static final ObjectMapper JSON = new ObjectMapper();
    @TempDir Path temporary;

    @Test void mapsExplicitUnansweredContractWithoutLeakingAnswerOrAnalysis() throws Exception {
        try (var context = open()) {
            var snapshot = context.snapshot();
            var model = context.adapter().viewModel();
            assertEquals("1.0", model.schemaVersion());
            assertEquals(snapshot.session().id(), model.session().sessionId());
            assertEquals(context.bank().assetId(), model.session().bankAssetId());
            assertEquals(snapshot.session().questionBankContentId(), model.session().bankContentId());
            assertEquals("q_demo_arraylist_structure", model.question().questionId());
            assertEquals(snapshot.questions().getFirst().sessionQuestion().id(), model.question().sessionQuestionId());
            assertEquals(0, model.question().index());
            assertEquals(4, model.question().total());
            assertEquals("SINGLE_CHOICE", model.question().type());
            assertEquals("TEXT", model.question().prompt().kind());
            assertEquals("ArrayList 的底层结构是什么？", model.question().prompt().text());
            assertEquals(3, model.question().options().size());
            assertEquals(1.0, model.question().maxScore());
            assertEquals(SharedPracticeViewModel.State.UNANSWERED, model.question().state());
            assertTrue(model.question().selectedOptionIds().isEmpty());
            assertNull(model.question().result());
            assertTrue(model.question().options().stream().allMatch(option -> option.feedback() == SharedPracticeViewModel.Feedback.NONE));
            var json = JSON.readTree(context.adapter().viewModelJson());
            assertEquals(Set.of("schemaVersion", "session", "question"), names(json));
            assertFalse(json.path("question").has("correctOptionIds"));
            assertFalse(json.path("question").has("analysis"));
            assertFalse(context.adapter().viewModelJson().contains("correctAnswer"));
            assertFalse(context.adapter().viewModelJson().contains("io.quizforge"));
        }
    }

    @Test void initialAndRetryUseCoreGradingAndPreserveImmutableAttempts() throws Exception {
        try (var context = open()) {
            var adapter = context.adapter();
            var draft = adapter.answerChanged(Set.of(INCORRECT));
            assertEquals(SharedPracticeViewModel.State.DRAFT, draft.question().state());
            assertEquals(Set.of(INCORRECT), Set.copyOf(draft.question().selectedOptionIds()));
            assertNull(draft.question().result());
            assertPersisted(adapter.snapshot(), restore(context));
            var submitted = adapter.submit();
            assertEquals(SharedPracticeViewModel.State.SUBMITTED, submitted.question().state());
            assertEquals("INCORRECT", submitted.question().result().status());
            assertEquals(0.0, submitted.question().result().score());
            assertEquals(1.0, submitted.question().result().maxScore());
            assertEquals("INITIAL", submitted.question().result().attemptMode());
            assertEquals(1, submitted.question().result().attemptNo());
            assertEquals(Set.of(CORRECT), Set.copyOf(submitted.question().result().correctOptionIds()));
            assertFalse(submitted.question().result().analysis().text().isBlank());
            assertEquals(SharedPracticeViewModel.Feedback.CORRECT, submitted.question().options().get(0).feedback());
            assertEquals(SharedPracticeViewModel.Feedback.INCORRECT, submitted.question().options().get(1).feedback());
            var initial = adapter.snapshot().questions().getFirst().attempts().getFirst();
            assertEquals(initial.id(), submitted.question().result().attemptId());
            assertPersisted(adapter.snapshot(), restore(context));

            var retry = adapter.retry();
            assertEquals(SharedPracticeViewModel.State.RETRYING, retry.question().state());
            assertTrue(retry.question().selectedOptionIds().isEmpty());
            assertNull(retry.question().result());
            assertTrue(retry.question().options().stream().allMatch(option -> option.feedback() == SharedPracticeViewModel.Feedback.NONE));
            assertEquals(initial, adapter.snapshot().questions().getFirst().attempts().getFirst());
            var retryDraft = adapter.answerChanged(Set.of(CORRECT));
            assertEquals(SharedPracticeViewModel.State.RETRYING, retryDraft.question().state());
            assertEquals(Set.of(CORRECT), Set.copyOf(retryDraft.question().selectedOptionIds()));
            var retryResult = adapter.submit().question().result();
            assertEquals("CORRECT", retryResult.status());
            assertEquals(1.0, retryResult.score());
            assertEquals("RETRY", retryResult.attemptMode());
            assertEquals(2, retryResult.attemptNo());
            var persisted = restore(context);
            var attempts = persisted.questions().getFirst().attempts();
            assertEquals(2, attempts.size());
            assertEquals(initial, attempts.getFirst());
            assertEquals(QuestionAttempt.Mode.INITIAL, attempts.getFirst().attemptMode());
            assertEquals(QuestionAttempt.Mode.RETRY, attempts.getLast().attemptMode());
            assertNotEquals(initial.id(), attempts.getLast().id());
        }
    }

    @Test void clearingDraftUsesCoreUnansweredAndRetryStateRules() throws Exception {
        try (var context = open()) {
            var adapter = context.adapter();
            adapter.answerChanged(Set.of(CORRECT));
            assertEquals(SharedPracticeViewModel.State.UNANSWERED, adapter.answerChanged(Set.of()).question().state());
            adapter.answerChanged(Set.of(CORRECT));
            adapter.submit();
            adapter.retry();
            adapter.answerChanged(Set.of(INCORRECT));
            var emptyRetry = adapter.answerChanged(Set.of());
            assertEquals(SharedPracticeViewModel.State.RETRYING, emptyRetry.question().state());
            assertTrue(emptyRetry.question().selectedOptionIds().isEmpty());
            assertEquals(1, restore(context).questions().getFirst().attempts().size());
        }
    }

    @Test void coreValidationFailuresKeepOriginalSnapshotAndAppendNoAttempt() throws Exception {
        try (var context = open()) {
            var adapter = context.adapter();
            var original = adapter.snapshot();
            assertThrows(IllegalStateException.class, adapter::submit);
            assertSame(original, adapter.snapshot());
            assertThrows(IllegalStateException.class, adapter::retry);
            assertSame(original, adapter.snapshot());
            assertThrows(IllegalArgumentException.class, () -> adapter.answerChanged(Set.of("missing-option")));
            assertSame(original, adapter.snapshot());
            assertThrows(IllegalArgumentException.class, () -> adapter.answerChanged(Set.of(CORRECT, INCORRECT)));
            assertSame(original, adapter.snapshot());
            assertTrue(restore(context).questions().getFirst().attempts().isEmpty());
            adapter.answerChanged(Set.of(CORRECT));
            adapter.submit();
            var submitted = adapter.snapshot();
            assertThrows(IllegalStateException.class, adapter::submit);
            assertThrows(IllegalStateException.class, () -> adapter.answerChanged(Set.of(INCORRECT)));
            assertSame(submitted, adapter.snapshot());
            assertEquals(1, restore(context).questions().getFirst().attempts().size());
        }
    }

    @Test void transactionFailureAfterDraftWritesRollsBackAndRetainsSnapshot() throws Exception {
        try (var context = open()) {
            var failing = failingAdapter(context);
            var original = failing.snapshot();
            assertThrows(IllegalStateException.class, () -> failing.answerChanged(Set.of(CORRECT)));
            assertSame(original, failing.snapshot());
            assertPersisted(original, restore(context));
        }
    }

    @Test void transactionFailureAfterSubmitWritesDoesNotCreateFalseSuccess() throws Exception {
        try (var context = open()) {
            context.adapter().answerChanged(Set.of(CORRECT));
            var failing = failingAdapter(context);
            var original = failing.snapshot();
            assertThrows(IllegalStateException.class, failing::submit);
            assertSame(original, failing.snapshot());
            var persisted = restore(context);
            assertPersisted(original, persisted);
            assertTrue(persisted.questions().getFirst().attempts().isEmpty());
            assertEquals(SharedPracticeViewModel.State.DRAFT, failing.viewModel().question().state());
            assertNull(failing.viewModel().question().result());
        }
    }

    @Test void transactionFailureAfterRetryWritesKeepsSubmittedAttempt() throws Exception {
        try (var context = open()) {
            context.adapter().answerChanged(Set.of(CORRECT));
            context.adapter().submit();
            var failing = failingAdapter(context);
            var original = failing.snapshot();
            assertThrows(IllegalStateException.class, failing::retry);
            assertSame(original, failing.snapshot());
            assertPersisted(original, restore(context));
            assertEquals(SharedPracticeViewModel.State.SUBMITTED, failing.viewModel().question().state());
        }
    }

    @Test void existingMixedBankCurrentQuestionIsSelectedThroughCoreAndTemporaryCleanupIsScoped() throws Exception {
        try (var context = open()) {
            var session = context.snapshot().session();
            context.service().updateCurrentQuestion(session.id(), session.questionBankContentId(), "q_demo_arraylist_properties");
            try (var reopened = SharedPracticeExample.open(fixture(), temporary.resolve("practice.db"))) {
                assertEquals("q_demo_arraylist_structure", reopened.snapshot().session().currentQuestionId());
                assertEquals(session.id(), reopened.snapshot().session().id());
            }
        }
        var owned = SharedPracticeExample.openTemporary(fixture());
        owned.adapter().answerChanged(Set.of(CORRECT));
        owned.close();
        owned.close();
        assertTrue(Files.exists(fixture()));
    }

    private SharedPracticeExample.Context open() { return SharedPracticeExample.open(fixture(), temporary.resolve("practice.db")); }
    private static void assertPersisted(ActivePracticeSnapshot expected, ActivePracticeSnapshot actual) {
        // Opening the session legitimately touches lastActivityAt; persisted question state must match.
        assertEquals(expected.questions(), actual.questions());
        assertEquals(expected.session().id(), actual.session().id());
        assertEquals(expected.session().currentQuestionId(), actual.session().currentQuestionId());
        assertEquals(expected.session().questionBankContentId(), actual.session().questionBankContentId());
    }
    private ActivePracticeSnapshot restore(SharedPracticeExample.Context context) {
        return context.service().openOrCreateActiveSession(context.bank(), new QuestionBankV2Codec().contentId(context.bank()));
    }
    private SharedPracticeAdapter failingAdapter(SharedPracticeExample.Context context) {
        var transaction = new SqlitePracticeTransaction(new SqliteDatabase(temporary.resolve("practice.db")));
        PracticeTransaction failAfterWrites = new PracticeTransaction() {
            @Override public <T> T execute(Function<Repositories, T> operation) {
                return transaction.execute(repositories -> {
                    operation.apply(repositories);
                    throw new IllegalStateException("Injected failure before transaction commit");
                });
            }
        };
        return new SharedPracticeAdapter(new PracticeSessionService(failAfterWrites, Clock.systemUTC()), context.snapshot());
    }
    private static Set<String> names(com.fasterxml.jackson.databind.JsonNode node) {
        var result = new java.util.HashSet<String>();
        node.fieldNames().forEachRemaining(result::add);
        return result;
    }
    private static Path fixture() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve("examples/step7-practice/Java集合练习.qbank");
            if (Files.isRegularFile(candidate)) return candidate;
            current = current.getParent();
        }
        throw new IllegalStateException("Real SINGLE_CHOICE example package not found");
    }
}
