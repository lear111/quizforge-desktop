package io.quizforge.core.practice;

import io.quizforge.core.port.QuestionResourceInput;
import io.quizforge.core.question.content.QuestionContentData;
import io.quizforge.core.question.model.Question;
import io.quizforge.core.question.model.ScoreSpec;
import io.quizforge.core.question.resource.QBankResource;
import io.quizforge.core.question.resource.ResourceKind;
import io.quizforge.core.question.type.objective.choice.ChoiceOption;
import io.quizforge.core.question.type.objective.reading.ReadingAnswerSpec;
import io.quizforge.core.question.type.objective.reading.ReadingItem;
import io.quizforge.core.question.type.objective.reading.ReadingPayload;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.MathContext;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Frozen article, item prompts and resource bytes, independent of the current bank. */
public record ReadingQuestionSnapshot(Question question, List<QBankResource> resources, Map<String,String> resourceData) {
    public ReadingQuestionSnapshot {
        resources = List.copyOf(resources);
        resourceData = Map.copyOf(resourceData);
    }

    public static Map<String,Object> logical(Question question) {
        var payload = (ReadingPayload) question.payload();
        var fields = new LinkedHashMap<String,Object>();
        fields.put("id", question.id());
        fields.put("prompt", QuestionContentData.encode(question.prompt()));
        fields.put("unitScore", question.scoreSpec().defaultMaxScore());
        fields.put("maxScore", question.scoreSpec().defaultMaxScore().multiply(BigDecimal.valueOf(payload.items().size())));
        if (question.analysis() != null) fields.put("analysis", QuestionContentData.encode(question.analysis()));
        fields.put("items", payload.items().stream().map(item -> Map.of("id", item.id(), "number", item.number(),
                "prompt", QuestionContentData.encode(item.prompt()), "options", item.options().stream().map(option ->
                        Map.of("id", option.id(), "content", QuestionContentData.encode(option.content()))).toList())).toList());
        fields.put("answers", ((ReadingAnswerSpec) question.answerSpec()).answers().stream().map(answer ->
                Map.of("itemId", answer.itemId(), "correctOptionId", answer.correctOptionId())).toList());
        return fields;
    }

    public static ReadingQuestionSnapshot capture(Question question, List<QBankResource> resources, QuestionResourceInput input) {
        var used = new HashSet<>(QuestionContentData.resourceIds(question.prompt()));
        used.addAll(QuestionContentData.resourceIds(question.analysis()));
        for (var item : ((ReadingPayload) question.payload()).items()) {
            used.addAll(QuestionContentData.resourceIds(item.prompt()));
            item.options().forEach(option -> used.addAll(QuestionContentData.resourceIds(option.content())));
        }
        var catalogue = resources.stream().filter(resource -> used.contains(resource.id())).toList();
        var data = new LinkedHashMap<String,String>();
        if (input != QuestionResourceInput.NONE) for (var resource : catalogue) try (var stream = input.open(resource)) {
            if (stream == null) throw new IOException("Missing resource: " + resource.id());
            var bytes = stream.readNBytes(EssayPracticeAnswer.MAX_DOCUMENT_CHARACTERS + 1);
            if (bytes.length > EssayPracticeAnswer.MAX_DOCUMENT_CHARACTERS
                    || !HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals(resource.sha256()))
                throw new IOException("Invalid reading resource: " + resource.id());
            data.put(resource.id(), Base64.getEncoder().encodeToString(bytes));
        } catch (IOException | java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException("Cannot snapshot reading resource", error);
        }
        return new ReadingQuestionSnapshot(question, catalogue, data);
    }

    public PracticePayload payload() {
        var fields = new LinkedHashMap<>(logical(question));
        fields.put("resources", resources.stream().map(resource -> Map.of("id", resource.id(), "kind", resource.kind().name(),
                "mediaType", resource.mediaType(), "path", resource.locator(), "sha256", resource.sha256())).toList());
        fields.put("resourceData", resourceData);
        return new PracticePayload(fields);
    }

    public static ReadingQuestionSnapshot from(PracticePayload payload) {
        var fields = QuestionContentData.map(payload.value());
        var items = ((List<?>) fields.get("items")).stream().map(value -> {
            var item = QuestionContentData.map(value);
            var options = ((List<?>) item.get("options")).stream().map(optionValue -> {
                var option = QuestionContentData.map(optionValue);
                return new ChoiceOption((String) option.get("id"), QuestionContentData.decode(option.get("content")));
            }).toList();
            return new ReadingItem((String) item.get("id"), ((Number) item.get("number")).intValue(),
                    QuestionContentData.decode(item.get("prompt")), options);
        }).toList();
        var answers = ((List<?>) fields.get("answers")).stream().map(value -> {
            var answer = QuestionContentData.map(value);
            return new ReadingAnswerSpec.Answer((String) answer.get("itemId"), (String) answer.get("correctOptionId"));
        }).toList();
        var unitScore = fields.containsKey("unitScore") ? new BigDecimal(fields.get("unitScore").toString())
                : new BigDecimal(fields.get("maxScore").toString()).divide(BigDecimal.valueOf(items.size()), MathContext.DECIMAL128);
        var question = new Question((String) fields.get("id"), "READING", List.of(), QuestionContentData.decode(fields.get("prompt")),
                new ReadingPayload(items), new ReadingAnswerSpec(answers), new ScoreSpec(unitScore), null,
                fields.containsKey("analysis") ? QuestionContentData.decode(fields.get("analysis")) : null, List.of());
        var resources = (fields.get("resources") instanceof List<?> values ? values : List.of()).stream().map(value -> {
            var resource = QuestionContentData.map(value);
            return new QBankResource((String) resource.get("id"), ResourceKind.valueOf((String) resource.get("kind")),
                    (String) resource.get("mediaType"), (String) resource.get("path"), (String) resource.get("sha256"));
        }).toList();
        var data = new LinkedHashMap<String,String>();
        if (fields.get("resourceData") instanceof Map<?,?> values)
            values.forEach((id, bytes) -> data.put((String) id, (String) bytes));
        return new ReadingQuestionSnapshot(question, resources, data);
    }

    public InputStream open(QBankResource resource) {
        var bytes = resourceData.get(resource.id());
        return bytes == null ? null : new ByteArrayInputStream(Base64.getDecoder().decode(bytes));
    }
}
