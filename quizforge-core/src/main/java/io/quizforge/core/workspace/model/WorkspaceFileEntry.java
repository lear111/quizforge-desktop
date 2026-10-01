package io.quizforge.core.workspace.model;

import java.util.Objects;

/** A user-visible filesystem entry. Asset metadata is optional decoration, never tree membership. */
public record WorkspaceFileEntry(String relativePath, String name, WorkspaceFileKind kind,
        String assetId, String contentId, String title, String issue) {
    public WorkspaceFileEntry {
        if (relativePath == null || relativePath.isBlank() || relativePath.startsWith("/")
                || relativePath.indexOf('\\') >= 0 || relativePath.matches("^[A-Za-z]:.*")) {
            throw new IllegalArgumentException("File path must be workspace-relative");
        }
        for (String segment : relativePath.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("Invalid file path");
            }
        }
        if (name == null || name.isBlank()) throw new IllegalArgumentException("File name is required");
        Objects.requireNonNull(kind);
    }

    public String parentPath() {
        int separator = relativePath.lastIndexOf('/');
        return separator < 0 ? "" : relativePath.substring(0, separator);
    }
}
