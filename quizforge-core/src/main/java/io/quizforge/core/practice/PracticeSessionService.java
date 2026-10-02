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
            boolean incomplete=persistedRows.stream().filter(q->QuestionTypes.isEssay(q.snapshot().questionType()) || QuestionTypes.isCloze(q.snapshot().questionType()) || QuestionTypes.isReading(q.snapshot().questionType()) || QuestionTypes.isMatching(q.snapshot().questionType()) || QuestionTypes.isTranslation(q.snapshot().questionType())).anyMatch(q->{
                var fields=QuestionContentData.map(q.snapshot().correctAnswer().value());
                var key = QuestionTypes.isTranslation(q.snapshot().questionType()) ? "translationPresentation"
                        : QuestionTypes.isMatching(q.snapshot().questionType()) ? "matchingPresentation"
                        : QuestionTypes.isReading(q.snapshot().questionType()) ? "readingPresentation"
                        : QuestionTypes.isCloze(q.snapshot().questionType()) ? "clozePresentation" : "essayPresentation";
                if(!(fields.get(key) instanceof Map<?,?> presentation))return true;
                return resources!=io.quizforge.core.port.QuestionResourceInput.NONE
                        && ((Map<?,?>)presentation.get("resourceData")).size()<((List<?>)presentation.get("resources")).size();
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
        Set<String> selected = Set.copyOf(selectedOptionIds);
        return transactions.execute(repositories -> {
            requireCurrent(repositories, sessionId, expectedContentId, questionId);
            var question = requireAnswerable(repositories, sessionId, questionId);
            validateSelection(question.snapshot(), selected);
            boolean retrying = question.practiceState() == PracticeSessionQuestion.State.RETRYING;
            if (!retrying && repositories.attempts().countBySessionQuestion(question.id()) != 0)
                throw new IllegalStateException("Initial answer already exists");
            Instant now = clock.instant();
            repositories.questions().updateDraft(sessionId, questionId,
                    selected.isEmpty() ? null : answer(selected), retrying ? PracticeSessionQuestion.State.RETRYING
                            : selected.isEmpty() ? PracticeSessionQuestion.State.UNANSWERED : PracticeSessionQuestion.State.DRAFT, now);
            repositories.sessions().touch(sessionId, now);
            return restore(repositories, sessionId);
        });
    }

    public ActivePracticeSnapshot saveEssayDraft(String sessionId, String expectedContentId, String questionId,
            EssayPracticeAnswer answer) {
        java.util.Objects.requireNonNull(answer);
        return transactions.execute(repositories -> {
            requireCurrent(repositories, sessionId, expectedContentId, questionId);
            var question = requireQuestion(repositories, sessionId, questionId);
            if (!QuestionTypes.isEssay(question.snapshot().questionType()))
                throw new IllegalArgumentException("Essay answer requires an essay question");
            if (question.practiceState() == PracticeSessionQuestion.State.SUBMITTED)
                throw new IllegalStateException("Answer already submitted; retry before editing");
            boolean revision = question.practiceState() == PracticeSessionQuestion.State.REVISING;
            boolean retry = question.practiceState() == PracticeSessionQuestion.State.RETRYING;
            var state = revision ? PracticeSessionQuestion.State.REVISING : retry ? PracticeSessionQuestion.State.RETRYING
                    : answer.empty() ? PracticeSessionQuestion.State.UNANSWERED : PracticeSessionQuestion.State.DRAFT;
            var payload = answer.empty() ? null : answer.payload();
            if (state == question.practiceState() && java.util.Objects.equals(payload, question.draftAnswer()))
                return restore(repositories, sessionId);
            Instant now = clock.instant();
            repositories.questions().updateDraft(sessionId, questionId, payload, state, now);
            repositories.sessions().touch(sessionId, now);
            return restore(repositories, sessionId);
        });
    }

    public ActivePracticeSnapshot saveMatchingDraft(String sessionId, String expectedContentId, String questionId,
            Map<String,String> assignments) {
        var answer = new MatchingPracticeAnswer(assignments);
        return transactions.execute(repositories -> {
            requireCurrent(repositories, sessionId, expectedContentId, questionId);
            var question = requireAnswerable(repositories, sessionId, questionId);
            if (!QuestionTypes.isMatching(question.snapshot().questionType()))
                throw new IllegalArgumentException("Matching answer requires a matching question");
            validateMatching(question.snapshot(), answer.assignments());
            boolean retrying = question.practiceState() == PracticeSessionQuestion.State.RETRYING;
            if (!retrying && repositories.attempts().countBySessionQuestion(question.id()) != 0)
                throw new IllegalStateException("Initial answer already exists");
            var state = retrying ? PracticeSessionQuestion.State.RETRYING
                    : answer.empty() ? PracticeSessionQuestion.State.UNANSWERED : PracticeSessionQuestion.State.DRAFT;
            var payload = answer.empty() ? null : answer.payload();
            if (state == question.practiceState() && java.util.Objects.equals(payload, question.draftAnswer()))
                return restore(repositories, sessionId);
            Instant now = clock.instant();
            repositories.questions().updateDraft(sessionId, questionId, payload, state, now);
            repositories.sessions().touch(sessionId, now);
            return restore(repositories, sessionId);
        });
    }

    private Question validateMatching(PracticeSessionQuestion.Snapshot snapshot, Map<String,String> assignments) {
        var data = QuestionContentData.map(snapshot.correctAnswer().value());
        var question = MatchingQuestionSnapshot.from(new PracticePayload(data.get("matching"))).question();
        io.quizforge.core.question.type.objective.matching.MatchingQuestionType.validateAssignments(question, assignments);
        return question;
    }
    public ActivePracticeSnapshot saveTranslationDraft(String sessionId, String expectedContentId, String questionId,
            Map<String,EssayPracticeAnswer> answers) {
        var answer = new TranslationPracticeAnswer(answers);
        return transactions.execute(repositories -> {
            requireCurrent(repositories, sessionId, expectedContentId, questionId);
            var question = requireAnswerable(repositories, sessionId, questionId);
            validateTranslation(question.snapshot(), answers);
            boolean retrying = question.practiceState() == PracticeSessionQuestion.State.RETRYING;
            if (!retrying && repositories.attempts().countBySessionQuestion(question.id()) != 0)
                throw new IllegalStateException("Initial answer already exists");
            var state = retrying ? PracticeSessionQuestion.State.RETRYING
                    : answer.empty() ? PracticeSessionQuestion.State.UNANSWERED : PracticeSessionQuestion.State.DRAFT;
            var payload = answer.empty() ? null : answer.payload();
            if (state == question.practiceState() && java.util.Objects.equals(payload, question.draftAnswer()))
                return restore(repositories, sessionId);
            Instant now = clock.instant();
            repositories.questions().updateDraft(sessionId, questionId, payload, state, now);
            repositories.sessions().touch(sessionId, now);
            return restore(repositories, sessionId);
        });
    }
    private Question validateTranslation(PracticeSessionQuestion.Snapshot snapshot, Map<String,EssayPracticeAnswer> answers) {
        if (!QuestionTypes.isTranslation(snapshot.questionType())) throw new IllegalArgumentException("Not a translation question");
        var data = QuestionContentData.map(snapshot.correctAnswer().value());
        var source = TranslationQuestionSnapshot.from(new PracticePayload(data.get("translation"))).question();
        QuestionBankPracticeSession.validateTranslations(source, answers);
        return source;
    }

    public ActivePracticeSnapshot submitAnswer(String sessionId, String expectedContentId, String questionId) {
        return transactions.execute(repositories -> {
            requireCurrent(repositories, sessionId, expectedContentId, questionId);
            var current = requireQuestion(repositories, sessionId, questionId);
            if (QuestionTypes.isTranslation(current.snapshot().questionType())) {
                var question = requireAnswerable(repositories, sessionId, questionId);
                var answer = TranslationPracticeAnswer.from(question.draftAnswer());
                var source = validateTranslation(question.snapshot(), answer.answers());
                if (answer.empty()) throw new IllegalStateException("Write an answer first");
                boolean retrying = question.practiceState() == PracticeSessionQuestion.State.RETRYING;
                var attempts = repositories.attempts().listBySessionQuestion(question.id());
                if (retrying ? attempts.isEmpty() : !attempts.isEmpty())
                    throw new IllegalStateException("Practice attempt state is inconsistent");
                var items = (io.quizforge.core.question.type.subjective.translation.TranslationPayload) source.payload();
                double maxScore = source.scoreSpec().defaultMaxScore().doubleValue() * items.items().size();
                Instant now = clock.instant();
                repositories.attempts().append(new QuestionAttempt(id("pa_"), question.id(),
                        repositories.attempts().nextAttemptNo(question.id()), retrying ? QuestionAttempt.Mode.RETRY : QuestionAttempt.Mode.INITIAL,
                        answer.payload(), QuestionAttempt.Result.UNSCORED, null, maxScore, now));
                repositories.questions().updateDraft(sessionId, questionId, null, PracticeSessionQuestion.State.SUBMITTED, now);
                repositories.sessions().touch(sessionId, now);
                return restore(repositories, sessionId);
            }
            if (QuestionTypes.isMatching(current.snapshot().questionType())) {
                var question = requireAnswerable(repositories, sessionId, questionId);
                var answer = MatchingPracticeAnswer.from(question.draftAnswer());
                var source = validateMatching(question.snapshot(), answer.assignments());
                if (answer.empty()) throw new IllegalStateException("Select an answer first");
                var attempts = repositories.attempts().listBySessionQuestion(question.id());
                boolean retrying = question.practiceState() == PracticeSessionQuestion.State.RETRYING;
                if (retrying ? attempts.isEmpty() : !attempts.isEmpty())
                    throw new IllegalStateException("Practice attempt state is inconsistent");
                int gradable = io.quizforge.core.question.type.objective.matching.MatchingQuestionType.gradableCount(source);
                double unit = source.scoreSpec().defaultMaxScore().doubleValue();
                int matched = io.quizforge.core.question.type.objective.matching.MatchingQuestionType.matchingCount(source, answer.assignments());
                Instant now = clock.instant();
                repositories.attempts().append(new QuestionAttempt(id("pa_"), question.id(),
                        repositories.attempts().nextAttemptNo(question.id()), retrying ? QuestionAttempt.Mode.RETRY : QuestionAttempt.Mode.INITIAL,
                        answer.payload(), matched == gradable ? QuestionAttempt.Result.CORRECT : QuestionAttempt.Result.INCORRECT,
                        unit * matched, unit * gradable, now));
                repositories.questions().updateDraft(sessionId, questionId, null, PracticeSessionQuestion.State.SUBMITTED, now);
                repositories.sessions().touch(sessionId, now);
                return restore(repositories, sessionId);
            }
            if (QuestionTypes.isEssay(current.snapshot().questionType())) {
                if (current.practiceState() == PracticeSessionQuestion.State.SUBMITTED)
                    throw new IllegalStateException("Answer already submitted");
                var answer = EssayPracticeAnswer.from(current.draftAnswer());
                if (answer.empty()) throw new IllegalStateException("Write an answer first");
                boolean revision = current.practiceState() == PracticeSessionQuestion.State.REVISING;
                boolean retry = current.practiceState() == PracticeSessionQuestion.State.RETRYING;
                var attempts = repositories.attempts().listBySessionQuestion(current.id());
                if ((revision || retry) ? attempts.isEmpty() : !attempts.isEmpty())
                    throw new IllegalStateException("Practice attempt state is inconsistent");
                Instant now = clock.instant();
                repositories.attempts().append(new QuestionAttempt(id("pa_"), current.id(),
                        repositories.attempts().nextAttemptNo(current.id()), revision ? QuestionAttempt.Mode.REVISION
                                : retry ? QuestionAttempt.Mode.RETRY : QuestionAttempt.Mode.INITIAL,
                        answer.payload(), QuestionAttempt.Result.UNSCORED, null,
                        ((Map<?,?>)current.snapshot().correctAnswer().value()).get("maxScore") instanceof Number maximum ? maximum.doubleValue() : null, now));
                repositories.questions().updateDraft(sessionId, questionId, null, PracticeSessionQuestion.State.SUBMITTED, now);
                repositories.sessions().touch(sessionId, now);
                return restore(repositories, sessionId);
            }
            var question = requireAnswerable(repositories, sessionId, questionId);
            if (question.draftAnswer() == null)
                throw new IllegalStateException("Select an answer first");
            var selected = PracticeRuntimeMapper.optionIds(question.draftAnswer());
            validateSelection(question.snapshot(), selected);
            if (selected.isEmpty()) throw new IllegalStateException("Select an answer first");
            var attempts = repositories.attempts().listBySessionQuestion(question.id());
            boolean retrying = question.practiceState() == PracticeSessionQuestion.State.RETRYING;
            if (retrying ? attempts.isEmpty() : !attempts.isEmpty())
                throw new IllegalStateException("Practice attempt state is inconsistent");
            @SuppressWarnings("unchecked")
            var correctData = (Map<String, Object>) question.snapshot().correctAnswer().value();
            var correct = PracticeRuntimeMapper.optionIds(new PracticePayload(correctData.get("correctOptionIds")));
            Instant now = clock.instant();
            Double score=null,maxScore=null;
            if(QuestionTypes.isChoice(question.snapshot().questionType()) && correctData.get("maxScore") instanceof Number maximum){
                maxScore=maximum.doubleValue();score=selected.equals(correct)?maxScore:0.0;
            }
            if(QuestionTypes.isCloze(question.snapshot().questionType()) || QuestionTypes.isReading(question.snapshot().questionType())){
                var data=QuestionContentData.map(correctData.get(QuestionTypes.isReading(question.snapshot().questionType()) ? "reading" : "cloze"));maxScore=((Number)data.get("maxScore")).doubleValue();
                long matched=selected.stream().filter(correct::contains).count();
                double unitScore=data.containsKey("unitScore")?((Number)data.get("unitScore")).doubleValue():maxScore/correct.size();
                score=unitScore*matched;
            }
            repositories.attempts().append(new QuestionAttempt(id("pa_"), question.id(),
                    repositories.attempts().nextAttemptNo(question.id()),
                    retrying ? QuestionAttempt.Mode.RETRY : QuestionAttempt.Mode.INITIAL,
                    answer(selected), selected.equals(correct) ? QuestionAttempt.Result.CORRECT : QuestionAttempt.Result.INCORRECT,
                    score, maxScore, now));
            repositories.questions().updateDraft(sessionId, questionId, null, PracticeSessionQuestion.State.SUBMITTED, now);
            repositories.sessions().touch(sessionId, now);
            return restore(repositories, sessionId);
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

    private void validateSelection(PracticeSessionQuestion.Snapshot snapshot, Set<String> selected) {
        if (QuestionTypes.isReading(snapshot.questionType())) {
            var items = new java.util.HashSet<String>();
            var ids = new java.util.HashSet<String>();
            for (Object value : (List<?>) snapshot.options().value()) {
                var option = (Map<?,?>) value;
                var id = (String) option.get("id");
                ids.add(id);
                if (selected.contains(id) && !items.add((String) option.get("itemId")))
                    throw new IllegalArgumentException("每道阅读小题只能选择一个选项");
            }
            if (!ids.containsAll(selected)) throw new IllegalArgumentException("Invalid reading option IDs");
            return;
        }
        if(QuestionTypes.isCloze(snapshot.questionType())){
            var blanks=new java.util.HashSet<String>();var ids=new java.util.HashSet<String>();
            for(Object value:(List<?>)snapshot.options().value()){
                var option=(Map<?,?>)value;var id=(String)option.get("id");ids.add(id);
                if(selected.contains(id) && !blanks.add((String)option.get("blankId")))throw new IllegalArgumentException("每个空只能选择一个选项");
            }
            if(!ids.containsAll(selected))throw new IllegalArgumentException("Invalid cloze option IDs");
            return;
        }
        if (!QuestionTypes.isChoice(snapshot.questionType()))
            throw new IllegalStateException("Unsupported practice question type");
        Set<String> options = new java.util.HashSet<>();
        for (Object value : (List<?>) snapshot.options().value()) options.add((String) ((Map<?, ?>) value).get("id"));
        if (!options.containsAll(selected) || QuestionTypes.isSingleChoice(snapshot.questionType()) && selected.size() > 1)
            throw new IllegalArgumentException("Invalid selected option IDs");
    }

    private PracticePayload answer(Set<String> selected) { return new PracticePayload(selected.stream().sorted().toList()); }

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
            var snapshot = mapper.map(current,bank.resources(),resources);
            var old = byId.get(current.id());
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
