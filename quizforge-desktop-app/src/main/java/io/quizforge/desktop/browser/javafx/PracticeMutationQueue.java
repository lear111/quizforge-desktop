package io.quizforge.desktop.browser.javafx;

import java.util.ArrayDeque;
import java.util.Objects;

/** Single-thread FIFO for one page's Practice question; contains no business state or transitions. */
final class PracticeMutationQueue {
    static final long MAX_OPERATION_SEQUENCE = 9_007_199_254_740_991L;
    private final ArrayDeque<Runnable> pending = new ArrayDeque<>();
    private long lastReceivedSeq;
    private boolean draining;

    /** Duplicate/older messages never execute again. Nested bridge calls enqueue behind this operation. */
    boolean offer(long operationSeq, Runnable operation) {
        Objects.requireNonNull(operation);
        if (operationSeq < 1 || operationSeq > MAX_OPERATION_SEQUENCE || operationSeq <= lastReceivedSeq) return false;
        lastReceivedSeq = operationSeq;
        pending.addLast(operation);
        if (draining) return true;
        draining = true;
        try {
            while (!pending.isEmpty()) pending.removeFirst().run();
        } finally { draining = false; }
        return true;
    }

    void clear() { pending.clear(); }
    void reset() { clear(); lastReceivedSeq = 0; }
}
