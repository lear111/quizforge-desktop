package io.quizforge.core.question;

import java.util.UUID;

public record QuestionBankId(UUID value) {
    public QuestionBankId { if (value == null) throw new IllegalArgumentException("Question bank ID required"); }
    public static QuestionBankId newId() { return new QuestionBankId(UUID.randomUUID()); }
    public static QuestionBankId parse(String value) { return new QuestionBankId(UUID.fromString(value)); }
    @Override public String toString() { return value.toString(); }
}
