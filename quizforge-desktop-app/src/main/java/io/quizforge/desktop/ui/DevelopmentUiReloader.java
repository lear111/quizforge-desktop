package io.quizforge.desktop.ui;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import javafx.stage.Modality;
import javafx.stage.Window;
import javafx.util.Duration;

/** Invoked by the development agent only; production creates no watcher or refresh handler. */
public final class DevelopmentUiReloader {
    private static boolean pending;
    private DevelopmentUiReloader() { }

    public static void refresh(Set<String> changedClasses) {
        if (!Boolean.getBoolean("quizforge.liveJava.enabled")) return;
        Platform.runLater(DevelopmentUiReloader::refreshWindows);
    }

    static void install(Scene scene) {
        if (!Boolean.getBoolean("quizforge.liveJava.enabled")) return;
        scene.setOnKeyPressed(event -> {
            if (event.isControlDown() && event.isAltDown() && event.getCode() == KeyCode.R) {
                refresh(Set.of());
                event.consume();
            }
        });
    }

    private static void refreshWindows() {
        // An open Canvas dialog holds callbacks into the parent editor. Keep those nodes alive
        // until the user saves/cancels; Vite HMR continues independently inside the WebView.
        if (Window.getWindows().stream().anyMatch(window -> window instanceof Stage stage
                && stage.isShowing() && stage.getModality() != Modality.NONE)) {
            if (!pending) {
                pending = true;
                System.out.println("[LiveJava] UI refresh deferred until the open dialog closes; draft retained");
                PauseTransition retry = new PauseTransition(Duration.seconds(1));
                retry.setOnFinished(event -> { pending = false; refreshWindows(); });
                retry.play();
            }
            return;
        }
        int refreshed = 0;
        for (Window window : List.copyOf(Window.getWindows())) {
            if (window.getScene() == null) continue;
            List<Node> targets = new ArrayList<>();
            collectTargets(window.getScene().getRoot(), targets);
            for (Node target : targets) {
                try {
                    refreshNode(target);
                    refreshed++;
                } catch (ReflectiveOperationException | RuntimeException failure) {
                    System.err.println("[LiveJava] UI refresh failed for " + target.getClass().getSimpleName()
                            + ": " + failure + "; model retained; fix source and use Ctrl+Alt+R or restart");
                }
            }
            if (!UiTheme.liveCssEnabled()) {
                // JavaFX caches file stylesheets by URL. Copied resource changes need a new URL.
                String version = "?liveJava=" + System.nanoTime();
                for (int i = 0; i < window.getScene().getStylesheets().size(); i++) {
                    String url = window.getScene().getStylesheets().get(i);
                    if (url.startsWith("file:") && (url.contains("/workspace.css") || url.contains("/markdown-preview.css")))
                        window.getScene().getStylesheets().set(i, url.split("\\?", 2)[0] + version);
                }
            }
            window.getScene().getRoot().requestLayout();
        }
        System.out.println("[LiveJava] UI_REFRESHED " + refreshed + " views (existing models/runtime retained)");
    }

    private static void collectTargets(Node node, List<Node> targets) {
        boolean authoring = node instanceof QuestionBankAuthoringView;
        if (authoring && field(node, "editorJump") == null) { targets.add(node); return; }
        if (node instanceof QuestionBankEditorView || node instanceof QuestionBankPracticeView
                || node instanceof PracticeHistoryView || node instanceof PracticeHistoryDetailView) {
            targets.add(node);
            return;
        }
        if (node instanceof FilePane pane && pane.getCenter() instanceof SafeMarkdownPreview.BrowseLayout) {
            targets.add(node); return;
        }
        if (node instanceof Parent parent)
            for (Node child : List.copyOf(parent.getChildrenUnmodifiable())) collectTargets(child, targets);
    }

