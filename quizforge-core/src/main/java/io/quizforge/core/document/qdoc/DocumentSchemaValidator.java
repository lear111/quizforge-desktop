package io.quizforge.core.document.qdoc;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Applies the selected template's schema rather than UI-specific heading rules. */
public final class DocumentSchemaValidator {
    private final Map<QDocDocument.TemplateRef, DocumentTemplate> templates;

    public DocumentSchemaValidator() { this(Set.of(DocumentTemplate.GENERAL_KNOWLEDGE)); }

    public DocumentSchemaValidator(Set<DocumentTemplate> supported) {
        var byReference = new java.util.HashMap<QDocDocument.TemplateRef, DocumentTemplate>();
        for (DocumentTemplate template : supported) {
            if (byReference.putIfAbsent(template.reference(), template) != null)
                throw new IllegalArgumentException("Duplicate document template");
        }
        templates = Map.copyOf(byReference);
    }

    public void validate(QDocDocument document) {
        if (document == null || !"quizforge-document".equals(document.format())
                || !"1.0".equals(document.schemaVersion())) fail("UNSUPPORTED_DOCUMENT_FORMAT");
        DocumentTemplate template = templates.get(document.template());
        if (template == null) fail("UNSUPPORTED_TEMPLATE");
        if (document.id() == null || !document.id().matches("doc_[A-Za-z0-9_-]+")) fail("INVALID_DOCUMENT_ID");
        if (blank(document.title()) || blank(document.language())) fail("MISSING_DOCUMENT_METADATA");
        if (document.content().isEmpty()) fail("MISSING_CHAPTER");
        Set<String> ids = new HashSet<>();
        for (DocumentNode node : document.content()) {
            if (node == null || !template.schema().rootTypes().contains(node.type())) fail("INVALID_ROOT_CHILD");
            validateNode(node, template.schema(), ids);
        }
    }

    private void validateNode(DocumentNode node, DocumentSchema schema, Set<String> ids) {
        if (node.type() == null || node.id() == null || !node.id().matches("[A-Za-z]+_[A-Za-z0-9_-]+")
                || blank(node.title())) fail("INVALID_NODE");
        String prefix = node.type().name().toLowerCase(java.util.Locale.ROOT) + "_";
        if (!node.id().startsWith(prefix)) fail("INVALID_NODE_ID");
        if (!ids.add(node.id())) fail("DUPLICATE_NODE_ID");
        boolean required = false;
        for (DocumentElement child : node.children()) {
            if (child instanceof DocumentNode nested) {
                if (nested.type() == null || !schema.nodeChildren().getOrDefault(node.type(), Set.of()).contains(nested.type()))
                    fail("INVALID_NODE_RELATION");
                if (nested.type() == schema.requiredChild().get(node.type())) required = true;
                validateNode(nested, schema, ids);
            } else if (child instanceof ContentBlock block) {
                if (!schema.blockParents().contains(node.type())) fail("INVALID_BLOCK_PARENT");
                validateBlock(block);
            } else fail("UNKNOWN_DOCUMENT_ELEMENT");
        }
        if (schema.requiredChild().containsKey(node.type()) && !required) fail("MISSING_REQUIRED_CHILD");
    }

    private void validateBlock(ContentBlock block) {
        if (block.type() == null) fail("UNKNOWN_BLOCK_TYPE");
        boolean list = block.type() == ContentBlockType.BULLET_LIST || block.type() == ContentBlockType.ORDERED_LIST;
        if (list) {
            if (block.items().isEmpty() || block.items().stream().anyMatch(this::blank)
                    || block.text() != null || block.language() != null) fail("INVALID_LIST_BLOCK");
        } else if (blank(block.text()) || !block.items().isEmpty()
                || block.type() != ContentBlockType.CODE_BLOCK && block.language() != null) {
            fail("INVALID_TEXT_BLOCK");
        }
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
    private static void fail(String code) { throw new IllegalArgumentException(code); }
}
