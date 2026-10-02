package io.quizforge.core.question.type.objective.reading;

import io.quizforge.core.question.content.TextContent;
import io.quizforge.core.question.model.*;
import io.quizforge.core.question.source.SourceRef;
import io.quizforge.core.question.type.*;
import io.quizforge.core.question.type.objective.choice.ChoiceOption;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import static io.quizforge.core.question.type.QuestionValidationContext.reject;

/** One passage with independent single-choice items and a shared analysis. */
public final class ReadingQuestionType implements QuestionTypeDefinition {
    public String id() { return "READING"; }
    public Family family() { return Family.OBJECTIVE; }
    public String payloadKind() { return "READING"; }
    public Class<ReadingPayload> payloadClass() { return ReadingPayload.class; }
    public String answerKind() { return "READING"; }
    public Class<ReadingAnswerSpec> answerClass() { return ReadingAnswerSpec.class; }

    public static ReadingItem newItem(int number, Function<String, String> ids) {
        var options = new ArrayList<ChoiceOption>();
        for (int i = 0; i < 4; i++) options.add(new ChoiceOption(ids.apply("opt_"), new TextContent("test")));
        return new ReadingItem(ids.apply("item_"), number, new TextContent("在这里输入小题题干。"), options);
    }

    public Question createDraft(Function<String, String> ids, List<SourceRef> sources) {
        var items = new ArrayList<ReadingItem>();
        for (int number = 1; number <= 5; number++) items.add(newItem(number, ids));
        var answers = items.stream().map(item -> new ReadingAnswerSpec.Answer(item.id(), item.options().getFirst().id())).toList();
        return new Question(ids.apply("q_"), id(), List.of(), new TextContent("请阅读文章并回答下列问题。\n\n在这里输入文章。"),
                new ReadingPayload(items), new ReadingAnswerSpec(answers), new ScoreSpec(new BigDecimal("2")), null, null, sources);
    }

    public Question duplicate(Question q, Function<String, String> ids) {
        var correct = Set.copyOf(((ReadingAnswerSpec) q.answerSpec()).correctOptionIds());
        var answers = new ArrayList<ReadingAnswerSpec.Answer>();
        var items = ((ReadingPayload) q.payload()).items().stream().map(item -> {
            var itemId = ids.apply("item_");
            var options = item.options().stream().map(option -> {
                var optionId = ids.apply("opt_");
                if (correct.contains(option.id())) answers.add(new ReadingAnswerSpec.Answer(itemId, optionId));
                return new ChoiceOption(optionId, option.content());
            }).toList();
            return new ReadingItem(itemId, item.number(), item.prompt(), options);
        }).toList();
        return new Question(ids.apply("q_"), id(), q.stimulusRefs(), q.prompt(), new ReadingPayload(items), new ReadingAnswerSpec(answers),
                q.scoreSpec(), q.evaluationSpec(), q.analysis(), q.sourceRefs());
    }

    public void validate(Question q, QuestionValidationContext context) {
        if (!(q.payload() instanceof ReadingPayload) || !(q.answerSpec() instanceof ReadingAnswerSpec))
            reject("READING requires reading payload and answers");
        var payload = (ReadingPayload) q.payload();
        var answers = (ReadingAnswerSpec) q.answerSpec();
        if (payload.items().isEmpty()) reject("阅读理解至少需要一道小题");
        var itemIds = new HashSet<String>();
        var answerIds = new HashSet<String>();
        for (int index = 0; index < payload.items().size(); index++) {
            var item = payload.items().get(index);
            if (item.id() == null || !item.id().matches("item_[A-Za-z0-9_-]+") || !itemIds.add(item.id()))
                reject("Invalid reading item ID");
            if (item.number() != index + 1) reject("阅读小题编号应从 1 连续递增");
            context.content().accept(item.prompt(), true);
            if (item.options().size() != 4) reject("每道阅读小题必须有四个选项");
            var optionIds = new HashSet<String>();
            for (var option : item.options()) {
                if (option.id() == null || !option.id().matches("opt_[A-Za-z0-9_-]+") || !optionIds.add(option.id())
                        || !context.optionIds().add(option.id())) reject("Invalid reading option ID");
                if (!(option.content() instanceof TextContent)) reject("第一版阅读理解选项仅支持文字");
                context.content().accept(option.content(), true);
            }
            var matches = answers.answers().stream().filter(answer -> item.id().equals(answer.itemId())).toList();
            if (matches.size() != 1 || !optionIds.contains(matches.getFirst().correctOptionId()))
                reject("每道阅读小题需要一个正确答案");
        }
        for (var answer : answers.answers())
            if (!itemIds.contains(answer.itemId()) || !answerIds.add(answer.itemId())) reject("Invalid reading answer");
    }

    public boolean evaluate(Question q, Set<String> selected) {
        return selected.equals(Set.copyOf(((ReadingAnswerSpec) q.answerSpec()).correctOptionIds()));
    }

    public static void validateSelection(ReadingPayload payload, Set<String> selected) {
        var options = payload.options().stream().map(ChoiceOption::id).collect(java.util.stream.Collectors.toSet());
        if (!options.containsAll(selected)) throw new IllegalArgumentException("Unknown reading option");
        for (var item : payload.items())
            if (item.options().stream().filter(option -> selected.contains(option.id())).count() > 1)
                throw new IllegalArgumentException("每道阅读小题只能选择一个选项");
    }
}
