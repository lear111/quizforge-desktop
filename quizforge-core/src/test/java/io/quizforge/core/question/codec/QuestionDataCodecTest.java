package io.quizforge.core.question.codec;

import io.quizforge.core.question.content.*;
import io.quizforge.core.question.model.*;
import io.quizforge.core.question.model.choice.*;
import io.quizforge.core.question.model.extension.ExtensionPayload;
import io.quizforge.core.question.type.extension.ExternalQuestionTypeDefinition;
import io.quizforge.core.question.service.*;
import io.quizforge.core.question.source.SourceRef;
import io.quizforge.core.question.type.QuestionTypeDefinition;
import io.quizforge.core.question.type.QuestionTypes;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuestionDataCodecTest {
    private QuestionBank bank(Question question) { return new QuestionBank("qb_bridge", "Bridge", List.of(), List.of(question), List.of()); }
    private final RichContent rich = new RichContent(new RichDocument(List.of(new ParagraphNode(List.of(
            new InlineTextNode("Formatted explanation", List.of(TextMark.BOLD))), TextAlignment.CENTER))));

    @Test void currentChoiceDataRetainsIdentityReferencesAndOptionalContent() {
        for (String type : List.of("SINGLE_CHOICE", "MULTIPLE_CHOICE", "sample.BINARY_DECISION")) {
            var original = new Question("q_original", type, List.of("stimulus_shared"), new TextContent("Prompt"),
                    new ChoicePayload(List.of(new ChoiceOption("opt_a", new TextContent("A")),
                            new ChoiceOption("opt_b", new TextContent("B")))),
                    new ChoiceAnswerSpec(List.of("opt_b")), new ScoreSpec(new BigDecimal("2.5")),
                    new EvaluationSpec(List.of(new EvaluationCriterion("meaning", "Meaning", BigDecimal.ONE)), "Guidance"), rich,
                    List.of(SourceRef.anchor("doc_source", "qfd:v2:" + "0".repeat(64), "section", 1, "Source", "Section")));
            var encoded = QuestionDataCodec.encodePersisted(original);
            assertEquals(original, QuestionDataCodec.decodeChoice(encoded, original));
            assertThrows(UnsupportedOperationException.class, () -> encoded.put("maxScore", 99));
            var edit = new LinkedHashMap<>(encoded);
            edit.put("id", "q_forged"); edit.put("type", "ESSAY");
            edit.put("sourceRefs", List.of()); edit.put("stimulusRefs", List.of());
            var updated = QuestionDataCodec.decodeChoice(edit, original);
            assertEquals(original, updated);
            edit.put("answerSpec", Map.of("correctOptionIds", List.of()));
            assertTrue(QuestionDataCodec.decodeChoice(edit, original).choiceAnswerSpec().correctOptionIds().isEmpty());
        }
    }

    @Test void generalizedExternalEditsRemainOpaqueAndPreserveEvaluationUnlessExplicitlyChanged() {
        var type = new ExternalQuestionTypeDefinition("bridge.TEST", "Bridge", QuestionTypeDefinition.Family.OBJECTIVE, "1.0.0",
                (operation, input) -> Map.of());
        QuestionTypes.register(type);
        try {
            var original = type.decodeQuestion(Map.of("prompt", Map.of("kind", "TEXT", "text", "External"),
                    "payload", Map.of("statement", "Opaque"), "answerSpec", Map.of("correct", true), "maxScore", 2), "q_external", List.of());
            original = new Question(original.id(), original.type(), original.stimulusRefs(), original.prompt(), original.payload(), original.answerSpec(),
                    original.scoreSpec(), new EvaluationSpec(List.of(), "Preserved"), null, original.sourceRefs());
            var edit = new QuestionBankEditorModel(bank(original));
            assertEquals(((ExtensionPayload)original.payload()).data(), QuestionDataCodec.encode(original).get("payload"));
            edit.setEditorQuestion(0, Map.of("payload", Map.of("statement", "Changed")));
            assertEquals("Changed", ((ExtensionPayload)edit.bank().questions().getFirst().payload()).data().get("statement"));
            assertEquals(original.evaluationSpec(), edit.bank().questions().getFirst().evaluationSpec());
            var before = edit.bank();
            assertThrows(IllegalArgumentException.class, () -> edit.setEditorQuestion(0, Map.of("payload", Map.of("statement", "Should not publish"), "evaluationSpec", "invalid")));
            assertSame(before, edit.bank());
        } finally { QuestionTypes.unregister(type.id()); }
    }
}
