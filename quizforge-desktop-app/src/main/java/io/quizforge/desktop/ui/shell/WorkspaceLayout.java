package io.quizforge.desktop.ui.shell;

import io.quizforge.desktop.ui.workspace.WorkspaceSidebar;
import io.quizforge.desktop.ui.workspace.WorkspaceTabManager;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.css.PseudoClass;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.transform.Transform;
import javafx.stage.Stage;

/** Window geometry only; workspace commands and file state belong to their owners. */
abstract class WorkspaceLayout extends BorderPane {
    private static final double DIVIDER_HANDLE_WIDTH = 6;
    private static final double DIVIDER_LINE_WIDTH = 0.75;
    private static final PseudoClass DIVIDER_ACTIVE = PseudoClass.getPseudoClass("divider-active");
    protected WorkspaceSidebar sidebar;
    protected WorkspaceTabManager tabs;
    private SplitPane split;
    private WindowChrome chrome;
    private final Region headerSeam = dividerSeam("workspace-header-seam");
    private final Region headerSeamRight = dividerSeam("workspace-header-seam-right");
    private final Region sidebarSeam = dividerSeam("workspace-sidebar-seam");
    private final Region outlineSeam = dividerSeam("workspace-outline-seam");
    private final ChangeListener<Bounds> outlineBounds = (ignored, before, after) -> positionSeams();
    private final ChangeListener<Transform> outlinePosition = (ignored, before, after) -> positionSeams();
    private final ChangeListener<Bounds> tabBounds = (ignored, before, after) -> positionSeams();
    private final ChangeListener<Transform> tabPosition = (ignored, before, after) -> positionSeams();
    private Node currentTab;
    private Region currentOutline;
    private boolean sidebarDividerActive;
    private boolean outlineDividerActive;
    private double sidebarWidth=250;
    protected void installLayout(Stage stage) {
        tabs.setMinWidth(320);
        split = new SplitPane(sidebar, tabs);
        split.setId("workspace-split");
        split.getStyleClass().add("workspace-split");
        SplitPane.setResizableWithParent(sidebar, false);
        split.widthProperty().addListener((obs, before, width) -> {
            if (before.doubleValue() == 0 && width.doubleValue() > 0) {
                split.setDividerPositions(250 / width.doubleValue());
            }
        });
        chrome = new WindowChrome(stage, tabs.tabBar(), sidebar, this::toggleFileList);
        setTop(chrome);
        setCenter(split);
        outlineSeam.setVisible(false);
        getChildren().addAll(headerSeam, headerSeamRight, sidebarSeam, outlineSeam);
        sidebar.widthProperty().addListener((ignored, before, after) -> positionSeams());
        tabs.tabBar().hvalueProperty().addListener((ignored, before, after) -> positionSeams());
        widthProperty().addListener((ignored, before, after) -> positionSeams());
        heightProperty().addListener((ignored, before, after) -> positionSeams());
        sceneProperty().addListener((ignored, before, after) -> Platform.runLater(this::positionSeams));
        addEventFilter(MouseEvent.MOUSE_MOVED, this::updateDividerEmphasis);
        addEventFilter(MouseEvent.MOUSE_DRAGGED, this::updateDividerEmphasis);
        addEventFilter(MouseEvent.MOUSE_PRESSED, this::updateDividerEmphasis);
        addEventFilter(MouseEvent.MOUSE_RELEASED, this::updateDividerEmphasis);
        addEventFilter(MouseEvent.MOUSE_EXITED, event -> setDividerEmphasis(false, false));
    }
    protected void trackActivePage() {
        trackTabSeam(tabs.activeTabNode());
        Region outline=tabs.activePane().visibleOutline();
        chrome.trackOutline(outline);trackOutlineSeam(outline);
        Platform.runLater(this::positionSeams);
    }
    private void toggleFileList() {
        if (split.getItems().contains(sidebar)) {
            if (sidebar.getWidth() > 0) sidebarWidth = sidebar.getWidth();
            split.getItems().remove(sidebar);
            chrome.setFileListVisible(false);
            sidebarSeam.setVisible(false);
            setDividerEmphasis(false, outlineDividerActive);
        } else {
            split.getItems().addFirst(sidebar);
            chrome.setFileListVisible(true);
            sidebarSeam.setVisible(true);
            Platform.runLater(() -> {
                if (split.getWidth() > 0)
                    split.setDividerPositions(Math.min(0.7, sidebarWidth / split.getWidth()));
                positionSeams();
            });
        }
    }

    @Override protected void layoutChildren() {
        super.layoutChildren();
        positionSeams();
    }

    private static Region dividerSeam(String id) {
        Region line = new Region();
        line.setId(id);
        line.getStyleClass().add("workspace-divider-seam");
        line.setManaged(false);
        line.setMouseTransparent(true);
        return line;
    }

