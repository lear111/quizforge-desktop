package io.quizforge.core.practice;

import io.quizforge.core.question.model.Question;
import io.quizforge.core.question.model.QuestionBank;
import io.quizforge.core.question.service.QuestionBankValidator;
import io.quizforge.core.question.type.QuestionTypes;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** In-memory representation of practice state; persistence is handled by application commands. */
public final class QuestionBankPracticeSession {
    public enum State { UNANSWERED, SELECTED, SUBMITTED }
    public record Result(int total, int correct, int incorrect) { }

    private final QuestionBank bank;
    private final Map<Integer, Set<String>> selections = new HashMap<>();
    private final Map<Integer, Map<String,String>> matchingAssignments = new HashMap<>();
    private final Map<Integer, Map<String,EssayPracticeAnswer>> translations = new HashMap<>();
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
    public Set<String> selected() { return selected(index); }
    public Set<String> selected(int questionIndex) {
        bank.questions().get(questionIndex);
        return Set.copyOf(selections.getOrDefault(questionIndex, Set.of()));
    }
    public Map<String,String> matchingAnswers() { return matchingAnswers(index); }
    public Map<String,String> matchingAnswers(int questionIndex) {
        bank.questions().get(questionIndex);
        return Map.copyOf(matchingAssignments.getOrDefault(questionIndex, Map.of()));
    }
    public void assignMatching(String blankId, String optionId) {
        matchingAssignments.put(index, matchingAfter(blankId, optionId));
    }
    public Map<String,EssayPracticeAnswer> translationAnswers() { return translationAnswers(index); }
    public Map<String,EssayPracticeAnswer> translationAnswers(int questionIndex) {
        bank.questions().get(questionIndex);
        return Map.copyOf(translations.getOrDefault(questionIndex, Map.of()));
    }
    public void assignTranslation(String itemId, EssayPracticeAnswer answer) {
        var answers = translationAfter(itemId, answer);
        translations.put(index, answers);
        contentStates.put(index, answers.isEmpty() ? State.UNANSWERED : State.SELECTED);
    }
    public Map<String,EssayPracticeAnswer> translationAfter(String itemId, EssayPracticeAnswer answer) {
        if (state() == State.SUBMITTED) throw new IllegalStateException("Answer already submitted");
        if (!QuestionTypes.isTranslation(current().type())) throw new IllegalArgumentException("Not a translation question");
        var answers = new HashMap<>(translationAnswers());
        if (answer == null || answer.empty()) answers.remove(itemId);
        else answers.put(itemId, answer);
        validateTranslations(current(), answers);
        var payload = (io.quizforge.core.question.type.subjective.translation.TranslationPayload) current().payload();
        if (payload.items().stream().noneMatch(item -> item.id().equals(itemId)))
            throw new IllegalArgumentException("Unknown translation item");
        return Map.copyOf(answers);
    }
    static void validateTranslations(Question question, Map<String,EssayPracticeAnswer> answers) {
        if (!QuestionTypes.isTranslation(question.type())) throw new IllegalArgumentException("Not a translation question");
        var payload = (io.quizforge.core.question.type.subjective.translation.TranslationPayload) question.payload();
        var ids = payload.items().stream().map(io.quizforge.core.question.type.subjective.translation.TranslationItem::id).collect(java.util.stream.Collectors.toSet());
        if (!ids.containsAll(answers.keySet())) throw new IllegalArgumentException("Unknown translation item");
        new TranslationPracticeAnswer(answers);
    }
    /** Compute the positional change without publishing it before persistence succeeds. */
    public Map<String,String> matchingAfter(String blankId, String optionId) {
        if (state() == State.SUBMITTED) throw new IllegalStateException("Answer already submitted");
        if (!QuestionTypes.isMatching(current().type())) throw new IllegalArgumentException("Not a matching question");
        var payload = (io.quizforge.core.question.type.objective.matching.MatchingPayload) current().payload();
        var blank = payload.blanks().stream().filter(value -> value.id().equals(blankId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown matching blank"));
        if (blank.locked()) throw new IllegalArgumentException("已给出的答案不能修改");
        var answers = new HashMap<>(matchingAnswers());
        if (optionId == null || optionId.isEmpty()) answers.remove(blankId);
        else answers.put(blankId, optionId);
        io.quizforge.core.question.type.objective.matching.MatchingQuestionType.validateAssignments(current(), answers);
        return Map.copyOf(answers);
    }
    public State state() {
        return state(index);
    }
    /** Read-only inspection of any question, without changing the current position. */
    public State state(int questionIndex) {
        bank.questions().get(questionIndex); // Keep the same index bounds as the question list.
        if (contentStates.containsKey(questionIndex)) return contentStates.get(questionIndex);
        if (submitted.containsKey(questionIndex)) return State.SUBMITTED;
        return selections.getOrDefault(questionIndex, Set.of()).isEmpty()
                && matchingAssignments.getOrDefault(questionIndex, Map.of()).isEmpty()
                && translations.getOrDefault(questionIndex, Map.of()).isEmpty() ? State.UNANSWERED : State.SELECTED;
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
        if (QuestionTypes.isReading(current().type())) {
            var payload = (io.quizforge.core.question.type.objective.reading.ReadingPayload) current().payload();
            var item = payload.items().stream().filter(value -> value.options().stream().anyMatch(o -> o.id().equals(optionId))).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown reading option"));
            var selected = new HashSet<>(selected());
            item.options().forEach(option -> selected.remove(option.id()));
            selected.add(optionId);
            return Set.copyOf(selected);
        }
        if(QuestionTypes.isCloze(current().type())){
            var payload=(io.quizforge.core.question.type.objective.cloze.ClozePayload)current().payload();
            var blank=payload.blanks().stream().filter(b->b.options().stream().anyMatch(o->o.id().equals(optionId))).findFirst()
                    .orElseThrow(()->new IllegalArgumentException("Unknown cloze option"));
            var selected=new HashSet<>(selected());blank.options().forEach(o->selected.remove(o.id()));selected.add(optionId);
            return Set.copyOf(selected);
        }
        boolean found = current().choicePayload().options().stream().anyMatch(option -> option.id().equals(optionId));
        if (!found) throw new IllegalArgumentException("Unknown option");
        Set<String> selected = new HashSet<>(selected());
        if (QuestionTypes.isSingleChoice(current().type())) {
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
        restoreState(currentIndex, restoredSelections, restoredSubmitted, summary, restoredContentStates, Map.of());
    }

    public void restoreState(int currentIndex, Map<Integer, Set<String>> restoredSelections,
            Map<Integer, Boolean> restoredSubmitted, boolean summary, Map<Integer, State> restoredContentStates,
            Map<Integer, Map<String,String>> restoredMatching) {
        restoreState(currentIndex, restoredSelections, restoredSubmitted, summary, restoredContentStates, restoredMatching, Map.of());
    }
    public void restoreState(int currentIndex, Map<Integer, Set<String>> restoredSelections,
            Map<Integer, Boolean> restoredSubmitted, boolean summary, Map<Integer, State> restoredContentStates,
            Map<Integer, Map<String,String>> restoredMatching,
            Map<Integer, Map<String,EssayPracticeAnswer>> restoredTranslations) {
        bank.questions().get(currentIndex);
        var validatedTranslations = new HashMap<Integer,Map<String,EssayPracticeAnswer>>();
        restoredTranslations.forEach((questionIndex, answers) -> {
            validateTranslations(bank.questions().get(questionIndex), answers);
            validatedTranslations.put(questionIndex, new TranslationPracticeAnswer(answers).answers());
        });
        var validatedMatching = new HashMap<Integer,Map<String,String>>();
        restoredMatching.forEach((questionIndex, assignments) -> {
            var question = bank.questions().get(questionIndex);
            if (!QuestionTypes.isMatching(question.type())) throw new IllegalStateException("Invalid restored matching question");
            io.quizforge.core.question.type.objective.matching.MatchingQuestionType.validateAssignments(question, assignments);
            validatedMatching.put(questionIndex, Map.copyOf(assignments));
        });
        Map<Integer, Set<String>> validated = new HashMap<>();
        restoredSelections.forEach((questionIndex, values) -> {
            var question = bank.questions().get(questionIndex);
            Set<String> optionIds = new HashSet<>();
            if(QuestionTypes.isCloze(question.type())){
                var payload=(io.quizforge.core.question.type.objective.cloze.ClozePayload)question.payload();
                io.quizforge.core.question.type.objective.cloze.ClozeQuestionType.validateSelection(payload,values);
                payload.options().forEach(option->optionIds.add(option.id()));
            } else if (QuestionTypes.isReading(question.type())) {
                var payload = (io.quizforge.core.question.type.objective.reading.ReadingPayload) question.payload();
                io.quizforge.core.question.type.objective.reading.ReadingQuestionType.validateSelection(payload, values);
                payload.options().forEach(option -> optionIds.add(option.id()));
            } else question.choicePayload().options().forEach(option -> optionIds.add(option.id()));
            if (!optionIds.containsAll(values) || QuestionTypes.isSingleChoice(question.type()) && values.size() > 1)
                throw new IllegalStateException("Invalid restored selection");
            validated.put(questionIndex, new HashSet<>(values));
        });
        restoredSubmitted.forEach((questionIndex, correct) -> {
            bank.questions().get(questionIndex);
            if (correct == null || validated.getOrDefault(questionIndex, Set.of()).isEmpty()
                    && validatedMatching.getOrDefault(questionIndex, Map.of()).isEmpty())
                throw new IllegalStateException("Submitted answer is missing");
        });
        selections.clear(); selections.putAll(validated);
        matchingAssignments.clear(); matchingAssignments.putAll(validatedMatching);
        translations.clear(); translations.putAll(validatedTranslations);
        submitted.clear(); submitted.putAll(restoredSubmitted);
        contentStates.clear(); contentStates.putAll(restoredContentStates);
        index = currentIndex;
        finished = summary;
    }

    public boolean submit() {
        if (state() != State.SELECTED) throw new IllegalStateException("Select an answer first");
        if (QuestionTypes.isTranslation(current().type())) {
            contentStates.put(index, State.SUBMITTED);
            return false;
        }
        boolean correct = QuestionTypes.isMatching(current().type())
                ? io.quizforge.core.question.type.objective.matching.MatchingQuestionType.evaluateAssignments(current(), matchingAnswers())
                : io.quizforge.core.question.type.QuestionTypes.require(current().type()).evaluate(current(),selected());
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
        return new Result(total, correct, answered - correct);
    }

}
