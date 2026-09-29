package io.quizforge.core.question;

import java.time.Instant;
import java.util.List;

public record StoredQuestion(QuestionId id, QuestionBankId questionBankId, QuestionType type,
        String stem, String analysis, String sourceChapter, String sourceSection,
        int sortOrder, Instant createdAt, List<QuestionOption> options) {
    public StoredQuestion { options = List.copyOf(options); }
}
