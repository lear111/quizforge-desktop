package io.quizforge.core.workspace;

import io.quizforge.core.question.*;

/** File content captured when a viewer is opened, rather than read from legacy tables. */
public record OpenedWorkspaceFile(WorkspaceFileEntry entry, String sourceText,
        QuestionBank questionBank, String bankRevision) {
    public OpenedWorkspaceFile(WorkspaceFileEntry entry, String sourceText, QuestionBank questionBank) {
        this(entry, sourceText, questionBank, entry.contentId());
    }
}
