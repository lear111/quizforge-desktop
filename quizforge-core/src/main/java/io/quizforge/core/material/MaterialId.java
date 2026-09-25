package io.quizforge.core.material;

import java.util.UUID;

public record MaterialId(UUID value) {
    public MaterialId {
        if (value == null) {
            throw new IllegalArgumentException("Material ID is required");
        }
    }

    public static MaterialId newId() {
        return new MaterialId(UUID.randomUUID());
    }

    public static MaterialId parse(String value) {
        return new MaterialId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
