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
    public void saveChoiceDraft(java.util.Set<String> selected) {
        hydrate(service.saveDraft(sessionId, contentId, session.current().id(), selected));
    }
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
    public java.util.Map<String,String> matchingAnswers(String questionId) {
        var row = questionState(questionId);
        var answer = row.sessionQuestion().practiceState() == PracticeSessionQuestion.State.SUBMITTED
                ? row.attempts().getLast().answer() : row.sessionQuestion().draftAnswer();
        return MatchingPracticeAnswer.from(answer).assignments();
    }
    public void saveMatchingDraft(java.util.Map<String,String> assignments) {
        hydrate(service.saveMatchingDraft(sessionId,contentId,session.current().id(),assignments));
    }
    public void assignMatching(String blankId, String optionId) {
        var assignments = session.matchingAfter(blankId, optionId);
        hydrate(service.saveMatchingDraft(sessionId, contentId, session.current().id(), assignments));
    }
    public java.util.Map<String,EssayPracticeAnswer> translationAnswers(String questionId) {
        var row = questionState(questionId);
        var answer = row.sessionQuestion().practiceState() == PracticeSessionQuestion.State.SUBMITTED
                ? row.attempts().getLast().answer() : row.sessionQuestion().draftAnswer();
        return TranslationPracticeAnswer.from(answer).answers();
    }
    public void saveTranslationDraft(java.util.Map<String,EssayPracticeAnswer> answers) {
        hydrate(service.saveTranslationDraft(sessionId,contentId,session.current().id(),answers));
    }
    public void assignTranslation(String itemId, EssayPracticeAnswer answer) {
        var answers = session.translationAfter(itemId, answer);
        hydrate(service.saveTranslationDraft(sessionId, contentId, session.current().id(), answers));
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
