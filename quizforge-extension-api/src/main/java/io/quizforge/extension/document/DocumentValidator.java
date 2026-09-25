package io.quizforge.extension.document;

public interface DocumentValidator {
    String formatId();

    String formatVersion();

    DocumentValidationResult validate(String candidateContent);
}
