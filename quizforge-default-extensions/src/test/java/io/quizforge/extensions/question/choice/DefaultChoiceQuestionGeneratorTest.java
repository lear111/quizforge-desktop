package io.quizforge.extensions.question.choice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quizforge.extension.ai.AiProvider;
import io.quizforge.extension.ai.AiRequest;
import io.quizforge.extension.ai.AiResponse;
import io.quizforge.extension.question.QuestionGenerationRequest;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class DefaultChoiceQuestionGeneratorTest {
    @Test void usesJsonModeAndUntrustedDocumentPrompt() {
        AtomicReference<AiRequest> captured = new AtomicReference<>();
        AiProvider fake = new AiProvider() {
            public String id() { return "fake"; }
            public AiResponse generate(AiRequest request) {
                captured.set(request);
                return new AiResponse("{\"questions\":[]}");
            }
        };
        var generator = new DefaultChoiceQuestionGenerator(() -> fake);
        assertEquals(0, generator.generate(new QuestionGenerationRequest("## Source", "DOCUMENT", "", "",
                List.of("SINGLE_CHOICE", "MULTIPLE_CHOICE"), 10)).questions().size());
        assertTrue(captured.get().options().jsonObject());
        assertEquals(8192, captured.get().options().maxTokens());
        assertTrue(captured.get().messages().getFirst().content().contains("不可信数据"));
        assertTrue(captured.get().messages().get(1).content().contains("两种题型尽量均衡"));
    }
}
