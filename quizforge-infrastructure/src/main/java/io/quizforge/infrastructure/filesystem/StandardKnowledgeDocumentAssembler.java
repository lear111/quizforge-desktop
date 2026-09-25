package io.quizforge.infrastructure.filesystem;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.quizforge.core.document.AssembledKnowledgeDocument;
import io.quizforge.core.port.KnowledgeDocumentAssembler;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.Node;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;

/** Adds QuizForge-owned identity to AI-produced semantic Markdown. */
public final class StandardKnowledgeDocumentAssembler implements KnowledgeDocumentAssembler {
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Parser MARKDOWN = Parser.builder()
            .includeSourceSpans(IncludeSourceSpans.BLOCKS).build();
    private final StandardKnowledgeDocumentV1 format = new StandardKnowledgeDocumentV1();

    @Override
    public String draftLanguage(String draft) {
        String normalized = normalize(draft);
        int end = frontMatterEnd(normalized);
        if (end < 0) return "zh-CN";
        try {
            JsonNode metadata = YAML.readTree(normalized.substring(4, end));
            String language = metadata == null ? "" : metadata.path("language").asText("").trim();
            if (language.isEmpty()) throw new IllegalArgumentException("Draft language is missing");
            return language;
        } catch (IOException error) {
            throw new IllegalArgumentException("Draft Front Matter is invalid", error);
        }
    }

    @Override
    public AssembledKnowledgeDocument assemble(String draft, String title, String language,
            String existingAssetId) {
        if (draft == null || title == null || title.isBlank() || language == null || language.isBlank()) {
            throw new IllegalArgumentException("Document content, title and language are required");
        }
        String assetId = existingAssetId == null ? newId("doc_") : existingAssetId;
        if (!assetId.matches("doc_[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("Existing document asset ID is invalid");
        }
        String normalized = normalize(draft);
        int end = frontMatterEnd(normalized);
        String body = end < 0 ? normalized : normalized.substring(end + 5);
        Node root = MARKDOWN.parse(body);
        Map<Integer, String> additions = new HashMap<>();
        Set<Integer> removed = new HashSet<>();
        for (Node node = root.getFirstChild(); node != null; node = node.getNext()) {
            if (node instanceof Heading heading && (heading.getLevel() == 2 || heading.getLevel() == 3)) {
                int line = heading.getSourceSpans().getLast().getLineIndex();
                additions.put(line, "<!-- qf:id=" + newId(heading.getLevel() == 2
                        ? "chapter_" : "section_") + " -->");
            } else if (node instanceof HtmlBlock block
                    && StandardKnowledgeDocumentV1.isIdComment(block.getLiteral())) {
                int first = block.getSourceSpans().getFirst().getLineIndex();
                int last = block.getSourceSpans().getLast().getLineIndex();
                for (int line = first; line <= last; line++) removed.add(line);
            }
        }
        String[] lines = body.split("\n", -1);
        List<String> assembledBody = new ArrayList<>();
        for (int line = 0; line < lines.length; line++) {
            if (!removed.contains(line)) assembledBody.add(lines[line]);
            if (additions.containsKey(line)) assembledBody.add(additions.get(line));
        }
        String joined = String.join("\n", assembledBody);
        if (!joined.endsWith("\n")) joined += "\n";
        String markdown = "---\n"
                + "quizforge_format: \"study-document\"\n"
                + "schema_version: \"1.0\"\n"
                + "quizforge_id: " + quoted(assetId) + "\n"
                + "title: " + quoted(title.trim()) + "\n"
                + "language: " + quoted(language.trim()) + "\n"
                + "---\n" + joined;
        var parsed = format.parseIfStandard(markdown).orElseThrow(() ->
                new IllegalArgumentException("Assembled document is not Standard Knowledge Document v1"));
        return new AssembledKnowledgeDocument(parsed.assetId(), parsed.contentId(),
                parsed.title(), parsed.schemaVersion(), markdown);
    }

    private int frontMatterEnd(String text) {
        if (!text.startsWith("---\n")) return -1;
        int end = text.indexOf("\n---\n", 4);
        if (end < 0) throw new IllegalArgumentException("Draft Front Matter is unclosed");
        return end;
    }

    private String quoted(String value) {
        try { return JSON.writeValueAsString(value); }
        catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }

    private String newId(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }

    private String normalize(String text) {
        if (text == null) throw new IllegalArgumentException("Document is missing");
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }
}
