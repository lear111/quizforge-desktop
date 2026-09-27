package io.quizforge.desktop.ui;

import io.quizforge.core.workspace.WorkspaceId;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.function.Consumer;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;

/** Owns the tab list; each tab owns its own FilePane and editor state. */
final class WorkspaceTabManager extends BorderPane {
    private final Supplier<FilePane> panes;
    private final FilePane emptyPane;
    private final BooleanSupplier discardDirty;
    private final List<WorkspaceTab> tabs = new ArrayList<>();
    private final HBox row = new HBox();
    private WorkspaceTab active;
    private Consumer<String> onActivate = ignored -> { };

    WorkspaceTabManager(Supplier<FilePane> panes, FilePane emptyPane, BooleanSupplier discardDirty) {
        this.panes = panes;
        this.emptyPane = emptyPane;
        this.discardDirty = discardDirty;
        setId("workspace-tab-manager");
        row.getStyleClass().add("workspace-tab-row");
        ScrollPane bar = new ScrollPane(row);
        bar.setId("workspace-tab-bar");
        bar.getStyleClass().add("workspace-tab-bar");
        bar.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        bar.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        bar.setFitToHeight(true);
        setTop(bar);
        setCenter(emptyPane);
    }

    WorkspaceTab openPreview(WorkspaceId workspace, String path) { return open(workspace, path, false); }
    WorkspaceTab openPinned(WorkspaceId workspace, String path) { return open(workspace, path, true); }

    private WorkspaceTab open(WorkspaceId workspace, String path, boolean pin) {
        path = WorkspaceTab.normalize(path);
        WorkspaceTab existing = findOpenTab(path);
        if (existing != null) {
            if (pin) existing.pin();
            activate(existing);
            return existing;
        }
        FilePane pane = panes.get();
        pane.open(workspace, path);
        if (pane.currentFile() == null) return null;
        WorkspaceTab preview = tabs.stream().filter(tab -> !tab.pinned()).findFirst().orElse(null);
        if (!pin && preview != null) {
            int index = tabs.indexOf(preview);
            tabs.set(index, new WorkspaceTab(path, pane, false));
            preview.pane().clear();
            active = tabs.get(index);
        } else {
            active = new WorkspaceTab(path, pane, pin);
            tabs.add(active);
        }
        WorkspaceTab opened = active;
        pane.onEditStart(() -> { opened.pin(); showActive(); });
        showActive();
        return active;
    }

    WorkspaceTab findOpenTab(String path) {
        String normalized = WorkspaceTab.normalize(path);
        return tabs.stream().filter(tab -> tab.path().equals(normalized)).findFirst().orElse(null);
    }

    void activate(WorkspaceTab tab) {
        if (!tabs.contains(tab)) throw new IllegalArgumentException("Tab is not open");
        active = tab;
        showActive();
    }

    boolean close(WorkspaceTab tab) {
        int index = tabs.indexOf(tab);
        if (index < 0) return false;
        if (tab.pane().hasUnsavedChanges() && !discardDirty.getAsBoolean()) return false;
        tabs.remove(index);
        tab.pane().clear();
        if (tab == active) active = tabs.isEmpty() ? null : tabs.get(Math.min(index, tabs.size() - 1));
        showActive();
        return true;
    }

    void closeAll() {
        for (WorkspaceTab tab : tabs) tab.pane().clear();
        tabs.clear();
        active = null;
        showActive();
    }

    void reloadAll(WorkspaceId workspace) {
        for (WorkspaceTab tab : List.copyOf(tabs)) {
            tab.pane().open(workspace, tab.path());
            if (tab.pane().currentFile() == null) closeUnder(tab.path());
        }
        showActive();
    }

    boolean hasUnsavedChanges() { return tabs.stream().anyMatch(tab -> tab.pane().hasUnsavedChanges()); }
    boolean hasUnsavedChangesUnder(String path) {
        return tabs.stream().anyMatch(tab -> (tab.path().equals(path) || tab.path().startsWith(path + "/"))
                && tab.pane().hasUnsavedChanges());
    }
    WorkspaceTab active() { return active; }
    void onActivate(Consumer<String> action) { onActivate = action; }
    FilePane activePane() { return active == null ? emptyPane : active.pane(); }
    List<WorkspaceTab> tabs() { return List.copyOf(tabs); }

    void closeUnder(String path) {
        for (WorkspaceTab tab : List.copyOf(tabs)) {
            if (tab.path().equals(path) || tab.path().startsWith(path + "/")) {
                int index = tabs.indexOf(tab);
                tabs.remove(tab);
                tab.pane().clear();
                if (tab == active) active = tabs.isEmpty() ? null : tabs.get(Math.min(index, tabs.size() - 1));
            }
        }
        showActive();
    }

    void renameUnder(WorkspaceId workspace, String oldPath, String newPath) {
        for (WorkspaceTab tab : tabs) {
            if (tab.path().equals(oldPath) || tab.path().startsWith(oldPath + "/")) {
                tab.path(newPath + tab.path().substring(oldPath.length()));
                tab.pane().open(workspace, tab.path());
            }
        }
        showActive();
    }

    private void showActive() {
        FilePane shown = activePane();
        // A node may only have one parent. Keep the active pane attached to this manager.
        setCenter(shown);
        row.getChildren().clear();
        for (WorkspaceTab tab : tabs) {
            Button select = new Button(tab.displayName());
            select.getStyleClass().add("workspace-tab-select");
            if (tab == active) select.getStyleClass().add("workspace-tab-selected");
            if (!tab.pinned()) select.getStyleClass().add("workspace-tab-preview");
            select.setId("workspace-tab-" + tabs.indexOf(tab));
            select.setOnAction(event -> activate(tab));
            Button close = new Button("×");
            close.getStyleClass().add("workspace-tab-close");
            close.setAccessibleText("关闭 " + tab.displayName());
            close.setOnAction(event -> close(tab));
            HBox item = new HBox(select, close);
            item.getStyleClass().add("workspace-tab");
            if (tab == active) item.getStyleClass().add("workspace-tab-active");
            row.getChildren().add(item);
        }
        if (active != null) onActivate.accept(active.path());
    }
}
