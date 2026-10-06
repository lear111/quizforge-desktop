package io.quizforge.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quizforge.core.question.type.extension.ExtensionDataValidationException;
import io.quizforge.infrastructure.extension.ExtensionSchemaValidator;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExtensionSchemaComplexityTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    private ExtensionSchemaValidator compile(String answer) {
        return new ExtensionSchemaValidator("test.safe", "{}", answer);
    }

    @Test void rejectsCatastrophicPatternBeforeCompilingOrValidatingIt() throws Exception {
        String schema = JSON.writeValueAsString(Map.of("type", "string", "pattern", "^(a+)+$"));
        assertTimeout(Duration.ofSeconds(1), () -> {
            var failure = assertThrows(ExtensionDataValidationException.class, () -> compile(schema));
            assertTrue(failure.getMessage().contains("/schemas/answer/pattern"));
        });
    }

    @Test void appliesTheSameProfileToPatternProperties() throws Exception {
        String schema = JSON.writeValueAsString(Map.of("patternProperties", Map.of("^(a+)+$", Map.of("type", "string"))));
        var failure = assertThrows(ExtensionDataValidationException.class, () -> compile(schema));
        assertTrue(failure.getMessage().contains("/patternProperties/"));
    }

    @Test void rejectsComplexEscapesAlternationRepeatedQuantifiersAndUnanchoredRepetition() throws Exception {
        for (String pattern : new String[] {"^(a)\\1$", "^a|b$", "^a+b+$", "a+b", "^a{1025}$", "^[a-z&&[^x]]+$", "^\\p{L}+$"}) {
            String schema = JSON.writeValueAsString(Map.of("pattern", pattern));
            assertThrows(ExtensionDataValidationException.class, () -> compile(schema), pattern);
        }
    }

    @Test void limitsPatternLengthAndTotalCountAcrossSchemas() throws Exception {
        assertThrows(ExtensionDataValidationException.class, () -> compile(JSON.writeValueAsString(Map.of("pattern", "a".repeat(257)))));
        var patterns = JSON.createObjectNode();
        for (int index = 0; index < 65; index++) patterns.putObject("key" + index).put("type", "string");
        String schema = JSON.createObjectNode().set("patternProperties", patterns).toString();
        assertThrows(ExtensionDataValidationException.class, () -> compile(schema));
    }

    @Test void acceptsSimpleAnchoredPatternsAndChecksActualData() {
        var validator = compile("""
                {"type":"object","properties":{"value":{"type":"string","pattern":"^[-A-Za-z0-9_]+$"}}}
                """);
        validator.answer(Map.of("value", "Hello_123-"));
        assertThrows(ExtensionDataValidationException.class, () -> validator.answer(Map.of("value", "not valid!")));
        var bounded = compile("""
                {"properties":{"value":{"type":"string","pattern":"^[A-Z]{1,4}$"}}}
                """);
        bounded.answer(Map.of("value", "ABCD"));
        assertThrows(ExtensionDataValidationException.class, () -> bounded.answer(Map.of("value", "ABCDE")));
    }

    @Test void limitsSourceCharactersBeforeJsonParsing() {
        String schema = "{\"description\":\"" + "a".repeat(256 * 1024) + "\"}";
        var failure = assertThrows(ExtensionDataValidationException.class, () -> compile(schema));
        assertTrue(failure.getMessage().contains("256 KiB"));
    }

    @Test void rejectsAcyclicReferenceDagWithExponentialExpansion() {
        var schema = JSON.createObjectNode();
        var definitions = schema.putObject("definitions");
        definitions.putObject("s0").put("type", "string");
        for (int index = 1; index <= 14; index++) {
            var branches = definitions.putObject("s" + index).putArray("allOf");
            branches.addObject().put("$ref", "#/definitions/s" + (index - 1));
            branches.addObject().put("$ref", "#/definitions/s" + (index - 1));
        }
        schema.putObject("properties").putObject("value").put("$ref", "#/definitions/s14");
        var failure = assertTimeout(Duration.ofSeconds(1), () ->
                assertThrows(ExtensionDataValidationException.class, () -> compile(schema.toString())));
        assertTrue(failure.getMessage().contains("expansion"));
    }
}
