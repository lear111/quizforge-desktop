package io.quizforge.infrastructure.json;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quizforge.core.practice.EssayPracticeAnswer;

/** Parser limits for owned native documents and their Base64 transport envelopes. */
public final class DocumentJson {
    private DocumentJson() { }
    public static ObjectMapper mapper() {
        int transportLimit = EssayPracticeAnswer.MAX_DOCUMENT_CHARACTERS / 3 * 4 + 4;
        return new ObjectMapper(JsonFactory.builder().streamReadConstraints(
                StreamReadConstraints.builder().maxStringLength(transportLimit).build()).build());
    }
}
