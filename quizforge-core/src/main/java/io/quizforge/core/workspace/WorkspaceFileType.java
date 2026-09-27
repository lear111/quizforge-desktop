package io.quizforge.core.workspace;

public enum WorkspaceFileType {
    MARKDOWN(".md"), QUESTION_BANK(".qbank"), QDOC(".qdoc");

    private final String extension;

    WorkspaceFileType(String extension) { this.extension = extension; }

    public String extension() { return extension; }
}
