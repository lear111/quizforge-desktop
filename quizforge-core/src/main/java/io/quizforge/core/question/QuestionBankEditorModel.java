package io.quizforge.core.question;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Mutable edit session over immutable QuestionBank values; identities remain local. */
public final class QuestionBankEditorModel {
    private QuestionBank bank;
    private boolean dirty;

    public QuestionBankEditorModel(QuestionBank bank) { this.bank = bank; }
    public QuestionBank bank() { return bank; }
    public boolean dirty() { return dirty; }

    public void setTitle(String title) { bank = copy(title, bank.questions()); }
    public void setStem(int question, String stem) {
        var old = entry(question);
        replace(question, new Question(old.id(), old.type(), old.stimulusRefs(), new TextContent(stem), old.choicePayload(), old.choiceAnswerSpec(), old.scoreSpec(), old.evaluationSpec(), old.analysis(), old.sourceRefs()));
    }
    public void setAnalysis(int question, String analysis) {
        var old = entry(question);
        replace(question, new Question(old.id(), old.type(), old.stimulusRefs(), old.prompt(), old.choicePayload(), old.choiceAnswerSpec(), old.scoreSpec(), old.evaluationSpec(), new TextContent(analysis), old.sourceRefs()));
    }
    public void setType(int question, String type) {
        if (!"SINGLE_CHOICE".equals(type) && !"MULTIPLE_CHOICE".equals(type))
            throw new IllegalArgumentException("Unsupported question type");
        var old = entry(question);
        replace(question, new Question(old.id(), type, old.stimulusRefs(), old.prompt(), old.choicePayload(), old.choiceAnswerSpec(), old.scoreSpec(), old.evaluationSpec(), old.analysis(), old.sourceRefs()));
    }
    public void setOptionContent(int question, int option, String content) {
        var old = entry(question);
        List<ChoiceOption> options = new ArrayList<>(old.choicePayload().options());
        options.set(option, new ChoiceOption(options.get(option).id(), new TextContent(content)));
        data(question, options, old.choiceAnswerSpec().correctOptionIds());
    }
    public void addOption(int question) {
        var old = entry(question);
        List<ChoiceOption> options = new ArrayList<>(old.choicePayload().options());
        options.add(new ChoiceOption(id("opt_"), new TextContent("New option")));
        data(question, options, old.choiceAnswerSpec().correctOptionIds());
    }
    public void deleteOption(int question, int option) {
        var old = entry(question);
        List<ChoiceOption> options = new ArrayList<>(old.choicePayload().options());
        String removed = options.remove(option).id();
        data(question, options, old.choiceAnswerSpec().correctOptionIds().stream()
                .filter(id -> !id.equals(removed)).toList());
    }
    public void setCorrect(int question, String optionId, boolean correct) {
        var old = entry(question);
        if (old.choicePayload().options().stream().noneMatch(option -> option.id().equals(optionId)))
            throw new IllegalArgumentException("Unknown option ID");
        List<String> ids = new ArrayList<>(old.choiceAnswerSpec().correctOptionIds());
        if ("SINGLE_CHOICE".equals(old.type()) && correct) ids = new ArrayList<>(List.of(optionId));
        else if (correct && !ids.contains(optionId)) ids.add(optionId);
        else if (!correct) ids.remove(optionId);
        data(question, old.choicePayload().options(), ids);
    }

    public int addQuestion(String type) {
        if (!"SINGLE_CHOICE".equals(type) && !"MULTIPLE_CHOICE".equals(type))
            throw new IllegalArgumentException("Unsupported question type");
        int optionCount = "SINGLE_CHOICE".equals(type) ? 2 : 3;
        List<ChoiceOption> options = new ArrayList<>();
        for (int i = 0; i < optionCount; i++) options.add(new ChoiceOption(id("opt_"), new TextContent("Option " + (i + 1))));
        List<String> correct = options.stream().limit("SINGLE_CHOICE".equals(type) ? 1 : 2)
                .map(ChoiceOption::id).toList();
        List<SourceRef> inherited = bank.questions().isEmpty() ? List.of()
                : bank.questions().getLast().sourceRefs();
        List<Question> questions = new ArrayList<>(bank.questions());
        questions.add(Question.choice(id("q_"), type, new TextContent("New question"), new TextContent("New analysis"), inherited, new ChoicePayload(options), new ChoiceAnswerSpec(correct)));
        bank = copy(bank.title(), questions);
        return questions.size() - 1;
    }

