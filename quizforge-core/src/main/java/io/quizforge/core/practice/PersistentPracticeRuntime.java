package io.quizforge.core.practice;

import io.quizforge.core.question.model.QuestionBank;

/** Persist first, then hydrate the same runtime object used by the view and its read-only Outline. */
public final class PersistentPracticeRuntime {
    private final PracticeSessionService service;
    private final QuestionBankPracticeSession session;
    private String sessionId;
    private final String contentId;
    private final io.quizforge.core.port.QuestionResourceInput resources;
    private final PracticeRuntimeMapper mapper = new PracticeRuntimeMapper();
    private ActivePracticeSnapshot snapshot;

    public PersistentPracticeRuntime(PracticeSessionService service, QuestionBank bank, String contentId) {
        this(service,bank,contentId,io.quizforge.core.port.QuestionResourceInput.NONE);
    }
    public PersistentPracticeRuntime(PracticeSessionService service,QuestionBank bank,String contentId,io.quizforge.core.port.QuestionResourceInput resources) {
        this.service = service;
        this.contentId = contentId;
        this.resources=resources;
        session = new QuestionBankPracticeSession(bank);
        var snapshot = service.openOrCreateActiveSession(bank, contentId,resources);
        sessionId = snapshot.session().id();
        hydrate(snapshot);
    }

    public QuestionBankPracticeSession session() { return session; }
    public String sessionId() { return sessionId; }
    public ActivePracticeSnapshot snapshot() { return snapshot; }
    public void refresh() { hydrate(service.loadActiveSession(sessionId, contentId)); }
    public io.quizforge.core.practice.draft.DraftCanvasDocument loadActiveDraftCanvas() {
        return service.loadActiveDraftCanvas(sessionId, contentId, session.current().id())
                .map(io.quizforge.core.practice.draft.ActiveDraftCanvas::document)
                .orElseGet(io.quizforge.core.practice.draft.DraftCanvasDocument::createEmpty);
    }
    public void saveActiveDraftCanvas(io.quizforge.core.practice.draft.DraftCanvasDocument document) {
        service.saveActiveDraftCanvas(sessionId, contentId, session.current().id(), document);
    }
    /** Display source switches atomically with the authoritative answer lifecycle. */
    public io.quizforge.core.practice.draft.DraftCanvasDocument loadDisplayedDraftCanvas() {
        return findDisplayedDraftCanvas().orElseGet(io.quizforge.core.practice.draft.DraftCanvasDocument::createEmpty);
    }
    public java.util.Optional<io.quizforge.core.practice.draft.DraftCanvasDocument> findDisplayedDraftCanvas() {
        var current = questionState(session.current().id());
        if (current.sessionQuestion().practiceState() == PracticeSessionQuestion.State.SUBMITTED) {
            if (current.attempts().isEmpty()) return java.util.Optional.empty();
            return service.findAttemptDraftSnapshot(current.attempts().getLast().id())
                    .map(io.quizforge.core.practice.draft.AttemptDraftSnapshot::document);
        }
        return service.loadActiveDraftCanvas(sessionId, contentId, session.current().id())
                .map(io.quizforge.core.practice.draft.ActiveDraftCanvas::document);
    }
    public void saveChoiceDraft(java.util.Set<String> selected) {
        hydrate(service.saveDraft(sessionId, contentId, session.current().id(), selected));
    }
    public void saveExtensionDraft(PracticePayload answer) {
        hydrate(service.saveExtensionDraft(sessionId, contentId, session.current().id(), answer));
    }
    public PracticePayload extensionAnswer(String questionId) {
        var row = questionState(questionId);
        return row.sessionQuestion().practiceState() == PracticeSessionQuestion.State.SUBMITTED
                ? row.attempts().getLast().answer() : row.sessionQuestion().draftAnswer();
    }
    public PracticeSummary summary() { return PracticeSummary.from(snapshot); }

    /** Only attempts belonging to the current question may be replayed. No active state is changed. */
    public io.quizforge.core.practice.draft.DraftCanvasDocument attemptDraft(String attemptId) {
        var current = questionState(session.current().id());
        if (current.attempts().stream().noneMatch(attempt -> attempt.id().equals(attemptId)))
            throw new IllegalArgumentException("Attempt does not belong to current question");
        return service.findAttemptDraftSnapshot(attemptId)
                .map(io.quizforge.core.practice.draft.AttemptDraftSnapshot::document)
                .orElseGet(io.quizforge.core.practice.draft.DraftCanvasDocument::createEmpty);
    }

    public ActivePracticeSnapshot.Question questionState(String questionId) {
        return snapshot.questions().stream().filter(row -> row.sessionQuestion().questionId().equals(questionId))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown practice question"));
    }

    private void hydrate(ActivePracticeSnapshot state) {
        mapper.hydrate(session, state);
        snapshot = state;
    }

    public void submit() {
        hydrate(service.submitAnswer(sessionId, contentId, session.current().id()));
    }

    public void retry() { hydrate(service.retryQuestion(sessionId, contentId, session.current().id())); }

    public void restart() {
        var next = service.restartPractice(sessionId, contentId, session.bank(), contentId,resources);
        hydrate(next);
        sessionId = next.session().id();
    }

    public void goTo(int index) {
        String questionId = session.bank().questions().get(index).id();
        hydrate(service.updateCurrentQuestion(sessionId, contentId, questionId));
    }

    public void previous() {
        if (session.finished()) goTo(session.bank().questions().size() - 1);
        else if (session.index() > 0) goTo(session.index() - 1);
    }

    public void next() {
        if (session.index() < session.bank().questions().size() - 1) goTo(session.index() + 1);
        else hydrate(service.updateCurrentView(sessionId, contentId, PracticeSession.View.SUMMARY));
    }
}
