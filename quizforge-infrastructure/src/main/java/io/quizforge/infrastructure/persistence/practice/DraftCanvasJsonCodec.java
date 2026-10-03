package io.quizforge.infrastructure.persistence.practice;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quizforge.core.practice.draft.DraftCanvasDocument;
import io.quizforge.core.practice.draft.DraftCanvasDocument.*;
import static io.quizforge.core.practice.draft.DraftCanvasDocument.*;

/** Strict persisted v1 codec, independent of all frontend code. */
public final class DraftCanvasJsonCodec {
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    public String encode(DraftCanvasDocument document) {
        try { return JSON.writeValueAsString(document); }
        catch (JsonProcessingException error) { throw new IllegalStateException("Could not serialize DraftCanvasDocument", error); }
    }

    /** Strict canonical parser: reject unknown fields rather than silently losing persisted data. */
    public static DraftCanvasDocument decode(String json) { return parseNode(read(json)); }

    /** Explicit compatibility entry for the preceding POC; canonical v1 never defaults required fields. */
    public static DraftCanvasDocument upgradePoc(String json) {
        JsonNode node = read(json);
        ObjectNode root = object(node, "draft");
        if (root.has("layoutVersion")) return parseNode(root);
        if (!SCHEMA_VERSION.equals(text(root, "schemaVersion"))) throw invalid("Unsupported draft schemaVersion");
        ObjectNode upgraded = root.deepCopy();
        upgraded.put("layoutVersion", LAYOUT_VERSION);
        ObjectNode card = object(upgraded.get("questionCard"), "questionCard");
        if (!card.has("width")) card.put("width", DEFAULT_CARD_WIDTH);
        JsonNode strokes = array(upgraded.get("strokes"), "strokes");
        for (JsonNode value : strokes) {
            ObjectNode stroke = object(value, "stroke");
            if (!stroke.has("color")) stroke.put("color", DEFAULT_COLOR);
            for (JsonNode point : array(stroke.get("points"), "stroke.points")) {
                ObjectNode geometry = object(point, "point");
                if (!geometry.has("pressure")) geometry.put("pressure", 0.5);
            }
        }
        return parseNode(upgraded);
    }

    private static DraftCanvasDocument parseNode(JsonNode value) {
        ObjectNode root = object(value, "draft");
        fields(root, "schemaVersion", "layoutVersion", "viewport", "questionCard", "strokes");
        String schema = text(root, "schemaVersion");
        String layout = text(root, "layoutVersion");
        if (!SCHEMA_VERSION.equals(schema)) throw invalid("Unsupported draft schemaVersion");
        if (!LAYOUT_VERSION.equals(layout)) throw invalid("Unsupported draft layoutVersion");
        ObjectNode view = object(root.get("viewport"), "viewport");
        fields(view, "x", "y", "zoom");
        ObjectNode card = object(root.get("questionCard"), "questionCard");
        fields(card, "x", "y", "width");
        var strokes = new java.util.ArrayList<Stroke>();
        for (JsonNode valueStroke : array(root.get("strokes"), "strokes")) {
            ObjectNode stroke = object(valueStroke, "stroke");
            fields(stroke, "id", "tool", "color", "width", "points");
            var points = new java.util.ArrayList<Point>();
            for (JsonNode valuePoint : array(stroke.get("points"), "stroke.points")) {
                ObjectNode point = object(valuePoint, "point");
                fields(point, "x", "y", "pressure");
                points.add(new Point(number(point, "x"), number(point, "y"), number(point, "pressure")));
            }
            strokes.add(new Stroke(text(stroke, "id"), text(stroke, "tool"), text(stroke, "color"), number(stroke, "width"), points));
        }
        return new DraftCanvasDocument(schema, layout,
                new Viewport(number(view, "x"), number(view, "y"), number(view, "zoom")),
                new QuestionCard(number(card, "x"), number(card, "y"), number(card, "width")), strokes);
    }

    private static JsonNode read(String json) {
        if (json == null) throw invalid("Draft JSON must be supplied");
        try { return JSON.readTree(json); }
        catch (JsonProcessingException error) { throw new IllegalArgumentException("Invalid DraftCanvasDocument JSON", error); }
    }
    private static ObjectNode object(JsonNode value, String name) {
        if (!(value instanceof ObjectNode object)) throw invalid(name + " must be an object");
        return object;
    }
    private static JsonNode array(JsonNode value, String name) {
        if (value == null || !value.isArray()) throw invalid(name + " must be an array");
        return value;
    }
    private static String text(ObjectNode object, String field) {
        JsonNode value = object.get(field);
        if (value == null || !value.isTextual()) throw invalid(field + " must be a string");
        return value.textValue();
    }
    private static double number(ObjectNode object, String field) {
        JsonNode value = object.get(field);
        if (value == null || !value.isNumber()) throw invalid(field + " must be a finite number");
        return finite(value.doubleValue(), field);
    }
    private static void fields(ObjectNode node, String... allowed) {
        var expected = java.util.Set.of(allowed);
        node.fieldNames().forEachRemaining(field -> {
            if (!expected.contains(field)) throw invalid("Unknown Draft field: " + field);
        });
    }
    private static double finite(double value, String field) {
        if (!Double.isFinite(value)) throw invalid(field + " must be finite");
        return value;
    }
    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}
