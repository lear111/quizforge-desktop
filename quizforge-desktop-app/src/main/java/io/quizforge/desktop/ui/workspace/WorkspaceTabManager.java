package io.quizforge.desktop.ui.workspace;

import io.quizforge.core.workspace.model.WorkspaceId;
import io.quizforge.desktop.ui.file.FilePane;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/** Owns the tab list; each tab owns its own FilePane and editor state. */
public final class WorkspaceTabManager extends BorderPane {
    private final Supplier<FilePane> panes;
    private final FilePane emptyPane;
    private final BooleanSupplier discardDirty;
    private final List<WorkspaceTab> tabs = new ArrayList<>();
    private final HBox row = new HBox();
    private final ScrollPane bar;
    private WorkspaceTab active;
    private HBox activeTabItem;
    private Consumer<String> onActivate = ignored -> { };

    public WorkspaceTabManager(Supplier<FilePane> panes, FilePane emptyPane, BooleanSupplier discardDirty) {
        this.panes = panes;
        this.emptyPane = emptyPane;
        this.discardDirty = discardDirty;
        setId("workspace-tab-manager");
        row.getStyleClass().add("workspace-tab-row");
        bar = new ScrollPane(row);
        bar.setId("workspace-tab-bar");
        bar.getStyleClass().add("workspace-tab-bar");
        bar.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        bar.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        bar.setFitToWidth(true);
        bar.setFitToHeight(true);
        setCenter(emptyPane);
    }

    public ScrollPane tabBar() { return bar; }
    public Node activeTabNode() { return activeTabItem; }

    public WorkspaceTab openPreview(WorkspaceId workspace, String path) { return open(workspace, path, false); }
    public WorkspaceTab openPinned(WorkspaceId workspace, String path) { return open(workspace, path, true); }

    private WorkspaceTab open(WorkspaceId workspace, String path, boolean pin) {
        path = WorkspaceTab.normalize(path);
        WorkspaceTab existing = findOpenTab(path);
        if (existing != null) {
            if (pin) existing.pin();
            activate(existing);
            return existing;
        }
        WorkspaceTab preview = tabs.stream().filter(tab -> !tab.pinned()).findFirst().orElse(null);
        if(!pin && preview!=null && !preview.pane().prepareClose())return null;
        FilePane pane = panes.get();
        pane.open(workspace, path);
        if (pane.currentFile() == null) return null;
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
        pane.centerProperty().addListener((ignored, before, after) -> {
            if (active != null && active.pane() == pane) onActivate.accept(active.path());
        });
        showActive();
        return active;
    }

    public WorkspaceTab findOpenTab(String path) {
        String normalized = WorkspaceTab.normalize(path);
        return tabs.stream().filter(tab -> tab.path().equals(normalized)).findFirst().orElse(null);
    }

    public void activate(WorkspaceTab tab) {
        if (!tabs.contains(tab)) throw new IllegalArgumentException("Tab is not open");
        active = tab;
        showActive();
    }

    public boolean close(WorkspaceTab tab) {
        int index = tabs.indexOf(tab);
        if (index < 0) return false;
        if (tab.pane().hasUnsavedChanges() && !discardDirty.getAsBoolean()) return false;
        if (!tab.pane().prepareClose()) return false;
        tabs.remove(index);
        tab.pane().clear();
        if (tab == active) active = tabs.isEmpty() ? null : tabs.get(Math.min(index, tabs.size() - 1));
        showActive();
        return true;
    }

    public boolean closeAll() {
        if(tabs.stream().anyMatch(tab->!tab.pane().prepareClose()))return false;
        for (WorkspaceTab tab : tabs) tab.pane().clear();
        tabs.clear();
        active = null;
        showActive();
        return true;
    }

    public void reloadAll(WorkspaceId workspace) {
        for (WorkspaceTab tab : List.copyOf(tabs)) {
            tab.pane().open(workspace, tab.path());
            if (tab.pane().currentFile() == null) closeUnder(tab.path());
        }
        showActive();
    }

    public boolean hasUnsavedChanges() { return tabs.stream().anyMatch(tab -> tab.pane().hasUnsavedChanges()); }
    public boolean hasUnsavedChangesUnder(String path) {
        return tabs.stream().anyMatch(tab -> (tab.path().equals(path) || tab.path().startsWith(path + "/"))
                && tab.pane().hasUnsavedChanges());
    }
    public WorkspaceTab active() { return active; }
    public void onActivate(Consumer<String> action) { onActivate = action; }
    public FilePane activePane() { return active == null ? emptyPane : active.pane(); }
    public List<WorkspaceTab> tabs() { return List.copyOf(tabs); }

    public void closeUnder(String path) {
        for (WorkspaceTab tab : List.copyOf(tabs)) {
            if (tab.path().equals(path) || tab.path().startsWith(path + "/")) {
                if (!tab.pane().prepareClose()) continue;
                int index = tabs.indexOf(tab);
                tabs.remove(tab);
                tab.pane().clear();
                if (tab == active) active = tabs.isEmpty() ? null : tabs.get(Math.min(index, tabs.size() - 1));
            }
        }
        showActive();
    }

    public void renameUnder(WorkspaceId workspace, String oldPath, String newPath) {
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
        shown.refreshSourceStatus();
        row.getChildren().clear();
        activeTabItem = null;
        for (WorkspaceTab tab : tabs) {
            Button select = new Button(tab.displayName());
            select.getStyleClass().add("workspace-tab-select");
            if (tab == active) select.getStyleClass().add("workspace-tab-selected");
            if (!tab.pinned()) select.getStyleClass().add("workspace-tab-preview");
            select.setId("workspace-tab-" + tabs.indexOf(tab));
            select.setTooltip(new Tooltip(tab.displayName()));
            HBox.setHgrow(select, Priority.ALWAYS);
            select.setOnAction(event -> activate(tab));
            Button close = new Button("×");
            close.getStyleClass().add("workspace-tab-close");
            close.setMinWidth(Region.USE_PREF_SIZE);
            close.setMaxWidth(Region.USE_PREF_SIZE);
            close.setAccessibleText("关闭 " + tab.displayName());
            close.setOnAction(event -> close(tab));
            HBox item = new HBox(select, close);
            item.getStyleClass().add("workspace-tab");
            if (tab == active) {
                item.getStyleClass().add("workspace-tab-active");
                activeTabItem = item;
            }
            row.getChildren().add(item);
        }
        onActivate.accept(active == null ? null : active.path());
    }
}
