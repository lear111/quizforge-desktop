package io.quizforge.core.question;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Mutable edit session over immutable QuestionBankFile values; identities remain local. */
public final class QuestionBankEditorModel {
    private QuestionBankFile bank;
    private boolean dirty;

    public QuestionBankEditorModel(QuestionBankFile bank) { this.bank = bank; }
    public QuestionBankFile bank() { return bank; }
    public boolean dirty() { return dirty; }

    public void setTitle(String title) { bank = copy(title, bank.sourceDocuments(), bank.questions()); }
    public void setStem(int question, String stem) {
        var old = entry(question);
        replace(question, new QuestionBankFile.Entry(old.id(), old.type(), stem, old.analysis(),
                old.sourceRefs(), old.data()));
    }
    public void setAnalysis(int question, String analysis) {
        var old = entry(question);
        replace(question, new QuestionBankFile.Entry(old.id(), old.type(), old.stem(), analysis,
                old.sourceRefs(), old.data()));
    }
    public void setType(int question, String type) {
        if (!"SINGLE_CHOICE".equals(type) && !"MULTIPLE_CHOICE".equals(type))
            throw new IllegalArgumentException("Unsupported question type");
        var old = entry(question);
        replace(question, new QuestionBankFile.Entry(old.id(), type, old.stem(), old.analysis(),
                old.sourceRefs(), old.data()));
    }
    public void setOptionContent(int question, int option, String content) {
        var old = entry(question);
        List<QuestionBankFile.Option> options = new ArrayList<>(old.data().options());
        options.set(option, new QuestionBankFile.Option(options.get(option).id(), content));
        data(question, options, old.data().correctOptionIds());
    }
    public void addOption(int question) {
        var old = entry(question);
        List<QuestionBankFile.Option> options = new ArrayList<>(old.data().options());
        options.add(new QuestionBankFile.Option(id("opt_"), "New option"));
        data(question, options, old.data().correctOptionIds());
    }
    public void deleteOption(int question, int option) {
        var old = entry(question);
        List<QuestionBankFile.Option> options = new ArrayList<>(old.data().options());
        String removed = options.remove(option).id();
        data(question, options, old.data().correctOptionIds().stream()
                .filter(id -> !id.equals(removed)).toList());
    }
    public void setCorrect(int question, String optionId, boolean correct) {
        var old = entry(question);
        if (old.data().options().stream().noneMatch(option -> option.id().equals(optionId)))
            throw new IllegalArgumentException("Unknown option ID");
        List<String> ids = new ArrayList<>(old.data().correctOptionIds());
        if ("SINGLE_CHOICE".equals(old.type()) && correct) ids = new ArrayList<>(List.of(optionId));
        else if (correct && !ids.contains(optionId)) ids.add(optionId);
        else if (!correct) ids.remove(optionId);
        data(question, old.data().options(), ids);
    }

    public int addQuestion(String type) {
        if (!"SINGLE_CHOICE".equals(type) && !"MULTIPLE_CHOICE".equals(type))
            throw new IllegalArgumentException("Unsupported question type");
        int optionCount = "SINGLE_CHOICE".equals(type) ? 2 : 3;
        List<QuestionBankFile.Option> options = new ArrayList<>();
        for (int i = 0; i < optionCount; i++) options.add(new QuestionBankFile.Option(id("opt_"), "Option " + (i + 1)));
        List<String> correct = options.stream().limit("SINGLE_CHOICE".equals(type) ? 1 : 2)
                .map(QuestionBankFile.Option::id).toList();
        List<QuestionBankFile.SourceRef> inherited = bank.questions().isEmpty() ? List.of()
                : bank.questions().getLast().sourceRefs();
        List<QuestionBankFile.Entry> questions = new ArrayList<>(bank.questions());
        questions.add(new QuestionBankFile.Entry(id("q_"), type, "New question", "New analysis",
                inherited, new QuestionBankFile.Data(options, correct)));
        bank = copy(bank.title(), bank.sourceDocuments(), questions);
        return questions.size() - 1;
    }

