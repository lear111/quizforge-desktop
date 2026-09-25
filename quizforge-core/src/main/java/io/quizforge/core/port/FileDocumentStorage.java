package io.quizforge.core.port;

import io.quizforge.core.workspace.WorkspaceId;

public interface FileDocumentStorage {
    StagedFile stageCreate(WorkspaceId workspaceId, String title, String markdown);

    StagedFile stageReplace(WorkspaceId workspaceId, String currentPath, String markdown);

    String read(WorkspaceId workspaceId, String currentPath);

    interface StagedFile extends AutoCloseable {
        String currentPath();

        void publish();

        void rollback();

        void complete();

        @Override void close();
    }
}
