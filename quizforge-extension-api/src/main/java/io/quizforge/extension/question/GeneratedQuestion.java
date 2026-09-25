package io.quizforge.extension.question;

import java.util.List;

/** Untrusted AI candidate. The Core validator decides whether it becomes a domain question. */
public record GeneratedQuestion(String type, String stem, List<GeneratedOption> options,
        List<String> correctAnswers, String analysis, String sourceChapter, String sourceSection) {
    public GeneratedQuestion {
        options = options == null ? List.of() : List.copyOf(options);
        correctAnswers = correctAnswers == null ? List.of() : List.copyOf(correctAnswers);
    }
}
