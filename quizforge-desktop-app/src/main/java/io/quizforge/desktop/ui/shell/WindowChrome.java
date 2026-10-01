package io.quizforge.desktop.ui.shell;

import io.quizforge.desktop.ui.shared.UiTheme;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/** Tab strip and window controls for the undecorated desktop window. */
final class WindowChrome extends HBox {
    private final Stage stage;
    private final WindowPlacement placement;
    private final Region sidebar;
    private final HBox sidebarCell = new HBox();
    private final Region sidebarDivider = new Region();
    private final Region outlineDivider = new Region();
    private final HBox outlineCell = new HBox();
    private final Button fileList;
    private final Button maximize;
    private Region trackedOutline;
    private final ChangeListener<Number> outlineWidth = (ignored, before, width) -> updateOutlineWidth();
    private boolean fileListVisible = true;
    private boolean dragging;
    private double dragOffsetX;
    private double dragOffsetY;

    WindowChrome(Stage stage, ScrollPane tabs, Region sidebar, Runnable toggleFileList) {
        this.stage = stage;
        this.placement = placementFor(stage);
        this.sidebar = sidebar;
        setId("window-chrome");
        getStyleClass().add("window-chrome");
        setAlignment(Pos.CENTER_LEFT);

        sidebarCell.setId("window-sidebar-cell");
        sidebarCell.getStyleClass().add("window-sidebar-cell");
        sidebarCell.setAlignment(Pos.CENTER_LEFT);
        sidebarCell.setMinWidth(Region.USE_PREF_SIZE);
        sidebarCell.setMaxWidth(Region.USE_PREF_SIZE);
        sidebarDivider.setId("window-sidebar-divider");
        sidebarDivider.getStyleClass().addAll("window-column-divider", "window-sidebar-divider");
        sidebar.widthProperty().addListener((ignored, before, width) -> updateSidebarWidth());
        updateSidebarWidth();

        outlineDivider.setId("window-outline-divider");
        outlineDivider.getStyleClass().addAll("window-column-divider", "window-outline-divider");
        outlineCell.setId("window-outline-cell");
        outlineCell.getStyleClass().add("window-outline-cell");
        outlineCell.setAlignment(Pos.CENTER_RIGHT);
        outlineCell.setMinWidth(Region.USE_PREF_SIZE);
        outlineCell.setMaxWidth(Region.USE_PREF_SIZE);
        outlineCell.setPrefWidth(260);

        fileList = new Button("", UiTheme.icon("panel"));
        fileList.setId("toggle-file-list");
        fileList.getStyleClass().add("window-sidebar-toggle");
        fileList.setOnAction(event -> toggleFileList.run());
        sidebarCell.getChildren().add(fileList);
        setFileListVisible(true);

        tabs.setMinWidth(0);
        HBox.setHgrow(tabs, Priority.ALWAYS);
        Region dragSpace = new Region();
        dragSpace.getStyleClass().add("window-drag-space");
        dragSpace.setMinWidth(0);
        HBox.setHgrow(dragSpace, Priority.ALWAYS);
        Button minimize = control("−", "最小化", "window-minimize", () -> stage.setIconified(true));
        maximize = control("□", "最大化", "window-maximize", this::toggleMaximize);
        Button close = control("×", "关闭", "window-close", () -> stage.fireEvent(
                new javafx.stage.WindowEvent(stage, javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST)));
        stage.maximizedProperty().addListener((ignored, before, maximized) -> updateMaximizeButton());
        placement.workAreaMaximized.addListener((ignored, before, maximized) -> updateMaximizeButton());
        outlineCell.getChildren().addAll(dragSpace, minimize, maximize, close);
        getChildren().addAll(sidebarCell, sidebarDivider, tabs, outlineDivider, outlineCell);

        addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() != MouseButton.PRIMARY || !dragSurface(event.getTarget())) return;
            dragging = true;
            dragOffsetX = event.getScreenX() - stage.getX();
            dragOffsetY = event.getScreenY() - stage.getY();
        });
        addEventHandler(MouseEvent.MOUSE_DRAGGED, event -> {
            if (!dragging || placement.isMaximized() || stage.isFullScreen()) return;
            stage.setX(event.getScreenX() - dragOffsetX);
            stage.setY(event.getScreenY() - dragOffsetY);
        });
        addEventHandler(MouseEvent.MOUSE_RELEASED, event -> dragging = false);
        addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2
                    && dragSurface(event.getTarget())) toggleMaximize();
        });
    }

    void setFileListVisible(boolean visible) {
        fileListVisible = visible;
        sidebarDivider.setVisible(visible);
        sidebarDivider.setManaged(visible);
        updateSidebarWidth();
        String label = visible ? "收起文件列表" : "展开文件列表";
        fileList.setTooltip(new Tooltip(label));
        fileList.setAccessibleText(label);
        fileList.getStyleClass().removeAll("file-list-hidden", "file-list-visible");
        fileList.getStyleClass().add(visible ? "file-list-visible" : "file-list-hidden");
    }

    void trackOutline(Region outline) {
        if (trackedOutline != null) trackedOutline.widthProperty().removeListener(outlineWidth);
        trackedOutline = outline;
        if (trackedOutline != null) trackedOutline.widthProperty().addListener(outlineWidth);
        outlineDivider.setVisible(outline != null);
        outlineDivider.setManaged(outline != null);
        updateOutlineWidth();
    }

    private void updateSidebarWidth() {
        sidebarCell.setPrefWidth(fileListVisible
                ? (sidebar.getWidth() > 0 ? sidebar.getWidth() : sidebar.getPrefWidth())
                : 42);
        alignWithDivider();
    }

    private void updateOutlineWidth() {
        double width = trackedOutline == null ? 160
                : trackedOutline.getWidth() > 0 ? trackedOutline.getWidth() : 260;
        outlineCell.setPrefWidth(width);
        alignWithDivider();
    }

    private void alignWithDivider() {
        // The center SplitPane lays out after this top row; update both in the same drag frame.
        if (getScene() != null) layout();
    }

    private Button control(String symbol, String description, String id, Runnable action) {
        Button button = new Button(symbol);
        button.setId(id);
        button.getStyleClass().add("window-control");
        button.setTooltip(new Tooltip(description));
        button.setAccessibleText(description);
        button.setOnAction(event -> action.run());
        return button;
    }

    private void toggleMaximize() {
        placement.toggleMaximize();
    }

    private void updateMaximizeButton() {
        boolean maximized = placement.isMaximized();
        maximize.setText(maximized ? "❐" : "□");
        String action = maximized ? "还原窗口" : "最大化";
        maximize.setTooltip(new Tooltip(action));
        maximize.setAccessibleText(action);
    }

    private boolean dragSurface(Object target) {
        if (!(target instanceof Node node)) return false;
        for (Node current = node; current != null && current != this; current = current.getParent()) {
            if (current instanceof Button || current instanceof ScrollBar) return false;
        }
        return true;
    }

    static void installFrame(Stage stage, Scene scene) {
        if (stage.getStyle() == StageStyle.UNDECORATED || stage.getStyle() == StageStyle.TRANSPARENT)
            new ResizeSupport(stage, scene).install();
        if (stage.getStyle() != StageStyle.TRANSPARENT) return;
        scene.setFill(Color.TRANSPARENT);
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(scene.widthProperty());
        clip.heightProperty().bind(scene.heightProperty());
        var maximized = placementFor(stage).workAreaMaximized.or(stage.maximizedProperty())
                .or(stage.fullScreenProperty());
        clip.arcWidthProperty().bind(Bindings.when(maximized).then(0.0).otherwise(20.0));
        clip.arcHeightProperty().bind(clip.arcWidthProperty());
        scene.getRoot().setClip(clip);
    }

    private static WindowPlacement placementFor(Stage stage) {
        return (WindowPlacement) stage.getProperties().computeIfAbsent(WindowPlacement.class,
                ignored -> new WindowPlacement(stage));
    }

    /** Custom chrome maximizes to the monitor's work area, preserving its taskbar. */
    private static final class WindowPlacement {
        private final Stage stage;
        private final BooleanProperty workAreaMaximized = new SimpleBooleanProperty(false);
        private Rectangle2D restoreBounds;

        WindowPlacement(Stage stage) { this.stage = stage; }

        boolean isMaximized() { return workAreaMaximized.get() || stage.isMaximized(); }

        void toggleMaximize() {
            if (stage.getStyle() != StageStyle.UNDECORATED && stage.getStyle() != StageStyle.TRANSPARENT) {
                stage.setMaximized(!stage.isMaximized());
                return;
            }
            if (workAreaMaximized.get()) {
                workAreaMaximized.set(false);
                apply(restoreBounds);
            } else if (stage.isMaximized()) {
                stage.setMaximized(false);
            } else {
                restoreBounds = new Rectangle2D(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight());
                Screen screen = Screen.getScreensForRectangle(restoreBounds).stream()
                        .max(java.util.Comparator.comparingDouble(candidate -> overlap(candidate.getBounds(), restoreBounds)))
                        .orElse(Screen.getPrimary());
                workAreaMaximized.set(true);
                apply(screen.getVisualBounds());
            }
        }

        private void apply(Rectangle2D bounds) {
            stage.setX(bounds.getMinX());
            stage.setY(bounds.getMinY());
            stage.setWidth(bounds.getWidth());
            stage.setHeight(bounds.getHeight());
        }

        private static double overlap(Rectangle2D screen, Rectangle2D window) {
            return Math.max(0, Math.min(screen.getMaxX(), window.getMaxX()) - Math.max(screen.getMinX(), window.getMinX()))
                    * Math.max(0, Math.min(screen.getMaxY(), window.getMaxY()) - Math.max(screen.getMinY(), window.getMinY()));
        }
    }

    private enum Edge {
        NONE(Cursor.DEFAULT), NORTH(Cursor.N_RESIZE), SOUTH(Cursor.S_RESIZE),
        EAST(Cursor.E_RESIZE), WEST(Cursor.W_RESIZE),
        NORTH_EAST(Cursor.NE_RESIZE), NORTH_WEST(Cursor.NW_RESIZE),
        SOUTH_EAST(Cursor.SE_RESIZE), SOUTH_WEST(Cursor.SW_RESIZE);

        final Cursor cursor;
        Edge(Cursor cursor) { this.cursor = cursor; }
        boolean west() { return this == WEST || this == NORTH_WEST || this == SOUTH_WEST; }
        boolean east() { return this == EAST || this == NORTH_EAST || this == SOUTH_EAST; }
        boolean north() { return this == NORTH || this == NORTH_WEST || this == NORTH_EAST; }
        boolean south() { return this == SOUTH || this == SOUTH_WEST || this == SOUTH_EAST; }
    }

    private static final class ResizeSupport {
        private static final double GRAB = 6;
        private final Stage stage;
        private final Scene scene;
        private Edge active = Edge.NONE;
        private double startX;
        private double startY;
        private double startWidth;
        private double startHeight;
        private double pointerX;
        private double pointerY;

        ResizeSupport(Stage stage, Scene scene) {
            this.stage = stage;
            this.scene = scene;
        }

        void install() {
            scene.addEventFilter(MouseEvent.MOUSE_MOVED, event -> {
                if (active != Edge.NONE) return;
                Edge edge = hit(event);
                scene.getRoot().setCursor(edge == Edge.NONE ? null : edge.cursor);
            });
            scene.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
                if (event.getButton() != MouseButton.PRIMARY) return;
                active = hit(event);
                if (active == Edge.NONE) return;
                startX = stage.getX();
                startY = stage.getY();
                startWidth = stage.getWidth();
                startHeight = stage.getHeight();
                pointerX = event.getScreenX();
                pointerY = event.getScreenY();
                event.consume();
            });
            scene.addEventFilter(MouseEvent.MOUSE_DRAGGED, event -> {
                if (active == Edge.NONE) return;
                double dx = event.getScreenX() - pointerX;
                double dy = event.getScreenY() - pointerY;
                if (active.west()) {
                    double width = Math.max(stage.getMinWidth(), startWidth - dx);
                    stage.setX(startX + startWidth - width);
                    stage.setWidth(width);
                } else if (active.east()) {
                    stage.setWidth(Math.max(stage.getMinWidth(), startWidth + dx));
                }
                if (active.north()) {
                    double height = Math.max(stage.getMinHeight(), startHeight - dy);
                    stage.setY(startY + startHeight - height);
                    stage.setHeight(height);
                } else if (active.south()) {
                    stage.setHeight(Math.max(stage.getMinHeight(), startHeight + dy));
                }
                event.consume();
            });
            scene.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
                if (active == Edge.NONE) return;
                active = Edge.NONE;
                event.consume();
            });
        }

        private Edge hit(MouseEvent event) {
            if (placementFor(stage).isMaximized() || stage.isFullScreen()) return Edge.NONE;
            boolean left = event.getSceneX() < GRAB;
            boolean right = event.getSceneX() > scene.getWidth() - GRAB;
            boolean top = event.getSceneY() < GRAB;
            boolean bottom = event.getSceneY() > scene.getHeight() - GRAB;
            if (top && left) return Edge.NORTH_WEST;
            if (top && right) return Edge.NORTH_EAST;
            if (bottom && left) return Edge.SOUTH_WEST;
            if (bottom && right) return Edge.SOUTH_EAST;
            if (left) return Edge.WEST;
            if (right) return Edge.EAST;
            if (top) return Edge.NORTH;
            if (bottom) return Edge.SOUTH;
            return Edge.NONE;
        }
    }
}
