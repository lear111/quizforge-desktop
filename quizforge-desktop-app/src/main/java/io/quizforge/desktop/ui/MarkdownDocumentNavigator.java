package io.quizforge.desktop.ui;

import io.quizforge.core.document.registered.MarkdownSourceRange;
import java.util.HashMap;
import java.util.Map;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;

/** Resolves source positions to nodes in the current JavaFX preview only. */
final class MarkdownDocumentNavigator {
    private record Position(int line, int column) { }

    private final ScrollPane scroll;
    private final Map<Position, Node> renderedBlocks = new HashMap<>();

    MarkdownDocumentNavigator(ScrollPane scroll) { this.scroll = scroll; }

    void register(MarkdownSourceRange source, Node rendered) {
        renderedBlocks.put(new Position(source.startLine(), source.startColumn()), rendered);
    }

    boolean jumpTo(MarkdownOutline.Entry entry) {
        if (entry.orphan() || entry.target() == null) return false;
        Node target = renderedBlocks.get(new Position(entry.target().startLine(),
                entry.target().startColumn()));
        if (target == null || target.getScene() == null) return false;
        scroll.applyCss();
        scroll.layout();
        Node content = scroll.getContent();
        Point2D inContent = content.sceneToLocal(target.localToScene(0, 0));
        double travel = content.getLayoutBounds().getHeight() - scroll.getViewportBounds().getHeight();
        double offset = Math.max(0, inContent.getY() - 48);
        scroll.setVvalue(travel <= 0 ? 0 : Math.min(1, offset / travel));
        return true;
    }
}
