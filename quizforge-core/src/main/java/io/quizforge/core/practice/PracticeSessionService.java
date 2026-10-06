package io.quizforge.core.practice;

import io.quizforge.core.port.PracticeTransaction;
import io.quizforge.core.question.content.QuestionContentData;
import io.quizforge.core.question.model.Question;
import io.quizforge.core.question.model.QuestionBank;
import io.quizforge.core.question.service.QuestionBankValidator;
import io.quizforge.core.question.type.QuestionTypes;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Opens/restores an active round and synchronizes it to a validated current QBank revision. */
public final class PracticeSessionService {
    private final PracticeTransaction transactions;
    private final Clock clock;
    private final PracticeQuestionSnapshotMapper mapper = new PracticeQuestionSnapshotMapper();
    private final QuestionBankValidator validator = new QuestionBankValidator();

    public PracticeSessionService(PracticeTransaction transactions, Clock clock) {
        this.transactions = transactions;
        this.clock = clock;
    }

    /** The caller supplies the contentId of this exact current file model; no path is accepted. */
    public ActivePracticeSnapshot openOrCreateActiveSession(QuestionBank bank, String contentId) {
        return openOrCreateActiveSession(bank,contentId,io.quizforge.core.port.QuestionResourceInput.NONE);
    }
    public ActivePracticeSnapshot openOrCreateActiveSession(QuestionBank bank,String contentId,io.quizforge.core.port.QuestionResourceInput resources) {
        validator.validate(bank); // Includes the existing rule that empty banks cannot be practiced.
        if (contentId == null || !contentId.matches("qfb:v2:[0-9a-f]{64}")) {
            throw new IllegalArgumentException("A current QBank contentId is required.");
        }
        return transactions.execute(repositories -> {
            Instant now = clock.instant();
            PracticeSession session = repositories.sessions().findActiveByQuestionBankAssetId(bank.assetId())
                    .orElseGet(() -> create(repositories, bank, contentId, now,resources));
            var persistedRows=repositories.questions().findBySessionId(session.id());
            var persistedIds = persistedRows.stream()
                    .map(PracticeSessionQuestion::questionId).toList();
            boolean incomplete=persistedRows.stream().anyMatch(q -> {
                var fields=QuestionContentData.map(q.snapshot().correctAnswer().value());
                if(!(fields.get("extensionPresentation") instanceof Map<?,?> presentation))return true;
                if(QuestionTypes.isExtension(q.snapshot().questionType()) && Boolean.TRUE.equals(fields.get("missingExtension")))return true;
                if(!QuestionTypes.isExtension(q.snapshot().questionType()) && !Boolean.TRUE.equals(fields.get("missingExtension")))return true;
                return resources!=io.quizforge.core.port.QuestionResourceInput.NONE
                    && presentation.get("resourceData") instanceof Map<?,?> data && presentation.get("resources") instanceof List<?> catalogue && data.size()<catalogue.size();
            });
            boolean missingScores=persistedRows.stream().anyMatch(q->!QuestionContentData.map(q.snapshot().correctAnswer().value()).containsKey("maxScore"));
            if (!session.questionBankContentId().equals(contentId)
                    || !persistedIds.equals(bank.questions().stream().map(Question::id).toList()) || incomplete || missingScores) {
                synchronize(repositories, session, bank, contentId, now,resources);
            } else {
                repositories.sessions().touch(session.id(), now);
            }
            return restore(repositories, session.id());
        });
    }

    /** Refresh an existing ACTIVE round without creating a session or changing its position. */
    public ActivePracticeSnapshot loadActiveSession(String sessionId, String expectedContentId) {
        return transactions.execute(repositories -> {
            requireActive(repositories, sessionId, expectedContentId);
            return restore(repositories, sessionId);
        });
    }

    public ActivePracticeSnapshot updateCurrentQuestion(String sessionId, String expectedContentId, String questionId) {
        return transactions.execute(repositories -> {
            requireActive(repositories, sessionId, expectedContentId);
            requireQuestion(repositories, sessionId, questionId);
            repositories.sessions().updateCurrentPosition(sessionId, PracticeSession.View.QUESTION, questionId);
            repositories.sessions().touch(sessionId, clock.instant());
            return restore(repositories, sessionId);
        });
    }

