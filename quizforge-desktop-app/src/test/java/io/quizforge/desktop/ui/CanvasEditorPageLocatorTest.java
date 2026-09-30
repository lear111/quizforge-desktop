package io.quizforge.desktop.ui;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class CanvasEditorPageLocatorTest {
    @Test void absentEnvironmentSelectsPackagedPage() {
        var url = new CanvasEditorPageLocator(null).editorUrl();
        assertTrue(url.startsWith(CanvasEditorPageLocator.class.getResource("canvas-editor.html").toExternalForm()));
        assertFalse(url.contains("127.0.0.1"));
        assertTrue(url.endsWith("contentWidth=672"));
    }
    @Test void environmentSelectsDevelopmentPage() {
        assertEquals("http://127.0.0.1:5173/?contentWidth=672",
                new CanvasEditorPageLocator("http://127.0.0.1:5173").editorUrl());
    }
    @Test void blankEnvironmentSelectsPackagedPage() {
        assertEquals(new CanvasEditorPageLocator(null).editorUrl(), new CanvasEditorPageLocator(" ").editorUrl());
    }
    @Test void invalidDevelopmentEndpointFailsWithoutFallback() {
        for (var url : new String[]{"https://example.com", "http://127.0.0.1:5174", "bad url"})
            assertThrows(IllegalArgumentException.class, () -> new CanvasEditorPageLocator(url));
    }
    @Test void webKitFileUrlNormalizationKeepsTheSamePageOnly() {
        var locator = new CanvasEditorPageLocator(null);
        assertTrue(locator.isEditorUrl(locator.editorUrl().replaceFirst("^file:/", "file:///")));
        assertFalse(locator.isEditorUrl("http://127.0.0.1:5173/other"));
        assertFalse(locator.isEditorUrl(locator.editorUrl().replace("canvas-editor.html", "other.html")));
    }
}
