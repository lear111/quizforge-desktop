package io.quizforge.desktop.dev;

import io.quizforge.desktop.ui.shared.UiTheme;
import java.io.UncheckedIOException;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Window;
import javafx.util.Duration;

/** Development-only CSS reload; it does not watch workspace files or change production startup. */
public final class LiveCssReloader {
    private LiveCssReloader() { }

    public static Runnable start(Scene scene) {
        if (!UiTheme.liveCssEnabled()) return () -> { };
        String[] loaded = { UiTheme.stylesheet(), UiTheme.markdownStylesheet() };
        Timeline timer = new Timeline(new KeyFrame(Duration.millis(600), event -> {
            String workspace;
            String markdown;
            try {
                workspace = UiTheme.stylesheet();
                markdown = UiTheme.markdownStylesheet();
            } catch (UncheckedIOException unavailableDuringSave) {
                return;
            }
            if (workspace.equals(loaded[0]) && markdown.equals(loaded[1])) return;
            for (Window window : Window.getWindows()) {
                Scene openScene = window.getScene();
                if (openScene == null) continue;
                replace(openScene.getStylesheets(), loaded[0], workspace);
                replace(openScene.getStylesheets(), loaded[1], markdown);
                replaceInTree(openScene.getRoot(), loaded[0], workspace, loaded[1], markdown);
            }
            loaded[0] = workspace;
            loaded[1] = markdown;
        }));
        timer.setCycleCount(Animation.INDEFINITE);
        timer.play();
        return timer::stop;
    }

    private static void replaceInTree(Node node, String oldWorkspace, String workspace,
            String oldMarkdown, String markdown) {
        if (!(node instanceof Parent parent)) return;
        replace(parent.getStylesheets(), oldWorkspace, workspace);
        replace(parent.getStylesheets(), oldMarkdown, markdown);
        for (Node child : parent.getChildrenUnmodifiable()) {
            replaceInTree(child, oldWorkspace, workspace, oldMarkdown, markdown);
        }
    }

    private static void replace(ObservableList<String> stylesheets, String previous, String next) {
        int index = stylesheets.indexOf(previous);
        if (index >= 0) stylesheets.set(index, next);
    }
}
