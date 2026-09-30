package io.quizforge.core.practice;

import io.quizforge.core.question.QuestionBankPracticeSession;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** One mapping between persisted state and the existing Practice/Outline runtime representation. */
public final class PracticeRuntimeMapper {
    public void hydrate(QuestionBankPracticeSession runtime, ActivePracticeSnapshot snapshot) {
        if (!runtime.bank().assetId().equals(snapshot.session().questionBankAssetId())
                || snapshot.questions().size() != runtime.bank().questions().size())
            throw new IllegalStateException("Practice snapshot does not match the bank");
        Map<Integer, Set<String>> selections = new HashMap<>();
        Map<Integer, Boolean> submitted = new HashMap<>();
        Map<Integer, QuestionBankPracticeSession.State> contentStates = new HashMap<>();
        int current = -1;
        var mapper = new PracticeQuestionSnapshotMapper();
        for (int index = 0; index < snapshot.questions().size(); index++) {
            var row = snapshot.questions().get(index);
            var question = row.sessionQuestion();
            var fileQuestion = runtime.bank().questions().get(index);
            if (!question.questionId().equals(fileQuestion.id()) || !question.snapshot().equals(mapper.map(fileQuestion)))
                throw new IllegalStateException("Practice snapshot has a different revision");
            if (question.questionId().equals(snapshot.session().currentQuestionId())) current = index;
            if ("ESSAY".equals(question.snapshot().questionType())) {
                if (question.practiceState() == PracticeSessionQuestion.State.SUBMITTED && row.attempts().isEmpty())
                    throw new IllegalStateException("Submitted essay attempt is missing");
                EssayPracticeAnswer.from(question.draftAnswer());
                row.attempts().forEach(attempt -> EssayPracticeAnswer.from(attempt.answer()));
                contentStates.put(index, question.practiceState() == PracticeSessionQuestion.State.SUBMITTED
                        ? QuestionBankPracticeSession.State.SUBMITTED : question.draftAnswer() == null
                                ? QuestionBankPracticeSession.State.UNANSWERED : QuestionBankPracticeSession.State.SELECTED);
                continue;
            }
            switch (question.practiceState()) {
                case UNANSWERED -> { }
                case DRAFT -> selections.put(index, optionIds(question.draftAnswer()));
                case RETRYING -> {
                    if (row.attempts().isEmpty()) throw new IllegalStateException("Retry attempt history is missing");
                    if (question.draftAnswer() != null) selections.put(index, optionIds(question.draftAnswer()));
                }
                case SUBMITTED -> {
                    if (row.attempts().isEmpty()) throw new IllegalStateException("Submitted attempt is missing");
                    var latest = row.attempts().getLast();
                    if (latest.result() == QuestionAttempt.Result.UNSCORED)
                        throw new IllegalStateException("Choice answer must be scored");
                    selections.put(index, optionIds(latest.answer()));
                    submitted.put(index, latest.result() == QuestionAttempt.Result.CORRECT);
                }
                default -> throw new IllegalStateException("Unsupported practice state: " + question.practiceState());
            }
        }
        if (current < 0 && snapshot.session().currentView() == PracticeSession.View.SUMMARY)
            current = runtime.bank().questions().size() - 1;
        if (current < 0) throw new IllegalStateException("Current question is missing");
        runtime.restoreState(current, selections, submitted, snapshot.session().currentView() == PracticeSession.View.SUMMARY, contentStates);
    }

    static Set<String> optionIds(PracticePayload payload) {
        if (payload == null || !(payload.value() instanceof List<?> values))
            throw new IllegalStateException("Choice answer must be an option ID list");
        Set<String> ids = new HashSet<>();
        for (Object value : values) {
            if (!(value instanceof String id) || !ids.add(id))
                throw new IllegalStateException("Invalid choice option IDs");
        }
        return Set.copyOf(ids);
    }
}
