package io.quizforge.core.port;

import io.quizforge.core.workspace.WorkspaceId;

public interface QuestionBankFileStorage {
    StagedFile stageCreate(WorkspaceId workspaceId, String title, String json);
    StagedFile stageReplace(WorkspaceId workspaceId, String relativePath, String json);
    String read(WorkspaceId workspaceId, String relativePath);

    interface StagedFile extends AutoCloseable {
        String currentPath();
        void publish();
        void rollback();
        void complete();
        @Override void close();
    }
}
