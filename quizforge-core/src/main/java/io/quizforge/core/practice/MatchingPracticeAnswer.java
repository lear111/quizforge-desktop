package io.quizforge.core.practice;

import java.util.LinkedHashMap;
import java.util.Map;

/** Positional answers: selecting the right pool in the wrong slots is still wrong. */
public record MatchingPracticeAnswer(Map<String,String> assignments) {
    public MatchingPracticeAnswer { assignments = Map.copyOf(assignments); }
    public boolean empty() { return assignments.isEmpty(); }
    public PracticePayload payload() { return new PracticePayload(assignments); }

    public static MatchingPracticeAnswer from(PracticePayload payload) {
        if (payload == null) return new MatchingPracticeAnswer(Map.of());
        if (!(payload.value() instanceof Map<?,?> values))
            throw new IllegalStateException("Matching answer must be a blank-to-option map");
        var assignments = new LinkedHashMap<String,String>();
        values.forEach((key,value) -> {
            if (!(key instanceof String blankId) || blankId.isBlank()
                    || !(value instanceof String optionId) || optionId.isBlank())
                throw new IllegalStateException("Invalid matching assignment");
            assignments.put(blankId, optionId);
        });
        return new MatchingPracticeAnswer(assignments);
    }
}
