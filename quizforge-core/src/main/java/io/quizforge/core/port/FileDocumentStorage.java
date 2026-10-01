package io.quizforge.core.port;

import io.quizforge.core.workspace.model.WorkspaceId;

public interface FileDocumentStorage {
    StagedFile stageReplace(WorkspaceId workspaceId, String currentPath, String markdown);

    String read(WorkspaceId workspaceId, String currentPath);

    default StagedFile stageReplace(WorkspaceId workspaceId, String path, String markdown, String expectedSource) {
        if (!expectedSource.equals(read(workspaceId,path))) throw new IllegalStateException("Markdown changed before staging");
        StagedFile delegate=stageReplace(workspaceId,path,markdown);
        return new StagedFile() {
            public String currentPath() { return delegate.currentPath(); }
            public void publish() {
                boolean changed;
                try { changed = !expectedSource.equals(read(workspaceId,path)); }
                catch (RuntimeException unreadable) {
                    delegate.rejectConflict("Markdown changed or cannot be read before publication");
                    throw unreadable;
                }
                if (changed) delegate.rejectConflict("Markdown changed before publication");
                delegate.publish();
            }
            public void rollback() { delegate.rollback(); }
            public void complete() { delegate.complete(); }
            public void close() { delegate.close(); }
        };
    }

    interface StagedFile extends AutoCloseable {
        String currentPath();

        void publish();

        void rollback();

        void complete();

        /** Reject an external revision without discarding recoverable staged edits. */
        default void rejectConflict(String message) { throw new IllegalStateException(message); }

        @Override void close();
    }
}
