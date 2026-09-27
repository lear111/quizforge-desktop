package io.quizforge.infrastructure.filesystem;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.quizforge.core.document.registered.AddressableMarkdownBlock;
import io.quizforge.core.document.registered.MarkdownBlockType;
import io.quizforge.core.document.registered.MarkdownSourceRange;
import io.quizforge.core.document.registered.NamedMarkdownAnchor;
import io.quizforge.core.document.registered.RegisteredMarkdownDocument;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.BulletList;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SourceSpan;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;

/** Parses registered Markdown without serializing the user's Markdown AST. */
public final class RegisteredMarkdownCodec {
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());
    private static final Parser MARKDOWN = Parser.builder()
            .includeSourceSpans(IncludeSourceSpans.BLOCKS).build();
    private static final Pattern ANCHOR = Pattern.compile("<!--\\s*qf:id=(node_[A-Za-z0-9_-]+)\\s*-->");
    private static final Pattern NAMED_ANCHOR = Pattern.compile("^\\s*<!--\\s*qf:anchor=(.*?)\\s*-->\\s*$");
    private static final Pattern TOP_LEVEL_KEY = Pattern.compile("^[^\\s#][^:]*:.*$");
    private static final int MAX_FRONT_MATTER = 64 * 1024;

    public record Prepared(String source, RegisteredMarkdownDocument document) { }

    public record PreparedAnchor(String source, RegisteredMarkdownDocument document,
            NamedMarkdownAnchor anchor) { }

    /** Reuses the same source-span binding rules for hand-written anchors in ordinary Markdown. */
    public List<NamedMarkdownAnchor> inspectAnchors(String source) {
        FrontMatter front = frontMatter(source);
        return analyzeAnchors(front == null ? source : front.body()).anchors();
    }

    public Optional<RegisteredMarkdownDocument> parseIfRegistered(String source, String relativePath) {
        FrontMatter front = frontMatter(source);
        if (front == null) return Optional.empty();
        JsonNode yaml = yaml(front);
        JsonNode quizforge = yaml.path("quizforge");
        if (quizforge.isMissingNode()) return Optional.empty();
        String assetId = registeredId(quizforge);
        int[] namespace = namespaceRange(source, front);
        BodyAnalysis body = analyze(front.body());
        AnchorAnalysis anchorAnalysis = analyzeAnchors(front.body());
        String title = title(yaml, body, relativePath);
        return Optional.of(new RegisteredMarkdownDocument(assetId,
                contentId(source, namespace), relativePath, title,
                body.addressed(), body.missing().size(), anchorAnalysis.anchors(),
                anchorAnalysis.errors()));
    }

    /** Inserts one named marker at a source-span-selected top-level block. */
    public PreparedAnchor prepareAnchor(String source, String relativePath, int bodyLine,
            int bodyColumn, String requestedName) {
        String name = NamedMarkdownAnchor.validateName(requestedName);
        FrontMatter front = frontMatter(source);
        JsonNode yaml = front == null ? null : yaml(front);
        if (yaml != null && yaml.has("quizforge_format")) {
            throw new IllegalArgumentException("Legacy study-document metadata has a different contract");
        }
        JsonNode quizforge = yaml == null ? null : yaml.path("quizforge");
        boolean registered = quizforge != null && !quizforge.isMissingNode();
        String assetId = registered ? registeredId(quizforge)
                : "doc_" + UUID.randomUUID().toString().replace("-", "");
        if (registered) namespaceRange(source, front);
        String body = front == null ? source : front.body();
        MarkdownSourceRange target = blockRanges(body).stream()
                .filter(range -> range.startLine() == bodyLine && range.startColumn() == bodyColumn)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("MARKDOWN_BLOCK_CHANGED"));
        AnchorAnalysis existing = analyzeAnchors(body);
        if (!existing.errors().isEmpty()) throw new IllegalArgumentException(existing.errors().getFirst());
        boolean alreadyThere = existing.anchors().stream().anyMatch(anchor ->
                anchor.name().equals(name) && target.equals(anchor.blockRange()));
        String eol = newline(source);
        String updatedBody = body;
        if (!alreadyThere) {
            List<Line> bodyLines = lines(body);
            int offset = bodyLines.get(bodyLine - 1).start();
            if (bodyLine > 1 && ANCHOR.matcher(bodyLines.get(bodyLine - 2).text().trim()).matches()) {
                offset = bodyLines.get(bodyLine - 2).start();
            }
            updatedBody = body.substring(0, offset) + "<!-- qf:anchor=" + name + " -->" + eol
                    + body.substring(offset);
        }
        String candidate;
        if (front == null) {
            candidate = "---" + eol + namespace(assetId, eol) + "---" + eol + updatedBody;
        } else {
            candidate = source.substring(0, front.yamlStart())
                    + source.substring(front.yamlStart(), front.closingStart())
                    + (registered ? "" : namespace(assetId, eol))
                    + source.substring(front.closingStart(), front.bodyStart()) + updatedBody;
        }
        RegisteredMarkdownDocument parsed = parseIfRegistered(candidate, relativePath)
                .orElseThrow(() -> new IllegalArgumentException("Anchor metadata was not readable"));
        NamedMarkdownAnchor created = parsed.anchors().stream()
                .filter(anchor -> anchor.name().equals(name) && !anchor.orphan()
                        && anchor.blockRange().startLine() == bodyLine + (alreadyThere ? 0 : 1))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("ORPHAN_ANCHOR"));
        return new PreparedAnchor(candidate, parsed, created);
    }

    /** Legacy 1.1 fixture writer; the current source-reference flow uses prepareAnchor. */
    @Deprecated
    public Prepared prepare(String source, String relativePath) {
        FrontMatter front = frontMatter(source);
        JsonNode yaml = front == null ? null : yaml(front);
        JsonNode quizforge = yaml == null ? null : yaml.path("quizforge");
        if (yaml != null && yaml.has("quizforge_format")) {
            throw new IllegalArgumentException("Legacy study-document metadata has a different contract");
        }
        boolean registered = quizforge != null && !quizforge.isMissingNode();
        String assetId = registered ? registeredId(quizforge)
                : "doc_" + UUID.randomUUID().toString().replace("-", "");
        if (registered) namespaceRange(source, front);

        String body = front == null ? source : front.body();
        BodyAnalysis analysis = analyze(body);
        if (analysis.addressed().isEmpty() && analysis.missing().isEmpty()) {
            throw new IllegalArgumentException("NO_ADDRESSABLE_MARKDOWN_BLOCK");
        }
        List<Insertion> insertions = new ArrayList<>();
        String eol = newline(source);
        for (MissingBlock missing : analysis.missing()) {
            String nodeId = "node_" + UUID.randomUUID().toString().replace("-", "");
            insertions.add(new Insertion(missing.offset(), "<!-- qf:id=" + nodeId + " -->" + eol));
        }
        StringBuilder addressedBody = new StringBuilder(body);
        insertions.sort((a, b) -> Integer.compare(b.offset(), a.offset()));
        for (Insertion insertion : insertions) addressedBody.insert(insertion.offset(), insertion.text());

        String candidate;
        if (front == null) {
            candidate = "---" + eol + namespace(assetId, eol) + "---" + eol + addressedBody;
        } else {
            String prefix = source.substring(0, front.yamlStart());
            String userYaml = source.substring(front.yamlStart(), front.closingStart());
            candidate = prefix + userYaml + (registered ? "" : namespace(assetId, eol))
                    + source.substring(front.closingStart(), front.bodyStart()) + addressedBody;
        }
        RegisteredMarkdownDocument document = parseIfRegistered(candidate, relativePath)
                .orElseThrow(() -> new IllegalArgumentException("Registration metadata was not readable"));
        if (document.unaddressedBlockCount() != 0) {
            throw new IllegalArgumentException("UNADDRESSED_BLOCK_AFTER_REGISTRATION");
        }
        return new Prepared(candidate, document);
    }

    private String registeredId(JsonNode quizforge) {
        if (!quizforge.isObject() || !"document".equals(text(quizforge, "format"))
                || !"1".equals(text(quizforge, "version"))) {
            throw new IllegalArgumentException("INVALID_QUIZFORGE_METADATA");
        }
        String id = text(quizforge, "assetId");
        if (!id.matches("doc_[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("INVALID_QUIZFORGE_ASSET_ID");
        }
        return id;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isValueNode() && !value.isNull() ? value.asText().trim() : "";
    }

    private String title(JsonNode yaml, BodyAnalysis body, String path) {
        String title = text(yaml, "title");
        if (!title.isBlank()) return title;
        if (!body.firstHeading().isBlank()) return body.firstHeading();
        String file = path.substring(path.lastIndexOf('/') + 1);
        return file.toLowerCase(java.util.Locale.ROOT).endsWith(".md")
                ? file.substring(0, file.length() - 3) : file;
    }

    private BodyAnalysis analyze(String body) {
        Node root = MARKDOWN.parse(body);
        List<Line> lines = lines(body);
        Map<Node, String> anchors = new HashMap<>();
        Set<String> ids = new HashSet<>();
        for (Node node = root.getFirstChild(); node != null; node = node.getNext()) {
            if (node instanceof HtmlBlock html) {
                Matcher marker = ANCHOR.matcher(html.getLiteral().trim());
                if (marker.matches()) {
                    if (!ids.add(marker.group(1))) throw new IllegalArgumentException("DUPLICATE_NODE_ID");
                    anchors.put(node, marker.group(1));
                }
            }
        }
        List<AddressableMarkdownBlock> addressed = new ArrayList<>();
        List<MissingBlock> missing = new ArrayList<>();
        String firstHeading = "";
        for (Node node = root.getFirstChild(); node != null; node = node.getNext()) {
            MarkdownBlockType type = type(node);
            if (type == null) continue;
            List<SourceSpan> spans = node.getSourceSpans();
            if (spans.isEmpty()) throw new IllegalArgumentException("UNSAFE_MARKDOWN_SOURCE_POSITION");
            SourceSpan first = spans.getFirst();
            SourceSpan last = spans.getLast();
            int lineIndex = first.getLineIndex();
            if (lineIndex < 0 || lineIndex >= lines.size()) {
                throw new IllegalArgumentException("UNSAFE_MARKDOWN_SOURCE_POSITION");
            }
            Line line = lines.get(lineIndex);
            if (first.getColumnIndex() > line.text().length()
                    || !line.text().substring(0, first.getColumnIndex()).isBlank()) {
                throw new IllegalArgumentException("UNSAFE_MARKDOWN_SOURCE_POSITION");
            }
            String display = line.text().strip();
            if (display.length() > 160) display = display.substring(0, 160);
            if (type == MarkdownBlockType.HEADING && firstHeading.isBlank()) {
                firstHeading = display.replaceFirst("^#{1,6}\\s+", "").strip();
            }
            Node previous = node.getPrevious();
            String id = previous == null ? null : anchors.get(previous);
            if (id != null) {
                List<SourceSpan> markerSpans = previous.getSourceSpans();
                if (markerSpans.isEmpty() || markerSpans.getLast().getLineIndex() != lineIndex - 1) {
                    throw new IllegalArgumentException("UNATTACHED_NODE_ID");
                }
                anchors.remove(previous);
                addressed.add(new AddressableMarkdownBlock(id, type, display,
                        new MarkdownSourceRange(lineIndex + 1, first.getColumnIndex() + 1,
                                last.getLineIndex() + 1, last.getColumnIndex() + last.getLength() + 1)));
            } else {
                missing.add(new MissingBlock(line.start()));
            }
        }
        if (!anchors.isEmpty()) throw new IllegalArgumentException("UNATTACHED_NODE_ID");
        return new BodyAnalysis(addressed, missing, firstHeading);
    }

    private List<MarkdownSourceRange> blockRanges(String body) {
        List<MarkdownSourceRange> result = new ArrayList<>();
        Node root = MARKDOWN.parse(body);
        for (Node node = root.getFirstChild(); node != null; node = node.getNext()) {
            if (type(node) == null) continue;
            if (node.getSourceSpans().isEmpty()) throw new IllegalArgumentException("UNSAFE_MARKDOWN_SOURCE_POSITION");
            SourceSpan first = node.getSourceSpans().getFirst();
            SourceSpan last = node.getSourceSpans().getLast();
            result.add(new MarkdownSourceRange(first.getLineIndex() + 1, first.getColumnIndex() + 1,
                    last.getLineIndex() + 1, last.getColumnIndex() + last.getLength() + 1));
        }
        return result;
    }

    private AnchorAnalysis analyzeAnchors(String body) {
        List<Line> lines = lines(body);
        List<MarkdownSourceRange> blocks = blockRanges(body);
        List<MarkdownSourceRange> codeRanges = new ArrayList<>();
        Node root = MARKDOWN.parse(body);
        for (Node node = root.getFirstChild(); node != null; node = node.getNext()) {
            if (!(node instanceof FencedCodeBlock || node instanceof IndentedCodeBlock)
                    || node.getSourceSpans().isEmpty()) continue;
            SourceSpan first = node.getSourceSpans().getFirst();
            SourceSpan last = node.getSourceSpans().getLast();
            codeRanges.add(new MarkdownSourceRange(first.getLineIndex() + 1,
                    first.getColumnIndex() + 1, last.getLineIndex() + 1,
                    last.getColumnIndex() + last.getLength() + 1));
        }
        Map<Integer, MarkdownSourceRange> byLine = new HashMap<>();
        blocks.forEach(range -> byLine.put(range.startLine(), range));
        List<NamedMarkdownAnchor> found = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Map<String, Integer> counts = new HashMap<>();
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index).text();
            if (!line.contains("qf:anchor=")) continue;
            final int sourceLine = index + 1;
            if (codeRanges.stream().anyMatch(range -> range.startLine() <= sourceLine
                    && range.endLine() >= sourceLine)) continue;
            Matcher marker = NAMED_ANCHOR.matcher(line);
            if (!marker.matches()) {
                errors.add("MALFORMED_ANCHOR at line " + (index + 1));
                continue;
            }
            String name;
            try { name = NamedMarkdownAnchor.validateName(marker.group(1)); }
            catch (IllegalArgumentException error) {
                errors.add(error.getMessage() + " at line " + (index + 1));
                continue;
            }
            int occurrence = counts.merge(name, 1, Integer::sum);
            int next = index + 1;
            while (next < lines.size() && (lines.get(next).text().isBlank()
                    || NAMED_ANCHOR.matcher(lines.get(next).text()).matches()
                    || ANCHOR.matcher(lines.get(next).text().trim()).matches())) next++;
            MarkdownSourceRange block = byLine.get(next + 1);
            if (block == null) errors.add("ORPHAN_ANCHOR " + name + " #" + occurrence);
            found.add(new NamedMarkdownAnchor(name, occurrence, block));
        }
        return new AnchorAnalysis(found, errors);
    }


    private MarkdownBlockType type(Node node) {
        if (node instanceof Heading) return MarkdownBlockType.HEADING;
        if (node instanceof Paragraph) return MarkdownBlockType.PARAGRAPH;
        if (node instanceof BulletList || node instanceof OrderedList) return MarkdownBlockType.LIST;
        if (node instanceof BlockQuote) return MarkdownBlockType.BLOCK_QUOTE;
        if (node instanceof FencedCodeBlock) return MarkdownBlockType.FENCED_CODE;
        if (node instanceof IndentedCodeBlock) return MarkdownBlockType.INDENTED_CODE;
        return null;
    }

    private FrontMatter frontMatter(String source) {
        if (source == null) throw new IllegalArgumentException("Markdown source is required");
        List<Line> lines = lines(source);
        if (lines.isEmpty() || !lines.getFirst().text().equals("---")
                || lines.getFirst().ending().isEmpty()) return null;
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).text().equals("---")) {
                int start = lines.getFirst().end();
                int close = lines.get(i).start();
                if (close - start > MAX_FRONT_MATTER) {
                    throw new IllegalArgumentException("Front Matter is too large");
                }
                return new FrontMatter(start, close, lines.get(i).end(),
                        source.substring(lines.get(i).end()), source.substring(start, close));
            }
        }
        if (source.contains("quizforge:")) throw new IllegalArgumentException("Unclosed QuizForge Front Matter");
        return null;
    }

    private JsonNode yaml(FrontMatter front) {
        if (front.yamlText().isBlank()) return YAML.createObjectNode();
        try {
            JsonNode parsed = YAML.readTree(front.yamlText());
            if (parsed == null || !parsed.isObject()) throw new IllegalArgumentException("Invalid Front Matter");
            return parsed;
        } catch (IOException error) {
            throw new IllegalArgumentException("Invalid Front Matter", error);
        }
    }

    private int[] namespaceRange(String source, FrontMatter front) {
        List<Line> yamlLines = lines(source.substring(front.yamlStart(), front.closingStart()));
        int start = -1;
        int end = -1;
        for (Line line : yamlLines) {
            if (line.text().matches("quizforge\\s*:\\s*(?:#.*)?")) {
                if (start >= 0) throw new IllegalArgumentException("DUPLICATE_QUIZFORGE_METADATA");
                start = front.yamlStart() + line.start();
            } else if (start >= 0 && end < 0 && TOP_LEVEL_KEY.matcher(line.text()).matches()) {
                end = front.yamlStart() + line.start();
            }
        }
        if (start < 0) throw new IllegalArgumentException("UNSAFE_QUIZFORGE_METADATA_LAYOUT");
        if (end < 0) end = front.closingStart();
        return new int[] {start, end};
    }

    private String contentId(String source, int[] namespace) {
        String withoutNamespace = source.substring(0, namespace[0]) + source.substring(namespace[1]);
        String canonical = Normalizer.normalize(withoutNamespace.replace("\r\n", "\n")
                .replace('\r', '\n'), Normalizer.Form.NFC);
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update("quizforge-registered-markdown-v1\0".getBytes(StandardCharsets.UTF_8));
            return "qfd:v2:" + HexFormat.of().formatHex(sha.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private String namespace(String assetId, String eol) {
        return "quizforge:" + eol + "  format: document" + eol + "  version: 1" + eol
                + "  assetId: " + assetId + eol;
    }

    private String newline(String source) {
        int next = source.indexOf('\n');
        if (next >= 1 && source.charAt(next - 1) == '\r') return "\r\n";
        if (next >= 0) return "\n";
        return source.indexOf('\r') >= 0 ? "\r" : "\n";
    }

    private List<Line> lines(String source) {
        List<Line> result = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c != '\r' && c != '\n') continue;
            int end = i + 1;
            if (c == '\r' && end < source.length() && source.charAt(end) == '\n') end++;
            result.add(new Line(start, end, source.substring(start, i), source.substring(i, end)));
            start = end;
            i = end - 1;
        }
        result.add(new Line(start, source.length(), source.substring(start), ""));
        return result;
    }

    private record FrontMatter(int yamlStart, int closingStart, int bodyStart, String body,
            String yamlText) { }
    private record Line(int start, int end, String text, String ending) { }
    private record MissingBlock(int offset) { }
    private record Insertion(int offset, String text) { }
    private record BodyAnalysis(List<AddressableMarkdownBlock> addressed,
            List<MissingBlock> missing, String firstHeading) { }
    private record AnchorAnalysis(List<NamedMarkdownAnchor> anchors, List<String> errors) { }
}
