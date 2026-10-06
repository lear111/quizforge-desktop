package io.quizforge.core.question.type.extension;

import io.quizforge.core.question.type.QuestionTypeDefinition;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExternalChoiceTemplateTest {
    @Test void extensionDefinedChoiceTypeCreatesAndDuplicatesWithoutBuiltinTypeName() {
        for (String id : List.of("TRUE_FALSE", "sample.BINARY_DECISION")) {
            var template = Map.<String,Object>of("id", "q_template", "type", id,
                "prompt", Map.of("kind", "TEXT", "text", "A statement"),
                "payload", Map.of("kind", "CHOICE", "options", List.of(
                    Map.of("id", "opt_true", "content", Map.of("kind", "TEXT", "text", "正确")),
                    Map.of("id", "opt_false", "content", Map.of("kind", "TEXT", "text", "错误")))),
                "answerSpec", Map.of("kind", "CHOICE", "correctOptionIds", List.of("opt_false")),
                "scoreSpec", Map.of("defaultMaxScore", 2));
            var type = new ExternalQuestionTypeDefinition(id, "判断题", QuestionTypeDefinition.Family.OBJECTIVE,
                "2.1.0", "sample.binary", 1, (operation, input) -> Map.of("errors", List.of()), template);
            var sequence = new AtomicInteger();
            var question = type.createDraft(prefix -> prefix + sequence.incrementAndGet(), List.of());
            assertEquals(id, question.type());
            assertEquals(2, question.choicePayload().options().size());
            assertEquals("错误", ((io.quizforge.core.question.content.TextContent)question.choicePayload().options().get(1).content()).text());
            var duplicate = type.duplicate(question, prefix -> prefix + sequence.incrementAndGet());
            assertNotEquals(question.id(), duplicate.id());
            assertNotEquals(question.choicePayload().options().get(1).id(), duplicate.choicePayload().options().get(1).id());
            assertEquals(List.of(duplicate.choicePayload().options().get(1).id()), duplicate.choiceAnswerSpec().correctOptionIds());
            assertEquals("CHOICE", ExternalQuestionTypeDefinition.object(type.encodeQuestion(duplicate).get("payload")).get("kind"));
            assertEquals(duplicate, type.decodeQuestion(type.encodeQuestion(duplicate), duplicate.id(), List.of()));
        }
    }
}
