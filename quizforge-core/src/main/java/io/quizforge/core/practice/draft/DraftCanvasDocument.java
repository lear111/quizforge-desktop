package io.quizforge.core.practice.draft;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** Immutable versioned World geometry shared by all practice clients. No UI or codec dependency. */
public record DraftCanvasDocument(String schemaVersion, String layoutVersion, Viewport viewport,
        QuestionCard questionCard, List<Stroke> strokes) {
    public static final String SCHEMA_VERSION = "1.0";
    public static final String LAYOUT_VERSION = "1";
    public static final double DEFAULT_CARD_WIDTH = 720;
    public static final String DEFAULT_COLOR = "#7660ab";
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

    private static double finite(double value, String field) {
        if (!Double.isFinite(value)) throw invalid(field + " must be a finite number");
        return value;
    }
    private static void positive(double value, String field) {
        if (finite(value, field) <= 0) throw invalid(field + " must be positive");
    }
    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}
