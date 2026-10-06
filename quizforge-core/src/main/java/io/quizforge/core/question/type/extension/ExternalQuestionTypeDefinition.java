package io.quizforge.core.question.type.extension;

import io.quizforge.core.question.codec.QuestionDataCodec;
import io.quizforge.core.question.model.extension.ExtensionAnswerSpec;
import io.quizforge.core.question.model.extension.ExtensionPayload;
import io.quizforge.core.practice.PracticePayload;
import io.quizforge.core.practice.PracticeSessionQuestion;
import io.quizforge.core.practice.QuestionAttempt;
import io.quizforge.core.question.content.QuestionContentData;
import io.quizforge.core.question.content.TextContent;
import io.quizforge.core.question.model.*;
import io.quizforge.core.question.source.SourceRef;
import io.quizforge.core.question.type.QuestionTypeDefinition;
import io.quizforge.core.question.type.QuestionValidationContext;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;

/** A type whose data and rules are delivered by an extension, not compiled into the host. */
public final class ExternalQuestionTypeDefinition implements QuestionTypeDefinition {
    private final String id;
    private final String label;
    private final Family family;
    private final String version;
    private final String extensionId;
    private final int dataVersion;
    private final QuestionExtensionRules rules;
    private final Map<String,Object> template;
    private final boolean nativeChoice;
    private final QuestionExtensionDataValidator dataValidator;

    public ExternalQuestionTypeDefinition(String id, String label, Family family, String version, QuestionExtensionRules rules) {
        this(id, label, family, version, id, 1, rules);
    }
    public ExternalQuestionTypeDefinition(String id, String label, Family family, String version, String extensionId, int dataVersion, QuestionExtensionRules rules) {
        this(id,label,family,version,extensionId,dataVersion,rules,null);
    }
    public ExternalQuestionTypeDefinition(String id, String label, Family family, String version, String extensionId, int dataVersion, QuestionExtensionRules rules, Map<String,Object> template) {
        this(id,label,family,version,extensionId,dataVersion,rules,template,QuestionExtensionDataValidator.NONE);
    }
    public ExternalQuestionTypeDefinition(String id, String label, Family family, String version, String extensionId, int dataVersion, QuestionExtensionRules rules, Map<String,Object> template, QuestionExtensionDataValidator dataValidator) {
        if (id == null || !id.matches("[A-Za-z][A-Za-z0-9_.-]{0,127}") || label == null || label.isBlank()
                || version == null || version.isBlank() || extensionId == null || extensionId.isBlank() || dataVersion < 1) throw new IllegalArgumentException("Invalid extension type metadata");
        this.id = id; this.label = label; this.family = Objects.requireNonNull(family);
        this.version = version; this.rules = Objects.requireNonNull(rules);
        this.extensionId = extensionId; this.dataVersion = dataVersion;
        this.template = template == null ? null : ExtensionPayload.freeze(template);
        this.nativeChoice = template != null && "CHOICE".equals(object(template.get("payload")).get("kind"));
        this.dataValidator = Objects.requireNonNull(dataValidator);
        if (this.template != null) dataValidator.question(this.template);
    }
    @Override public String id() { return id; }
    @Override public String label() { return label; }
    @Override public Family family() { return family; }
    public String version() { return version; }
    public String extensionId() { return extensionId; }
    public int dataVersion() { return dataVersion; }
    @Override public String payloadKind() { return nativeChoice ? "CHOICE" : "EXTENSION"; }
    @Override public Class<? extends QuestionPayload> payloadClass() { return nativeChoice ? io.quizforge.core.question.model.choice.ChoicePayload.class : ExtensionPayload.class; }
    @Override public String answerKind() { return payloadKind(); }
    @Override public Class<? extends QuestionAnswerSpec> answerClass() { return nativeChoice ? io.quizforge.core.question.model.choice.ChoiceAnswerSpec.class : ExtensionAnswerSpec.class; }
    @Override public boolean multipleSelection() { return "MULTIPLE_CHOICE".equals(id); }