    public ActivePracticeSnapshot updateCurrentView(String sessionId, String expectedContentId, PracticeSession.View view) {
        java.util.Objects.requireNonNull(view);
        return transactions.execute(repositories -> {
            var session = requireActive(repositories, sessionId, expectedContentId);
            repositories.sessions().updateCurrentPosition(sessionId, view, session.currentQuestionId());
            repositories.sessions().touch(sessionId, clock.instant());
            return restore(repositories, sessionId);
        });
    }

    public ActivePracticeSnapshot saveDraft(String sessionId, String expectedContentId, String questionId,
            Set<String> selectedOptionIds) {
        return saveExtensionDraft(sessionId,expectedContentId,questionId,new PracticePayload(Map.of("selectedOptionIds",selectedOptionIds.stream().sorted().toList())));
    }

    /** Generic answer storage for installed types, preserving the same transaction and retry gates. */
    public ActivePracticeSnapshot saveExtensionDraft(String sessionId, String expectedContentId, String questionId, PracticePayload answer) {
        java.util.Objects.requireNonNull(answer);
        return transactions.execute(repositories -> {
            requireCurrent(repositories, sessionId, expectedContentId, questionId);
            var question = requireAnswerable(repositories, sessionId, questionId);
            var type = extensionType(question.snapshot());
            boolean empty = type.validateAnswer(question.snapshot(), answer);
            boolean retrying = question.practiceState() == PracticeSessionQuestion.State.RETRYING;
            if (!retrying && repositories.attempts().countBySessionQuestion(question.id()) != 0)
                throw new IllegalStateException("Initial answer already exists");
            var now = clock.instant();
            repositories.questions().updateDraft(sessionId, questionId, empty ? null : answer,
                    retrying ? PracticeSessionQuestion.State.RETRYING : empty ? PracticeSessionQuestion.State.UNANSWERED : PracticeSessionQuestion.State.DRAFT, now);
            repositories.sessions().touch(sessionId, now);
            return restore(repositories, sessionId);
        });
    }

    private static io.quizforge.core.question.type.extension.ExternalQuestionTypeDefinition extensionType(PracticeSessionQuestion.Snapshot snapshot) {
        QuestionTypes.require(snapshot.questionType());
        var metadata = io.quizforge.core.question.type.extension.ExternalQuestionTypeDefinition.object(
                io.quizforge.core.question.type.extension.ExternalQuestionTypeDefinition.object(snapshot.correctAnswer().value()).get("extension"));
        return QuestionTypes.requireVersion(snapshot.questionType(), (String) metadata.get("version"));
    }

    /** Geometry has the same revision/current-question guard as answer working state, regardless of type. */
    public java.util.Optional<io.quizforge.core.practice.draft.ActiveDraftCanvas> loadActiveDraftCanvas(
            String sessionId, String expectedContentId, String questionId) {
        return transactions.execute(repositories -> {
            requireCurrent(repositories, sessionId, expectedContentId, questionId);
            return repositories.activeDrafts().find(requireQuestion(repositories, sessionId, questionId).id());
        });
    }

    public void saveActiveDraftCanvas(String sessionId, String expectedContentId, String questionId,
            io.quizforge.core.practice.draft.DraftCanvasDocument document) {
        java.util.Objects.requireNonNull(document);
        transactions.execute(repositories -> {
            requireCurrent(repositories, sessionId, expectedContentId, questionId);
            var question = requireQuestion(repositories, sessionId, questionId);
            if (question.practiceState() == PracticeSessionQuestion.State.SUBMITTED)
                throw new IllegalStateException("Answer already submitted; retry before editing Draft Canvas");
            Instant now = clock.instant();
            repositories.activeDrafts().save(new io.quizforge.core.practice.draft.ActiveDraftCanvas(question.id(), document, now));
            repositories.sessions().touch(sessionId, now);
            return null;
        });
    }

