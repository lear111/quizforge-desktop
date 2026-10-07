package io.quizforge.core.question.codec;

import io.quizforge.core.question.content.QuestionContent;
import io.quizforge.core.question.content.QuestionContentData;
import io.quizforge.core.question.content.TextContent;
import io.quizforge.core.question.model.*;
import io.quizforge.core.question.model.extension.ExtensionPayload;
import io.quizforge.core.question.model.extension.ExtensionAnswerSpec;
import io.quizforge.core.question.model.choice.*;
import java.math.BigDecimal;
import java.util.*;

/**
 * Question JSON bridge used by extension pages, templates and frozen snapshots.
 * Stores only the shared CHOICE contract and opaque EXTENSION data.
 */
public final class QuestionDataCodec {
    private QuestionDataCodec() { }

    public static Map<String, Object> encode(Question question) {
        if (question.payload() instanceof ExtensionPayload) {
            var data = new LinkedHashMap<String,Object>();
            data.put("id",question.id()); data.put("type",question.type());
            data.put("prompt",content(question.prompt()));
            data.put("payload",((ExtensionPayload)question.payload()).data());
            data.put("answerSpec",((ExtensionAnswerSpec)question.answerSpec()).data());
            data.put("maxScore",question.scoreSpec().defaultMaxScore());
            data.put("analysis",content(question.analysis()));
            data.put("evaluationSpec", encodeEvaluation(question.evaluationSpec()));
            return ExtensionPayload.freeze(data);
        }
        var data = new LinkedHashMap<String, Object>();
        data.put("id", question.id()); data.put("type", question.type());
        data.put("prompt", QuestionContentData.encode(question.prompt()));
        data.put("payload", payload(question.payload()));
        data.put("answerSpec", answer(question.answerSpec()));
        data.put("maxScore", question.scoreSpec().defaultMaxScore());
        data.put("evaluationSpec", encodeEvaluation(question.evaluationSpec()));
        data.put("analysis", content(question.analysis()));
        return ExtensionPayload.freeze(data);
    }

    /** Persisted Question shape, shared by templates, package pages and the .qbank codec. */
    public static Map<String,Object> encodePersisted(Question question) {
        var data = new LinkedHashMap<>(encode(question));
        var payload = new LinkedHashMap<>(object(data.get("payload")));
        var answer = new LinkedHashMap<>(object(data.get("answerSpec")));
        String kind = switch(question.payload()) {
            case ChoicePayload ignored -> "CHOICE";
            case ExtensionPayload ignored -> "EXTENSION";
            default -> throw new IllegalArgumentException("Unknown stored payload");
        };
        if (question.payload() instanceof ExtensionPayload) {
            payload = new LinkedHashMap<>(Map.of("data", payload));
            answer = new LinkedHashMap<>(Map.of("data", answer));
        }
        payload.put("kind",kind); answer.put("kind",kind);
        data.put("payload",payload); data.put("answerSpec",answer);
        data.remove("maxScore");
        data.put("scoreSpec",Map.of("defaultMaxScore",question.scoreSpec().defaultMaxScore()));
        data.put("stimulusRefs",question.stimulusRefs());
        data.put("sourceRefs",question.sourceRefs().stream().map(ref -> fields("documentAssetId",ref.documentAssetId(),
            "documentContentId",ref.documentContentId(),"address",fields("kind",ref.address().kind().name(),"value",ref.address().value(),"occurrence",ref.occurrence()),
            "documentTitle",ref.documentTitle(),"sectionTitle",ref.sectionTitle())).toList());
        data.entrySet().removeIf(entry -> entry.getValue() == null);
        return ExtensionPayload.freeze(data);
    }

    /** Decode the shared CHOICE editor value, retaining host identities and references. */
    public static Question decodeChoice(Map<String, Object> data, Question original) {
        if (!(original.payload() instanceof ChoicePayload))
            throw new IllegalArgumentException("CHOICE data is required");
        var prompt = requiredContent(data.get("prompt"));
        var payload = object(data.get("payload"));
        var spec = object(data.get("answerSpec"));
        if ((original.type().equals("SINGLE_CHOICE") || original.type().equals("MULTIPLE_CHOICE")) && !(prompt instanceof TextContent))
            throw new IllegalArgumentException("单选和多选题干仅支持普通文本");
        var decodedPayload = new ChoicePayload(options(payload.get("options")));
        var decodedSpec = new ChoiceAnswerSpec(strings(spec.get("correctOptionIds")));
        return new Question(original.id(), original.type(), original.stimulusRefs(), prompt, decodedPayload, decodedSpec,
                new ScoreSpec(positiveScore(data.containsKey("scoreSpec") ? object(data.get("scoreSpec")).get("defaultMaxScore") : data.get("maxScore"))), decodeEvaluation(data.get("evaluationSpec")),
                optionalContent(data.get("analysis")), original.sourceRefs());
    }

