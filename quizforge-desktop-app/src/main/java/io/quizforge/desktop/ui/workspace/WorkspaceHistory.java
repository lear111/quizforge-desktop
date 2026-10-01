package io.quizforge.desktop.ui.workspace;

import io.quizforge.core.workspace.model.Workspace;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Only desktop recency is stored here; names and workspace identities come from WorkspaceService. */
public final class WorkspaceHistory {
    private final Path storage;
    private final List<String> recent = new ArrayList<>();

    public WorkspaceHistory(Path storage) {
        this.storage = storage;
        try { if (Files.isRegularFile(storage)) recent.addAll(Files.readAllLines(storage)); }
        catch (IOException ignored) { /* A missing UI preference must not prevent opening a workspace. */ }
    }

    public List<Workspace> order(List<Workspace> available) {
        return available.stream().sorted(Comparator.comparingInt(workspace -> {
            int position = recent.indexOf(workspace.id().toString());
            return position < 0 ? Integer.MAX_VALUE : position;
        })).toList();
    }

    public void visit(Workspace workspace) {
        recent.remove(workspace.id().toString());
        recent.addFirst(workspace.id().toString());
        try {
            Files.createDirectories(storage.toAbsolutePath().getParent());
            Files.write(storage, recent);
        } catch (IOException ignored) { /* Recency remains available for this session. */ }
    }
}