    public java.util.Optional<io.quizforge.core.practice.draft.AttemptDraftSnapshot> findAttemptDraftSnapshot(String attemptId) {
        return transactions.execute(repositories -> repositories.draftSnapshots().find(attemptId));
    }

    private void appendAttemptWithDraft(PracticeTransaction.Repositories repositories, QuestionAttempt attempt) {
        repositories.attempts().append(attempt);
        repositories.activeDrafts().find(attempt.sessionQuestionId()).ifPresent(active ->
                repositories.draftSnapshots().append(new io.quizforge.core.practice.draft.AttemptDraftSnapshot(
                        attempt.id(), active.document(), attempt.submittedAt())));
        repositories.activeDrafts().delete(attempt.sessionQuestionId());
    }

    public ActivePracticeSnapshot submitAnswer(String sessionId, String expectedContentId, String questionId) {
        return transactions.execute(repositories -> {
            requireCurrent(repositories,sessionId,expectedContentId,questionId);
            var question=requireAnswerable(repositories,sessionId,questionId);
            var grade=extensionType(question.snapshot()).grade(question.snapshot(),question.draftAnswer());
            boolean retrying=question.practiceState()==PracticeSessionQuestion.State.RETRYING;
            var attempts=repositories.attempts().listBySessionQuestion(question.id());
            if(retrying ? attempts.isEmpty() : !attempts.isEmpty())throw new IllegalStateException("Practice attempt state is inconsistent");
            var now=clock.instant();
            appendAttemptWithDraft(repositories,new QuestionAttempt(id("pa_"),question.id(),repositories.attempts().nextAttemptNo(question.id()),
                retrying ? QuestionAttempt.Mode.RETRY : QuestionAttempt.Mode.INITIAL,question.draftAnswer(),grade.result(),grade.score(),grade.maxScore(),now));
            repositories.questions().updateDraft(sessionId,questionId,null,PracticeSessionQuestion.State.SUBMITTED,now);
            repositories.sessions().touch(sessionId,now);return restore(repositories,sessionId);
        });
    }

    public ActivePracticeSnapshot retryQuestion(String sessionId, String expectedContentId, String questionId) {
        return transactions.execute(repositories -> {
            requireCurrent(repositories, sessionId, expectedContentId, questionId);
            var question = requireQuestion(repositories, sessionId, questionId);
            if (question.practiceState() != PracticeSessionQuestion.State.SUBMITTED
                    || repositories.attempts().countBySessionQuestion(question.id()) == 0)
                throw new IllegalStateException("Only a submitted question can be retried");
            Instant now = clock.instant();
            repositories.questions().updateDraft(sessionId, questionId, null, PracticeSessionQuestion.State.RETRYING, now);
            repositories.activeDrafts().delete(question.id());
            repositories.sessions().touch(sessionId, now);
            return restore(repositories, sessionId);
        });
    }

    public ActivePracticeSnapshot restartPractice(String sessionId, String expectedContentId,
            QuestionBank currentBank, String currentContentId) {
        return restartPractice(sessionId,expectedContentId,currentBank,currentContentId,io.quizforge.core.port.QuestionResourceInput.NONE);
    }
    public ActivePracticeSnapshot restartPractice(String sessionId,String expectedContentId,QuestionBank currentBank,
            String currentContentId,io.quizforge.core.port.QuestionResourceInput resources) {
        validator.validate(currentBank);
        if (currentContentId == null || !currentContentId.matches("qfb:v2:[0-9a-f]{64}"))
            throw new IllegalArgumentException("A current QBank contentId is required.");
        return transactions.execute(repositories -> {
            var old = requireActive(repositories, sessionId, expectedContentId);
            if (!old.questionBankAssetId().equals(currentBank.assetId()))
                throw new IllegalArgumentException("Restart bank does not match the active session");
            Instant now = clock.instant();
            repositories.sessions().archive(sessionId, now);
            var next = create(repositories, currentBank, currentContentId, now,resources);
            return restore(repositories, next.id());
        });
    }

