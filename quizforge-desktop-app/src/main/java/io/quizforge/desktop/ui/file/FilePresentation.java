package io.quizforge.desktop.ui.file;

import io.quizforge.core.document.registered.RegisteredMarkdownDocument;
import io.quizforge.core.workspace.model.OpenedWorkspaceFile;
import io.quizforge.core.workspace.model.WorkspaceFileKind;

/** UI state does not register assets or alter their on-disk validity. */
public record FilePresentation(OpenedWorkspaceFile file, boolean empty, boolean draft,
        RegisteredMarkdownDocument registeredMarkdown) {
    public FilePresentation(OpenedWorkspaceFile file, boolean empty, boolean draft) {
        this(file, empty, draft, null);
    }
    public WorkspaceFileKind kind() { return file.entry().kind(); }
    public boolean asset() { return kind() == WorkspaceFileKind.REGISTERED_MARKDOWN || kind() == WorkspaceFileKind.QUESTION_BANK; }
    public boolean supportsMode() { return asset() || kind() == WorkspaceFileKind.MARKDOWN
            || kind() == WorkspaceFileKind.INVALID_REGISTERED_MARKDOWN
                    && file.sourceText() != null
                    && file.entry().relativePath().toLowerCase(java.util.Locale.ROOT).endsWith(".md"); }
}
