package io.quizforge.core.practice;

import java.util.Map;

/** Self-contained essay answer. Native Canvas JSON includes its embedded images. */
public record EssayPracticeAnswer(String text, String document) {
    public static final int MAX_DOCUMENT_CHARACTERS = 64 * 1024 * 1024;
    public EssayPracticeAnswer {
        java.util.Objects.requireNonNull(text);
        if (document != null && document.isBlank()) throw new IllegalArgumentException("Empty Canvas document");
        if (document != null && document.length() > MAX_DOCUMENT_CHARACTERS)
            throw new IllegalArgumentException("Essay document is too large");
    }

    public boolean empty() { return document == null && text.isBlank(); }

    public PracticePayload payload() {
        return new PracticePayload(document == null ? Map.of("kind", "TEXT", "text", text)
                : Map.of("kind", "CANVAS_DOCUMENT", "text", text, "document", document));
    }

    public static EssayPracticeAnswer from(PracticePayload payload) {
        if (payload == null) return new EssayPracticeAnswer("", null);
        if (!(payload.value() instanceof Map<?, ?> fields) || !(fields.get("text") instanceof String text))
            throw new IllegalArgumentException("Invalid essay answer");
        if ("TEXT".equals(fields.get("kind"))) return new EssayPracticeAnswer(text, null);
        if ("CANVAS_DOCUMENT".equals(fields.get("kind")) && fields.get("document") instanceof String document)
            return new EssayPracticeAnswer(text, document);
        throw new IllegalArgumentException("Invalid essay answer kind");
    }
}
