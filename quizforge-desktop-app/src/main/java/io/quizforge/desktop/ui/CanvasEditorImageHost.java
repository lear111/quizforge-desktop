package io.quizforge.desktop.ui;

import java.util.Objects;
import javafx.application.Platform;

/** Small asynchronous lifecycle/image API exposed to the editor page. */
public final class CanvasEditorImageHost {
    private final Runnable chooseImage;
    private final Runnable editorReady;

    public CanvasEditorImageHost(Runnable chooseImage) {
        this(chooseImage, () -> {});
    }

    CanvasEditorImageHost(Runnable chooseImage, Runnable editorReady) {
        this.chooseImage = Objects.requireNonNull(chooseImage);
        this.editorReady = Objects.requireNonNull(editorReady);
    }

    public void chooseImage() {
        Platform.runLater(chooseImage);
    }

    public void editorReady() { Platform.runLater(editorReady); }
}
