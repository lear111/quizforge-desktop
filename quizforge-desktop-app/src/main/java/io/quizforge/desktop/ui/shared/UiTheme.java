package io.quizforge.desktop.ui.shared;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Objects;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;

/** Shared visual primitives for the desktop workspace and its dialogs. */
public final class UiTheme {
    private static final String LIVE_CSS_PROPERTY = "quizforge.ui.liveCssDir";
    private static final String LIVE_CSS_ENV = "QUIZFORGE_LIVE_CSS_DIR";

    private UiTheme() { }

    public static String stylesheet() {
        return stylesheet("workspace.css");
    }

    public static String markdownStylesheet() {
        return stylesheet("markdown-preview.css");
    }

    public static boolean liveCssEnabled() {
        return liveCssDirectory() != null;
    }

    private static String stylesheet(String name) {
        Path directory = liveCssDirectory();
        if (directory == null) {
            return Objects.requireNonNull(UiTheme.class.getResource("/styles/"+name)).toExternalForm();
        }
        try {
            byte[] css = Files.readAllBytes(directory.resolve(name));
            return "data:text/css;base64," + Base64.getEncoder().encodeToString(css);
        } catch (IOException error) {
            throw new UncheckedIOException("Could not read live CSS: " + name, error);
        }
    }

    private static Path liveCssDirectory() {
        String configured = System.getProperty(LIVE_CSS_PROPERTY);
        if (configured == null || configured.isBlank()) configured = System.getenv(LIVE_CSS_ENV);
        if (configured != null && !configured.isBlank()) return Path.of(configured);
        return developmentCssDirectory(Path.of(System.getProperty("user.dir")),
                System.getProperty("sun.java.command", ""),
                System.getProperty("java.class.path", ""));
    }

