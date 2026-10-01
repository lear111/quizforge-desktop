package io.quizforge.infrastructure.persistence.practice;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quizforge.core.ErrorCode;
import io.quizforge.core.QuizForgeException;
import io.quizforge.core.practice.PracticePayload;

/** Persistence encoding only; deliberately independent of the .qbank file contract. */
final class PracticePayloadJsonCodec {
    private final ObjectMapper json = new ObjectMapper(com.fasterxml.jackson.core.JsonFactory.builder()
            .streamReadConstraints(com.fasterxml.jackson.core.StreamReadConstraints.builder()
                    .maxStringLength(io.quizforge.core.practice.EssayPracticeAnswer.MAX_DOCUMENT_CHARACTERS / 3 * 4 + 4).build()).build())
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS);

    String encode(PracticePayload payload) {
        if (payload == null) return null;
        try {
            return json.writeValueAsString(payload.value());
        } catch (JsonProcessingException error) {
            throw failure(error);
        }
    }

    PracticePayload decode(String source) {
        if (source == null) return null;
        try {
            return new PracticePayload(json.readValue(source, Object.class));
        } catch (JsonProcessingException | IllegalArgumentException | NullPointerException error) {
            throw failure(error);
        }
    }

    private QuizForgeException failure(Exception error) {
        return new QuizForgeException(ErrorCode.PERSISTENCE_FAILED,
                "Could not encode or decode practice payload.", error);
    }
}
