package io.quizforge.core.question.service;

import io.quizforge.core.question.content.QuestionContent;
import io.quizforge.core.question.content.QuestionContentData;
import io.quizforge.core.question.content.TextContent;
import io.quizforge.core.question.model.EvaluationCriterion;
import io.quizforge.core.question.model.EvaluationSpec;
import io.quizforge.core.question.model.Question;
import io.quizforge.core.question.model.QuestionBank;
import io.quizforge.core.question.model.ScoreSpec;
import io.quizforge.core.question.resource.QBankResource;
import io.quizforge.core.question.source.SourceRef;
import io.quizforge.core.question.type.QuestionTypes;
import io.quizforge.core.question.model.choice.ChoiceAnswerSpec;
import io.quizforge.core.question.model.choice.ChoiceOption;
import io.quizforge.core.question.model.choice.ChoicePayload;
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
        var removed = new java.util.HashSet<>(QuestionContentData.resourceIds(old.analysis()));
        removed.removeAll(QuestionContentData.resourceIds(analysis));
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
        var definition=QuestionTypes.require(type);
        var old=entry(question); if(old.type().equals(type))return;
        var removed=questionResources(old);
        var draft=definition.createDraft(this::id,old.sourceRefs());
        replace(question,new Question(old.id(),type,old.stimulusRefs(),draft.prompt(),draft.payload(),draft.answerSpec(),draft.scoreSpec(),draft.evaluationSpec(),draft.analysis(),old.sourceRefs()));
        removeUnusedImages(removed);
    }
    public void setOptionContent(int question, int option, String content) {
        var old = entry(question);
        List<ChoiceOption> options = new ArrayList<>(old.choicePayload().options());
        options.set(option, new ChoiceOption(options.get(option).id(), new TextContent(content)));
        data(question, options, old.choiceAnswerSpec().correctOptionIds());
    }

    /** Apply package authoring data without changing the bank's typed question formats. */
    public void setEditorQuestion(int index, java.util.Map<String, Object> fields) {
        var type = QuestionTypes.require(entry(index).type());
        if (type instanceof io.quizforge.core.question.type.extension.ExternalQuestionTypeDefinition extension) {
            // Check the raw candidate before the typed decoder can discard unexpected fields.
            var candidate = new java.util.LinkedHashMap<>(io.quizforge.core.question.type.extension.ExternalQuestionTypeDefinition.encodeQuestion(entry(index)));
            candidate.putAll(fields);
            extension.validateQuestionData(candidate);
        }
        setExtensionQuestion(index,fields);
    }

    private static java.util.Map<String, Object> mergedEditorFields(java.util.Map<String, Object> original,
            java.util.Map<String, Object> fields) {
        var merged = new java.util.LinkedHashMap<>(original);
        var frozen = io.quizforge.core.question.type.extension.ExternalQuestionTypeDefinition.object(new io.quizforge.core.practice.PracticePayload(fields).value());
        for (var key : java.util.List.of("prompt", "payload", "answerSpec", "analysis", "maxScore", "scoreSpec", "evaluationSpec"))
            if (frozen.containsKey(key)) merged.put(key, frozen.get(key));
        return merged;
    }

    /** Apply the extension editor's full data atomically while retaining host identity and references. */
    public void setExtensionQuestion(int index, java.util.Map<String, Object> fields) {
        var old = entry(index);
        var type = QuestionTypes.require(old.type());
        if (!(type instanceof io.quizforge.core.question.type.extension.ExternalQuestionTypeDefinition extension))
            throw new IllegalArgumentException("Not an extension question");
        // Validate/freeze the entire edit before publishing it; partial editors must not reset omitted fields.
        var original = new java.util.LinkedHashMap<>(io.quizforge.core.question.type.extension.ExternalQuestionTypeDefinition.encodeQuestion(old));
        original.put("evaluationSpec", io.quizforge.core.question.codec.QuestionDataCodec.encodeEvaluation(old.evaluationSpec()));
        var merged = mergedEditorFields(original, fields);
        var edited = extension.decodeQuestion(merged, old.id(), old.sourceRefs());
        var evaluation = io.quizforge.core.question.codec.QuestionDataCodec.decodeEvaluation(merged.get("evaluationSpec"));
        var replacement = new Question(old.id(), old.type(), old.stimulusRefs(), edited.prompt(), edited.payload(), edited.answerSpec(),
                edited.scoreSpec(), evaluation, edited.analysis(), old.sourceRefs());
        if (!old.equals(replacement)) replace(index, replacement);
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
        if (QuestionTypes.isSingleChoice(old.type()) && correct) ids = new ArrayList<>(List.of(optionId));
        else if (correct && !ids.contains(optionId)) ids.add(optionId);
        else if (!correct) ids.remove(optionId);
        data(question, old.choicePayload().options(), ids);
    }

    public int addQuestion(String type) {
        var definition=QuestionTypes.find(type).orElseThrow(()->new IllegalArgumentException("Unsupported question type: "+type));
        var inherited=bank.questions().isEmpty()?List.<SourceRef>of():bank.questions().getLast().sourceRefs();
        var questions=new ArrayList<>(bank.questions());
        questions.add(definition.createDraft(this::id,inherited));
        bank=copy(bank.title(),questions);
        return questions.size()-1;
    }

    public int duplicateQuestion(int question) {
        var source=entry(question);
        var questions=new ArrayList<>(bank.questions());
        questions.add(question+1,QuestionTypes.require(source.type()).duplicate(source,this::id));
        bank=copy(bank.title(),questions);
        return question+1;
    }

    /** Move one complete question to its final index, retaining all nested identities and resources. */
    public void moveQuestion(int from, int to) {
        java.util.Objects.checkIndex(from, bank.questions().size());
        java.util.Objects.checkIndex(to, bank.questions().size());
        if (from == to) return;
        var questions = new ArrayList<>(bank.questions());
        var moved = questions.remove(from);
        questions.add(to, moved);
        bank = copy(bank.title(), questions);
    }

    public void deleteQuestion(int question) {
        var removed = entry(question);
        var images = questionResources(removed);
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
        var old=entry(index); QuestionTypes.require(old.type());
        if(QuestionTypes.isChoice(old.type()) && !(content instanceof TextContent))throw new IllegalArgumentException("单选和多选题干仅支持普通文本");
        var removed=new java.util.HashSet<>(QuestionContentData.resourceIds(old.prompt()));removed.removeAll(QuestionContentData.resourceIds(content));
        replace(index,new Question(old.id(),old.type(),old.stimulusRefs(),content,old.payload(),old.answerSpec(),old.scoreSpec(),old.evaluationSpec(),old.analysis(),old.sourceRefs()));
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
        // The host cannot infer arbitrary nested resource references belonging to opaque extension data.
        if (bank.questions().stream().anyMatch(question -> question.payload() instanceof io.quizforge.core.question.model.extension.ExtensionPayload)) return;
        java.util.Set<String> used=new java.util.HashSet<>();
        bank.stimuli().forEach(s->used.addAll(QuestionContentData.resourceIds(s.content())));
        for(var q:bank.questions()) {
            used.addAll(questionResources(q));
        }
        bank=new QuestionBank(bank.assetId(),bank.title(),bank.schemaVersion(),bank.stimuli(),bank.questions(),bank.resources().stream()
                .filter(r->!candidates.contains(r.id()) || used.contains(r.id())).toList());
    }
    private java.util.Set<String> questionResources(Question q) {
        var resources = new java.util.HashSet<>(QuestionContentData.resourceIds(q.prompt()));
        // Opaque extension data may reference resources anywhere. Preserve the catalogue unless its rule understands the edit.
        if (q.payload() instanceof io.quizforge.core.question.model.extension.ExtensionPayload)
            bank.resources().forEach(resource -> resources.add(resource.id()));
        resources.addAll(QuestionContentData.resourceIds(q.analysis()));
        if (q.payload() instanceof ChoicePayload c) c.options().forEach(o -> resources.addAll(QuestionContentData.resourceIds(o.content())));
        return resources;
    }
}
