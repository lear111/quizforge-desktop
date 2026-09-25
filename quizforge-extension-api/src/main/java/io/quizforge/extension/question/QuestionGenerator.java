package io.quizforge.extension.question;

public interface QuestionGenerator {
    QuestionGenerationResult generate(QuestionGenerationRequest request);
}