    private void trackOutlineSeam(Region outline) {
        if (currentOutline != null) {
            currentOutline.boundsInParentProperty().removeListener(outlineBounds);
            currentOutline.localToSceneTransformProperty().removeListener(outlinePosition);
        }
        currentOutline = outline;
        outlineSeam.setVisible(outline != null);
        if (outline == null) setDividerEmphasis(sidebarDividerActive, false);
        if (outline != null) {
            outline.boundsInParentProperty().addListener(outlineBounds);
            outline.localToSceneTransformProperty().addListener(outlinePosition);
        }
        positionSeams();
    }

    private void trackTabSeam(Node tab) {
        if (currentTab != null) {
            currentTab.boundsInParentProperty().removeListener(tabBounds);
            currentTab.localToSceneTransformProperty().removeListener(tabPosition);
        }
        currentTab = tab;
        if (tab != null) {
            tab.boundsInParentProperty().addListener(tabBounds);
            tab.localToSceneTransformProperty().addListener(tabPosition);
        }
    }

    private void positionSeams() {
        if (getScene() == null || getHeight() <= 0) return;
        if (chrome.getHeight() > 0) {
            double y = chrome.getHeight() - DIVIDER_LINE_WIDTH;
            Node activeTab = tabs.activeTabNode();
            if (activeTab != null && activeTab.getScene() != null && activeTab.getBoundsInLocal().getWidth() > 0) {
                double barLeft = sceneToLocal(tabs.tabBar().localToScene(0, 0)).getX();
                double barRight = barLeft + tabs.tabBar().getWidth();
                Bounds tabBounds = activeTab.localToScene(activeTab.getBoundsInLocal());
                double tabLeft = sceneToLocal(tabBounds.getMinX(), 0).getX();
                double tabRight = sceneToLocal(tabBounds.getMaxX(), 0).getX();
                double gapLeft = Math.max(barLeft, Math.min(barRight, tabLeft));
                double gapRight = Math.max(gapLeft, Math.min(barRight, tabRight));
                headerSeam.resizeRelocate(0, y, gapLeft, DIVIDER_LINE_WIDTH);
                headerSeamRight.resizeRelocate(gapRight, y, getWidth() - gapRight, DIVIDER_LINE_WIDTH);
            } else {
                headerSeam.resizeRelocate(0, y, getWidth(), DIVIDER_LINE_WIDTH);
                headerSeamRight.resizeRelocate(getWidth(), y, 0, DIVIDER_LINE_WIDTH);
            }
        }
        if (sidebarSeam.isVisible() && sidebar.getParent() != null) {
            double offset = sidebarDividerActive ? 0 : DIVIDER_HANDLE_WIDTH - DIVIDER_LINE_WIDTH;
            double edge = sidebar.localToScene(sidebar.getWidth() + offset, 0).getX();
            sidebarSeam.resizeRelocate(sceneToLocal(edge, 0).getX(), 0,
                    sidebarDividerActive ? DIVIDER_HANDLE_WIDTH : DIVIDER_LINE_WIDTH, getHeight());
        }
        if (outlineSeam.isVisible() && currentOutline != null && currentOutline.getScene() != null) {
            double edge = currentOutline.localToScene(0, 0).getX() - DIVIDER_HANDLE_WIDTH;
            outlineSeam.resizeRelocate(sceneToLocal(edge, 0).getX(), 0,
                    outlineDividerActive ? DIVIDER_HANDLE_WIDTH : DIVIDER_LINE_WIDTH, getHeight());
        }
    }

    private void updateDividerEmphasis(MouseEvent event) {
        SplitPane owner = dividerOwner(event.getTarget());
        setDividerEmphasis(owner == split, owner != null && owner == outlinePane());
    }

    private SplitPane outlinePane() {
        for (Node parent = currentOutline; parent != null; parent = parent.getParent())
            if (parent instanceof SplitPane pane) return pane;
        return null;
    }

    private static SplitPane dividerOwner(Object target) {
        if (!(target instanceof Node node)) return null;
        for (Node child = node; child != null; child = child.getParent()) {
            if (!child.getStyleClass().contains("split-pane-divider")) continue;
            for (Node parent = child.getParent(); parent != null; parent = parent.getParent())
                if (parent instanceof SplitPane pane) return pane;
        }
        return null;
    }

    private void setDividerEmphasis(boolean sidebarActive, boolean outlineActive) {
        if (sidebarDividerActive == sidebarActive && outlineDividerActive == outlineActive) return;
        sidebarDividerActive = sidebarActive;
        outlineDividerActive = outlineActive;
        sidebarSeam.pseudoClassStateChanged(DIVIDER_ACTIVE, sidebarActive);
        outlineSeam.pseudoClassStateChanged(DIVIDER_ACTIVE, outlineActive);
        positionSeams();
    }
}
