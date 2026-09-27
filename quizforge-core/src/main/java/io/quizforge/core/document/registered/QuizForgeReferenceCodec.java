package io.quizforge.core.document.registered;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Strict clipboard URI codec; no filesystem path or JavaFX dependency. */
public final class QuizForgeReferenceCodec {
    public String encode(QuizForgeReference reference) {
        if (reference == null) throw new IllegalArgumentException("Reference is required");
        String uri = "quizforge://document/" + reference.documentAssetId();
        if (!reference.isAnchor()) return uri;
        return uri + "/anchor/" + encodePart(reference.anchorName()) + "?occurrence="
                + reference.occurrence() + "&contentId=" + encodePart(reference.documentContentId());
    }

    public QuizForgeReference decode(String text) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("Reference URI is required");
        final URI uri;
        try { uri = new URI(text); }
        catch (URISyntaxException error) { throw new IllegalArgumentException("Malformed QuizForge reference URI", error); }
        if (!"quizforge".equals(uri.getScheme()) || !"document".equals(uri.getRawAuthority())
                || uri.getRawFragment() != null || uri.isOpaque())
            throw new IllegalArgumentException("Invalid QuizForge reference scheme or authority");
        String[] segments = uri.getRawPath() == null ? new String[0] : uri.getRawPath().split("/", -1);
        if (segments.length == 2 && segments[0].isEmpty() && uri.getRawQuery() == null)
            return QuizForgeReference.document(segments[1]);
        if (segments.length != 4 || !segments[0].isEmpty() || !"anchor".equals(segments[2])
                || uri.getRawQuery() == null) throw new IllegalArgumentException("Invalid anchor reference URI");
        String[] parameters = uri.getRawQuery().split("&", -1);
        if (parameters.length != 2 || !parameters[0].startsWith("occurrence=")
                || !parameters[1].startsWith("contentId="))
            throw new IllegalArgumentException("Invalid anchor reference parameters");
        try {
            int occurrence = Integer.parseInt(parameters[0].substring("occurrence=".length()));
            return QuizForgeReference.anchor(segments[1],
                    decodePart(parameters[1].substring("contentId=".length())),
                    decodePart(segments[3]), occurrence);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Invalid anchor occurrence", error);
        }
    }

    private String encodePart(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String decodePart(String value) {
        if (value.matches(".*%(?![0-9A-Fa-f]{2}).*"))
            throw new IllegalArgumentException("Malformed percent encoding");
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
