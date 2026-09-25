package io.quizforge.core.port;

import io.quizforge.core.question.QuestionBank;
import io.quizforge.core.workspace.WorkspaceId;
import java.util.Optional;

public interface QuestionBankRepository {
    Optional<QuestionBank> findByWorkspace(WorkspaceId workspaceId);
    void replace(QuestionBank bank);
}
