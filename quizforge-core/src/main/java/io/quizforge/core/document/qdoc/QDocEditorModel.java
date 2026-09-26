package io.quizforge.core.document.qdoc;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** In-memory edits of a QDoc. Paths address positions only within this edit session. */
public final class QDocEditorModel {
    private final DocumentSchema schema;
    private QDocDocument document;
    private boolean dirty;

    public QDocEditorModel(QDocDocument document, DocumentTemplate template) {
        if (!document.template().equals(template.reference())) throw new IllegalArgumentException("UNSUPPORTED_TEMPLATE");
        this.document = document;
        this.schema = template.schema();
    }

    public QDocDocument document() { return document; }
    public boolean dirty() { return dirty; }
    public Set<DocumentNodeType> allowedNodes(List<Integer> parent) {
        return schema.allowedNodes(parent.isEmpty() ? null : node(parent).type());
    }
    public Set<ContentBlockType> allowedBlocks(List<Integer> parent) {
        return schema.allowedBlocks(parent.isEmpty() ? null : node(parent).type());
    }

    public void setDocumentTitle(String title) {
        document = new QDocDocument(document.format(), document.schemaVersion(), document.id(),
                document.template(), title, document.language(), document.content());
        dirty = true;
    }

    public void addNode(List<Integer> parent, DocumentNodeType type) {
        if (!allowedNodes(parent).contains(type)) throw new IllegalArgumentException("INVALID_NODE_RELATION");
        append(parent, new DocumentNode(type.name().toLowerCase(java.util.Locale.ROOT) + "_"
                + UUID.randomUUID(), type, "New " + display(type), List.of()));
    }

    public void addBlock(List<Integer> parent, ContentBlockType type) {
        if (!allowedBlocks(parent).contains(type)) throw new IllegalArgumentException("INVALID_BLOCK_PARENT");
        ContentBlock block = type == ContentBlockType.BULLET_LIST || type == ContentBlockType.ORDERED_LIST
                ? ContentBlock.list(type, List.of("New item")) : ContentBlock.text(type, "New content");
        append(parent, block);
    }

    public void setNodeTitle(List<Integer> path, String title) {
        DocumentNode old = node(path);
        replace(path, new DocumentNode(old.id(), old.type(), title, old.children()));
    }

    public void setBlockText(List<Integer> path, String text) {
        ContentBlock old = block(path);
        replace(path, new ContentBlock(old.type(), text, old.items(), old.language()));
    }

    public void setCodeLanguage(List<Integer> path, String language) {
        ContentBlock old = block(path);
        if (old.type() != ContentBlockType.CODE_BLOCK) throw new IllegalArgumentException("NOT_CODE_BLOCK");
        replace(path, new ContentBlock(old.type(), old.text(), old.items(), language.isBlank() ? null : language));
    }

    public void addListItem(List<Integer> path) {
        ContentBlock old = block(path);
        List<String> items = new ArrayList<>(list(old));
        items.add("New item");
        replace(path, ContentBlock.list(old.type(), items));
    }

    public void setListItem(List<Integer> path, int index, String value) {
        ContentBlock old = block(path);
        List<String> items = new ArrayList<>(list(old));
        items.set(index, value);
        replace(path, ContentBlock.list(old.type(), items));
    }

    public void delete(List<Integer> path) {
        if (path.isEmpty()) throw new IllegalArgumentException("Cannot delete document");
        List<Integer> parent = path.subList(0, path.size() - 1);
        List<DocumentElement> children = new ArrayList<>(children(parent));
        children.remove(path.getLast().intValue());
        setChildren(parent, children);
    }

    public DocumentElement element(List<Integer> path) {
        if (path.isEmpty()) throw new IllegalArgumentException("Document has no element path");
        DocumentElement current = document.content().get(path.getFirst());
        for (int i = 1; i < path.size(); i++) current = ((DocumentNode) current).children().get(path.get(i));
        return current;
    }

    public boolean hasContent() {
        return document.content().stream().anyMatch(this::containsBlock);
    }

    private boolean containsBlock(DocumentElement element) {
        return element instanceof ContentBlock || element instanceof DocumentNode node
                && node.children().stream().anyMatch(this::containsBlock);
    }

    private DocumentNode node(List<Integer> path) { return (DocumentNode) element(path); }
    private ContentBlock block(List<Integer> path) { return (ContentBlock) element(path); }
    private List<String> list(ContentBlock block) {
        if (block.type() != ContentBlockType.BULLET_LIST && block.type() != ContentBlockType.ORDERED_LIST)
            throw new IllegalArgumentException("NOT_LIST_BLOCK");
        return block.items();
    }

    private String display(DocumentNodeType type) {
        return type.name().substring(0, 1) + type.name().substring(1).toLowerCase(java.util.Locale.ROOT);
    }

    private List<DocumentElement> children(List<Integer> parent) {
        return parent.isEmpty() ? new ArrayList<>(document.content()) : node(parent).children();
    }

    private void append(List<Integer> parent, DocumentElement child) {
        List<DocumentElement> children = new ArrayList<>(children(parent));
        children.add(child);
        setChildren(parent, children);
    }

    private void setChildren(List<Integer> parent, List<DocumentElement> children) {
        if (parent.isEmpty()) {
            List<DocumentNode> roots = children.stream().map(DocumentNode.class::cast).toList();
            document = new QDocDocument(document.format(), document.schemaVersion(), document.id(),
                    document.template(), document.title(), document.language(), roots);
            dirty = true;
        } else {
            DocumentNode old = node(parent);
            replace(parent, new DocumentNode(old.id(), old.type(), old.title(), children));
        }
    }

    private void replace(List<Integer> path, DocumentElement replacement) {
        List<Integer> parent = path.subList(0, path.size() - 1);
        List<DocumentElement> children = new ArrayList<>(children(parent));
        children.set(path.getLast(), replacement);
        setChildren(parent, children);
    }
}
