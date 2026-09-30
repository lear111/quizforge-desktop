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
        setPrompt(question, new TextContent(stem));
    }
    public void setAnalysis(int question, String analysis) {
        setAnalysis(question, new TextContent(analysis));
    }
    public void setAnalysis(int question, QuestionContent analysis) {
        var old = entry(question);
        var removed = new java.util.HashSet<>(QuestionContentData.imageIds(old.analysis()));
        removed.removeAll(QuestionContentData.imageIds(analysis));
        replace(question, new Question(old.id(), old.type(), old.stimulusRefs(), old.prompt(), old.payload(), old.answerSpec(), old.scoreSpec(), old.evaluationSpec(), analysis, old.sourceRefs()));
        removeUnusedImages(removed);
    }
    public void setEvaluatorGuidance(int question, String guidance) {
        var old = entry(question);
        var criteria = old.evaluationSpec() == null ? List.<EvaluationCriterion>of() : old.evaluationSpec().criteria();
        var text = guidance == null || guidance.isBlank() ? null : guidance;
        var spec = text == null && criteria.isEmpty() ? null : new EvaluationSpec(criteria, text);
        replace(question, new Question(old.id(), old.type(), old.stimulusRefs(), old.prompt(), old.payload(), old.answerSpec(), old.scoreSpec(), spec, old.analysis(), old.sourceRefs()));
    }
    public void setType(int question, String type) {
        if (!"SINGLE_CHOICE".equals(type) && !"MULTIPLE_CHOICE".equals(type) && !"ESSAY".equals(type))
            throw new IllegalArgumentException("Unsupported question type");
        var old = entry(question);
        if (old.type().equals(type)) return;
        if (!"ESSAY".equals(type) && old.prompt() instanceof RichContent)
            throw new IllegalArgumentException("含富文本的作文题不能转换为选择题");
        QuestionPayload payload = "ESSAY".equals(type) ? new EssayPayload(null)
                : old.payload() instanceof ChoicePayload ? old.payload() : new ChoicePayload(List.of(
                    new ChoiceOption(id("opt_"),new TextContent("Option 1")),new ChoiceOption(id("opt_"),new TextContent("Option 2")),new ChoiceOption(id("opt_"),new TextContent("Option 3"))));
        QuestionAnswerSpec answer = "ESSAY".equals(type) ? new EssayAnswerSpec(null)
                : old.answerSpec() instanceof ChoiceAnswerSpec ? old.answerSpec() : new ChoiceAnswerSpec(
                    ((ChoicePayload)payload).options().stream().limit("SINGLE_CHOICE".equals(type)?1:2).map(ChoiceOption::id).toList());
        replace(question, new Question(old.id(), type, old.stimulusRefs(), old.prompt(), payload, answer, old.scoreSpec(), old.evaluationSpec(), old.analysis(), old.sourceRefs()));
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
        if (!"SINGLE_CHOICE".equals(type) && !"MULTIPLE_CHOICE".equals(type) && !"ESSAY".equals(type))
            throw new IllegalArgumentException("Unsupported question type");
        int optionCount = "SINGLE_CHOICE".equals(type) ? 2 : 3;
        List<ChoiceOption> options = new ArrayList<>();
        for (int i = 0; i < optionCount; i++) options.add(new ChoiceOption(id("opt_"), new TextContent("Option " + (i + 1))));
        List<String> correct = options.stream().limit("SINGLE_CHOICE".equals(type) ? 1 : 2)
                .map(ChoiceOption::id).toList();
        List<SourceRef> inherited = bank.questions().isEmpty() ? List.of()
                : bank.questions().getLast().sourceRefs();
        List<Question> questions = new ArrayList<>(bank.questions());
        questions.add("ESSAY".equals(type) ? new Question(id("q_"),type,List.of(),new TextContent("New essay question"),
                new EssayPayload(null),new EssayAnswerSpec(null),ScoreSpec.defaultScore(),null,null,inherited)
                : Question.choice(id("q_"), type, new TextContent("New question"), new TextContent("New analysis"), inherited, new ChoicePayload(options), new ChoiceAnswerSpec(correct)));
        bank = copy(bank.title(), questions);
        return questions.size() - 1;
    }

    public int duplicateQuestion(int question) {
        var source = entry(question);
        if ("ESSAY".equals(source.type())) {
            List<Question> questions = new ArrayList<>(bank.questions());
            questions.add(question + 1,new Question(id("q_"),source.type(),source.stimulusRefs(),source.prompt(),source.payload(),source.answerSpec(),source.scoreSpec(),source.evaluationSpec(),source.analysis(),source.sourceRefs()));
            bank=copy(bank.title(),questions);
            return question+1;
        }
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
        var removed = entry(question);
        var images = new java.util.HashSet<>(QuestionContentData.imageIds(removed.prompt()));
        images.addAll(QuestionContentData.imageIds(removed.analysis()));
        if (removed.answerSpec() instanceof EssayAnswerSpec essay)
            images.addAll(QuestionContentData.imageIds(essay.referenceAnswer()));
        List<Question> questions = new ArrayList<>(bank.questions());
        questions.remove(question);
        bank = copy(bank.title(), questions);
        removeUnusedImages(images);
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
        questions.set(question, new Question(old.id(), old.type(), old.stimulusRefs(), old.prompt(), old.payload(), old.answerSpec(), old.scoreSpec(), old.evaluationSpec(), old.analysis(), refs));
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

    public void setPrompt(int index, QuestionContent content) {
        var old=entry(index); var removed=new java.util.HashSet<>(QuestionContentData.imageIds(old.prompt()));
        removed.removeAll(QuestionContentData.imageIds(content));
        replace(index,new Question(old.id(),old.type(),old.stimulusRefs(),content,old.payload(),old.answerSpec(),old.scoreSpec(),old.evaluationSpec(),old.analysis(),old.sourceRefs()));
        removeUnusedImages(removed);
    }
    public void setEssayPayload(int index, EssayPayload payload) {
        var q=entry(index);
        if(!"ESSAY".equals(q.type())) throw new IllegalArgumentException("Not an essay");
        replace(index,new Question(q.id(),q.type(),q.stimulusRefs(),q.prompt(),payload,q.answerSpec(),q.scoreSpec(),q.evaluationSpec(),q.analysis(),q.sourceRefs()));
    }
    public void setReferenceAnswer(int index, QuestionContent reference) {
        var q=entry(index); var removed=new java.util.HashSet<>(QuestionContentData.imageIds(q.essayAnswerSpec().referenceAnswer()));
        removed.removeAll(QuestionContentData.imageIds(reference));
        replace(index,new Question(q.id(),q.type(),q.stimulusRefs(),q.prompt(),q.payload(),new EssayAnswerSpec(reference),q.scoreSpec(),q.evaluationSpec(),q.analysis(),q.sourceRefs()));
        removeUnusedImages(removed);
    }
    public void setMaxScore(int index, java.math.BigDecimal score) {
        if(score==null || score.signum()<=0) throw new IllegalArgumentException("分值必须大于 0");
        var q=entry(index);
        replace(index,new Question(q.id(),q.type(),q.stimulusRefs(),q.prompt(),q.payload(),q.answerSpec(),new ScoreSpec(score),q.evaluationSpec(),q.analysis(),q.sourceRefs()));
    }
    public void addResource(QBankResource resource) {
        if(bank.resources().stream().anyMatch(r->r.id().equals(resource.id()))) throw new IllegalArgumentException("Duplicate resource");
        var resources=new ArrayList<>(bank.resources());resources.add(resource);
        bank=new QuestionBank(bank.assetId(),bank.title(),bank.schemaVersion(),bank.stimuli(),bank.questions(),resources);dirty=true;
    }
    private void removeUnusedImages(java.util.Set<String> candidates) {
        if(candidates.isEmpty()) return;
        java.util.Set<String> used=new java.util.HashSet<>();
        bank.stimuli().forEach(s->used.addAll(QuestionContentData.imageIds(s.content())));
        for(var q:bank.questions()) {
            used.addAll(QuestionContentData.imageIds(q.prompt()));used.addAll(QuestionContentData.imageIds(q.analysis()));
            if(q.answerSpec() instanceof EssayAnswerSpec e) used.addAll(QuestionContentData.imageIds(e.referenceAnswer()));
            if(q.payload() instanceof ChoicePayload c) c.options().forEach(o->used.addAll(QuestionContentData.imageIds(o.content())));
        }
        bank=new QuestionBank(bank.assetId(),bank.title(),bank.schemaVersion(),bank.stimuli(),bank.questions(),bank.resources().stream()
                .filter(r->!candidates.contains(r.id()) || used.contains(r.id())).toList());
    }
}
