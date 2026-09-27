package io.quizforge.desktop.ui;

import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

/** One boundary for copying text from desktop commands. */
@FunctionalInterface
interface TextClipboard {
    void write(String value);

    static TextClipboard system() {
        return value -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(value);
            Clipboard.getSystemClipboard().setContent(content);
        };
    }
}
