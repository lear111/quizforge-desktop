package io.quizforge.desktop.ui.content.document.canvas;

/** JSON transport shared by the current Canvas components. */
final class ContentJson {
    private ContentJson() { }
    static String write(Object value) {
        try { return CanvasNativeDocument.JSON.writeValueAsString(value); }
        catch (com.fasterxml.jackson.core.JsonProcessingException failure) {
            throw new IllegalArgumentException("Could not encode editor data",failure);
        }
    }
}
