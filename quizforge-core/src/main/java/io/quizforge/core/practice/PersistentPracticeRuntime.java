package io.quizforge.core.practice;

import io.quizforge.core.question.*;

/** Persist first, then hydrate the same runtime object used by the view and its read-only Outline. */
public final class PersistentPracticeRuntime {
    private final PracticeSessionService service;
    private final QuestionBankPracticeSession session;
    private String sessionId;
    private final String contentId;
    private final PracticeRuntimeMapper mapper = new PracticeRuntimeMapper();
    private ActivePracticeSnapshot snapshot;

    public PersistentPracticeRuntime(PracticeSessionService service, QuestionBank bank, String contentId) {
        this.service = service;
        this.contentId = contentId;
        session = new QuestionBankPracticeSession(bank);
        var snapshot = service.openOrCreateActiveSession(bank, contentId);
        sessionId = snapshot.session().id();
        hydrate(snapshot);
    }

    public QuestionBankPracticeSession session() { return session; }
    public String sessionId() { return sessionId; }
    public PracticeSummary summary() { return PracticeSummary.from(snapshot); }

    public ActivePracticeSnapshot.Question questionState(String questionId) {
        return snapshot.questions().stream().filter(row -> row.sessionQuestion().questionId().equals(questionId))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown practice question"));
    }

    public EssayPracticeAnswer essayAnswer(String questionId) {
        var row = questionState(questionId);
        var answer = row.sessionQuestion().practiceState() == PracticeSessionQuestion.State.SUBMITTED
                ? row.attempts().getLast().answer() : row.sessionQuestion().draftAnswer();
        return EssayPracticeAnswer.from(answer);
    }

    public void saveEssayDraft(String questionId, EssayPracticeAnswer answer) {
        hydrate(service.saveEssayDraft(sessionId, contentId, questionId, answer));
    }

    private void hydrate(ActivePracticeSnapshot state) {
        mapper.hydrate(session, state);
        snapshot = state;
    }

    public void select(String optionId) {
        var selected = session.selectionAfter(optionId);
        hydrate(service.saveDraft(sessionId, contentId, session.current().id(), selected));
    }

    public void submit() {
        hydrate(service.submitAnswer(sessionId, contentId, session.current().id()));
    }

    public void retry() { hydrate(service.retryQuestion(sessionId, contentId, session.current().id())); }

    public void restart() {
        var next = service.restartPractice(sessionId, contentId, session.bank(), contentId);
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
