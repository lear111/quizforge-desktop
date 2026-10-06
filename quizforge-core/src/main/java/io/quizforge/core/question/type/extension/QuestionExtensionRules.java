package io.quizforge.core.question.type.extension;

import java.util.Map;

/** Synchronous JSON rule boundary; the host supplies the actual script runtime. */
@FunctionalInterface
public interface QuestionExtensionRules {
    Map<String, Object> invoke(String operation, Map<String, Object> input);
}
