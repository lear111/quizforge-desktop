package io.quizforge.core.question;

import io.quizforge.core.document.StandardDocumentId;
import io.quizforge.core.workspace.WorkspaceId;
import java.time.Instant;
import java.util.List;

public record QuestionBank(QuestionBankId id, WorkspaceId workspaceId, StandardDocumentId sourceDocumentId,
        String name, GenerationScopeType generationScopeType, String sourceChapter,
        String sourceSection, int requestedQuestionCount, Instant createdAt, Instant updatedAt,
        Instant generatedAt, List<Question> questions) {
    public QuestionBank { questions = List.copyOf(questions); }
}
