package io.quizforge.core.port;

import io.quizforge.core.workspace.WorkspaceId;
import io.quizforge.core.question.QuestionBank;

public interface QuestionBankFileStorage {
    StagedFile stageCreate(WorkspaceId workspaceId, String title, QuestionBank bank);
    StagedFile stageReplace(WorkspaceId workspaceId, String relativePath, QuestionBank bank);
    QuestionBank read(WorkspaceId workspaceId, String relativePath);
    default StagedFile stageReplace(WorkspaceId workspaceId, String relativePath, QuestionBank bank,
            QuestionResourceInput resources) {
        return stageReplace(workspaceId, relativePath, bank);
    }

    interface StagedFile extends AutoCloseable {
        String currentPath();
        void publish();
        void rollback();
        void complete();
        @Override void close();
    }
}
