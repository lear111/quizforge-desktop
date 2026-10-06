package io.quizforge.core.question.type;

/** Stable interaction target. The host assigns global outline numbers to gradable targets. */
public record QuestionTarget(String id, int number, boolean gradable, boolean locked, String label) {
    public QuestionTarget {
        if (id == null || id.isBlank() || number < 1) throw new IllegalArgumentException("Invalid question target");
        if (label == null) label = "";
    }
}
