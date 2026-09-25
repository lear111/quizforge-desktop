package io.quizforge.core.question;

import java.util.UUID;

public record QuestionId(UUID value) {
    public QuestionId { if (value == null) throw new IllegalArgumentException("Question ID required"); }
    public static QuestionId newId() { return new QuestionId(UUID.randomUUID()); }
    public static QuestionId parse(String value) { return new QuestionId(UUID.fromString(value)); }
    @Override public String toString() { return value.toString(); }
}
