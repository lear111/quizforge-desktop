package io.quizforge.desktop.ui;

import io.quizforge.core.workspace.OpenedWorkspaceFile;
import io.quizforge.core.workspace.WorkspaceFileKind;
import io.quizforge.core.document.qdoc.QDocDocument;

/** UI state does not register assets or alter their on-disk validity. */
record FilePresentation(OpenedWorkspaceFile file, boolean empty, boolean draft, QDocDocument document) {
    FilePresentation(OpenedWorkspaceFile file, boolean empty, boolean draft) {
        this(file, empty, draft, null);
    }
    WorkspaceFileKind kind() { return file.entry().kind(); }
    boolean asset() { return kind() == WorkspaceFileKind.STANDARD_DOCUMENT || kind() == WorkspaceFileKind.QUESTION_BANK; }
    boolean supportsMode() { return asset() || kind() == WorkspaceFileKind.MARKDOWN
            || kind() == WorkspaceFileKind.INVALID_STANDARD_DOCUMENT
                    && file.sourceText() != null
                    && file.entry().relativePath().toLowerCase(java.util.Locale.ROOT).endsWith(".md"); }
    boolean offersAi() { return asset() && empty; }
}
