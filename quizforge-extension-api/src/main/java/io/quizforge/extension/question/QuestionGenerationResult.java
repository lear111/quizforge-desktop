package io.quizforge.extension.question;

import java.util.List;

public record QuestionGenerationResult(List<GeneratedQuestion> questions) {
    public QuestionGenerationResult {
        questions = List.copyOf(questions);
    }
}
