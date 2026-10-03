package io.quizforge.desktop.poc.sharedpractice;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PracticeMutationQueueTest {
    @Test void nestedAnswerSubmitAndRetryOperationsDrainInFifoOrder() {
        var queue = new PracticeMutationQueue();
        var executed = new ArrayList<String>();
        assertTrue(queue.offer(41, () -> {
            executed.add("ANSWER_CHANGED #41 started");
            assertTrue(queue.offer(42, () -> executed.add("ANSWER_CHANGED #42")));
            assertTrue(queue.offer(43, () -> executed.add("SUBMIT #43")));
            assertTrue(queue.offer(44, () -> executed.add("RETRY #44")));
            assertEquals(List.of("ANSWER_CHANGED #41 started"), executed, "Nested arrivals wait for the current operation");
            executed.add("ANSWER_CHANGED #41 completed");
        }));
        assertEquals(List.of("ANSWER_CHANGED #41 started", "ANSWER_CHANGED #41 completed",
                "ANSWER_CHANGED #42", "SUBMIT #43", "RETRY #44"), executed);
    }

    @Test void duplicateOlderAndNonJavaScriptSafeSequencesCannotExecuteAgain() {
        var queue = new PracticeMutationQueue();
        var executed = new ArrayList<Long>();
        assertFalse(queue.offer(0, () -> executed.add(0L)));
        assertFalse(queue.offer(-1, () -> executed.add(-1L)));
        assertFalse(queue.offer(PracticeMutationQueue.MAX_OPERATION_SEQUENCE + 1, () -> executed.add(Long.MAX_VALUE)));
        assertTrue(queue.offer(10, () -> executed.add(10L)));
        assertFalse(queue.offer(10, () -> executed.add(10L)));
        assertFalse(queue.offer(9, () -> executed.add(9L)));
        assertTrue(queue.offer(12, () -> executed.add(12L)));
        assertTrue(queue.offer(PracticeMutationQueue.MAX_OPERATION_SEQUENCE,
                () -> executed.add(PracticeMutationQueue.MAX_OPERATION_SEQUENCE)));
        assertEquals(List.of(10L, 12L, PracticeMutationQueue.MAX_OPERATION_SEQUENCE), executed);
    }
}
