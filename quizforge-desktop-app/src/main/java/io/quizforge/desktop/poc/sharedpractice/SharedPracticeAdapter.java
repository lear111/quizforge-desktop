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
    private final io.quizforge.core.practice.PersistentPracticeRuntime runtime;

    public SharedPracticeAdapter(PracticeSessionService service, ActivePracticeSnapshot snapshot) {
        this.service = Objects.requireNonNull(service);
        runtime = null;
        SharedPracticeViewModel.from(snapshot); // Reject unsupported current questions at the boundary.
        this.snapshot = snapshot;
    }

    /** Embedded mode shares the formal runtime, including its current question and hydrated answer. */
    public SharedPracticeAdapter(io.quizforge.core.practice.PersistentPracticeRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime);
        service = null;
        SharedPracticeViewModel.from(runtime.snapshot());
    }
    public synchronized ActivePracticeSnapshot snapshot() { return runtime == null ? snapshot : runtime.snapshot(); }
    public synchronized SharedPracticeViewModel viewModel() { return SharedPracticeViewModel.from(snapshot()); }
    public synchronized String viewModelJson() { return viewModel().toJson(); }

    public synchronized SharedPracticeViewModel answerChanged(Set<String> selectedOptionIds) {
        if (runtime != null) { runtime.saveChoiceDraft(selectedOptionIds); return viewModel(); }
        var session = snapshot.session();
        return apply(() -> service.saveDraft(session.id(), session.questionBankContentId(),
                session.currentQuestionId(), selectedOptionIds));
    }

    public synchronized SharedPracticeViewModel essayChanged(String text) {
        var answer=new io.quizforge.core.practice.EssayPracticeAnswer(text,null);
        if(runtime!=null){runtime.saveEssayDraft(runtime.session().current().id(),answer);return viewModel();}
        var session=snapshot.session();return apply(()->service.saveEssayDraft(session.id(),session.questionBankContentId(),session.currentQuestionId(),answer));
    }
    public synchronized SharedPracticeViewModel textAnswersChanged(java.util.Map<String,String> answers) {
        var converted=new java.util.LinkedHashMap<String,io.quizforge.core.practice.EssayPracticeAnswer>();
        answers.forEach((id,text)->converted.put(id,new io.quizforge.core.practice.EssayPracticeAnswer(text,null)));
        if(runtime!=null){runtime.saveTranslationDraft(converted);return viewModel();}
        var session=snapshot.session();return apply(()->service.saveTranslationDraft(session.id(),session.questionBankContentId(),session.currentQuestionId(),converted));
    }
    public synchronized SharedPracticeViewModel assignmentsChanged(java.util.Map<String,String> assignments) {
        if(runtime!=null){runtime.saveMatchingDraft(assignments);return viewModel();}
        var session=snapshot.session();return apply(()->service.saveMatchingDraft(session.id(),session.questionBankContentId(),session.currentQuestionId(),assignments));
    }
    public synchronized SharedPracticeViewModel submit() {
        if (runtime != null) { runtime.submit(); return viewModel(); }
        var session = snapshot.session();
        return apply(() -> service.submitAnswer(session.id(), session.questionBankContentId(), session.currentQuestionId()));
    }

    public synchronized SharedPracticeViewModel retry() {
        if (runtime != null) { runtime.retry(); return viewModel(); }
        var session = snapshot.session();
        // Core atomically clears active geometry and retains all frozen snapshots.
        return apply(() -> service.retryQuestion(session.id(), session.questionBankContentId(), session.currentQuestionId()));
    }

    public synchronized io.quizforge.core.practice.draft.DraftCanvasDocument loadDraft() {
        if (runtime != null) return runtime.loadActiveDraftCanvas();
        var session = snapshot.session();
        return service.loadActiveDraftCanvas(session.id(), session.questionBankContentId(), session.currentQuestionId())
                .map(io.quizforge.core.practice.draft.ActiveDraftCanvas::document)
                .orElseGet(io.quizforge.core.practice.draft.DraftCanvasDocument::createEmpty);
    }

    public synchronized void saveDraft(io.quizforge.core.practice.draft.DraftCanvasDocument document) {
        if (runtime != null) { runtime.saveActiveDraftCanvas(document); return; }
        var session = snapshot.session();
        service.saveActiveDraftCanvas(session.id(), session.questionBankContentId(), session.currentQuestionId(), document);
    }

    private SharedPracticeViewModel apply(Supplier<ActivePracticeSnapshot> operation) {
        var next = operation.get(); // Failure propagates, preserving the original snapshot object.
        var model = SharedPracticeViewModel.from(next);
        snapshot = next;
        return model;
    }
}
