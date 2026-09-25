package io.quizforge.extension.question;

import java.util.List;

public record QuestionGenerationRequest(String documentContent, String scope, String sourceChapter,
        String sourceSection, List<String> questionTypes, int questionCount) {
    public QuestionGenerationRequest {
        questionTypes = List.copyOf(questionTypes);
    }
}