    public int duplicateQuestion(int question) {
        var source = entry(question);
        Map<String, String> optionIds = new LinkedHashMap<>();
        List<ChoiceOption> options = source.choicePayload().options().stream().map(option -> {
            String fresh = id("opt_");
            optionIds.put(option.id(), fresh);
            return new ChoiceOption(fresh, option.content());
        }).toList();
        List<String> correct = source.choiceAnswerSpec().correctOptionIds().stream().map(optionIds::get).toList();
        var duplicate = new Question(id("q_"), source.type(), source.stimulusRefs(), source.prompt(), new ChoicePayload(options), new ChoiceAnswerSpec(correct), source.scoreSpec(), source.evaluationSpec(), source.analysis(), source.sourceRefs());
        List<Question> questions = new ArrayList<>(bank.questions());
        questions.add(question + 1, duplicate);
        bank = copy(bank.title(), questions);
        return question + 1;
    }

    public void deleteQuestion(int question) {
        List<Question> questions = new ArrayList<>(bank.questions());
        questions.remove(question);
        bank = copy(bank.title(), questions);
    }

    public void addSourceRef(int question, SourceRef ref) {
        var old = entry(question);
        List<SourceRef> refs = new ArrayList<>(old.sourceRefs());
        refs.add(ref);
        sourceRefs(question, refs);
    }
    public void replaceSourceRef(int question, int reference, SourceRef ref) {
        var old = entry(question);
        List<SourceRef> refs = new ArrayList<>(old.sourceRefs());
        refs.set(reference, ref);
        sourceRefs(question, refs);
    }
    public void deleteSourceRef(int question, int reference) {
        var old = entry(question);
        List<SourceRef> refs = new ArrayList<>(old.sourceRefs());
        refs.remove(reference);
        sourceRefs(question, refs);
    }

    private Question entry(int question) { return bank.questions().get(question); }
    private void data(int question, List<ChoiceOption> options, List<String> correct) {
        var old = entry(question);
        replace(question, new Question(old.id(), old.type(), old.stimulusRefs(), old.prompt(), new ChoicePayload(options), new ChoiceAnswerSpec(correct), old.scoreSpec(), old.evaluationSpec(), old.analysis(), old.sourceRefs()));
    }
    private void sourceRefs(int question, List<SourceRef> refs) {
        var old = entry(question);
        List<Question> questions = new ArrayList<>(bank.questions());
        questions.set(question, new Question(old.id(), old.type(), old.stimulusRefs(), old.prompt(), old.choicePayload(), old.choiceAnswerSpec(), old.scoreSpec(), old.evaluationSpec(), old.analysis(), refs));
        validateSourceRevisions(questions);
        bank = copy(bank.title(), questions);
    }
    private void validateSourceRevisions(List<Question> questions) {
        Map<String, String> revisions = new LinkedHashMap<>();
        for (var question : questions) for (var ref : question.sourceRefs()) {
            var previous = revisions.putIfAbsent(ref.documentAssetId(), ref.documentContentId());
            if (previous != null && !previous.equals(ref.documentContentId()))
                throw new IllegalArgumentException("Questions cannot use two revisions of one document");
        }
    }
    private void replace(int question, Question replacement) {
        List<Question> questions = new ArrayList<>(bank.questions());
        questions.set(question, replacement);
        bank = copy(bank.title(), questions);
    }
    private QuestionBank copy(String title, List<Question> questions) {
        dirty = true;
        return new QuestionBank(bank.assetId(), title, bank.schemaVersion(), bank.stimuli(), questions, bank.resources());
    }
    private String id(String prefix) { return prefix + UUID.randomUUID(); }
}
