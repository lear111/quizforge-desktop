package io.quizforge.desktop.ui.workspace;

import io.quizforge.desktop.ui.file.FilePane;

/** One runtime file view. Paths are workspace-relative and use forward slashes. */
public final class WorkspaceTab {
    private String path;
    private final FilePane pane;
    private boolean pinned;

    public WorkspaceTab(String path, FilePane pane, boolean pinned) {
        this.path = normalize(path);
        this.pane = pane;
        this.pinned = pinned;
    }

    public static String normalize(String path) {
        if (path == null || path.isBlank()) throw new IllegalArgumentException("File path is required");
        String normalized = path.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.matches("^[A-Za-z]:.*"))
            throw new IllegalArgumentException("Tab path must be workspace-relative");
        for (String part : normalized.split("/", -1))
            if (part.isBlank() || part.equals(".") || part.equals(".."))
                throw new IllegalArgumentException("Invalid tab path");
        return normalized;
    }

    public String path() { return path; }
    public void path(String value) { path = normalize(value); }
    public String displayName() { return path.substring(path.lastIndexOf('/') + 1); }
    public FilePane pane() { return pane; }
    public boolean pinned() { return pinned; }
    public void pin() { pinned = true; }
}