    /** Package access for state-preservation tests; all calls must be on the FX thread. */
    static void refreshNode(Node node) throws ReflectiveOperationException {
        if (!Platform.isFxApplicationThread()) throw new IllegalStateException("JavaFX thread required");
        Snapshot snapshot = Snapshot.capture(node);
        List<ScrollPane> parents = new ArrayList<>();
        List<Double> positions = new ArrayList<>();
        for (Node parent = node.getParent(); parent != null; parent = parent.getParent()) {
            if (parent instanceof ScrollPane scroll) { parents.add(scroll); positions.add(scroll.getVvalue()); }
        }
        String method = node instanceof PracticeHistoryView ? "refresh"
                : node instanceof FilePane || node instanceof QuestionBankEditorView ? "refreshForDevelopment" : "render";
        Method render = node.getClass().getDeclaredMethod(method);
        render.setAccessible(true);
        render.invoke(node);
        node.applyCss();
        if (node instanceof Parent parent) parent.layout();
        snapshot.restore(node);
        for (int i = 0; i < parents.size(); i++) parents.get(i).setVvalue(positions.get(i));
        Platform.runLater(() -> {
            snapshot.restorePositions(node);
            for (int i = 0; i < parents.size(); i++) parents.get(i).setVvalue(positions.get(i));
        });
    }

    private static Object field(Object instance, String name) {
        try { Field field = instance.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(instance); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }

    private record TextState(String text, int anchor, int caret, boolean focused, double top, double left) { }
    private record ScrollState(double horizontal, double vertical) { }
    private static final class Snapshot {
        private final Map<String, TextState> text = new HashMap<>();
        private final Map<String, ScrollState> scrolls = new HashMap<>();
        private final Map<String, double[]> dividers = new HashMap<>();
        private final Map<String, Boolean> expanded = new HashMap<>();

        static Snapshot capture(Node root) {
            Snapshot snapshot = new Snapshot();
            visit(root, "root", (node, key) -> {
                if (node instanceof TextInputControl input)
                    snapshot.text.put(key, new TextState(input.getText(), input.getAnchor(), input.getCaretPosition(), input.isFocused(),
                            input instanceof TextArea area ? area.getScrollTop() : 0,
                            input instanceof TextArea area ? area.getScrollLeft() : 0));
                if (node instanceof ScrollPane scroll)
                    snapshot.scrolls.put(key, new ScrollState(scroll.getHvalue(), scroll.getVvalue()));
                if (node instanceof SplitPane split) snapshot.dividers.put(key, split.getDividerPositions());
                if (node instanceof TitledPane pane) snapshot.expanded.put(key, pane.isExpanded());
            });
            return snapshot;
        }

        void restore(Node root) {
            visit(root, "root", (node, key) -> {
                TextState state = text.get(key);
                if (node instanceof TextInputControl input && state != null) {
                    if (!Objects.equals(input.getText(), state.text())) input.setText(state.text());
                    input.selectRange(state.anchor(), state.caret());
                    if (state.focused()) input.requestFocus();
                    if (input instanceof TextArea area) { area.setScrollTop(state.top()); area.setScrollLeft(state.left()); }
                }
                if (node instanceof TitledPane pane && expanded.containsKey(key)) pane.setExpanded(expanded.get(key));
            });
            restorePositions(root);
        }

        void restorePositions(Node root) {
            visit(root, "root", (node, key) -> {
                ScrollState scroll = scrolls.get(key);
                if (node instanceof ScrollPane pane && scroll != null) { pane.setHvalue(scroll.horizontal()); pane.setVvalue(scroll.vertical()); }
                double[] positions = dividers.get(key);
                if (node instanceof SplitPane split && positions != null) split.setDividerPositions(positions);
            });
        }

        private static void visit(Node node, String path, java.util.function.BiConsumer<Node, String> consumer) {
            String key = node.getId() == null ? path + ":" + node.getClass().getSimpleName() : "id:" + node.getId();
            consumer.accept(node, key);
            if (node instanceof Parent parent) {
                List<Node> children = List.copyOf(parent.getChildrenUnmodifiable());
                for (int i = 0; i < children.size(); i++) visit(children.get(i), path + "/" + i, consumer);
            }
        }
    }
}
