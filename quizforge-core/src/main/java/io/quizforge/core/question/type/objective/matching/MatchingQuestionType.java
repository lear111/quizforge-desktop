package io.quizforge.core.question.type.objective.matching;

import io.quizforge.core.question.content.TextContent;
import io.quizforge.core.question.model.*;
import io.quizforge.core.question.source.SourceRef;
import io.quizforge.core.question.type.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import static io.quizforge.core.question.type.QuestionValidationContext.reject;

/** Eight ordered paragraphs, with three given hints and five graded positions. */
public final class MatchingQuestionType implements QuestionTypeDefinition {
    public static final int SLOT_COUNT = 8;
    public static final int HINT_COUNT = 3;
    public String id() { return "MATCHING"; }
    public Family family() { return Family.OBJECTIVE; }
    public String payloadKind() { return "MATCHING"; }
    public Class<MatchingPayload> payloadClass() { return MatchingPayload.class; }
    public String answerKind() { return "MATCHING"; }
    public Class<MatchingAnswerSpec> answerClass() { return MatchingAnswerSpec.class; }

    public Question createDraft(Function<String, String> ids, List<SourceRef> sources) {
        var blanks = new ArrayList<MatchingBlank>();
        var options = new ArrayList<MatchingOption>();
        for (int i = 0; i < SLOT_COUNT; i++) {
            blanks.add(new MatchingBlank(ids.apply("blank_"), i + 1, i == 0 || i == 3 || i == 5));
            options.add(new MatchingOption(ids.apply("opt_"), String.valueOf((char) ('A' + i))));
        }
        var answers = new ArrayList<MatchingAnswerSpec.Answer>();
        for (int i = 0; i < blanks.size(); i++) answers.add(new MatchingAnswerSpec.Answer(blanks.get(i).id(), options.get(i).id()));
        return new Question(ids.apply("q_"), id(), List.of(), new TextContent("在这里输入完整题目和 A–H 选项。"),
                new MatchingPayload(blanks, options), new MatchingAnswerSpec(answers), new ScoreSpec(new BigDecimal("2")), null, null, sources);
    }

    public Question duplicate(Question q, Function<String, String> ids) {
        var payload = (MatchingPayload) q.payload();
        var blankIds = new HashMap<String, String>();
        var optionIds = new HashMap<String, String>();
        var blanks = payload.blanks().stream().map(b -> {
            var fresh = ids.apply("blank_"); blankIds.put(b.id(), fresh); return new MatchingBlank(fresh, b.number(), b.locked());
        }).toList();
        var options = payload.options().stream().map(o -> {
            var fresh = ids.apply("opt_"); optionIds.put(o.id(), fresh); return new MatchingOption(fresh, o.label());
        }).toList();
        var answers = ((MatchingAnswerSpec) q.answerSpec()).answers().stream().map(a ->
                new MatchingAnswerSpec.Answer(blankIds.get(a.blankId()), optionIds.get(a.correctOptionId()))).toList();
        return new Question(ids.apply("q_"), id(), q.stimulusRefs(), q.prompt(), new MatchingPayload(blanks, options), new MatchingAnswerSpec(answers),
                q.scoreSpec(), q.evaluationSpec(), q.analysis(), q.sourceRefs());
    }

