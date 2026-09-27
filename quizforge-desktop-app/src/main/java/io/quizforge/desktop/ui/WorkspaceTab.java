package io.quizforge.desktop.ui;

/** One runtime file view. Paths are workspace-relative and use forward slashes. */
final class WorkspaceTab {
    private String path;
    private final FilePane pane;
    private boolean pinned;

    WorkspaceTab(String path, FilePane pane, boolean pinned) {
        this.path = normalize(path);
        this.pane = pane;
        this.pinned = pinned;
    }

    static String normalize(String path) {
        if (path == null || path.isBlank()) throw new IllegalArgumentException("File path is required");
        String normalized = path.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.matches("^[A-Za-z]:.*"))
            throw new IllegalArgumentException("Tab path must be workspace-relative");
        for (String part : normalized.split("/", -1))
            if (part.isBlank() || part.equals(".") || part.equals(".."))
                throw new IllegalArgumentException("Invalid tab path");
        return normalized;
    }

    String path() { return path; }
    void path(String value) { path = normalize(value); }
    String displayName() { return path.substring(path.lastIndexOf('/') + 1); }
    FilePane pane() { return pane; }
    boolean pinned() { return pinned; }
    void pin() { pinned = true; }
}
