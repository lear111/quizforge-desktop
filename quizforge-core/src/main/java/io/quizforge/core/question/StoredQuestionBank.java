package io.quizforge.core.question;

import io.quizforge.core.document.StandardDocumentId;
import io.quizforge.core.workspace.WorkspaceId;
import java.time.Instant;
import java.util.List;

public record StoredQuestionBank(QuestionBankId id, WorkspaceId workspaceId, StandardDocumentId sourceDocumentId,
        String name, GenerationScopeType generationScopeType, String sourceChapter,
        String sourceSection, int requestedQuestionCount, Instant createdAt, Instant updatedAt,
        Instant generatedAt, List<StoredQuestion> questions) {
    public StoredQuestionBank { questions = List.copyOf(questions); }
}