    public int duplicateQuestion(int question) {
        var source = entry(question);
        Map<String, String> optionIds = new LinkedHashMap<>();
        List<QuestionBankFile.Option> options = source.data().options().stream().map(option -> {
            String fresh = id("opt_");
            optionIds.put(option.id(), fresh);
            return new QuestionBankFile.Option(fresh, option.content());
        }).toList();
        List<String> correct = source.data().correctOptionIds().stream().map(optionIds::get).toList();
        var duplicate = new QuestionBankFile.Entry(id("q_"), source.type(), source.stem(),
                source.analysis(), source.sourceRefs(), new QuestionBankFile.Data(options, correct));
        List<QuestionBankFile.Entry> questions = new ArrayList<>(bank.questions());
        questions.add(question + 1, duplicate);
        bank = copy(bank.title(), bank.sourceDocuments(), questions);
        return question + 1;
    }

    public void deleteQuestion(int question) {
        List<QuestionBankFile.Entry> questions = new ArrayList<>(bank.questions());
        questions.remove(question);
        bank = copy(bank.title(), sources(questions), questions);
    }

    public void addSourceRef(int question, QuestionBankFile.SourceRef ref) {
        var old = entry(question);
        List<QuestionBankFile.SourceRef> refs = new ArrayList<>(old.sourceRefs());
        refs.add(ref);
        sourceRefs(question, refs);
    }
    public void replaceSourceRef(int question, int reference, QuestionBankFile.SourceRef ref) {
        var old = entry(question);
        List<QuestionBankFile.SourceRef> refs = new ArrayList<>(old.sourceRefs());
        refs.set(reference, ref);
        sourceRefs(question, refs);
    }
    public void deleteSourceRef(int question, int reference) {
        var old = entry(question);
        List<QuestionBankFile.SourceRef> refs = new ArrayList<>(old.sourceRefs());
        refs.remove(reference);
        sourceRefs(question, refs);
    }

    private QuestionBankFile.Entry entry(int question) { return bank.questions().get(question); }
    private void data(int question, List<QuestionBankFile.Option> options, List<String> correct) {
        var old = entry(question);
        replace(question, new QuestionBankFile.Entry(old.id(), old.type(), old.stem(), old.analysis(),
                old.sourceRefs(), new QuestionBankFile.Data(options, correct)));
    }
    private void sourceRefs(int question, List<QuestionBankFile.SourceRef> refs) {
        var old = entry(question);
        List<QuestionBankFile.Entry> questions = new ArrayList<>(bank.questions());
        questions.set(question, new QuestionBankFile.Entry(old.id(), old.type(), old.stem(),
                old.analysis(), refs, old.data()));
        bank = copy(bank.title(), sources(questions), questions);
    }
    private List<QuestionBankFile.SourceDocument> sources(List<QuestionBankFile.Entry> questions) {
        Map<String, QuestionBankFile.SourceDocument> sources = new LinkedHashMap<>();
        for (var question : questions) for (var ref : question.sourceRefs()) {
            var candidate = new QuestionBankFile.SourceDocument(ref.documentAssetId(),
                    ref.documentContentId(), ref.documentTitle());
            var previous = sources.putIfAbsent(candidate.assetId(), candidate);
            if (previous != null && !previous.contentId().equals(candidate.contentId()))
                throw new IllegalArgumentException("Questions cannot use two revisions of one document");
        }
        return List.copyOf(sources.values());
    }
    private void replace(int question, QuestionBankFile.Entry replacement) {
        List<QuestionBankFile.Entry> questions = new ArrayList<>(bank.questions());
        questions.set(question, replacement);
        bank = copy(bank.title(), bank.sourceDocuments(), questions);
    }
    private QuestionBankFile copy(String title, List<QuestionBankFile.SourceDocument> sources,
            List<QuestionBankFile.Entry> questions) {
        dirty = true;
        return new QuestionBankFile(bank.format(), bank.schemaVersion(), bank.id(), title, sources, questions);
    }
    private String id(String prefix) { return prefix + UUID.randomUUID(); }
}
