package io.quizforge.core.question;

import java.time.Instant;
import java.util.List;

public record Question(QuestionId id, QuestionBankId questionBankId, QuestionType type,
        String stem, String analysis, String sourceChapter, String sourceSection,
        int sortOrder, Instant createdAt, List<QuestionOption> options) {
    public Question { options = List.copyOf(options); }
}
