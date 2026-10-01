package io.quizforge.core.workspace.model;

public enum WorkspaceFileType {
    MARKDOWN(".md"), QUESTION_BANK(".qbank");

    private final String extension;

    WorkspaceFileType(String extension) { this.extension = extension; }

    public String extension() { return extension; }
}
