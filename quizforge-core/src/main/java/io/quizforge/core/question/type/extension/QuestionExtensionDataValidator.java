package io.quizforge.core.question.type.extension;

import java.util.Map;

/** Trusted schema implementation supplied by the host, outside the extension's rules engine. */
public interface QuestionExtensionDataValidator {
    void question(Map<String, Object> value);
    void answer(Map<String, Object> value);

    /** For programmatically registered host fixtures; installed packages always supply a validator. */
    QuestionExtensionDataValidator NONE = new QuestionExtensionDataValidator() {
        public void question(Map<String, Object> value) { }
        public void answer(Map<String, Object> value) { }
    };
}
