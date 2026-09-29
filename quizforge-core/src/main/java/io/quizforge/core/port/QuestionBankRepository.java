package io.quizforge.core.port;

import io.quizforge.core.question.StoredQuestionBank;
import io.quizforge.core.workspace.WorkspaceId;
import java.util.Optional;

public interface QuestionBankRepository {
    Optional<StoredQuestionBank> findByWorkspace(WorkspaceId workspaceId);
    void replace(StoredQuestionBank bank);
}
