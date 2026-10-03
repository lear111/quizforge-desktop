package io.quizforge.desktop.poc.sharedpractice;

import io.quizforge.core.practice.ActivePracticeSnapshot;
import io.quizforge.core.practice.PracticeSessionService;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/** Thin intent adapter: the most recent successful Core snapshot is the only practice state. */
public final class SharedPracticeAdapter {
    private final PracticeSessionService service;
    private ActivePracticeSnapshot snapshot;

    public SharedPracticeAdapter(PracticeSessionService service, ActivePracticeSnapshot snapshot) {
        this.service = Objects.requireNonNull(service);
        SharedPracticeViewModel.from(snapshot); // Reject unsupported current questions at the boundary.
        this.snapshot = snapshot;
    }

    public synchronized ActivePracticeSnapshot snapshot() { return snapshot; }
    public synchronized SharedPracticeViewModel viewModel() { return SharedPracticeViewModel.from(snapshot); }
    public synchronized String viewModelJson() { return viewModel().toJson(); }

    public synchronized SharedPracticeViewModel answerChanged(Set<String> selectedOptionIds) {
        var session = snapshot.session();
        return apply(() -> service.saveDraft(session.id(), session.questionBankContentId(),
                session.currentQuestionId(), selectedOptionIds));
    }

    public synchronized SharedPracticeViewModel submit() {
        var session = snapshot.session();
        return apply(() -> service.submitAnswer(session.id(), session.questionBankContentId(), session.currentQuestionId()));
    }

    public synchronized SharedPracticeViewModel retry() {
        var session = snapshot.session();
        // Canvas strokes remain outside Practice; a later phase will specify their retry lifecycle.
        return apply(() -> service.retryQuestion(session.id(), session.questionBankContentId(), session.currentQuestionId()));
    }

    private SharedPracticeViewModel apply(Supplier<ActivePracticeSnapshot> operation) {
        var next = operation.get(); // Failure propagates, preserving the original snapshot object.
        var model = SharedPracticeViewModel.from(next);
        snapshot = next;
        return model;
    }
}
