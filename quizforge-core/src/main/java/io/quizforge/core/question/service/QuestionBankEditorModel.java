package io.quizforge.core.question.service;

import io.quizforge.core.question.content.QuestionContent;
import io.quizforge.core.question.content.QuestionContentData;
import io.quizforge.core.question.content.TextContent;
import io.quizforge.core.question.model.EvaluationCriterion;
import io.quizforge.core.question.model.EvaluationSpec;
import io.quizforge.core.question.model.Question;
import io.quizforge.core.question.model.QuestionAnswerSpec;
import io.quizforge.core.question.model.QuestionBank;
import io.quizforge.core.question.model.QuestionPayload;
import io.quizforge.core.question.model.ScoreSpec;
import io.quizforge.core.question.resource.QBankResource;
import io.quizforge.core.question.source.SourceRef;
import io.quizforge.core.question.type.QuestionTypes;
import io.quizforge.core.question.type.objective.choice.ChoiceAnswerSpec;
import io.quizforge.core.question.type.objective.choice.ChoiceOption;
import io.quizforge.core.question.type.objective.choice.ChoicePayload;
import io.quizforge.core.question.type.subjective.essay.EssayAnswerSpec;
import io.quizforge.core.question.type.subjective.essay.EssayPayload;
import io.quizforge.core.question.type.objective.cloze.*;
import io.quizforge.core.question.type.objective.reading.*;
import io.quizforge.core.question.type.objective.matching.*;
import io.quizforge.core.question.type.subjective.translation.*;
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
        QuestionTypes.find(type).orElseThrow(()->new IllegalArgumentException("Unsupported question type: "+type));
        var old = entry(question);
        if (old.type().equals(type)) return;
        var removed = questionResources(old);
        if (QuestionTypes.isTranslation(type)) {
            var items = new ArrayList<TranslationItem>();
            for (var sentence : TranslationQuestionType.sentences(QuestionContentData.plainText(old.prompt())))
                items.add(new TranslationItem(id("item_"), items.size() + 1, sentence));
            var answers = items.stream().map(item -> new TranslationAnswerSpec.Answer(item.id(), null)).toList();
            replace(question, new Question(old.id(), type, old.stimulusRefs(), old.prompt(), new TranslationPayload(items),
                    new TranslationAnswerSpec(answers), new ScoreSpec(new java.math.BigDecimal("2")), old.evaluationSpec(), old.analysis(), old.sourceRefs()));
            removeUnusedImages(removed);
            return;
        }
        if (QuestionTypes.isReading(type) || QuestionTypes.isMatching(type)) {
            var draft = QuestionTypes.require(type).createDraft(this::id, old.sourceRefs());
            replace(question, new Question(old.id(), type, old.stimulusRefs(), old.prompt(), draft.payload(), draft.answerSpec(),
                    draft.scoreSpec(), old.evaluationSpec(), old.analysis(), old.sourceRefs()));
            removeUnusedImages(removed);
            return;
        }
        if(QuestionTypes.isCloze(type)){
            var numbers=ClozeQuestionType.numbers(QuestionContentData.plainText(old.prompt()));ClozeQuestionType.validateNumbers(numbers);
            var blanks=numbers.stream().map(number->ClozeQuestionType.newBlank(number,this::id)).toList();
            var answers=blanks.stream().map(b->new ClozeAnswerSpec.Answer(b.id(),b.options().getFirst().id())).toList();
            replace(question,new Question(old.id(),type,old.stimulusRefs(),old.prompt(),new ClozePayload(blanks),new ClozeAnswerSpec(answers),old.scoreSpec(),old.evaluationSpec(),old.analysis(),old.sourceRefs()));
            removeUnusedImages(removed);
            return;
        }
        if (!QuestionTypes.isEssay(type) && !(old.prompt() instanceof TextContent))
            throw new IllegalArgumentException("含富文本的题目不能转换为选择题");
        QuestionPayload payload = QuestionTypes.isEssay(type) ? new EssayPayload(null)
                : old.payload() instanceof ChoicePayload ? old.payload() : new ChoicePayload(List.of(
                    new ChoiceOption(id("opt_"),new TextContent("Option 1")),new ChoiceOption(id("opt_"),new TextContent("Option 2")),new ChoiceOption(id("opt_"),new TextContent("Option 3"))));
        QuestionAnswerSpec answer = QuestionTypes.isEssay(type) ? new EssayAnswerSpec(null)
                : old.answerSpec() instanceof ChoiceAnswerSpec ? old.answerSpec() : new ChoiceAnswerSpec(
                    ((ChoicePayload)payload).options().stream().limit(QuestionTypes.isSingleChoice(type)?1:2).map(ChoiceOption::id).toList());
        replace(question, new Question(old.id(), type, old.stimulusRefs(), old.prompt(), payload, answer, old.scoreSpec(), old.evaluationSpec(), old.analysis(), old.sourceRefs()));
        removeUnusedImages(removed);
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
        var old=entry(index); var removed=new java.util.HashSet<>(QuestionContentData.resourceIds(old.prompt()));
        removed.removeAll(QuestionContentData.resourceIds(content));
        var payload=old.payload();var answer=old.answerSpec();
        if(QuestionTypes.isCloze(old.type())){
            var numbers=ClozeQuestionType.numbers(QuestionContentData.plainText(content));
            var previous=(ClozePayload)payload;var correct=(ClozeAnswerSpec)answer;
            ClozeQuestionType.validateNewNumbers(numbers,previous.blanks().size());
            var blanks=new ArrayList<>(previous.blanks());
            int last=numbers.stream().mapToInt(Integer::intValue).max().orElse(0);
            for(int number=blanks.size()+1;number<=last;number++)blanks.add(ClozeQuestionType.newBlank(number,this::id));
            var answers=blanks.stream().map(b->correct.answers().stream().filter(a->a.blankId().equals(b.id())).findFirst()
                    .orElseGet(()->new ClozeAnswerSpec.Answer(b.id(),b.options().getFirst().id()))).toList();
            payload=new ClozePayload(blanks);answer=new ClozeAnswerSpec(answers);
        }
        if (QuestionTypes.isTranslation(old.type())) {
            var sentences = TranslationQuestionType.sentences(QuestionContentData.plainText(content));
            var previous = (TranslationPayload) payload;
            var references = ((TranslationAnswerSpec) answer).referenceAnswers();
            // Match identical sentence occurrences in order. Changed text never inherits an unrelated answer.
            var available = new LinkedHashMap<String, java.util.ArrayDeque<TranslationItem>>();
            previous.items().forEach(item -> available.computeIfAbsent(item.text(), ignored -> new java.util.ArrayDeque<>()).add(item));
            var items = new ArrayList<TranslationItem>();
            var answers = new ArrayList<TranslationAnswerSpec.Answer>();
            for (var sentence : sentences) {
                var occurrences = available.get(sentence);
                String itemId = occurrences == null || occurrences.isEmpty() ? id("item_") : occurrences.removeFirst().id();
                items.add(new TranslationItem(itemId, items.size() + 1, sentence));
                answers.add(new TranslationAnswerSpec.Answer(itemId, references.get(itemId)));
            }
            references.values().forEach(reference -> removed.addAll(QuestionContentData.resourceIds(reference)));
            payload = new TranslationPayload(items);
            answer = new TranslationAnswerSpec(answers);
        }
        replace(index,new Question(old.id(),old.type(),old.stimulusRefs(),content,payload,answer,old.scoreSpec(),old.evaluationSpec(),old.analysis(),old.sourceRefs()));
        removeUnusedImages(removed);
    }
    public void setClozeOption(int index,String blankId,int option,String text){
        var q=entry(index);var blanks=new ArrayList<>(((ClozePayload)q.payload()).blanks());
        for(int i=0;i<blanks.size();i++)if(blanks.get(i).id().equals(blankId)){
            var b=blanks.get(i);var options=new ArrayList<>(b.options());
            options.set(option,new ChoiceOption(options.get(option).id(),new TextContent(text)));
            blanks.set(i,new ClozeBlank(b.id(),b.number(),options));
        }
        replace(index,new Question(q.id(),q.type(),q.stimulusRefs(),q.prompt(),new ClozePayload(blanks),q.answerSpec(),q.scoreSpec(),q.evaluationSpec(),q.analysis(),q.sourceRefs()));
    }
    public void setClozeCorrect(int index,String blankId,String optionId){
        var q=entry(index);var b=((ClozePayload)q.payload()).blanks().stream().filter(blank->blank.id().equals(blankId)).findFirst().orElseThrow();
        if(b.options().stream().noneMatch(o->o.id().equals(optionId)))throw new IllegalArgumentException("Unknown cloze option");
        var answers=((ClozeAnswerSpec)q.answerSpec()).answers().stream().map(a->a.blankId().equals(blankId)?new ClozeAnswerSpec.Answer(blankId,optionId):a).toList();
        replace(index,new Question(q.id(),q.type(),q.stimulusRefs(),q.prompt(),q.payload(),new ClozeAnswerSpec(answers),q.scoreSpec(),q.evaluationSpec(),q.analysis(),q.sourceRefs()));
    }
    public void addClozeBlank(int index){
        var q=entry(index);var blanks=new ArrayList<>(((ClozePayload)q.payload()).blanks());
        var blank=ClozeQuestionType.newBlank(blanks.size()+1,this::id);blanks.add(blank);
        var answers=new ArrayList<>(((ClozeAnswerSpec)q.answerSpec()).answers());
        answers.add(new ClozeAnswerSpec.Answer(blank.id(),blank.options().getFirst().id()));
        replace(index,new Question(q.id(),q.type(),q.stimulusRefs(),q.prompt(),new ClozePayload(blanks),new ClozeAnswerSpec(answers),q.scoreSpec(),q.evaluationSpec(),q.analysis(),q.sourceRefs()));
    }
    public void setEssayPayload(int index, EssayPayload payload) {
        var q=entry(index);
        if(!QuestionTypes.isEssay(q.type())) throw new IllegalArgumentException("Not an essay");
        replace(index,new Question(q.id(),q.type(),q.stimulusRefs(),q.prompt(),payload,q.answerSpec(),q.scoreSpec(),q.evaluationSpec(),q.analysis(),q.sourceRefs()));
    }
    public void setReadingPrompt(int index, String itemId, QuestionContent content) {
        var q = entry(index);
        var item = readingItem(q, itemId);
        var removed = new java.util.HashSet<>(QuestionContentData.resourceIds(item.prompt()));
        removed.removeAll(QuestionContentData.resourceIds(content));
        var items = ((ReadingPayload) q.payload()).items().stream().map(current -> current.id().equals(itemId)
                ? new ReadingItem(current.id(), current.number(), content, current.options()) : current).toList();
        readingData(index, items, ((ReadingAnswerSpec) q.answerSpec()).answers());
        removeUnusedImages(removed);
    }
    public void setReadingOption(int index, String itemId, int option, String text) {
        var q = entry(index);
        var item = readingItem(q, itemId);
        var options = new ArrayList<>(item.options());
        options.set(option, new ChoiceOption(options.get(option).id(), new TextContent(text)));
        var items = ((ReadingPayload) q.payload()).items().stream().map(current -> current.id().equals(itemId)
                ? new ReadingItem(current.id(), current.number(), current.prompt(), options) : current).toList();
        readingData(index, items, ((ReadingAnswerSpec) q.answerSpec()).answers());
    }
    public void setReadingCorrect(int index, String itemId, String optionId) {
        var q = entry(index);
        var item = readingItem(q, itemId);
        if (item.options().stream().noneMatch(option -> option.id().equals(optionId)))
            throw new IllegalArgumentException("Unknown reading option");
        var answers = ((ReadingAnswerSpec) q.answerSpec()).answers().stream().map(answer -> answer.itemId().equals(itemId)
                ? new ReadingAnswerSpec.Answer(itemId, optionId) : answer).toList();
        readingData(index, ((ReadingPayload) q.payload()).items(), answers);
    }
    public void addReadingItem(int index) {
        var q = entry(index);
        var items = new ArrayList<>(((ReadingPayload) q.payload()).items());
        var item = ReadingQuestionType.newItem(items.size() + 1, this::id);
        items.add(item);
        var answers = new ArrayList<>(((ReadingAnswerSpec) q.answerSpec()).answers());
        answers.add(new ReadingAnswerSpec.Answer(item.id(), item.options().getFirst().id()));
        readingData(index, items, answers);
    }
    public void deleteReadingItem(int index, String itemId) {
        var q = entry(index);
        var removed = readingItem(q, itemId);
        var existing = ((ReadingPayload) q.payload()).items();
        if (existing.size() == 1) throw new IllegalArgumentException("阅读理解至少保留一道小题");
        var items = new ArrayList<ReadingItem>();
        for (var item : existing) if (!item.id().equals(itemId))
            items.add(new ReadingItem(item.id(), items.size() + 1, item.prompt(), item.options()));
        var answers = ((ReadingAnswerSpec) q.answerSpec()).answers().stream().filter(answer -> !answer.itemId().equals(itemId)).toList();
        readingData(index, items, answers);
        removeUnusedImages(QuestionContentData.resourceIds(removed.prompt()));
    }
    private ReadingItem readingItem(Question q, String itemId) {
        if (!QuestionTypes.isReading(q.type())) throw new IllegalArgumentException("Not a reading question");
        return ((ReadingPayload) q.payload()).items().stream().filter(item -> item.id().equals(itemId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown reading item"));
    }
    private void readingData(int index, List<ReadingItem> items, List<ReadingAnswerSpec.Answer> answers) {
        var q = entry(index);
        replace(index, new Question(q.id(), q.type(), q.stimulusRefs(), q.prompt(), new ReadingPayload(items),
                new ReadingAnswerSpec(answers), q.scoreSpec(), q.evaluationSpec(), q.analysis(), q.sourceRefs()));
    }
    public void setMatchingCorrect(int index, String blankId, String optionId) {
        var q = entry(index); var payload = matchingPayload(q);
        var target = matchingBlank(payload, blankId);
        if (payload.options().stream().noneMatch(o -> o.id().equals(optionId)))
            throw new IllegalArgumentException("Unknown matching option");
        var assignments = new LinkedHashMap<>(((MatchingAnswerSpec) q.answerSpec()).assignments());
        String previous = assignments.get(blankId);
        if (java.util.Objects.equals(previous, optionId)) return;
        if (target.locked()) throw new IllegalArgumentException("请先解锁该答案槽，再修改正确答案");
        var holder = assignments.entrySet().stream().filter(a -> a.getValue().equals(optionId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("缺少选项对应的答案槽"));
        if (matchingBlank(payload, holder.getKey()).locked()) throw new IllegalArgumentException("已锁定答案的字母不能移动");
        assignments.put(holder.getKey(), previous);
        assignments.put(blankId, optionId);
        matchingData(index, payload, assignments);
    }
    public void setMatchingLocked(int index, String blankId, boolean locked) {
        var q = entry(index); var payload = matchingPayload(q);
        matchingBlank(payload, blankId);
        var blanks = payload.blanks().stream().map(b -> b.id().equals(blankId)
                ? new MatchingBlank(b.id(), b.number(), locked) : b).toList();
        if (blanks.stream().filter(MatchingBlank::locked).count() > MatchingQuestionType.HINT_COUNT)
            throw new IllegalArgumentException("最多锁定三个提示位置，请先解锁其他位置");
        matchingData(index, new MatchingPayload(blanks, payload.options()), ((MatchingAnswerSpec) q.answerSpec()).assignments());
    }
    private MatchingPayload matchingPayload(Question q) {
        if (!QuestionTypes.isMatching(q.type())) throw new IllegalArgumentException("Not a matching question");
        return (MatchingPayload) q.payload();
    }
    private MatchingBlank matchingBlank(MatchingPayload payload, String blankId) {
        return payload.blanks().stream().filter(b -> b.id().equals(blankId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown matching blank"));
    }
    private void matchingData(int index, MatchingPayload payload, Map<String, String> assignments) {
        MatchingQuestionType.validateCorrectAssignments(payload, assignments);
        var answers = new MatchingAnswerSpec(assignments.entrySet().stream()
                .map(a -> new MatchingAnswerSpec.Answer(a.getKey(), a.getValue())).toList());
        var q = entry(index);
        replace(index, new Question(q.id(), q.type(), q.stimulusRefs(), q.prompt(), payload, answers,
                q.scoreSpec(), q.evaluationSpec(), q.analysis(), q.sourceRefs()));
    }
    public void setReferenceAnswer(int index, QuestionContent reference) {
        var q=entry(index); var removed=new java.util.HashSet<>(QuestionContentData.resourceIds(q.essayAnswerSpec().referenceAnswer()));
        removed.removeAll(QuestionContentData.resourceIds(reference));
        replace(index,new Question(q.id(),q.type(),q.stimulusRefs(),q.prompt(),q.payload(),new EssayAnswerSpec(reference),q.scoreSpec(),q.evaluationSpec(),q.analysis(),q.sourceRefs()));
        removeUnusedImages(removed);
    }
    public void setTranslationReference(int index, String itemId, QuestionContent reference) {
        var q = entry(index);
        if (!QuestionTypes.isTranslation(q.type())) throw new IllegalArgumentException("Not a translation question");
        if (((TranslationPayload) q.payload()).items().stream().noneMatch(item -> item.id().equals(itemId)))
            throw new IllegalArgumentException("Unknown translation item");
        var spec = (TranslationAnswerSpec) q.answerSpec();
        var removed = new java.util.HashSet<>(QuestionContentData.resourceIds(spec.referenceAnswers().get(itemId)));
        removed.removeAll(QuestionContentData.resourceIds(reference));
        var answers = spec.answers().stream().map(answer -> answer.itemId().equals(itemId)
                ? new TranslationAnswerSpec.Answer(itemId, reference) : answer).toList();
        replace(index, new Question(q.id(), q.type(), q.stimulusRefs(), q.prompt(), q.payload(), new TranslationAnswerSpec(answers),
                q.scoreSpec(), q.evaluationSpec(), q.analysis(), q.sourceRefs()));
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
        bank.stimuli().forEach(s->used.addAll(QuestionContentData.resourceIds(s.content())));
        for(var q:bank.questions()) {
            used.addAll(questionResources(q));
        }
        bank=new QuestionBank(bank.assetId(),bank.title(),bank.schemaVersion(),bank.stimuli(),bank.questions(),bank.resources().stream()
                .filter(r->!candidates.contains(r.id()) || used.contains(r.id())).toList());
    }
    private java.util.Set<String> questionResources(Question q) {
        var resources = new java.util.HashSet<>(QuestionContentData.resourceIds(q.prompt()));
        resources.addAll(QuestionContentData.resourceIds(q.analysis()));
        if (q.answerSpec() instanceof EssayAnswerSpec e) resources.addAll(QuestionContentData.resourceIds(e.referenceAnswer()));
        if (q.answerSpec() instanceof TranslationAnswerSpec t)
            t.referenceAnswers().values().forEach(reference -> resources.addAll(QuestionContentData.resourceIds(reference)));
        if (q.payload() instanceof ChoicePayload c) c.options().forEach(o -> resources.addAll(QuestionContentData.resourceIds(o.content())));
        if (q.payload() instanceof ReadingPayload r) r.items().forEach(item -> resources.addAll(QuestionContentData.resourceIds(item.prompt())));
        return resources;
    }
}
