package io.quizforge.core.workspace;

import java.util.Comparator;
import java.util.List;

/** Snapshot of all visible entries, including ordinary files and empty folders. */
public record WorkspaceFileTree(List<WorkspaceFileEntry> entries, String registryWarning) {
    public WorkspaceFileTree { entries = List.copyOf(entries); }

    public List<WorkspaceFileEntry> childrenOf(String parentPath) {
        return entries.stream().filter(entry -> entry.parentPath().equals(parentPath))
                .sorted(Comparator.comparing((WorkspaceFileEntry entry) ->
                                entry.kind() != WorkspaceFileKind.DIRECTORY)
                        .thenComparing(WorkspaceFileEntry::name, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(WorkspaceFileEntry::name)
                        .thenComparing(WorkspaceFileEntry::relativePath))
                .toList();
    }

    public boolean hasFiles() {
        return entries.stream().anyMatch(entry -> entry.kind() != WorkspaceFileKind.DIRECTORY);
    }
}
