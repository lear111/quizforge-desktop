package io.quizforge.core.practice.draft;

import java.util.ArrayList;
import java.util.List;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DraftCanvasValueTest {
    @Test void documentAndNestedCollectionsAreImmutable() {
        var points = new ArrayList<>(List.of(new DraftCanvasDocument.Point(1, 2, .5)));
        var stroke = new DraftCanvasDocument.Stroke("one", "PEN", "#abc", 2, points);
        var strokes = new ArrayList<>(List.of(stroke));
        var empty = DraftCanvasDocument.createEmpty();
        var document = new DraftCanvasDocument("1.0", "1", empty.viewport(), empty.questionCard(), strokes);
        points.clear(); strokes.clear();
        assertEquals(1, document.strokes().getFirst().points().size());
        assertThrows(UnsupportedOperationException.class, () -> document.strokes().clear());
        assertThrows(UnsupportedOperationException.class, () -> stroke.points().clear());
    }
    @Test void invalidVersionsGeometryAndIdentityAreRejected() {
        var empty = DraftCanvasDocument.createEmpty();
        assertThrows(IllegalArgumentException.class, () -> new DraftCanvasDocument("2", "1", empty.viewport(), empty.questionCard(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new DraftCanvasDocument.Viewport(Double.NaN, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new DraftCanvasDocument.Viewport(0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new DraftCanvasDocument.QuestionCard(0, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> new DraftCanvasDocument.Point(0, 0, 1.1));
        assertThrows(IllegalArgumentException.class, () -> new ActiveDraftCanvas("", empty, Instant.now()));
        assertThrows(IllegalArgumentException.class, () -> new AttemptDraftSnapshot("", empty, Instant.now()));
    }
    @Test void cameraChangesCannotMutateFrozenValue() {
        var first = DraftCanvasDocument.createEmpty();
        var frozen = new AttemptDraftSnapshot("attempt", first, Instant.now());
        var active = new ActiveDraftCanvas("question", first.withViewport(new DraftCanvasDocument.Viewport(10, -20, 2)), Instant.now());
        assertEquals(0, frozen.document().viewport().x());
        assertEquals(first.questionCard(), active.document().questionCard());
        assertEquals(first.strokes(), active.document().strokes());
    }
    @Test void textAndPaperAreImmutableValidatedAndPreservedByCameraChanges() {
        var empty = DraftCanvasDocument.createEmpty();
        assertTrue(empty.texts().isEmpty()); assertNull(empty.paper());
        var note = new DraftCanvasDocument.TextAnnotation("note", -5, 12, 180, 16, "#252933", "草稿\n第二行");
        var texts = new ArrayList<>(List.of(note));
        var paper = new DraftCanvasDocument.Paper("#fff8dc", DraftCanvasDocument.PaperPattern.LINES);
        var document = new DraftCanvasDocument("1.0", "1", empty.viewport(), empty.questionCard(), List.of(), texts, paper);
        texts.clear();
        assertEquals(List.of(note), document.texts());
        assertThrows(UnsupportedOperationException.class, () -> document.texts().clear());
        var moved = document.withViewport(new DraftCanvasDocument.Viewport(1, 2, 3));
        assertEquals(document.texts(), moved.texts()); assertEquals(paper, moved.paper());
        assertThrows(IllegalArgumentException.class, () -> new DraftCanvasDocument("1.0", "1", empty.viewport(), empty.questionCard(), List.of(), List.of(note, note), paper));
        assertThrows(IllegalArgumentException.class, () -> new DraftCanvasDocument.TextAnnotation(" ", 0, 0, 1, 1, "#fff", ""));
        assertThrows(IllegalArgumentException.class, () -> new DraftCanvasDocument.TextAnnotation("n", Double.NaN, 0, 1, 1, "#fff", ""));
        assertThrows(IllegalArgumentException.class, () -> new DraftCanvasDocument.TextAnnotation("n", 0, 0, 0, 1, "#fff", ""));
        assertThrows(IllegalArgumentException.class, () -> new DraftCanvasDocument.TextAnnotation("n", 0, 0, 1, 0, "#fff", ""));
        assertThrows(IllegalArgumentException.class, () -> new DraftCanvasDocument.TextAnnotation("n", 0, 0, 1, 1, "blue", ""));
        assertThrows(IllegalArgumentException.class, () -> new DraftCanvasDocument.TextAnnotation("n", 0, 0, 1, 1, "#fff", null));
        assertThrows(IllegalArgumentException.class, () -> new DraftCanvasDocument.TextAnnotation("n", 0, 0, 1, 1, "#fff", "x".repeat(10001)));
        assertThrows(IllegalArgumentException.class, () -> new DraftCanvasDocument.Paper("paper", DraftCanvasDocument.PaperPattern.PLAIN));
    }
}