    public Map<String, Object> invoke(String operation, Map<String, Object> input) {
        var frozen = ExtensionPayload.freeze(input);
        if (frozen.containsKey("question")) dataValidator.question(object(frozen.get("question")));
        if (operation.equals("validateAnswer") || operation.equals("grade")) {
            var answer = object(frozen.get("answer"));
            if (!answer.isEmpty()) dataValidator.answer(answer);
            // An empty object is the framework's unanswered sentinel, irrespective of extension rules.
            if (operation.equals("validateAnswer") && answer.isEmpty()) return Map.of("errors", List.of(), "empty", true);
            if (operation.equals("grade") && answer.isEmpty()) throw new IllegalStateException("Write an answer first");
        }
        var output = ExtensionPayload.freeze(Objects.requireNonNull(rules.invoke(operation, frozen), "Extension returned no response"));
        switch (operation) {
            case "createDraft", "duplicate" -> dataValidator.question(output);
            case "validate", "validateAnswer" -> {
                if (!(output.get("errors") instanceof List<?> errors))
                    throw ExtensionDataValidationException.at("/rules/" + operation + "/errors", "must be an array of strings");
                for (int index = 0; index < errors.size(); index++) if (!(errors.get(index) instanceof String))
                    throw ExtensionDataValidationException.at("/rules/" + operation + "/errors/" + index, "must be a string");
                if (operation.equals("validateAnswer") && !(output.get("empty") instanceof Boolean))
                    throw ExtensionDataValidationException.at("/rules/validateAnswer/empty", "must be boolean");
            }
            case "targets" -> targetsFrom(output);
            case "snapshot" -> {
                if (output.containsKey("maxScore")) {
                    try { positiveScore(output.get("maxScore")); }
                    catch (IllegalArgumentException failure) { throw ExtensionDataValidationException.at("/rules/snapshot/maxScore",failure.getMessage()); }
                }
                if (output.containsKey("targets")) targetsFrom(output,"/rules/snapshot");
            }
            case "grade" -> validateGradeOutput(output, positiveScore(frozen.get("maxScore")));
            default -> throw new IllegalArgumentException("Unsupported rules operation: " + operation);
        }
        return output;
    }
    public void validateQuestionData(Map<String,Object> value) { dataValidator.question(value); }
    private static void checkErrors(Map<String, Object> output) {
        if (!(output.get("errors") instanceof List<?> errors))
            throw new IllegalArgumentException("Extension validation must return an errors list");
        if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("; ", errors.stream().map(String::valueOf).toList()));
    }
    @Override public void validate(Question question, QuestionValidationContext context) {
        if (!id.equals(question.type()) || !payloadClass().isInstance(question.payload())
                || !answerClass().isInstance(question.answerSpec())) throw new IllegalArgumentException("Extension data does not match its registered type");
        new MissingExtensionQuestionType(question).validate(question,context);
        checkErrors(invoke("validate", Map.of("question", encodeQuestion(question))));
    }
    @Override public Question createDraft(Function<String, String> newId, List<SourceRef> sources) {
        var ids = ids(newId);
        if (template != null) return decodeQuestion(reidentify(template,newId),(String)ids.get("question"),sources);
        return decodeQuestion(invoke("createDraft", Map.of("ids", ids)), (String) ids.get("question"), sources);
    }
    @Override public Question duplicate(Question question, Function<String, String> newId) {
        var ids = ids(newId);
        if (template != null) {
            var copied = decodeQuestion(reidentify(encodeQuestion(question),newId),(String)ids.get("question"),question.sourceRefs());
            return new Question(copied.id(),id,question.stimulusRefs(),copied.prompt(),copied.payload(),copied.answerSpec(),copied.scoreSpec(),question.evaluationSpec(),copied.analysis(),question.sourceRefs());
        }
        var result = decodeQuestion(invoke("duplicate", Map.of("question", encodeQuestion(question), "ids", ids)), (String) ids.get("question"), question.sourceRefs());
        return new Question(result.id(), id, question.stimulusRefs(), result.prompt(), result.payload(), result.answerSpec(),
                result.scoreSpec(), question.evaluationSpec(), result.analysis(), result.sourceRefs());
    }
    private static Map<String, Object> ids(Function<String, String> newId) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("question", newId.apply("q_"));
        for (var prefix : List.of("item", "opt", "blank")) {
            var generated = new ArrayList<String>();
            for (int index = 0; index < 64; index++) generated.add(newId.apply(prefix + "_"));
            values.put(prefix + "s", generated);
            values.put(prefix.equals("opt") ? "option" : prefix, generated.getFirst());
        }
        return values;
    }
    public Question decodeQuestion(Map<String, Object> data, String questionId, List<SourceRef> sources) {
        dataValidator.question(data);
        Object prompt = data.getOrDefault("prompt", Map.of("kind", "TEXT", "text", label));
        var maximum = data.containsKey("scoreSpec") ? object(data.get("scoreSpec")).get("defaultMaxScore") : data.getOrDefault("maxScore", BigDecimal.ONE);
        if (nativeChoice) {
            var original = new Question(questionId,id,List.of(),QuestionContentData.decode(prompt),
                new io.quizforge.core.question.model.choice.ChoicePayload(List.of()),
                new io.quizforge.core.question.model.choice.ChoiceAnswerSpec(List.of()),new ScoreSpec(positiveScore(maximum)),null,null,sources);
            return QuestionDataCodec.decodeChoice(data,original);
        }
        var payload = object(data.get("payload")); var answer = object(data.get("answerSpec"));
        if ("EXTENSION".equals(payload.get("kind"))) payload = object(payload.get("data"));
        if ("EXTENSION".equals(answer.get("kind"))) answer = object(answer.get("data"));
        return new Question(questionId, id, List.of(), QuestionContentData.decode(prompt),
                new ExtensionPayload(payload), new ExtensionAnswerSpec(answer),
                new ScoreSpec(positiveScore(maximum)), QuestionDataCodec.decodeEvaluation(data.get("evaluationSpec")),
                data.get("analysis") == null ? new TextContent("") : QuestionContentData.decode(data.get("analysis")), sources);
    }
    public static Map<String, Object> encodeQuestion(Question question) {
        return QuestionDataCodec.encodePersisted(question);
    }
    private static Map<String,Object> reidentify(Map<String,Object> data,Function<String,String> newId) {
        var copied = new LinkedHashMap<>(data);
        if ("CHOICE".equals(object(data.get("payload")).get("kind"))) {
            var payload = new LinkedHashMap<>(object(data.get("payload")));
            var mapping = new HashMap<String,String>();
            var options = ((List<?>)payload.get("options")).stream().map(value -> {
                var option = new LinkedHashMap<>(object(value));
                var old = (String)option.get("id"); var fresh = newId.apply("opt_");
                mapping.put(old,fresh); option.put("id",fresh); return option;
            }).toList();
            payload.put("options",options); copied.put("payload",payload);
            var answer = new LinkedHashMap<>(object(data.get("answerSpec")));
            answer.put("correctOptionIds",((List<?>)answer.get("correctOptionIds")).stream().map(value -> {
                var fresh = mapping.get(value); if (fresh == null) throw new IllegalArgumentException("Template answer references an unknown option"); return fresh;
            }).toList()); copied.put("answerSpec",answer);
        }
        return ExtensionPayload.freeze(copied);
    }
    public List<Map<String, Object>> targets(Question question) {
        return targetsFrom(invoke("targets", Map.of("question", encodeQuestion(question))));
    }
    @Override public List<io.quizforge.core.question.type.QuestionTarget> outlineTargets(Question question) {
        var values = targets(question); var result = new ArrayList<io.quizforge.core.question.type.QuestionTarget>();
        for (var value : values) {
            result.add(new io.quizforge.core.question.type.QuestionTarget((String) value.get("id"), ((Number) value.get("number")).intValue(),
                    (Boolean) value.get("gradable"), (Boolean) value.get("locked"), (String) value.get("label")));
        }
        return List.copyOf(result);
    }
    public static List<Map<String, Object>> targetsFrom(Map<String, Object> result) {
        return targetsFrom(result,"/rules/targets");
    }
    private static List<Map<String,Object>> targetsFrom(Map<String,Object> result,String path) {
        if (!(result.get("targets") instanceof List<?> items)) throw ExtensionDataValidationException.at(path+"/targets","must be an array");
        Set<String> ids = new HashSet<>();
        Set<Integer> numbers = new HashSet<>();
        var values = new ArrayList<Map<String, Object>>();
        for (var item : items) {
            String field = path + "/targets/" + values.size();
            if (!(item instanceof Map)) throw ExtensionDataValidationException.at(field,"must be an object");
            var value = new LinkedHashMap<>(object(item));
            if (!(value.get("id") instanceof String targetId) || targetId.isBlank() || !ids.add(targetId))
                throw ExtensionDataValidationException.at(field+"/id","must be nonempty and unique");
            int number = values.size() + 1;
            if (value.containsKey("number")) {
                if (!(value.get("number") instanceof Number candidate)) throw ExtensionDataValidationException.at(field+"/number","must be a positive integer");
                try { number = new BigDecimal(candidate.toString()).intValueExact(); }
                catch (NumberFormatException | ArithmeticException malformed) { throw ExtensionDataValidationException.at(field+"/number","must be a positive integer"); }
            }
            if (number < 1 || !numbers.add(number)) throw ExtensionDataValidationException.at(field+"/number","must be positive and unique");
            for (var key : List.of("locked", "gradable"))
                if (value.containsKey(key) && !(value.get(key) instanceof Boolean)) throw ExtensionDataValidationException.at(field+"/"+key,"must be boolean");
            if (value.containsKey("label") && !(value.get("label") instanceof String)) throw ExtensionDataValidationException.at(field+"/label","must be text");
            boolean locked = Boolean.TRUE.equals(value.get("locked"));
            value.put("number", number); value.put("locked", locked);
            value.put("gradable", !locked && !Boolean.FALSE.equals(value.get("gradable")));
            value.putIfAbsent("label", "");
            values.add(ExtensionPayload.freeze(value));
        }
        return List.copyOf(values);
    }
    /** Frozen logical and presentation data; answerSpec never enters the public question map. */
    public PracticeSessionQuestion.Snapshot snapshot(Question question) {
        var encoded = encodeQuestion(question);
        var output = invoke("snapshot", Map.of("question", encoded));
        var maximum = positiveScore(output.getOrDefault("maxScore", question.scoreSpec().defaultMaxScore()));
        var targets = output.containsKey("targets") ? targetsFrom(output) : targets(question);
        var publicQuestion = new LinkedHashMap<>(encoded);
        publicQuestion.remove("answerSpec"); publicQuestion.remove("analysis");
        publicQuestion.put("maxScore", maximum);
        var presentation = new LinkedHashMap<String, Object>();
        presentation.put("question", publicQuestion); presentation.put("targets", targets);
        presentation.put("extensionId", extensionId); presentation.put("version", version); presentation.put("dataVersion", dataVersion);
        if (output.get("data") != null) presentation.put("data", output.get("data"));
        var logical = new LinkedHashMap<String, Object>();
        logical.put("version", version); logical.put("question", encoded);
        logical.put("extensionId", extensionId); logical.put("dataVersion", dataVersion);
        logical.put("payload", encoded.get("payload"));
        logical.put("answerSpec", encoded.get("answerSpec"));
        var correct = new LinkedHashMap<String, Object>();
        correct.put("correctOptionIds", question.answerSpec() instanceof io.quizforge.core.question.model.choice.ChoiceAnswerSpec choice ? choice.correctOptionIds() : List.of());
        correct.put("maxScore", maximum); correct.put("extension", logical); correct.put("extensionPresentation", presentation);
        return new PracticeSessionQuestion.Snapshot(id, QuestionContentData.plainText(question.prompt()), new PracticePayload(List.of()),
                new PracticePayload(correct), QuestionContentData.plainText(question.analysis()), new PracticePayload(List.of()));
    }
    public boolean validateAnswer(PracticeSessionQuestion.Snapshot snapshot, PracticePayload answer) {
        var result = invoke("validateAnswer", Map.of("question", frozenQuestion(snapshot), "answer", answer == null ? Map.of() : answer.value()));
        checkErrors(result);
        if (!(result.get("empty") instanceof Boolean empty)) throw new IllegalArgumentException("Extension must declare answer emptiness");
        return empty;
    }
    public record Grade(QuestionAttempt.Result result, Double score, double maxScore) { }
    public Grade grade(PracticeSessionQuestion.Snapshot snapshot, PracticePayload answer) {
        if (validateAnswer(snapshot, answer)) throw new IllegalStateException("Write an answer first");
        var data = object(snapshot.correctAnswer().value());
        var max = positiveScore(data.get("maxScore"));
        var result = invoke("grade", Map.of("question", frozenQuestion(snapshot), "answer", answer.value(), "maxScore", max));
        return validateGradeOutput(result, max);
    }
    private static Grade validateGradeOutput(Map<String,Object> result, BigDecimal max) {
        if (!(result.get("status") instanceof String statusValue) || !Set.of("CORRECT", "INCORRECT", "UNSCORED").contains(statusValue))
            throw ExtensionDataValidationException.at("/rules/grade/status", "must be CORRECT, INCORRECT or UNSCORED");
        var status = QuestionAttempt.Result.valueOf((String) result.get("status"));
        if (result.containsKey("maxScore") && (!(result.get("maxScore") instanceof Number) || positiveScore(result.get("maxScore")).compareTo(max) != 0))
            throw ExtensionDataValidationException.at("/rules/grade/maxScore", "cannot change the frozen maximum score");
        Double score = null;
        if (status != QuestionAttempt.Result.UNSCORED) {
            if (!(result.get("score") instanceof Number value)) throw ExtensionDataValidationException.at("/rules/grade/score", "must be numeric for a scored result");
            var points = new BigDecimal(value.toString());
            if (points.signum() < 0 || points.compareTo(max) > 0) throw ExtensionDataValidationException.at("/rules/grade/score", "is outside the allowed range");
            if (status == QuestionAttempt.Result.CORRECT && points.compareTo(max) != 0)
                throw ExtensionDataValidationException.at("/rules/grade/score", "a correct result requires full credit");
            if (status == QuestionAttempt.Result.INCORRECT && points.compareTo(max) == 0)
                throw ExtensionDataValidationException.at("/rules/grade/score", "full credit requires CORRECT status");
            score = points.doubleValue();
        } else if (!result.containsKey("score") || result.get("score") != null) throw ExtensionDataValidationException.at("/rules/grade/score", "an unscored result requires null");
        return new Grade(status, score, max.doubleValue());
    }
    public static Map<String, Object> frozenQuestion(PracticeSessionQuestion.Snapshot snapshot) {
        return object(object(object(snapshot.correctAnswer().value()).get("extension")).get("question"));
    }
    @SuppressWarnings("unchecked") public static Map<String, Object> object(Object value) {
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException("Extension JSON object is required");
        return (Map<String, Object>) map;
    }
    private static BigDecimal positiveScore(Object value) {
        if (!(value instanceof Number number)) throw new IllegalArgumentException("Extension maximum score must be numeric");
        var score = new BigDecimal(number.toString());
        if (score.signum() <= 0 || !Double.isFinite(score.doubleValue())) throw new IllegalArgumentException("Extension maximum score must be positive and finite");
        return score;
    }
}
