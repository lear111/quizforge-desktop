package io.quizforge.infrastructure.filesystem;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.commonmark.node.Code;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Node;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;

/** The file-backed study-document v1 contract; independent of the legacy AI draft format. */
public final class StandardKnowledgeDocumentV1 {
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());
    private static final Pattern ID_COMMENT = Pattern.compile("(?s)\\s*<!--\\s*qf:id=([A-Za-z0-9_-]+)\\s*-->\\s*");
    private static final Parser MARKDOWN = Parser.builder().build();
    private static final int MAX_FRONT_MATTER_CHARS = 64 * 1024;

    public record Parsed(String assetId, String contentId, String title, String schemaVersion) { }

    /** Returns empty for ordinary Markdown; a declared but invalid document is an error. */
    public Optional<Parsed> parseIfStandard(String source) {
        if (source == null) throw new IllegalArgumentException("Document is missing");
        String normalized = normalize(source);
        if (!normalized.startsWith("---\n")) return Optional.empty();
        int end = normalized.indexOf("\n---\n", 4);
        if (end < 0) {
            if (normalized.contains("quizforge_format")) {
                throw new IllegalArgumentException("Unclosed Front Matter");
            }
            return Optional.empty();
        }
        if (end - 4 > MAX_FRONT_MATTER_CHARS) throw new IllegalArgumentException("Front Matter is too large");
        JsonNode front;
        try {
            front = YAML.readTree(normalized.substring(4, end));
        } catch (IOException error) {
            throw new IllegalArgumentException("Invalid Front Matter", error);
        }
        if (front == null || !front.isObject()
                || !"study-document".equals(value(front, "quizforge_format"))) return Optional.empty();
        String version = required(front, "schema_version");
        if (!"1.0".equals(version)) throw new IllegalArgumentException("Unsupported schema_version");
        String assetId = required(front, "quizforge_id");
        if (!assetId.matches("doc_[A-Za-z0-9_-]+")) throw new IllegalArgumentException("Invalid quizforge_id");
        String title = required(front, "title");
        String language = required(front, "language");
        String body = normalized.substring(end + 5);
        validateBody(body);
        return Optional.of(new Parsed(assetId, contentId(version, title, language, body), title, version));
    }

    private void validateBody(String body) {
        Node root = MARKDOWN.parse(body);
        int h1 = 0;
        int chapters = 0;
        int sectionsInChapter = 0;
        boolean inSection = false;
        boolean sectionHasContent = false;
        Set<String> ids = new HashSet<>();
        Node consumedId = null;
        for (Node node = root.getFirstChild(); node != null; node = node.getNext()) {
            if (node == consumedId) continue;
            if (node instanceof Heading heading) {
                if (inSection && !sectionHasContent) throw new IllegalArgumentException("EMPTY_SECTION");
                inSection = false;
                sectionHasContent = false;
                switch (heading.getLevel()) {
                    case 1 -> {
                        if (++h1 != 1 || chapters > 0 || textOf(heading).isBlank()) {
                            throw new IllegalArgumentException("INVALID_H1");
                        }
                    }
                    case 2 -> {
                        if (h1 != 1 || chapters > 0 && sectionsInChapter == 0
                                || textOf(heading).isBlank()) {
                            throw new IllegalArgumentException("INVALID_CHAPTER");
                        }
                        chapters++;
                        sectionsInChapter = 0;
                        requireId(heading, "chapter_", ids);
                        consumedId = heading.getNext();
                    }
                    case 3 -> {
                        if (chapters == 0 || textOf(heading).isBlank()) {
                            throw new IllegalArgumentException("INVALID_SECTION");
                        }
                        sectionsInChapter++;
                        requireId(heading, "section_", ids);
                        consumedId = heading.getNext();
                        inSection = true;
                    }
                    default -> throw new IllegalArgumentException("INVALID_HEADING_LEVEL");
                }
            } else if (node instanceof HtmlBlock block
                    && ID_COMMENT.matcher(block.getLiteral()).matches()) {
                throw new IllegalArgumentException("UNATTACHED_CONTENT_ID");
            } else if (inSection && hasLearningContent(node)) {
                sectionHasContent = true;
            }
        }
        if (inSection && !sectionHasContent) throw new IllegalArgumentException("EMPTY_SECTION");
        if (h1 != 1 || chapters == 0 || sectionsInChapter == 0) {
            throw new IllegalArgumentException("INVALID_DOCUMENT_STRUCTURE");
        }
    }

    private void requireId(Heading heading, String prefix, Set<String> ids) {
        Node next = heading.getNext();
        String literal = next instanceof HtmlBlock block ? block.getLiteral()
                : next instanceof HtmlInline inline ? inline.getLiteral() : "";
        Matcher match = ID_COMMENT.matcher(literal);
        if (!match.matches() || !match.group(1).startsWith(prefix)) {
            throw new IllegalArgumentException("MISSING_" + prefix.toUpperCase() + "ID");
        }
        if (!ids.add(match.group(1))) throw new IllegalArgumentException("DUPLICATE_CONTENT_ID");
    }

    private boolean hasLearningContent(Node node) {
        if (node instanceof Text text) return !text.getLiteral().isBlank();
        if (node instanceof Code code) return !code.getLiteral().isBlank();
        if (node instanceof FencedCodeBlock block) return !block.getLiteral().isBlank();
        if (node instanceof IndentedCodeBlock block) return !block.getLiteral().isBlank();
        if (node instanceof HtmlBlock block) {
            return !block.getLiteral().isBlank() && !ID_COMMENT.matcher(block.getLiteral()).matches();
        }
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            if (hasLearningContent(child)) return true;
        }
        return false;
    }

    private String textOf(Node node) {
        StringBuilder text = new StringBuilder();
        appendText(node, text);
        return text.toString();
    }

    private void appendText(Node node, StringBuilder text) {
        if (node instanceof Text value) text.append(value.getLiteral());
        else if (node instanceof Code value) text.append(value.getLiteral());
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) appendText(child, text);
    }

    private String contentId(String version, String title, String language, String body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update("quizforge-study-document-canonical-v1\0".getBytes(StandardCharsets.UTF_8));
            for (String field : new String[] {version, title, language, body}) {
                byte[] utf8 = field.getBytes(StandardCharsets.UTF_8);
                digest.update(ByteBuffer.allocate(4).putInt(utf8.length).array());
                digest.update(utf8);
            }
            return "qfd:v1:" + HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private String required(JsonNode node, String name) {
        String value = value(node, name);
        if (value.isBlank()) throw new IllegalArgumentException("Missing " + name);
        return value;
    }

    private String value(JsonNode node, String name) {
        JsonNode value = node.path(name);
        return value.isTextual() ? normalize(value.asText()).trim() : "";
    }

    private String normalize(String value) {
        return Normalizer.normalize(value.replace("\r\n", "\n").replace('\r', '\n'), Normalizer.Form.NFC);
    }
}
