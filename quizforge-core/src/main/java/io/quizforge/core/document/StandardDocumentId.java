package io.quizforge.core.document;

import java.util.UUID;

public record StandardDocumentId(UUID value) {
    public StandardDocumentId {
        if (value == null) {
            throw new IllegalArgumentException("Document ID is required");
        }
    }

    public static StandardDocumentId newId() {
        return new StandardDocumentId(UUID.randomUUID());
    }

    public static StandardDocumentId parse(String value) {
        return new StandardDocumentId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
