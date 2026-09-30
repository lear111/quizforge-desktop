package io.quizforge.core.question;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** In-memory representation of practice state; persistence is handled by application commands. */
public final class QuestionBankPracticeSession {
    public enum State { UNANSWERED, SELECTED, SUBMITTED }
    public record Result(int total, int correct, int incorrect, int accuracyPercent) { }

    private final QuestionBank bank;
    private final Map<Integer, Set<String>> selections = new HashMap<>();
    private final Map<Integer, Boolean> submitted = new HashMap<>();
    private final Map<Integer, State> contentStates = new HashMap<>();
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
    public Set<String> selected() { return Set.copyOf(selections.getOrDefault(index, Set.of())); }
    public State state() {
        return state(index);
    }
    /** Read-only inspection of any question, without changing the current position. */
    public State state(int questionIndex) {
        bank.questions().get(questionIndex); // Keep the same index bounds as the question list.
        if (contentStates.containsKey(questionIndex)) return contentStates.get(questionIndex);
        if (submitted.containsKey(questionIndex)) return State.SUBMITTED;
        return selections.getOrDefault(questionIndex, Set.of()).isEmpty() ? State.UNANSWERED : State.SELECTED;
    }
    public boolean correct() {
        return correct(index);
    }
    public boolean correct(int questionIndex) {
        if (state(questionIndex) != State.SUBMITTED) throw new IllegalStateException("Answer has not been submitted");
        if (!submitted.containsKey(questionIndex)) throw new IllegalStateException("Answer has not been scored");
        return submitted.get(questionIndex);
    }

    public void select(String optionId) {
        selections.put(index, new HashSet<>(selectionAfter(optionId)));
    }

    /** Compute a selection without changing the visible state before persistence succeeds. */
    public Set<String> selectionAfter(String optionId) {
        if (state() == State.SUBMITTED) throw new IllegalStateException("Answer already submitted");
        boolean found = current().choicePayload().options().stream().anyMatch(option -> option.id().equals(optionId));
        if (!found) throw new IllegalArgumentException("Unknown option");
        Set<String> selected = new HashSet<>(selected());
        if ("SINGLE_CHOICE".equals(current().type())) {
            selected.clear();
            selected.add(optionId);
        } else if (!selected.add(optionId)) selected.remove(optionId);
        return Set.copyOf(selected);
    }

    /** Direct hydration, never replayed clicks or submissions. Validate before replacing state. */
    public void restoreState(int currentIndex, Map<Integer, Set<String>> restoredSelections,
            Map<Integer, Boolean> restoredSubmitted, boolean summary) {
        restoreState(currentIndex, restoredSelections, restoredSubmitted, summary, Map.of());
    }

    public void restoreState(int currentIndex, Map<Integer, Set<String>> restoredSelections,
            Map<Integer, Boolean> restoredSubmitted, boolean summary, Map<Integer, State> restoredContentStates) {
        bank.questions().get(currentIndex);
        Map<Integer, Set<String>> validated = new HashMap<>();
        restoredSelections.forEach((questionIndex, values) -> {
            var question = bank.questions().get(questionIndex);
            Set<String> optionIds = new HashSet<>();
            question.choicePayload().options().forEach(option -> optionIds.add(option.id()));
            if (!optionIds.containsAll(values) || "SINGLE_CHOICE".equals(question.type()) && values.size() > 1)
                throw new IllegalStateException("Invalid restored selection");
            validated.put(questionIndex, new HashSet<>(values));
        });
        restoredSubmitted.forEach((questionIndex, correct) -> {
            bank.questions().get(questionIndex);
            if (correct == null || validated.getOrDefault(questionIndex, Set.of()).isEmpty())
                throw new IllegalStateException("Submitted answer is missing");
        });
        selections.clear(); selections.putAll(validated);
        submitted.clear(); submitted.putAll(restoredSubmitted);
        contentStates.clear(); contentStates.putAll(restoredContentStates);
        index = currentIndex;
        finished = summary;
    }

    public boolean submit() {
        if (state() != State.SELECTED) throw new IllegalStateException("Select an answer first");
        boolean correct = selected().equals(Set.copyOf(current().choiceAnswerSpec().correctOptionIds()));
        submitted.put(index, correct);
        return correct;
    }

    public void previous() { if (index > 0) index--; }

    public void next() {
        if (index < bank.questions().size() - 1) { index++; return; }
        finished = true;
    }

    public Result result() {
        if (!finished) throw new IllegalStateException("Practice is not finished");
        int total = bank.questions().size();
        int correct = (int) submitted.values().stream().filter(Boolean::booleanValue).count();
        int answered = submitted.size();
        return new Result(total, correct, answered - correct,
                answered == 0 ? 0 : (int) Math.round(100.0 * correct / answered));
    }

}
