package io.quizforge.core.practice;

import io.quizforge.core.port.QuestionResourceInput;
import io.quizforge.core.question.content.QuestionContentData;
import io.quizforge.core.question.model.Question;
import io.quizforge.core.question.model.ScoreSpec;
import io.quizforge.core.question.resource.QBankResource;
import io.quizforge.core.question.resource.ResourceKind;
import io.quizforge.core.question.type.objective.matching.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.util.*;

/** Frozen rich article, answer slots and analysis survive edits or deletion of the bank. */
public record MatchingQuestionSnapshot(Question question, List<QBankResource> resources, Map<String,String> resourceData) {
    public MatchingQuestionSnapshot {
        resources = List.copyOf(resources);
        resourceData = Map.copyOf(resourceData);
    }

    public static Map<String,Object> logical(Question question) {
        var payload = (MatchingPayload) question.payload();
        var fields = new LinkedHashMap<String,Object>();
        fields.put("id", question.id());
        fields.put("prompt", QuestionContentData.encode(question.prompt()));
        fields.put("unitScore", question.scoreSpec().defaultMaxScore());
        fields.put("maxScore", question.scoreSpec().defaultMaxScore().multiply(BigDecimal.valueOf(MatchingQuestionType.gradableCount(question))));
        if (question.analysis() != null) fields.put("analysis", QuestionContentData.encode(question.analysis()));
        fields.put("blanks", payload.blanks().stream().map(blank -> Map.of("id", blank.id(), "number", blank.number(), "locked", blank.locked())).toList());
        fields.put("options", payload.options().stream().map(option -> Map.of("id", option.id(), "label", option.label())).toList());
        fields.put("answers", ((MatchingAnswerSpec) question.answerSpec()).answers().stream().map(answer ->
                Map.of("blankId", answer.blankId(), "correctOptionId", answer.correctOptionId())).toList());
        return fields;
    }

    public static MatchingQuestionSnapshot capture(Question question, List<QBankResource> resources, QuestionResourceInput input) {
        var used = new HashSet<>(QuestionContentData.resourceIds(question.prompt()));
        used.addAll(QuestionContentData.resourceIds(question.analysis()));
        var catalogue = resources.stream().filter(resource -> used.contains(resource.id())).toList();
        var data = new LinkedHashMap<String,String>();
        if (input != QuestionResourceInput.NONE) for (var resource : catalogue) try (var stream = input.open(resource)) {
            if (stream == null) throw new IOException("Missing resource: " + resource.id());
            var bytes = stream.readNBytes(EssayPracticeAnswer.MAX_DOCUMENT_CHARACTERS + 1);
            if (bytes.length > EssayPracticeAnswer.MAX_DOCUMENT_CHARACTERS
                    || !HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals(resource.sha256()))
                throw new IOException("Invalid matching resource: " + resource.id());
            data.put(resource.id(), Base64.getEncoder().encodeToString(bytes));
        } catch (IOException | java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException("Cannot snapshot matching resource", error);
        }
        return new MatchingQuestionSnapshot(question, catalogue, data);
    }

    public PracticePayload payload() {
        var fields = new LinkedHashMap<>(logical(question));
        fields.put("resources", resources.stream().map(resource -> Map.of("id", resource.id(), "kind", resource.kind().name(),
                "mediaType", resource.mediaType(), "path", resource.locator(), "sha256", resource.sha256())).toList());
        fields.put("resourceData", resourceData);
        return new PracticePayload(fields);
    }

    public static MatchingQuestionSnapshot from(PracticePayload payload) {
        var fields = QuestionContentData.map(payload.value());
        var blanks = ((List<?>) fields.get("blanks")).stream().map(value -> {
            var blank = QuestionContentData.map(value);
            return new MatchingBlank((String) blank.get("id"), ((Number) blank.get("number")).intValue(), Boolean.TRUE.equals(blank.get("locked")));
        }).toList();
        var options = ((List<?>) fields.get("options")).stream().map(value -> {
            var option = QuestionContentData.map(value);
            return new MatchingOption((String) option.get("id"), (String) option.get("label"));
        }).toList();
        var answers = ((List<?>) fields.get("answers")).stream().map(value -> {
            var answer = QuestionContentData.map(value);
            return new MatchingAnswerSpec.Answer((String) answer.get("blankId"), (String) answer.get("correctOptionId"));
        }).toList();
        var question = new Question((String) fields.get("id"), "MATCHING", List.of(), QuestionContentData.decode(fields.get("prompt")),
                new MatchingPayload(blanks, options), new MatchingAnswerSpec(answers), new ScoreSpec(new BigDecimal(fields.get("unitScore").toString())), null,
                fields.containsKey("analysis") ? QuestionContentData.decode(fields.get("analysis")) : null, List.of());
        var resources = (fields.get("resources") instanceof List<?> values ? values : List.of()).stream().map(value -> {
            var resource = QuestionContentData.map(value);
            return new QBankResource((String) resource.get("id"), ResourceKind.valueOf((String) resource.get("kind")),
                    (String) resource.get("mediaType"), (String) resource.get("path"), (String) resource.get("sha256"));
        }).toList();
        var data = new LinkedHashMap<String,String>();
        if (fields.get("resourceData") instanceof Map<?,?> values)
            values.forEach((id, bytes) -> data.put((String) id, (String) bytes));
        return new MatchingQuestionSnapshot(question, resources, data);
    }

    public InputStream open(QBankResource resource) {
        var bytes = resourceData.get(resource.id());
        return bytes == null ? null : new ByteArrayInputStream(Base64.getDecoder().decode(bytes));
    }
}