    private static Map<String, Object> payload(QuestionPayload value) {
        return switch (value) {
            case ChoicePayload choice -> Map.of("options", encodeOptions(choice.options()));
            default -> throw new IllegalArgumentException("Unknown stored payload");
        };
    }
    private static Map<String, Object> answer(QuestionAnswerSpec value) {
        return switch (value) {
            case ChoiceAnswerSpec choice -> Map.of("correctOptionIds", choice.correctOptionIds());
            default -> throw new IllegalArgumentException("Unknown stored answer specification");
        };
    }
    public static Object encodeEvaluation(EvaluationSpec value) {
        return value == null ? null : fields("criteria", value.criteria().stream().map(criterion ->
                fields("id", criterion.id(), "description", criterion.description(), "weight", criterion.weight())).toList(),
                "evaluatorGuidance", value.evaluatorGuidance());
    }
    public static EvaluationSpec decodeEvaluation(Object value) {
        if (value == null) return null;
        var data = object(value);
        return new EvaluationSpec(objects(data.get("criteria")).stream().map(criterion ->
                new EvaluationCriterion(id(criterion.get("id")), text(criterion.get("description")), numeric(criterion.get("weight")))).toList(),
                nullableText(data.get("evaluatorGuidance")));
    }
    private static List<Map<String, Object>> encodeOptions(List<ChoiceOption> options) {
        return options.stream().map(option -> fields("id", option.id(), "content", content(option.content()))).toList();
    }
    private static List<ChoiceOption> options(Object value) {
        return objects(value).stream().map(option -> {
            var content = requiredContent(option.get("content"));
            if (!(content instanceof TextContent)) throw new IllegalArgumentException("选项仅支持普通文本");
            return new ChoiceOption(id(option.get("id")), content);
        }).toList();
    }
    private static Object content(QuestionContent value) { return value == null ? null : QuestionContentData.encode(value); }
    private static QuestionContent optionalContent(Object value) { return value == null ? null : requiredContent(value); }
    private static QuestionContent requiredContent(Object value) {
        try { return QuestionContentData.decode(value); }
        catch (RuntimeException failure) { throw new IllegalArgumentException("Invalid question content", failure); }
    }
    private static Map<String, Object> fields(Object... values) {
        var result = new LinkedHashMap<String, Object>();
        for (int i = 0; i < values.length; i += 2) result.put((String) values[i], values[i + 1]);
        return result;
    }
    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value) {
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException("Question JSON object is required");
        return (Map<String, Object>) map;
    }
    private static List<?> list(Object value) {
        if (!(value instanceof List<?> result)) throw new IllegalArgumentException("Editor array is required");
        return result;
    }
    private static List<Map<String, Object>> objects(Object value) { return list(value).stream().map(QuestionDataCodec::object).toList(); }
    private static List<String> strings(Object value) { return list(value).stream().map(QuestionDataCodec::id).toList(); }
    private static String text(Object value) {
        if (!(value instanceof String result)) throw new IllegalArgumentException("Editor text is required");
        return result;
    }
    private static String nullableText(Object value) { return value == null ? null : text(value); }
    private static String id(Object value) {
        var result = text(value);
        if (result.isBlank()) throw new IllegalArgumentException("Editor identity is required");
        return result;
    }
    private static BigDecimal numeric(Object value) {
        if (!(value instanceof Number result)) throw new IllegalArgumentException("Editor numeric value is required");
        try { return new BigDecimal(result.toString()); }
        catch (NumberFormatException failure) { throw new IllegalArgumentException("Editor numeric value must be finite", failure); }
    }
    private static BigDecimal positiveScore(Object value) {
        var result = numeric(value);
        if (result.signum() <= 0 || !Double.isFinite(result.doubleValue())) throw new IllegalArgumentException("Maximum score must be positive and finite");
        return result;
    }
}
