package io.quizforge.extension.document;

import io.quizforge.extension.ai.AiProvider;
import java.util.List;
import java.util.Objects;

public record DocumentProcessRequest(List<SourceMaterial> sources, AiProvider provider) {
    public DocumentProcessRequest {
        sources = List.copyOf(sources);
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("At least one source is required");
        }
        Objects.requireNonNull(provider);
    }
}
