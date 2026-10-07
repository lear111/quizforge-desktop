package io.quizforge.core.practice;

import io.quizforge.core.question.model.*;
import io.quizforge.core.question.type.QuestionTypes;
import io.quizforge.core.question.type.QuestionTypeDefinition;
import io.quizforge.core.question.type.extension.ExternalQuestionTypeDefinition;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PracticeRuntimeMapperCacheTest {
    private static final String TYPE = "test.CACHED_SNAPSHOT";
    private final AtomicInteger calls = new AtomicInteger();
    private ExternalQuestionTypeDefinition definition() {
        return new ExternalQuestionTypeDefinition(TYPE, "Cached", QuestionTypeDefinition.Family.OBJECTIVE,
                "1.0.0", (operation, input) -> {
            if (operation.equals("snapshot")) calls.incrementAndGet();
            return switch (operation) {
                case "createDraft" -> Map.of("prompt", Map.of("kind", "TEXT", "text", "Question"),
                        "payload", Map.of("statement", "Question"), "answerSpec", Map.of("correct", true), "maxScore", 2);
                case "validate" -> Map.of("errors", List.of());
                case "snapshot", "targets" -> Map.of("targets", List.of(Map.of("id", "answer", "number", 1, "gradable", true)));
                default -> throw new IllegalArgumentException(operation);
            };
        });
    }
    @AfterEach void cleanup() { QuestionTypes.unregister(TYPE); }
    private ActivePracticeSnapshot snapshot(QuestionBank bank) {
        var now = Instant.now();
        var session = new PracticeSession("session", bank.assetId(), "content", "Bank", PracticeSession.Status.ACTIVE,
                PracticeSession.View.QUESTION, bank.questions().getFirst().id(), now, now, null);
        var rows = new ArrayList<ActivePracticeSnapshot.Question>();
        for (int i = 0; i < bank.questions().size(); i++) {
            var q = bank.questions().get(i);
            rows.add(new ActivePracticeSnapshot.Question(new PracticeSessionQuestion("row" + i, "session", q.id(), i,
                    new PracticeQuestionSnapshotMapper().map(q), PracticeSessionQuestion.State.UNANSWERED, null, now, now), List.of()));
        }
        return new ActivePracticeSnapshot(session, rows);
    }
    @Test void unchangedBankDoesNotReinvokeRulesAndStoredRevisionsAreStillChecked() {
        var type = definition(); QuestionTypes.register(type);
        var bank = new QuestionBank("qb_cache", "Bank", List.of(),
                List.of(type.createDraft(prefix -> prefix + "one", List.of()), type.createDraft(prefix -> prefix + "two", List.of())), List.of());
        var state = snapshot(bank); var runtime = new QuestionBankPracticeSession(bank); var mapper = new PracticeRuntimeMapper();
        calls.set(0); mapper.hydrate(runtime, state);
        assertEquals(2, calls.get());
        for (int i = 0; i < 10; i++) mapper.hydrate(runtime, state);
        assertEquals(2, calls.get(), "Repeated saves/navigation must reuse the checked logical snapshots");
        var original = state.questions().getFirst().sessionQuestion(); var s = original.snapshot();
        var changed = new PracticeSessionQuestion.Snapshot(s.questionType(), "Tampered", s.options(), s.correctAnswer(), s.analysis(), s.sourceRefs());
        var rows = new ArrayList<>(state.questions());
        rows.set(0, new ActivePracticeSnapshot.Question(new PracticeSessionQuestion(original.id(), original.sessionId(), original.questionId(),
                0, changed, original.practiceState(), null, original.createdAt(), original.updatedAt()), List.of()));
        assertThrows(IllegalStateException.class, () -> mapper.hydrate(runtime, new ActivePracticeSnapshot(state.session(), rows)));
    }
    @Test void replacedDefinitionAndDifferentBankInvalidateCache() {
        var type = definition(); QuestionTypes.register(type);
        var bank = new QuestionBank("qb_cache", "Bank", List.of(), List.of(type.createDraft(prefix -> prefix + "one", List.of())), List.of());
        var state = snapshot(bank); var runtime = new QuestionBankPracticeSession(bank); var mapper = new PracticeRuntimeMapper();
        mapper.hydrate(runtime, state); calls.set(0);
        QuestionTypes.unregister(TYPE); QuestionTypes.register(definition());
        mapper.hydrate(runtime, state); assertEquals(1, calls.get());
        mapper.hydrate(new QuestionBankPracticeSession(new QuestionBank(bank.assetId(), bank.title(), List.of(), bank.questions(), List.of())), state);
        assertEquals(2, calls.get());
    }
}
