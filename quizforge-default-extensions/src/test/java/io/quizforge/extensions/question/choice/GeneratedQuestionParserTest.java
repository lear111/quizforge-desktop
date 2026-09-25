package io.quizforge.extensions.question.choice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.quizforge.extension.question.QuestionOutputParseException;
import org.junit.jupiter.api.Test;

class GeneratedQuestionParserTest {
    private final GeneratedQuestionParser parser = new GeneratedQuestionParser();

    @Test void parsesValidJson() {
        var result = parser.parse("""
                {"questions":[{"type":"SINGLE_CHOICE","stem":"What?","options":[{"key":"A","content":"Yes"}],
                "correctAnswers":["A"],"analysis":"Because","sourceChapter":"C","sourceSection":"S"}]}
                """);
        assertEquals(1, result.questions().size());
        assertEquals("A", result.questions().getFirst().options().getFirst().key());
    }

    @Test void invalidTopLevelFails() {
        assertThrows(QuestionOutputParseException.class, () -> parser.parse("not json"));
        assertThrows(QuestionOutputParseException.class, () -> parser.parse("{}"));
        assertThrows(QuestionOutputParseException.class, () -> parser.parse("{\"questions\":{}}"));
    }

    @Test void malformedCandidatesRemainUntrustedCandidates() {
        var result = parser.parse("{\"questions\":[{\"type\":\"UNKNOWN\"},{\"type\":7,\"options\":true}]}");
        assertEquals(2, result.questions().size());
        assertEquals("UNKNOWN", result.questions().getFirst().type());
        assertEquals(0, result.questions().get(1).options().size());
        assertEquals("", result.questions().get(1).type());
    }
}
