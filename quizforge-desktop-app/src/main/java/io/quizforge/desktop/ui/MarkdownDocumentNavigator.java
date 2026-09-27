package io.quizforge.desktop.ui;

import io.quizforge.core.document.registered.MarkdownSourceRange;
import java.util.HashMap;
import java.util.Map;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;

/** Resolves source positions to nodes in the current JavaFX preview only. */
final class MarkdownDocumentNavigator {
    private record Position(int line, int column) { }

    private final ScrollPane scroll;
    private final Map<Position, Node> renderedBlocks = new HashMap<>();
    private Node firstBlock;

    MarkdownDocumentNavigator(ScrollPane scroll) { this.scroll = scroll; }

    void register(MarkdownSourceRange source, Node rendered) {
        if (firstBlock == null) firstBlock = rendered;
        renderedBlocks.put(new Position(source.startLine(), source.startColumn()), rendered);
    }

    boolean jumpTo(MarkdownOutline.Entry entry) {
        if (entry.orphan() || entry.target() == null) return false;
        Node target = renderedBlocks.get(new Position(entry.target().startLine(),
                entry.target().startColumn()));
        if (target == null || target.getScene() == null) return false;
        // A newly attached tab has not necessarily had its first pulse. Lay out the
        // whole scene root before measuring the target in scene coordinates.
        javafx.scene.Parent root = scroll.getScene().getRoot();
        root.applyCss();
        root.layout();
        Node content = scroll.getContent();
        Point2D inContent = content.sceneToLocal(target.localToScene(0, 0));
        double travel = content.getLayoutBounds().getHeight() - scroll.getViewportBounds().getHeight();
        double offset = Math.max(0, inContent.getY() - 48);
        scroll.setVvalue(travel <= 0 ? 0 : Math.min(1, offset / travel));
        return true;
    }

    /** Rendering is synchronous; wait for the scene and viewport layout before measuring scroll distance. */
    boolean jumpWhenReady(MarkdownOutline.Entry entry) {
        if (entry.orphan() || entry.target() == null) return false;
        Node target = renderedBlocks.get(new Position(entry.target().startLine(),
                entry.target().startColumn()));
        if (target == null) return false;
        new PendingJump(entry, target).start();
        return true;
    }

    private final class PendingJump implements InvalidationListener {
        private final MarkdownOutline.Entry entry;
        private final Node target;
        private boolean complete;

        PendingJump(MarkdownOutline.Entry entry, Node target) {
            this.entry = entry;
            this.target = target;
        }

        void start() {
            scroll.sceneProperty().addListener(this);
            scroll.viewportBoundsProperty().addListener(this);
            scroll.getContent().layoutBoundsProperty().addListener(this);
            target.boundsInParentProperty().addListener(this);
            tryFinish();
        }

        @Override public void invalidated(Observable ignored) { tryFinish(); }

        private void tryFinish() {
            if (complete || scroll.getScene() == null || target.getScene() == null
                    || scroll.getViewportBounds().getHeight() <= 0
                    || scroll.getContent().getLayoutBounds().getHeight() <= 0) return;
            Point2D position = scroll.getContent().sceneToLocal(target.localToScene(0, 0));
            if (target != firstBlock && position.getY() <= 0) return;
            complete = true;
            scroll.sceneProperty().removeListener(this);
            scroll.viewportBoundsProperty().removeListener(this);
            scroll.getContent().layoutBoundsProperty().removeListener(this);
            target.boundsInParentProperty().removeListener(this);
            jumpTo(entry);
        }
    }
}
