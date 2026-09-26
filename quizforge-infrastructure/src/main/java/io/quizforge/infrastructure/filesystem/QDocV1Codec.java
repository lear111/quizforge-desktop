package io.quizforge.infrastructure.filesystem;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quizforge.core.document.qdoc.ContentBlock;
import io.quizforge.core.document.qdoc.ContentBlockType;
import io.quizforge.core.document.qdoc.DocumentElement;
import io.quizforge.core.document.qdoc.DocumentNode;
import io.quizforge.core.document.qdoc.DocumentNodeType;
import io.quizforge.core.document.qdoc.DocumentSchemaValidator;
import io.quizforge.core.document.qdoc.QDocDocument;
import io.quizforge.core.port.QDocCodec;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/** Strict JSON boundary for QDoc v1; content IDs hash the model, never JSON bytes. */
public final class QDocV1Codec implements QDocCodec {
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private final DocumentSchemaValidator validator = new DocumentSchemaValidator();

    public QDocDocument parse(String source) {
        try {
            JsonNode root = JSON.readTree(source);
            object(root, Set.of("format", "schemaVersion", "id", "template", "title", "language", "content"));
            JsonNode template = root.get("template");
            object(template, Set.of("id", "version"));
            JsonNode content = array(root.get("content"));
            List<DocumentNode> chapters = new ArrayList<>();
            for (JsonNode child : content) chapters.add(node(child));
            QDocDocument document = new QDocDocument(string(root, "format"), string(root, "schemaVersion"),
                    string(root, "id"), new QDocDocument.TemplateRef(string(template, "id"),
                    string(template, "version")), string(root, "title"), string(root, "language"), chapters);
            validator.validate(document);
            return document;
        } catch (IOException error) {
            throw new IllegalArgumentException("Invalid QDoc JSON", error);
        }
    }

    public String write(QDocDocument document) {
        validator.validate(document);
        ObjectNode root = JSON.createObjectNode();
        root.put("format", document.format());
        root.put("schemaVersion", document.schemaVersion());
        root.put("id", document.id());
        ObjectNode template = root.putObject("template");
        template.put("id", document.template().id());
        template.put("version", document.template().version());
        root.put("title", document.title());
        root.put("language", document.language());
        ArrayNode content = root.putArray("content");
        document.content().forEach(node -> content.add(encode(node)));
        try { return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(root) + "\n"; }
        catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }

    public String contentId(QDocDocument document) {
        validator.validate(document);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            value(out, "qdoc-canonical-v1");
            value(out, document.format());
            value(out, document.schemaVersion());
            value(out, document.template().id());
            value(out, document.template().version());
            value(out, document.title());
            value(out, document.language());
            out.writeInt(document.content().size());
            for (DocumentNode node : document.content()) canonical(out, node);
            out.flush();
            return "qfd:v1:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(bytes.toByteArray()));
        } catch (IOException | NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private void canonical(DataOutputStream out, DocumentElement element) throws IOException {
        if (element instanceof DocumentNode node) {
            value(out, "node"); value(out, node.type().name()); value(out, node.id()); value(out, node.title());
            out.writeInt(node.children().size());
            for (DocumentElement child : node.children()) canonical(out, child);
        } else if (element instanceof ContentBlock block) {
            value(out, "block"); value(out, block.type().name()); value(out, block.text());
            value(out, block.language());
            out.writeInt(block.items().size());
            for (String item : block.items()) value(out, item);
        }
    }

    private void value(DataOutputStream out, String text) throws IOException {
        if (text == null) { out.writeInt(-1); return; }
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private DocumentNode node(JsonNode value) {
        object(value, Set.of("id", "type", "title", "children"));
        DocumentNodeType type = DocumentNodeType.valueOf(string(value, "type"));
        List<DocumentElement> children = new ArrayList<>();
        for (JsonNode child : array(value.get("children"))) {
            if (!child.isObject()) throw new IllegalArgumentException("Invalid document element");
            children.add(child.has("id") ? node(child) : block(child));
        }
        return new DocumentNode(string(value, "id"), type, string(value, "title"), children);
    }

    private ContentBlock block(JsonNode value) {
        object(value, Set.of("type", "text", "items", "language"));
        ContentBlockType type = ContentBlockType.valueOf(string(value, "type"));
        String text = optional(value, "text");
        String language = optional(value, "language");
        List<String> items = new ArrayList<>();
        if (value.has("items")) {
            for (JsonNode item : array(value.get("items"))) {
                if (!item.isTextual()) throw new IllegalArgumentException("Invalid list item");
                items.add(item.textValue());
            }
        }
        return new ContentBlock(type, text, items, language);
    }

    private ObjectNode encode(DocumentNode node) {
        ObjectNode json = JSON.createObjectNode();
        json.put("id", node.id()); json.put("type", node.type().name()); json.put("title", node.title());
        ArrayNode children = json.putArray("children");
        for (DocumentElement child : node.children()) children.add(child instanceof DocumentNode nested
                ? encode(nested) : encode((ContentBlock) child));
        return json;
    }

    private ObjectNode encode(ContentBlock block) {
        ObjectNode json = JSON.createObjectNode();
        json.put("type", block.type().name());
        if (block.text() != null) json.put("text", block.text());
        if (!block.items().isEmpty()) {
            ArrayNode items = json.putArray("items");
            block.items().forEach(items::add);
        }
        if (block.language() != null) json.put("language", block.language());
        return json;
    }

    private void object(JsonNode value, Set<String> allowed) {
        if (value == null || !value.isObject()) throw new IllegalArgumentException("Expected QDoc object");
        value.fieldNames().forEachRemaining(name -> {
            if (!allowed.contains(name)) throw new IllegalArgumentException("Unknown QDoc field: " + name);
        });
    }

    private JsonNode array(JsonNode value) {
        if (value == null || !value.isArray()) throw new IllegalArgumentException("Expected QDoc array");
        return value;
    }

    private String string(JsonNode object, String name) {
        JsonNode value = object.get(name);
        if (value == null || !value.isTextual()) throw new IllegalArgumentException("Missing QDoc text: " + name);
        return value.textValue();
    }

    private String optional(JsonNode object, String name) {
        JsonNode value = object.get(name);
        if (value == null) return null;
        if (!value.isTextual()) throw new IllegalArgumentException("Invalid QDoc text: " + name);
        return value.textValue();
    }
}
