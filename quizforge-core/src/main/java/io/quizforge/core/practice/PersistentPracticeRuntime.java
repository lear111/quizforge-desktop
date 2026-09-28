package io.quizforge.core.practice;

import io.quizforge.core.question.QuestionBankFile;
import io.quizforge.core.question.QuestionBankPracticeSession;

/** Persist first, then hydrate the same runtime object used by the view and its read-only Outline. */
public final class PersistentPracticeRuntime {
    private final PracticeSessionService service;
    private final QuestionBankPracticeSession session;
    private final String sessionId;
    private final String contentId;
    private final PracticeRuntimeMapper mapper = new PracticeRuntimeMapper();

    public PersistentPracticeRuntime(PracticeSessionService service, QuestionBankFile bank, String contentId) {
        this.service = service;
        this.contentId = contentId;
        session = new QuestionBankPracticeSession(bank);
        var snapshot = service.openOrCreateActiveSession(bank, contentId);
        sessionId = snapshot.session().id();
        mapper.hydrate(session, snapshot);
    }

    public QuestionBankPracticeSession session() { return session; }
    public String sessionId() { return sessionId; }

    public void select(String optionId) {
        var selected = session.selectionAfter(optionId);
        mapper.hydrate(session, service.saveDraft(sessionId, contentId, session.current().id(), selected));
    }

    public void submit() {
        mapper.hydrate(session, service.submitAnswer(sessionId, contentId, session.current().id()));
    }

    public void goTo(int index) {
        String questionId = session.bank().questions().get(index).id();
        mapper.hydrate(session, service.updateCurrentQuestion(sessionId, contentId, questionId));
    }

    public void previous() { if (session.index() > 0) goTo(session.index() - 1); }

    public void next() {
        if (session.index() < session.bank().questions().size() - 1) goTo(session.index() + 1);
        else mapper.hydrate(session, service.updateCurrentView(sessionId, contentId, PracticeSession.View.SUMMARY));
    }
}