    public void validate(Question q, QuestionValidationContext context) {
        if (!(q.payload() instanceof MatchingPayload) || !(q.answerSpec() instanceof MatchingAnswerSpec)) reject("MATCHING requires matching payload and answers");
        var payload = (MatchingPayload) q.payload();
        if (payload.blanks().size() != SLOT_COUNT || payload.options().size() != SLOT_COUNT) reject("段落排序需要八个答案槽和 A–H 八个选项");
        if (payload.blanks().stream().filter(MatchingBlank::locked).count() != HINT_COUNT) reject("八选五需要锁定三个提示位置，保留五个作答位置");
        var blankIds = new HashSet<String>();
        for (int i = 0; i < payload.blanks().size(); i++) {
            var blank = payload.blanks().get(i);
            if (blank.id() == null || !blank.id().matches("blank_[A-Za-z0-9_-]+") || !blankIds.add(blank.id())) reject("Invalid matching blank ID");
            if (blank.number() != i + 1) reject("答案槽编号应从 1 连续递增");
        }
        var labels = new HashSet<String>();
        for (var option : payload.options()) {
            if (option.id() == null || !option.id().matches("opt_[A-Za-z0-9_-]+") || !context.optionIds().add(option.id())) reject("Invalid matching option ID");
            if (option.label() == null || !option.label().matches("[A-H]") || !labels.add(option.label())) reject("选项标签必须为互不重复的 A–H");
        }
        try { validateCorrectAssignments(payload, ((MatchingAnswerSpec) q.answerSpec()).assignments()); }
        catch (IllegalArgumentException error) { reject(error.getMessage()); }
    }

    public boolean evaluate(Question q, Set<String> selected) {
        throw new IllegalArgumentException("段落匹配必须按答案槽保存答案");
    }

    /** Standard answers include hints and are a complete permutation. */
    public static void validateCorrectAssignments(MatchingPayload payload, Map<String, String> assignments) {
        validateBounds(payload, assignments);
        if (new HashSet<>(assignments.values()).size() != assignments.size())
            throw new IllegalArgumentException("标准答案中的每个字母只能用于一个答案槽");
        var blanks = payload.blanks().stream().map(MatchingBlank::id).collect(java.util.stream.Collectors.toSet());
        if (!assignments.keySet().equals(blanks)) throw new IllegalArgumentException("每个答案槽必须设置一个正确答案");
    }

    /** Basic draft bounds; use the Question overload to also exclude hint letters. */
    public static void validateAssignments(MatchingPayload payload, Map<String, String> assignments) {
        validateBounds(payload, assignments);
        for (var blank : payload.blanks()) if (blank.locked() && assignments.containsKey(blank.id()))
            throw new IllegalArgumentException("锁定答案槽不能作答");
    }

    public static void validateAssignments(Question q, Map<String, String> assignments) {
        var payload = (MatchingPayload) q.payload();
        validateAssignments(payload, assignments);
        var correct = ((MatchingAnswerSpec) q.answerSpec()).assignments();
        var reserved = payload.blanks().stream().filter(MatchingBlank::locked).map(b -> correct.get(b.id())).collect(java.util.stream.Collectors.toSet());
        if (assignments.values().stream().anyMatch(reserved::contains)) throw new IllegalArgumentException("已给出的答案字母不能重复使用");
    }

    private static void validateBounds(MatchingPayload payload, Map<String, String> assignments) {
        if (assignments == null) throw new IllegalArgumentException("Missing matching assignments");
        var blanks = payload.blanks().stream().map(MatchingBlank::id).collect(java.util.stream.Collectors.toSet());
        var options = payload.options().stream().map(MatchingOption::id).collect(java.util.stream.Collectors.toSet());
        for (var assignment : assignments.entrySet()) {
            if (!blanks.contains(assignment.getKey())) throw new IllegalArgumentException("Unknown matching blank");
            if (!options.contains(assignment.getValue())) throw new IllegalArgumentException("Unknown matching option");
        }
    }

    public static int gradableCount(Question q) {
        return (int) ((MatchingPayload) q.payload()).blanks().stream().filter(b -> !b.locked()).count();
    }

    public static int matchingCount(Question q, Map<String, String> assignments) {
        validateAssignments(q, assignments);
        var correct = ((MatchingAnswerSpec) q.answerSpec()).assignments();
        return (int) assignments.entrySet().stream().filter(a -> Objects.equals(correct.get(a.getKey()), a.getValue())).count();
    }

    public static boolean evaluateAssignments(Question q, Map<String, String> assignments) {
        return matchingCount(q, assignments) == gradableCount(q);
    }
}