    private PracticeSession requireActive(PracticeTransaction.Repositories repositories, String sessionId, String revision) {
        var session = repositories.sessions().findById(sessionId)
                .orElseThrow(() -> new IllegalStateException("Practice session is missing"));
        if (session.status() != PracticeSession.Status.ACTIVE)
            throw new IllegalStateException("Archived practice cannot be changed");
        if (!session.questionBankContentId().equals(revision))
            throw new IllegalStateException("Practice revision changed; reopen the bank");
        return session;
    }

    private void requireCurrent(PracticeTransaction.Repositories repositories, String sessionId, String revision, String questionId) {
        var session = requireActive(repositories, sessionId, revision);
        if (session.currentView() != PracticeSession.View.QUESTION || !questionId.equals(session.currentQuestionId()))
            throw new IllegalStateException("Current practice question changed; reopen the bank");
    }

    private PracticeSessionQuestion requireQuestion(PracticeTransaction.Repositories repositories, String sessionId, String questionId) {
        return repositories.questions().findBySessionIdAndQuestionId(sessionId, questionId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown practice question"));
    }

    private PracticeSessionQuestion requireAnswerable(PracticeTransaction.Repositories repositories, String sessionId, String questionId) {
        var question = requireQuestion(repositories, sessionId, questionId);
        if (question.practiceState() != PracticeSessionQuestion.State.UNANSWERED
                && question.practiceState() != PracticeSessionQuestion.State.DRAFT
                && question.practiceState() != PracticeSessionQuestion.State.RETRYING)
            throw new IllegalStateException("Answer already submitted");
        return question;
    }

    private PracticeSession create(PracticeTransaction.Repositories repositories, QuestionBank bank,
            String contentId, Instant now,io.quizforge.core.port.QuestionResourceInput resources) {
        var session = new PracticeSession(id("ps_"), bank.assetId(), contentId, bank.title(),
                PracticeSession.Status.ACTIVE, PracticeSession.View.QUESTION, bank.questions().getFirst().id(),
                now, now, null);
        repositories.sessions().create(session);
        List<PracticeSessionQuestion> questions = new ArrayList<>();
        for (int index = 0; index < bank.questions().size(); index++) {
            var question = bank.questions().get(index);
            questions.add(newQuestion(id("psq_"), session.id(), question.id(), index,
                    mapper.map(question,bank.resources(),resources), now, now));
        }
        repositories.questions().createAll(questions);
        return session;
    }

    private void synchronize(PracticeTransaction.Repositories repositories, PracticeSession session,
            QuestionBank bank, String contentId, Instant now,io.quizforge.core.port.QuestionResourceInput resources) {
        List<PracticeSessionQuestion> previous = repositories.questions().findBySessionId(session.id());
        Map<String, PracticeSessionQuestion> byId = new HashMap<>();
        for (var question : previous) byId.put(question.questionId(), question);
        Set<String> currentIds = bank.questions().stream().map(Question::id).collect(Collectors.toSet());
        boolean needsQuestionView = false;
        for (var question : previous) {
            if (!currentIds.contains(question.questionId())) {
                repositories.questions().deleteBySessionIdAndQuestionId(session.id(), question.questionId());
                needsQuestionView = true;
            }
        }
        for (int order = 0; order < bank.questions().size(); order++) {
            var current = bank.questions().get(order);
            var old = byId.get(current.id());
            String frozenVersion = old != null && current.type().equals(old.snapshot().questionType()) && QuestionTypes.isExtension(current.type()) && io.quizforge.core.question.type.extension.ExternalQuestionTypeDefinition.object(old.snapshot().correctAnswer().value()).get("extension") instanceof Map<?,?>
                    ? (String) io.quizforge.core.question.type.extension.ExternalQuestionTypeDefinition.object(
                            io.quizforge.core.question.type.extension.ExternalQuestionTypeDefinition.object(old.snapshot().correctAnswer().value()).get("extension")).get("version") : null;
            var snapshot = mapper.map(current,bank.resources(),resources,frozenVersion);
            // Renaming/reordering a bank must not upgrade the rules of an unchanged active question.
            if (old != null && frozenVersion != null && semanticChange(old.snapshot(), snapshot))
                snapshot = mapper.map(current,bank.resources(),resources);
            if (old == null) {
                repositories.questions().create(newQuestion(id("psq_"), session.id(), current.id(), order,
                        snapshot, now, now));
                needsQuestionView = true;
            } else if (semanticChange(old.snapshot(), snapshot)) {
                // Reset the whole active question so existing FK cascade removes its attempts.
                // Keep its persistence identity and original creation time; all operations are atomic.
                repositories.questions().deleteBySessionIdAndQuestionId(session.id(), current.id());
                repositories.questions().create(newQuestion(old.id(), session.id(), current.id(), order,
                        snapshot, old.createdAt(), now));
                needsQuestionView = true;
            } else if (!old.snapshot().equals(snapshot)) {
                repositories.questions().updateSnapshot(session.id(), current.id(), order, snapshot, now);
            } else if (old.questionOrder() != order) {
                repositories.questions().updateOrder(session.id(), current.id(), order, now);
            }
        }
        PracticeSession.View view = session.currentView();
        String currentId = session.currentQuestionId();
        if (view == PracticeSession.View.SUMMARY && needsQuestionView) view = PracticeSession.View.QUESTION;
        if (view == PracticeSession.View.QUESTION && !currentIds.contains(currentId)) {
            currentId = replacementCurrent(previous, session.currentQuestionId(), currentIds, bank.questions().getFirst().id());
        }
        if (view != session.currentView() || !java.util.Objects.equals(currentId, session.currentQuestionId())) {
            repositories.sessions().updateCurrentPosition(session.id(), view, currentId);
        }
        // Never advance the revision before the entire question synchronization succeeds.
        repositories.sessions().updateBankSnapshot(session.id(), contentId, bank.title(), now);
    }

    private boolean semanticChange(PracticeSessionQuestion.Snapshot before, PracticeSessionQuestion.Snapshot after) {
        before=PracticeQuestionSnapshotMapper.logical(before);after=PracticeQuestionSnapshotMapper.logical(after);
        return !before.questionType().equals(after.questionType()) || !before.stem().equals(after.stem())
                || !before.options().equals(after.options()) || !before.correctAnswer().equals(after.correctAnswer());
    }

    private String replacementCurrent(List<PracticeSessionQuestion> previous, String removedId,
            Set<String> currentIds, String firstId) {
        for (int index = 0; index < previous.size(); index++) {
            if (!previous.get(index).questionId().equals(removedId)) continue;
            // Prefer the nearest surviving successor in the previous order, then the predecessor.
            for (int next = index + 1; next < previous.size(); next++) {
                if (currentIds.contains(previous.get(next).questionId())) return previous.get(next).questionId();
            }
            for (int prior = index - 1; prior >= 0; prior--) {
                if (currentIds.contains(previous.get(prior).questionId())) return previous.get(prior).questionId();
            }
            break;
        }
        return firstId;
    }

    private ActivePracticeSnapshot restore(PracticeTransaction.Repositories repositories, String sessionId) {
        var session = repositories.sessions().findById(sessionId).orElseThrow();
        var questions = repositories.questions().findBySessionId(sessionId).stream()
                .map(question -> new ActivePracticeSnapshot.Question(question,
                        repositories.attempts().listBySessionQuestion(question.id()))).toList();
        return new ActivePracticeSnapshot(session, questions);
    }

    private PracticeSessionQuestion newQuestion(String id, String sessionId, String questionId, int order,
            PracticeSessionQuestion.Snapshot snapshot, Instant createdAt, Instant updatedAt) {
        return new PracticeSessionQuestion(id, sessionId, questionId, order, snapshot,
                PracticeSessionQuestion.State.UNANSWERED, null, createdAt, updatedAt);
    }

    private String id(String prefix) { return prefix + UUID.randomUUID(); }
}
