package io.quizforge.core.question;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Transient, deterministic practice state over the persisted QuestionBank model. */
public final class QuestionBankPracticeSession {
    public enum State { UNANSWERED, SELECTED, SUBMITTED }
    public record Result(int total, int correct, int incorrect, int accuracyPercent) { }

    private final QuestionBankFile bank;
    private final Map<Integer, Set<String>> selections = new HashMap<>();
    private final Map<Integer, Boolean> submitted = new HashMap<>();
    private int index;
    private boolean finished;

    public QuestionBankPracticeSession(QuestionBankFile bank) {
        new QuestionBankValidator().validate(bank);
        this.bank = bank;
    }

    public QuestionBankFile bank() { return bank; }
    public int index() { return index; }
    public boolean finished() { return finished; }
    public boolean canFinish() { return submitted.size() == bank.questions().size(); }
    public QuestionBankFile.Entry current() { return bank.questions().get(index); }
    public Set<String> selected() { return Set.copyOf(selections.getOrDefault(index, Set.of())); }
    public State state() {
        if (submitted.containsKey(index)) return State.SUBMITTED;
        return selected().isEmpty() ? State.UNANSWERED : State.SELECTED;
    }
    public boolean correct() {
        if (state() != State.SUBMITTED) throw new IllegalStateException("Answer has not been submitted");
        return submitted.get(index);
    }

    public void select(String optionId) {
        if (state() == State.SUBMITTED) throw new IllegalStateException("Answer already submitted");
        boolean found = current().data().options().stream().anyMatch(option -> option.id().equals(optionId));
        if (!found) throw new IllegalArgumentException("Unknown option");
        Set<String> selected = selections.computeIfAbsent(index, ignored -> new HashSet<>());
        if ("SINGLE_CHOICE".equals(current().type())) {
            selected.clear();
            selected.add(optionId);
        } else if (!selected.add(optionId)) selected.remove(optionId);
    }

    public boolean submit() {
        if (state() != State.SELECTED) throw new IllegalStateException("Select an answer first");
        boolean correct = selected().equals(Set.copyOf(current().data().correctOptionIds()));
        submitted.put(index, correct);
        return correct;
    }

    public void previous() { if (index > 0) index--; }

    public void next() {
        if (index < bank.questions().size() - 1) { index++; return; }
        if (!canFinish())
            throw new IllegalStateException("Submit every question before finishing");
        finished = true;
    }

    public Result result() {
        if (!finished) throw new IllegalStateException("Practice is not finished");
        int total = bank.questions().size();
        int correct = (int) submitted.values().stream().filter(Boolean::booleanValue).count();
        return new Result(total, correct, total - correct, (int) Math.round(100.0 * correct / total));
    }

    public void restart() {
        selections.clear();
        submitted.clear();
        index = 0;
        finished = false;
    }
}
