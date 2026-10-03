package io.quizforge.desktop.poc.draftcanvas.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DraftCanvasDocumentTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test void sharedJavaScriptFixtureRoundTripsWithBothVersionsPreserved() throws Exception {
        // The frontend tests read this very same source fixture, rather than a separate Java mock.
        var document = DraftCanvasDocument.parse(Files.readString(sharedFixture()));
        assertEquals("1.0", document.schemaVersion());
        assertEquals("1", document.layoutVersion());
        assertEquals(new DraftCanvasDocument.Viewport(-25.5, 17.25, 1.5), document.viewport());
        assertEquals(new DraftCanvasDocument.QuestionCard(120, 70, 720), document.questionCard());
        assertEquals(1, document.strokes().size());
        assertEquals("shared-contract-stroke-1", document.strokes().getFirst().id());
        assertEquals("#7054a5", document.strokes().getFirst().color());
        assertEquals(2.4, document.strokes().getFirst().width());
        assertEquals(document, DraftCanvasDocument.parse(document.toJson()));
        assertEquals("1.0", JSON.readTree(document.toJson()).path("schemaVersion").asText());
        assertEquals("1", JSON.readTree(document.toJson()).path("layoutVersion").asText());
    }

    @Test void defaultLayoutAndViewportChangesPreserveWorldGeometryAndSnapshotWidth() throws Exception {
        var empty = DraftCanvasDocument.createEmpty();
        assertEquals(720, empty.questionCard().width());
        assertEquals(new DraftCanvasDocument.Viewport(0, 0, 1), empty.viewport());
        assertTrue(empty.strokes().isEmpty());
        var document = DraftCanvasDocument.parse(Files.readString(sharedFixture()));
        var moved = document.withViewport(new DraftCanvasDocument.Viewport(-480, 215, 0.75));
        assertSame(document.questionCard(), moved.questionCard());
        assertEquals(document.strokes(), moved.strokes());
        assertEquals(document.questionCard().width(), moved.questionCard().width());
        assertEquals(moved, DraftCanvasDocument.parse(moved.toJson()));
        assertEquals(new DraftCanvasDocument.Viewport(-25.5, 17.25, 1.5), document.viewport());
        // Screen dimensions are deliberately absent from the persisted layout contract.
        assertFalse(moved.toJson().contains("screenWidth"));
        assertFalse(moved.toJson().contains("clientX"));
    }

    @Test void constructorsCopyEveryMutableListAndExposeOnlyImmutableValues() {
        var points = new ArrayList<>(List.of(new DraftCanvasDocument.Point(10, 20, 0.5)));
        var stroke = new DraftCanvasDocument.Stroke("stable-1", "PEN", "#abc", 2, points);
        var strokes = new ArrayList<>(List.of(stroke));
        var document = new DraftCanvasDocument("1.0", "1", new DraftCanvasDocument.Viewport(0, 0, 1),
                new DraftCanvasDocument.QuestionCard(120, 70, 720), strokes);
        points.clear();
        strokes.clear();
        assertEquals(1, document.strokes().size());
        assertEquals(1, document.strokes().getFirst().points().size());
        assertThrows(UnsupportedOperationException.class, () -> document.strokes().clear());
        assertThrows(UnsupportedOperationException.class, () -> stroke.points().clear());
    }

    @Test void strictCanonicalParserRejectsMissingRequiredFieldsAndUnknownVersions() throws Exception {
        ObjectNode valid = (ObjectNode) JSON.readTree(Files.readString(sharedFixture()));
        for (String field : List.of("schemaVersion", "layoutVersion", "viewport", "questionCard", "strokes")) {
            var invalid = valid.deepCopy();
            invalid.remove(field);
            assertThrows(IllegalArgumentException.class, () -> DraftCanvasDocument.parse(invalid.toString()), field);
        }
        for (String field : List.of("schemaVersion", "layoutVersion")) {
            var invalid = valid.deepCopy();
            invalid.put(field, "future");
            assertThrows(IllegalArgumentException.class, () -> DraftCanvasDocument.parse(invalid.toString()), field);
            assertThrows(IllegalArgumentException.class, () -> DraftCanvasDocument.upgradePoc(invalid.toString()), field);
        }
        var cardWithoutWidth = valid.deepCopy();
        ((ObjectNode) cardWithoutWidth.get("questionCard")).remove("width");
        assertThrows(IllegalArgumentException.class, () -> DraftCanvasDocument.parse(cardWithoutWidth.toString()));
        assertThrows(IllegalArgumentException.class, () -> DraftCanvasDocument.upgradePoc(cardWithoutWidth.toString()));
        var withoutColor = valid.deepCopy();
        ((ObjectNode) withoutColor.path("strokes").get(0)).remove("color");
        assertThrows(IllegalArgumentException.class, () -> DraftCanvasDocument.parse(withoutColor.toString()));
        var withoutPressure = valid.deepCopy();
        ((ObjectNode) withoutPressure.path("strokes").get(0).path("points").get(0)).remove("pressure");
        assertThrows(IllegalArgumentException.class, () -> DraftCanvasDocument.parse(withoutPressure.toString()));
    }

    @Test void strictParserRejectsCoercionDuplicateKeysAndTrailingTokens() throws Exception {
        ObjectNode valid = (ObjectNode) JSON.readTree(Files.readString(sharedFixture()));
        var stringZoom = valid.deepCopy();
        ((ObjectNode) stringZoom.get("viewport")).put("zoom", "1");
        assertThrows(IllegalArgumentException.class, () -> DraftCanvasDocument.parse(stringZoom.toString()));
        var numericLayout = valid.deepCopy();
        numericLayout.put("layoutVersion", 1);
        assertThrows(IllegalArgumentException.class, () -> DraftCanvasDocument.parse(numericLayout.toString()));
        var nullCoordinate = valid.deepCopy();
        ((ObjectNode) nullCoordinate.get("questionCard")).putNull("x");
        assertThrows(IllegalArgumentException.class, () -> DraftCanvasDocument.parse(nullCoordinate.toString()));
        assertThrows(IllegalArgumentException.class, () -> DraftCanvasDocument.parse(valid + " {}"));
        assertThrows(IllegalArgumentException.class, () -> DraftCanvasDocument.parse(
                "{\"schemaVersion\":\"1.0\",\"schemaVersion\":\"1.0\"}"));
    }

    @Test void explicitPocUpgradePreservesStoredWidthAndOnlyDefaultsOldMissingFields() throws Exception {
        ObjectNode old = (ObjectNode) JSON.readTree(Files.readString(sharedFixture()));
        old.remove("layoutVersion");
        ((ObjectNode) old.get("questionCard")).put("width", 600);
        ((ObjectNode) old.path("strokes").get(0)).remove("color");
        ((ObjectNode) old.path("strokes").get(0).path("points").get(0)).remove("pressure");
        assertThrows(IllegalArgumentException.class, () -> DraftCanvasDocument.parse(old.toString()));
        var upgraded = DraftCanvasDocument.upgradePoc(old.toString());
        assertEquals("1", upgraded.layoutVersion());
        assertEquals(600, upgraded.questionCard().width());
        assertEquals("#7660ab", upgraded.strokes().getFirst().color());
        assertEquals(0.5, upgraded.strokes().getFirst().points().getFirst().pressure());
        assertEquals(upgraded, DraftCanvasDocument.parse(upgraded.toJson()));
        ((ObjectNode) old.get("questionCard")).remove("width");
        assertEquals(720, DraftCanvasDocument.upgradePoc(old.toString()).questionCard().width());
        var nullWidth = old.deepCopy();
        ((ObjectNode) nullWidth.get("questionCard")).putNull("width");
        assertThrows(IllegalArgumentException.class, () -> DraftCanvasDocument.upgradePoc(nullWidth.toString()));
    }

    @Test void finitePositiveGeometryPressureToolColorAndUniqueIdsAreRequired() throws Exception {
        ObjectNode valid = (ObjectNode) JSON.readTree(Files.readString(sharedFixture()));
        for (double zoom : List.of(0.0, -1.0)) {
            var invalid = valid.deepCopy();
            ((ObjectNode) invalid.get("viewport")).put("zoom", zoom);
            assertThrows(IllegalArgumentException.class, () -> DraftCanvasDocument.parse(invalid.toString()));
        }
        for (double pressure : List.of(-0.1, 1.1)) {
            var invalid = valid.deepCopy();
            ((ObjectNode) invalid.path("strokes").get(0).path("points").get(0)).put("pressure", pressure);
            assertThrows(IllegalArgumentException.class, () -> DraftCanvasDocument.parse(invalid.toString()));
        }
        var duplicate = valid.deepCopy();
        ((com.fasterxml.jackson.databind.node.ArrayNode) duplicate.get("strokes")).add(duplicate.path("strokes").get(0).deepCopy());
        assertThrows(IllegalArgumentException.class, () -> DraftCanvasDocument.parse(duplicate.toString()));
        var unsupportedTool = valid.deepCopy();
        ((ObjectNode) unsupportedTool.path("strokes").get(0)).put("tool", "ERASER");
        assertThrows(IllegalArgumentException.class, () -> DraftCanvasDocument.parse(unsupportedTool.toString()));
        assertThrows(IllegalArgumentException.class, () -> new DraftCanvasDocument.Viewport(Double.NaN, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new DraftCanvasDocument.QuestionCard(0, 0, Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> new DraftCanvasDocument.Point(0, Double.NEGATIVE_INFINITY, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new DraftCanvasDocument.Stroke("", "PEN", "#abc", 1, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new DraftCanvasDocument.Stroke("one", "PEN", "red", 1,
                List.of(new DraftCanvasDocument.Point(0, 0, 1))));
        assertThrows(IllegalArgumentException.class, () -> new DraftCanvasDocument.Stroke("one", "PEN", "#abc", 0,
                List.of(new DraftCanvasDocument.Point(0, 0, 1))));
        assertThrows(IllegalArgumentException.class, () -> DraftCanvasDocument.parse(
                valid.toString().replace("-25.5", "1e999")));
    }

    @Test void explicitProjectionDoesNotSerializeDomSvgOrArbitraryUnknownProperties() throws Exception {
        ObjectNode supplied = (ObjectNode) JSON.readTree(Files.readString(sharedFixture()));
        supplied.put("dom", "<div>implementation detail</div>");
        supplied.put("svg", "<path/>");
        ((ObjectNode) supplied.get("questionCard")).put("screenWidth", 320);
        var document = DraftCanvasDocument.parse(supplied.toString());
        var encoded = JSON.readTree(document.toJson());
        assertFalse(encoded.has("dom"));
        assertFalse(encoded.has("svg"));
        assertFalse(encoded.path("questionCard").has("screenWidth"));
        assertEquals(720, document.questionCard().width());
    }

    private static Path sharedFixture() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve("quizforge-desktop-app/editor-web/draft-canvas/test/fixtures/document-v1.json");
            if (Files.isRegularFile(candidate)) return candidate;
            current = current.getParent();
        }
        throw new IllegalStateException("Shared Java/JavaScript document fixture not found");
    }
}
