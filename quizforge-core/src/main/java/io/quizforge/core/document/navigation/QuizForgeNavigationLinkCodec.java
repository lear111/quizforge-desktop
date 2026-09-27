package io.quizforge.core.document.navigation;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** Strict path-independent URI codec for current asset navigation. */
public final class QuizForgeNavigationLinkCodec {
    public String encode(QuizForgeNavigationLink link) {
        if (link == null) throw new IllegalArgumentException("Navigation link is required");
        String base = "quizforge://asset/" + link.assetId();
        if (link.target() instanceof QuizForgeNavigationLink.AssetTarget) return base;
        if (link.target() instanceof QuizForgeNavigationLink.HeadingTarget heading)
            return base + "/heading/" + encodePart(heading.headingText())
                    + "?occurrence=" + heading.occurrence();
        if (link.target() instanceof QuizForgeNavigationLink.AnchorTarget anchor)
            return base + "/anchor/" + encodePart(anchor.anchorName())
                    + "?occurrence=" + anchor.occurrence();
        throw new IllegalArgumentException("Unknown navigation target");
    }

    public QuizForgeNavigationLink decode(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Navigation URI is required");
        URI uri;
        try { uri = new URI(value); }
        catch (URISyntaxException error) {
            throw new IllegalArgumentException("Malformed navigation URI", error);
        }
        if (!"quizforge".equals(uri.getScheme()) || !"asset".equals(uri.getRawAuthority())
                || uri.isOpaque() || uri.getRawFragment() != null || uri.getRawPath() == null)
            throw new IllegalArgumentException("Invalid navigation URI scheme or authority");
        String[] path = uri.getRawPath().split("/", -1);
        if (path.length == 2 && path[0].isEmpty() && uri.getRawQuery() == null)
            return QuizForgeNavigationLink.asset(decodePart(path[1]));
        if (path.length != 4 || !path[0].isEmpty() || uri.getRawQuery() == null
                || !uri.getRawQuery().matches("occurrence=[1-9][0-9]*"))
            throw new IllegalArgumentException("Invalid navigation target or occurrence");
        int occurrence;
        try { occurrence = Integer.parseInt(uri.getRawQuery().substring("occurrence=".length())); }
        catch (NumberFormatException error) {
            throw new IllegalArgumentException("Invalid navigation occurrence", error);
        }
        String assetId = decodePart(path[1]);
        String target = decodePart(path[3]);
        return switch (path[2]) {
            case "heading" -> QuizForgeNavigationLink.heading(assetId, target, occurrence);
            case "anchor" -> QuizForgeNavigationLink.anchor(assetId, target, occurrence);
            default -> throw new IllegalArgumentException("Unknown navigation target type");
        };
    }

    private String encodePart(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String decodePart(String raw) {
        StringBuilder result = new StringBuilder();
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        for (int index = 0; index < raw.length();) {
            if (raw.charAt(index) == '%') {
                if (index + 2 >= raw.length()) throw new IllegalArgumentException("Malformed percent encoding");
                int high = Character.digit(raw.charAt(index + 1), 16);
                int low = Character.digit(raw.charAt(index + 2), 16);
                if (high < 0 || low < 0) throw new IllegalArgumentException("Malformed percent encoding");
                encoded.write((high << 4) | low);
                index += 3;
            } else {
                appendUtf8(result, encoded);
                result.append(raw.charAt(index++));
            }
        }
        appendUtf8(result, encoded);
        return result.toString();
    }

    private void appendUtf8(StringBuilder result, ByteArrayOutputStream bytes) {
        if (bytes.size() == 0) return;
        try {
            result.append(StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes.toByteArray())));
        } catch (CharacterCodingException error) {
            throw new IllegalArgumentException("Malformed UTF-8 navigation target", error);
        }
        bytes.reset();
    }
}
