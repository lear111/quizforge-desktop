package io.quizforge.extensions.question.choice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quizforge.extension.question.GeneratedOption;
import io.quizforge.extension.question.GeneratedQuestion;
import io.quizforge.extension.question.QuestionGenerationResult;
import io.quizforge.extension.question.QuestionOutputParseException;
import java.util.ArrayList;
import java.util.List;

public final class GeneratedQuestionParser {
    private final ObjectMapper mapper = new ObjectMapper();

    public QuestionGenerationResult parse(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            if (root == null || !root.isObject() || !root.path("questions").isArray()) {
                throw new QuestionOutputParseException("AI output must contain a questions array.");
            }
            List<GeneratedQuestion> questions = new ArrayList<>();
            for (JsonNode item : root.path("questions")) {
                List<GeneratedOption> options = new ArrayList<>();
                if (item.path("options").isArray()) {
                    for (JsonNode option : item.path("options")) {
                        options.add(new GeneratedOption(string(option, "key"), string(option, "content")));
                    }
                }
                List<String> correct = new ArrayList<>();
                if (item.path("correctAnswers").isArray()) {
                    for (JsonNode answer : item.path("correctAnswers")) {
                        correct.add(answer.isTextual() ? answer.asText() : "");
                    }
                }
                questions.add(new GeneratedQuestion(string(item, "type"), string(item, "stem"),
                        options, correct, string(item, "analysis"),
                        string(item, "sourceChapter"), string(item, "sourceSection")));
            }
            return new QuestionGenerationResult(questions);
        } catch (QuestionOutputParseException error) {
            throw error;
        } catch (Exception error) {
            throw new QuestionOutputParseException("AI output is not valid JSON.");
        }
    }

    private String string(JsonNode node, String name) {
        JsonNode value = node.path(name);
        return value.isTextual() ? value.asText() : "";
    }
}
