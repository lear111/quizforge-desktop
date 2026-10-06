package io.quizforge.core.question.model.extension;

import io.quizforge.core.question.model.QuestionAnswerSpec;
import java.util.Map;

/** The extension's correct-answer specification, kept separate from public presentation. */
public record ExtensionAnswerSpec(Map<String, Object> data) implements QuestionAnswerSpec {
    public ExtensionAnswerSpec { data = ExtensionPayload.freeze(data); }
}
