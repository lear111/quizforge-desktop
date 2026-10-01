package io.quizforge.desktop.ui.shared;

import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

/** One boundary for copying text from desktop commands. */
@FunctionalInterface
public interface TextClipboard {
    public void write(String value);

    public static TextClipboard system() {
        return value -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(value);
            Clipboard.getSystemClipboard().setContent(content);
        };
    }
}
