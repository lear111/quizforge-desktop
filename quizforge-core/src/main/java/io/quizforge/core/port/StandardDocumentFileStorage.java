package io.quizforge.core.port;

import io.quizforge.core.workspace.WorkspaceId;

public interface StandardDocumentFileStorage {
    StagedDocument stage(WorkspaceId workspaceId, String content);

    String read(WorkspaceId workspaceId);

    interface StagedDocument extends AutoCloseable {
        void publish();

        void rollback();

        void complete();

        @Override
        void close();
    }
}
