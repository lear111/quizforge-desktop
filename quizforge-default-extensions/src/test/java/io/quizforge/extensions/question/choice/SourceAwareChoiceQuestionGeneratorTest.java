package io.quizforge.extensions.question.choice;

import static org.junit.jupiter.api.Assertions.*;

import io.quizforge.extension.question.QuestionOutputParseException;
import io.quizforge.extension.question.SourceAwareQuestionGenerator;
import org.junit.jupiter.api.Test;

class SourceAwareChoiceQuestionGeneratorTest {
    private final SourceAwareChoiceQuestionGenerator generator =
            new SourceAwareChoiceQuestionGenerator(() -> { throw new AssertionError(); });

    @Test void parsesSourceAwareCandidateWithoutTrustingForgedIds() {
        var result = generator.parse("""
                {"questions":[{"id":"q_forged","type":"SINGLE_CHOICE","stem":"Question?",
                "analysis":"Because.","options":[{"id":"opt_forged","key":"A","content":"Yes"},
                {"key":"B","content":"No"}],"correctOptionKeys":["A"],
                "sourceRefs":[{"documentAssetId":"doc_a","sectionId":"section_a"}]}]}
                """);
        assertEquals(1, result.size());
        SourceAwareQuestionGenerator.Candidate candidate = result.getFirst();
        assertEquals("doc_a", candidate.sourceRefs().getFirst().documentAssetId());
        assertEquals("section_a", candidate.sourceRefs().getFirst().sectionId());
        assertEquals("A", candidate.correctOptionKeys().getFirst());
    }

    @Test void malformedJsonFailsWholeResponse() {
        assertThrows(QuestionOutputParseException.class, () -> generator.parse("not JSON"));
        assertThrows(QuestionOutputParseException.class, () -> generator.parse("{\"questions\":{}}"));
    }

    @Test void incompleteCandidateRemainsAvailableForPartialRejection() {
        var result = generator.parse("{\"questions\":[{\"stem\":\"Incomplete\"}]}");
        assertEquals(1, result.size());
        assertTrue(result.getFirst().sourceRefs().isEmpty());
    }
}
