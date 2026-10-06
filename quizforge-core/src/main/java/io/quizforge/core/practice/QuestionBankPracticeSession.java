package io.quizforge.core.practice;

import io.quizforge.core.question.model.Question;
import io.quizforge.core.question.model.QuestionBank;
import io.quizforge.core.question.service.QuestionBankValidator;
import io.quizforge.core.question.type.QuestionTarget;
import io.quizforge.core.question.type.QuestionTypes;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Shared navigation and outline state hydrated from committed practice snapshots. */
public final class QuestionBankPracticeSession {
    public enum State { UNANSWERED, SELECTED, SUBMITTED }

    private final QuestionBank bank;
    private final Map<Integer, Boolean> submitted = new HashMap<>();
    private final Map<Integer, State> contentStates = new HashMap<>();
    private Map<String, List<QuestionTarget>> snapshotTargets = Map.of();
    private int index;
    private boolean finished;

    public QuestionBankPracticeSession(QuestionBank bank) {
        new QuestionBankValidator().validate(bank);
        this.bank = bank;
    }

    public QuestionBank bank() { return bank; }
    public int index() { return index; }
    public boolean finished() { return finished; }
    public Question current() { return bank.questions().get(index); }
    public State state() { return state(index); }
    public State state(int questionIndex) {
        bank.questions().get(questionIndex);
        return contentStates.getOrDefault(questionIndex, State.UNANSWERED);
    }
    public boolean correct() { return correct(index); }
    public boolean correct(int questionIndex) {
        if (state(questionIndex) != State.SUBMITTED) throw new IllegalStateException("Answer has not been submitted");
        if (!submitted.containsKey(questionIndex)) throw new IllegalStateException("Answer has not been scored");
        return submitted.get(questionIndex);
    }
    void restoreTargets(Map<String, List<QuestionTarget>> targets) { snapshotTargets = Map.copyOf(targets); }
    public List<QuestionTarget> outlineTargets(Question question) {
        return snapshotTargets.containsKey(question.id()) ? snapshotTargets.get(question.id())
                : QuestionTypes.forData(question).outlineTargets(question);
    }
    void restoreState(int currentIndex, Map<Integer, Boolean> restoredSubmitted,
            boolean summary, Map<Integer, State> restoredContentStates) {
        bank.questions().get(currentIndex);
        submitted.clear();submitted.putAll(restoredSubmitted);
        contentStates.clear();contentStates.putAll(restoredContentStates);
        index=currentIndex;finished=summary;
    }
}