    public static Path developmentCssDirectory(Path workingDirectory, String command, String classpath) {
        String application = "io.quizforge.desktop.bootstrap.DesktopApplication";
        if (!command.equals(application) && !command.startsWith(application + " ")) return null;
        if (!classpath.contains("target/classes") && !classpath.contains("target\\classes")) return null;
        Path[] candidates = {
                workingDirectory.resolve("src/main/resources/styles"),
                workingDirectory.resolve("quizforge-desktop-app/src/main/resources/styles")
        };
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate.resolve("workspace.css"))
                    && Files.isRegularFile(candidate.resolve("markdown-preview.css"))) {
                return candidate;
            }
        }
        return null;
    }

    public static void apply(Scene scene) {
        scene.getStylesheets().add(stylesheet());
        scene.getStylesheets().add(markdownStylesheet());
    }

    public static void apply(Dialog<?> dialog) {
        dialog.getDialogPane().getStylesheets().add(stylesheet());
        dialog.getDialogPane().getStyleClass().add("workspace-dialog");
    }

    public static Label label(String text, String style) {
        Label label = new Label(text);
        label.getStyleClass().add(style);
        label.setWrapText(true);
        return label;
    }

    public static Button button(String text, String icon, String style, Runnable action) {
        Button button = new Button(text, icon(icon));
        button.getStyleClass().add(style);
        button.setOnAction(event -> action.run());
        return button;
    }

    public static Button iconButton(String icon, String description, Runnable action) {
        Button button = button("", icon, "icon-button", action);
        button.setTooltip(new Tooltip(description));
        button.setAccessibleText(description);
        return button;
    }

    public static Node icon(String name) {
        SVGPath path = new SVGPath();
        path.setContent(switch (name) {
            case "folder" -> "M2 5 L8 5 L10 7 L18 7 L18 17 L2 17 Z";
            case "file" -> "M5 2 L12 2 L17 7 L17 18 L5 18 Z M12 2 L12 7 L17 7 M8 11 L14 11 M8 14 L14 14";
            case "book" -> "M10 5 Q6 2 1 4 L1 17 Q6 15 10 18 Q14 15 19 17 L19 4 Q14 2 10 5 Z M10 5 L10 18";
            case "book-pen" -> "M9 5 Q5 2 1 4 L1 17 Q5 15 9 18 L9 5 Q13 2 18 4 L18 9 M11 17 L12 13 L17 8 L20 11 L15 16 Z M16 9 L19 12";
            case "markdown" -> "M3 2 L13 2 L17 6 L17 18 L3 18 Z M6 14 L6 8 L9 11 L12 8 L12 14";
            case "qbank" -> "M3 2 L17 2 L17 18 L3 18 Z M7 7 Q7 4 10 4 Q14 4 13 7 L10 10 L10 12 M10 15 L10 15.2";
            case "chevron" -> "M5 8 L10 13 L15 8";
            case "check" -> "M3 10 L8 15 L17 5";
            case "edit" -> "M3 13 L13 3 L17 7 L7 17 L2 18 Z M11 5 L15 9";
            case "refresh" -> "M17 7 A7 7 0 1 0 17 13 M17 2 L17 7 L12 7";
            case "grid" -> "M2 2 L8 2 L8 8 L2 8 Z M12 2 L18 2 L18 8 L12 8 Z M2 12 L8 12 L8 18 L2 18 Z M12 12 L18 12 L18 18 L12 18 Z";
            case "clock" -> "M10 2 A8 8 0 1 1 9.99 2 M10 5 L10 10 L14 12";
            case "plus" -> "M10 3 L10 17 M3 10 L17 10";
            case "settings" -> "M8 1 L12 1 L12.5 3.3 L14.2 4.3 L16.5 3.6 L18.5 7 L16.7 8.6 L16.7 11.4 L18.5 13 L16.5 16.4 L14.2 15.7 L12.5 16.7 L12 19 L8 19 L7.5 16.7 L5.8 15.7 L3.5 16.4 L1.5 13 L3.3 11.4 L3.3 8.6 L1.5 7 L3.5 3.6 L5.8 4.3 L7.5 3.3 Z M13 10 A3 3 0 1 1 7 10 A3 3 0 1 1 13 10";
            case "panel" -> "M2 3 L18 3 L18 17 L2 17 Z M7 3 L7 17";
            case "upload" -> "M10 13 L10 2 M6 6 L10 2 L14 6 M3 12 L3 18 L17 18 L17 12";
            case "arrow" -> "M3 10 L17 10 M12 5 L17 10 L12 15";
            case "arrow-left" -> "M17 10 L3 10 M8 5 L3 10 L8 15";
            case "save" -> "M3 2 L15 2 L18 5 L18 18 L2 18 L2 2 Z M6 2 L6 8 L14 8 L14 2 M6 18 L6 12 L14 12 L14 18";
            case "more" -> "M4 10 L4.1 10 M10 10 L10.1 10 M16 10 L16.1 10";
            case "copy" -> "M7 7 L18 7 L18 18 L7 18 Z M13 4 L13 2 L2 2 L2 13 L4 13";
            case "trash" -> "M3 5 L17 5 M7 5 L7 2 L13 2 L13 5 M5 5 L6 18 L14 18 L15 5 M8 8 L8 15 M12 8 L12 15";
            case "close" -> "M5 5 L15 15 M15 5 L5 15";
            case "finish" -> "M4 18 L4 2 L16 2 L16 11 L4 11 M8 2 L8 11 M12 2 L12 11 M4 6.5 L16 6.5";
            case "spark" -> "M10 2 L12 8 L18 10 L12 12 L10 18 L8 12 L2 10 L8 8 Z";
            default -> throw new IllegalArgumentException("Unknown icon: " + name);
        });
        path.getStyleClass().add("line-icon");
        StackPane box = new StackPane(path);
        box.setMinSize(20, 20);
        box.setPrefSize(20, 20);
        box.setMaxSize(20, 20);
        box.setAccessibleText(name);
        box.getStyleClass().add("icon-" + name);
        return box;
    }

    /** Compact sidebar-only icons; other surfaces keep their existing icon family. */
    public static Node workspaceTreeIcon(String name) {
        SVGPath path = new SVGPath();
        path.setContent(switch (name) {
            case "folder" -> "M1 3 L5.5 3 L7 4.5 L13 4.5 L13 11.5 L1 11.5 Z";
            case "markdown" -> "M3 1.5 L8 1.5 L11 4.5 L11 12.5 L3 12.5 Z M8 1.5 L8 4.5 L11 4.5 M5 7 L9 7 M5 9.5 L9 9.5";
            case "qbank" -> "M2.5 1.5 L11.5 1.5 L11.5 12.5 L2.5 12.5 Z M4.5 4.5 L4.6 4.5 M6.5 4.5 L9.5 4.5 M4.5 7 L4.6 7 M6.5 7 L9.5 7 M4.5 9.5 L4.6 9.5 M6.5 9.5 L9.5 9.5";
            default -> "M3 1.5 L8 1.5 L11 4.5 L11 12.5 L3 12.5 Z M8 1.5 L8 4.5 L11 4.5";
        });
        path.getStyleClass().add("line-icon");
        StackPane box = new StackPane(path);
        box.setMinSize(14, 14);
        box.setPrefSize(14, 14);
        box.setMaxSize(14, 14);
        box.setAccessibleText(name.equals("qbank") ? "题库文件" : name);
        box.getStyleClass().addAll("workspace-tree-icon", "icon-" + name);
        return box;
    }

    public static Node workspaceMenuIcon(String name) {
        SVGPath path = new SVGPath();
        path.setContent(switch (name) {
            case "check" -> "M2 7 L5.5 10.5 L12 3.5";
            case "folder" -> "M1 3 L5.5 3 L7 4.5 L13 4.5 L13 11.5 L1 11.5 Z";
            case "folder-open" -> "M1 11.5 L1 3 L5 3 L6.5 4.5 L12 4.5 L12 6 M1 11.5 L3 6 L13 6 L11 11.5 Z";
            case "folder-plus" -> "M1 3 L5.5 3 L7 4.5 L13 4.5 L13 11.5 L1 11.5 Z M7 6 L7 10 M5 8 L9 8";
            case "file-plus" -> "M3 1.5 L8 1.5 L11 4.5 L11 12.5 L3 12.5 Z M8 1.5 L8 4.5 L11 4.5 M7 6.5 L7 10.5 M5 8.5 L9 8.5";
            case "refresh" -> "M11.5 5 A5 5 0 1 0 11.5 9 M11.5 1.5 L11.5 5 L8 5";
            case "link" -> "M5.5 8.5 L8.5 5.5 M4.5 7.5 L3 9 A2.1 2.1 0 0 0 6 12 L8 10 M6 4 L8 2 A2.1 2.1 0 0 1 11 5 L9.5 6.5";
            case "copy" -> "M5 5 L12 5 L12 12 L5 12 Z M9 3 L9 1.5 L1.5 1.5 L1.5 9 L3 9";
            case "edit" -> "M2 9 L9.5 1.5 L12.5 4.5 L5 12 L1.5 12.5 Z M8 3 L11 6";
            case "trash" -> "M2 4 L12 4 M5 4 L5 2 L9 2 L9 4 M3.5 4 L4 12 L10 12 L10.5 4 M6 6 L6 10 M8 6 L8 10";
            default -> throw new IllegalArgumentException("Unknown workspace menu icon: " + name);
        });
        path.getStyleClass().add("workspace-menu-icon-path");
        StackPane icon = new StackPane(path);
        icon.setMinSize(14, 14);
        icon.setPrefSize(14, 14);
        icon.setMaxSize(14, 14);
        StackPane gutter = new StackPane(icon);
        gutter.setMinSize(18, 14);
        gutter.setPrefSize(18, 14);
        gutter.setMaxSize(18, 14);
        gutter.getStyleClass().addAll("workspace-menu-icon", "icon-" + name);
        return gutter;
    }

    public static ScrollPane scroll(Node content) {
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        return scroll;
    }

    public static VBox emptyState(String icon, String title, String description) {
        StackPane mark = new StackPane(icon(icon));
        mark.getStyleClass().add("empty-mark");
        mark.setMaxSize(48, 48);
        VBox box = new VBox(14, mark, label(title, "section-title"), label(description, "muted"));
        box.getStyleClass().add("empty-state");
        return box;
    }

    public static VBox quietState(String title, String description) {
        VBox box = new VBox(10, label(title, "quiet-state-title"), label(description, "muted"));
        box.getStyleClass().add("quiet-state");
        return box;
    }
}
