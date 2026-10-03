package io.quizforge.desktop.poc.draftcanvas.contract;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** Internal v1 geometry contract. This DTO contains world geometry, never DOM/SVG internals. */
public record DraftCanvasDocument(String schemaVersion, String layoutVersion, Viewport viewport,
        QuestionCard questionCard, List<Stroke> strokes) {
    public static final String SCHEMA_VERSION = "1.0";
    public static final String LAYOUT_VERSION = "1";
    public static final double DEFAULT_CARD_WIDTH = 720;
    public static final String DEFAULT_COLOR = "#7660ab";
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final Pattern COLOR = Pattern.compile("^#[0-9a-f]{3}(?:[0-9a-f]{3}(?:[0-9a-f]{2})?)?$", Pattern.CASE_INSENSITIVE);

    /** Offsets are world units: screen = (world + offset) * zoom. */
    public record Viewport(double x, double y, double zoom) {
        public Viewport { finite(x, "viewport.x"); finite(y, "viewport.y"); positive(zoom, "viewport.zoom"); }
    }
    /** Stored width is a fixed logical layout width, independent of device viewport size. */
    public record QuestionCard(double x, double y, double width) {
        public QuestionCard { finite(x, "questionCard.x"); finite(y, "questionCard.y"); positive(width, "questionCard.width"); }
    }
    public record Point(double x, double y, double pressure) {
        public Point {
            finite(x, "point.x"); finite(y, "point.y"); finite(pressure, "point.pressure");
            if (pressure < 0 || pressure > 1) throw invalid("point.pressure must be between 0 and 1");
        }
    }
    public record Stroke(String id, String tool, String color, double width, List<Point> points) {
        public Stroke {
            if (id == null || id.isBlank()) throw invalid("stroke.id must be a nonempty stable string");
            if (!"PEN".equals(tool)) throw invalid("stroke.tool must be PEN");
            if (color == null || !COLOR.matcher(color).matches()) throw invalid("stroke.color must be a hexadecimal CSS color");
            positive(width, "stroke.width");
            points = List.copyOf(Objects.requireNonNull(points, "stroke.points"));
            if (points.isEmpty()) throw invalid("stroke.points must contain at least one world point");
        }
    }

    public DraftCanvasDocument {
        if (!SCHEMA_VERSION.equals(schemaVersion)) throw invalid("Unsupported draft schemaVersion");
        if (!LAYOUT_VERSION.equals(layoutVersion)) throw invalid("Unsupported draft layoutVersion");
        Objects.requireNonNull(viewport, "viewport");
        Objects.requireNonNull(questionCard, "questionCard");
        strokes = List.copyOf(Objects.requireNonNull(strokes, "strokes"));
        var ids = new HashSet<String>();
        for (Stroke stroke : strokes) if (!ids.add(stroke.id())) throw invalid("stroke ids must be unique");
    }

    public static DraftCanvasDocument createEmpty() {
        return new DraftCanvasDocument(SCHEMA_VERSION, LAYOUT_VERSION, new Viewport(0, 0, 1),
                new QuestionCard(120, 70, DEFAULT_CARD_WIDTH), List.of());
    }

    /** Pan and zoom replace only the camera; durable question/stroke geometry remains identical. */
    public DraftCanvasDocument withViewport(Viewport next) {
        return new DraftCanvasDocument(schemaVersion, layoutVersion, next, questionCard, strokes);
    }

    public String toJson() {
        try { return JSON.writeValueAsString(this); }
        catch (JsonProcessingException error) { throw new IllegalStateException("Could not serialize DraftCanvasDocument", error); }
    }

    /** Strict known-version parser. Unknown properties are stripped by this explicit DTO projection. */
    public static DraftCanvasDocument parse(String json) { return parseNode(read(json)); }

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
        String schema = text(root, "schemaVersion");
        String layout = text(root, "layoutVersion");
        if (!SCHEMA_VERSION.equals(schema)) throw invalid("Unsupported draft schemaVersion");
        if (!LAYOUT_VERSION.equals(layout)) throw invalid("Unsupported draft layoutVersion");
        ObjectNode view = object(root.get("viewport"), "viewport");
        ObjectNode card = object(root.get("questionCard"), "questionCard");
        var strokes = new java.util.ArrayList<Stroke>();
        for (JsonNode valueStroke : array(root.get("strokes"), "strokes")) {
            ObjectNode stroke = object(valueStroke, "stroke");
            var points = new java.util.ArrayList<Point>();
            for (JsonNode valuePoint : array(stroke.get("points"), "stroke.points")) {
                ObjectNode point = object(valuePoint, "point");
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
    private static double finite(double value, String field) {
        if (!Double.isFinite(value)) throw invalid(field + " must be a finite number");
        return value;
    }
    private static void positive(double value, String field) {
        if (finite(value, field) <= 0) throw invalid(field + " must be positive");
    }
    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}
