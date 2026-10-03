package io.quizforge.core.practice;

import io.quizforge.core.practice.draft.DraftCanvasDocument;
import java.util.Objects;

/** Immutable read result; unavailable formats remain stored exactly as they were. */
public record HistoryDraftReplay(Status status, DraftCanvasDocument document, String message) {
    public enum Status { READY, MISSING, UNAVAILABLE }
    public HistoryDraftReplay {
        Objects.requireNonNull(status);
        if (status == Status.READY) Objects.requireNonNull(document);
        else if (document != null) throw new IllegalArgumentException("Only a ready replay has geometry");
    }
    public static HistoryDraftReplay ready(DraftCanvasDocument document) { return new HistoryDraftReplay(Status.READY, document, null); }
    public static HistoryDraftReplay missing() { return new HistoryDraftReplay(Status.MISSING, null, null); }
    public static HistoryDraftReplay unavailable(String message) { return new HistoryDraftReplay(Status.UNAVAILABLE, null, message); }
}
