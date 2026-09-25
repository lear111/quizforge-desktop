package io.quizforge.extension.document;

import java.util.List;

public record DocumentValidationResult(String title, List<String> errors) {
    public DocumentValidationResult {
        errors = List.copyOf(errors);
    }

    public boolean valid() {
        return errors.isEmpty();
    }
}
