package io.quizforge.core.practice;

import io.quizforge.core.port.QuestionResourceInput;
import io.quizforge.core.question.content.QuestionContentData;
import io.quizforge.core.question.model.Question;
import io.quizforge.core.question.model.ScoreSpec;
import io.quizforge.core.question.resource.QBankResource;
import io.quizforge.core.question.resource.ResourceKind;
import io.quizforge.core.question.type.subjective.translation.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.util.*;

/** Frozen article, translation sentences, references and analysis survive bank edits or deletion. */
public record TranslationQuestionSnapshot(Question question, List<QBankResource> resources, Map<String,String> resourceData) {
    public TranslationQuestionSnapshot {
        resources = List.copyOf(resources);
        resourceData = Map.copyOf(resourceData);
    }

    public static Map<String,Object> logical(Question question) {
        var payload = (TranslationPayload) question.payload();
        var fields = new LinkedHashMap<String,Object>();
        fields.put("id", question.id());
        fields.put("prompt", QuestionContentData.encode(question.prompt()));
        fields.put("unitScore", question.scoreSpec().defaultMaxScore());
        fields.put("maxScore", question.scoreSpec().defaultMaxScore().multiply(BigDecimal.valueOf(payload.items().size())));
        if (question.analysis() != null) fields.put("analysis", QuestionContentData.encode(question.analysis()));
        fields.put("items", payload.items().stream().map(item -> Map.of("id", item.id(), "number", item.number(), "text", item.text())).toList());
        fields.put("answers", ((TranslationAnswerSpec) question.answerSpec()).answers().stream().map(answer -> {
            var entry = new LinkedHashMap<String,Object>();
            entry.put("itemId", answer.itemId());
            if (answer.referenceAnswer() != null) entry.put("referenceAnswer", QuestionContentData.encode(answer.referenceAnswer()));
            return entry;
        }).toList());
        return fields;
    }

    public static TranslationQuestionSnapshot capture(Question question, List<QBankResource> resources, QuestionResourceInput input) {
        var used = new HashSet<>(QuestionContentData.resourceIds(question.prompt()));
        used.addAll(QuestionContentData.resourceIds(question.analysis()));
        ((TranslationAnswerSpec) question.answerSpec()).answers().forEach(answer ->
                used.addAll(QuestionContentData.resourceIds(answer.referenceAnswer())));
        var catalogue = resources.stream().filter(resource -> used.contains(resource.id())).toList();
        var data = new LinkedHashMap<String,String>();
        if (input != QuestionResourceInput.NONE) for (var resource : catalogue) try (var stream = input.open(resource)) {
            if (stream == null) throw new IOException("Missing resource: " + resource.id());
            var bytes = stream.readNBytes(EssayPracticeAnswer.MAX_DOCUMENT_CHARACTERS + 1);
            if (bytes.length > EssayPracticeAnswer.MAX_DOCUMENT_CHARACTERS
                    || !HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals(resource.sha256()))
                throw new IOException("Invalid translation resource: " + resource.id());
            data.put(resource.id(), Base64.getEncoder().encodeToString(bytes));
        } catch (IOException | java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException("Cannot snapshot translation resource", error);
        }
        return new TranslationQuestionSnapshot(question, catalogue, data);
    }

    public PracticePayload payload() {
        var fields = new LinkedHashMap<>(logical(question));
        fields.put("resources", resources.stream().map(resource -> Map.of("id", resource.id(), "kind", resource.kind().name(),
                "mediaType", resource.mediaType(), "path", resource.locator(), "sha256", resource.sha256())).toList());
        fields.put("resourceData", resourceData);
        return new PracticePayload(fields);
    }

    public static TranslationQuestionSnapshot from(PracticePayload payload) {
        var fields = QuestionContentData.map(payload.value());
        var items = ((List<?>) fields.get("items")).stream().map(value -> {
            var item = QuestionContentData.map(value);
            return new TranslationItem((String) item.get("id"), ((Number) item.get("number")).intValue(), (String) item.get("text"));
        }).toList();
        var answers = ((List<?>) fields.get("answers")).stream().map(value -> {
            var answer = QuestionContentData.map(value);
            return new TranslationAnswerSpec.Answer((String) answer.get("itemId"),
                    answer.containsKey("referenceAnswer") ? QuestionContentData.decode(answer.get("referenceAnswer")) : null);
        }).toList();
        var question = new Question((String) fields.get("id"), "TRANSLATION", List.of(), QuestionContentData.decode(fields.get("prompt")),
                new TranslationPayload(items), new TranslationAnswerSpec(answers), new ScoreSpec(new BigDecimal(fields.get("unitScore").toString())), null,
                fields.containsKey("analysis") ? QuestionContentData.decode(fields.get("analysis")) : null, List.of());
        var resources = (fields.get("resources") instanceof List<?> values ? values : List.of()).stream().map(value -> {
            var resource = QuestionContentData.map(value);
            return new QBankResource((String) resource.get("id"), ResourceKind.valueOf((String) resource.get("kind")),
                    (String) resource.get("mediaType"), (String) resource.get("path"), (String) resource.get("sha256"));
        }).toList();
        var data = new LinkedHashMap<String,String>();
        if (fields.get("resourceData") instanceof Map<?,?> values)
            values.forEach((id, bytes) -> data.put((String) id, (String) bytes));
        return new TranslationQuestionSnapshot(question, resources, data);
    }

    public InputStream open(QBankResource resource) {
        var bytes = resourceData.get(resource.id());
        return bytes == null ? null : new ByteArrayInputStream(Base64.getDecoder().decode(bytes));
    }
}
