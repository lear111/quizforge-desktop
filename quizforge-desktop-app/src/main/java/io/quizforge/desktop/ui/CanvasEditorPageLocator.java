package io.quizforge.desktop.ui;

import java.net.URI;
import java.net.URL;
import java.util.Objects;

/** Selects the editor page without exposing development tooling to authoring code. */
final class CanvasEditorPageLocator {
    static final String DEV_URL_ENV = "QUIZFORGE_CANVAS_EDITOR_DEV_URL";
    private final String url;

    CanvasEditorPageLocator() { this(System.getenv(DEV_URL_ENV)); }

    CanvasEditorPageLocator(String devUrl) {
        this(devUrl, CanvasEditorPageLocator.class.getResource("canvas-editor.html"));
    }

    CanvasEditorPageLocator(String devUrl, URL packagedPage) {
        if (devUrl == null || devUrl.isBlank()) {
            url = Objects.requireNonNull(packagedPage,
                    "Missing packaged Canvas Editor page").toExternalForm();
        } else {
            var uri = URI.create(devUrl.trim());
            if (!"http".equals(uri.getScheme()) || !"127.0.0.1".equals(uri.getHost())
                    || uri.getPort() != 5173 || uri.getUserInfo() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException(DEV_URL_ENV + " must use http://127.0.0.1:5173");
            }
            url = uri.getRawPath().isEmpty() ? uri.toString().replace(":5173", ":5173/") : uri.toString();
        }
    }

    String editorUrl() {
        // JarURLConnection includes a query in the entry name; fragment stays page-local.
        return url + (url.startsWith("jar:") ? "#" : url.contains("?") ? "&" : "?")
                + "contentWidth=" + QuestionContentLayout.QUESTION_CONTENT_WIDTH;
    }

    boolean isEditorUrl(String location) {
        try { return URI.create(editorUrl()).equals(URI.create(location)); }
        catch (IllegalArgumentException invalid) { return false; }
    }
}
