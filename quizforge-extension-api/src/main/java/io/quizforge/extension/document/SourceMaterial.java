package io.quizforge.extension.document;

import java.util.Objects;

public record SourceMaterial(String name, String content) {
    public SourceMaterial {
        Objects.requireNonNull(name);
        Objects.requireNonNull(content);
    }
}
