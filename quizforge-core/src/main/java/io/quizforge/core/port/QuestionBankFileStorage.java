package io.quizforge.core.port;

import io.quizforge.core.question.model.QuestionBank;
import io.quizforge.core.workspace.model.WorkspaceId;

public interface QuestionBankFileStorage {
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
        /** Reject an external revision without discarding recoverable staged edits. */
        default void rejectConflict(String message) { throw new IllegalStateException(message); }
        @Override void close();
    }

    default StagedFile stageReplace(WorkspaceId workspaceId, String path, QuestionBank bank,
            QuestionResourceInput resources, QuestionBank expected) {
        if (!expected.equals(read(workspaceId,path))) throw new IllegalStateException("QuestionBank changed before staging");
        StagedFile delegate=stageReplace(workspaceId,path,bank,resources);
        return new StagedFile() {
            public String currentPath() { return delegate.currentPath(); }
            public void publish() {
                boolean changed;
                try { changed = !expected.equals(read(workspaceId,path)); }
                catch (RuntimeException unreadable) {
                    delegate.rejectConflict("QuestionBank changed or cannot be read before publication");
                    throw unreadable;
                }
                if (changed) delegate.rejectConflict("QuestionBank changed before publication");
                delegate.publish();
            }
            public void rollback() { delegate.rollback(); }
            public void complete() { delegate.complete(); }
            public void close() { delegate.close(); }
        };
    }
}
