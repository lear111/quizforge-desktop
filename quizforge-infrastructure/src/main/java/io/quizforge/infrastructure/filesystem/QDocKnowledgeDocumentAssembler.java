package io.quizforge.infrastructure.filesystem;

import io.quizforge.core.document.AssembledKnowledgeDocument;
import io.quizforge.core.document.qdoc.ContentBlock;
import io.quizforge.core.document.qdoc.ContentBlockType;
import io.quizforge.core.document.qdoc.DocumentElement;
import io.quizforge.core.document.qdoc.DocumentNode;
import io.quizforge.core.document.qdoc.DocumentNodeType;
import io.quizforge.core.document.qdoc.DocumentTemplate;
import io.quizforge.core.document.qdoc.QDocDocument;
import io.quizforge.core.port.KnowledgeDocumentAssembler;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.BulletList;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;

/** Converts validated semantic AI Markdown into QuizForge-owned structured identity. */
public final class QDocKnowledgeDocumentAssembler implements KnowledgeDocumentAssembler {
    private static final Parser MARKDOWN = Parser.builder()
            .includeSourceSpans(IncludeSourceSpans.BLOCKS).build();
    private final StandardKnowledgeDocumentAssembler legacy = new StandardKnowledgeDocumentAssembler();
    private final QDocV1Codec codec = new QDocV1Codec();

    @Override public String draftLanguage(String draft) { return legacy.draftLanguage(draft); }

    @Override
    public AssembledKnowledgeDocument assemble(String draft, String title, String language, String existingAssetId) {
        if (draft == null || title == null || title.isBlank() || language == null || language.isBlank())
            throw new IllegalArgumentException("Document content, title and language are required");
        String id = existingAssetId == null ? newId("doc_") : existingAssetId;
        String normalized = draft.replace("\r\n", "\n").replace('\r', '\n');
        int frontEnd = normalized.startsWith("---\n") ? normalized.indexOf("\n---\n", 4) : -1;
        if (normalized.startsWith("---\n") && frontEnd < 0) throw new IllegalArgumentException("Unclosed AI Front Matter");
        String body = frontEnd < 0 ? normalized : normalized.substring(frontEnd + 5);
        Node root = MARKDOWN.parse(body);
        List<DocumentNode> chapters = new ArrayList<>();
        List<DocumentElement> chapterChildren = null;
        List<DocumentElement> sectionChildren = null;
        String chapterTitle = null, chapterId = null, sectionTitle = null, sectionId = null;
        for (Node node = root.getFirstChild(); node != null; node = node.getNext()) {
            if (node instanceof Heading heading) {
                if (sectionChildren != null) chapterChildren.add(new DocumentNode(sectionId,
                        DocumentNodeType.SECTION, sectionTitle, sectionChildren));
                sectionChildren = null;
                if (heading.getLevel() == 1) continue;
                if (heading.getLevel() == 2) {
                    if (chapterChildren != null) chapters.add(new DocumentNode(chapterId,
                            DocumentNodeType.CHAPTER, chapterTitle, chapterChildren));
                    chapterTitle = headingText(body, heading);
                    chapterId = newId("chapter_");
                    chapterChildren = new ArrayList<>();
                } else if (heading.getLevel() == 3 && chapterChildren != null) {
                    sectionTitle = headingText(body, heading);
                    sectionId = newId("section_");
                    sectionChildren = new ArrayList<>();
                } else throw new IllegalArgumentException("Unsupported AI heading structure");
            } else if (sectionChildren != null) {
                if (node instanceof HtmlBlock html && StandardKnowledgeDocumentV1.isIdComment(html.getLiteral()))
                    continue;
                ContentBlock block = block(body, node);
                if (block != null) sectionChildren.add(block);
            }
        }
        if (sectionChildren != null) chapterChildren.add(new DocumentNode(sectionId,
                DocumentNodeType.SECTION, sectionTitle, sectionChildren));
        if (chapterChildren != null) chapters.add(new DocumentNode(chapterId,
                DocumentNodeType.CHAPTER, chapterTitle, chapterChildren));
        QDocDocument document = new QDocDocument("quizforge-document", "1.0", id,
                DocumentTemplate.GENERAL_KNOWLEDGE.reference(), title.trim(), language.trim(), chapters);
        String json = codec.write(document);
        return new AssembledKnowledgeDocument(id, codec.contentId(document), document.title(), "1.0", json);
    }

    private ContentBlock block(String body, Node node) {
        if (node instanceof FencedCodeBlock code) {
            String info = code.getInfo();
            return new ContentBlock(ContentBlockType.CODE_BLOCK, code.getLiteral(), List.of(),
                    info == null || info.isBlank() ? null : info);
        }
        if (node instanceof IndentedCodeBlock code) return ContentBlock.text(ContentBlockType.CODE_BLOCK,
                code.getLiteral());
        if (node instanceof BulletList || node instanceof OrderedList) {
            List<String> items = new ArrayList<>();
            for (Node item = node.getFirstChild(); item != null; item = item.getNext()) {
                if (item instanceof ListItem) {
                    String raw = slice(body, item).replaceFirst("(?m)^\\s*(?:[-+*]|\\d+[.)])\\s+", "").trim();
                    if (!raw.isBlank()) items.add(raw);
                }
            }
            return items.isEmpty() ? null : ContentBlock.list(node instanceof BulletList
                    ? ContentBlockType.BULLET_LIST : ContentBlockType.ORDERED_LIST, items);
        }
        String value = slice(body, node).trim();
        if (value.isBlank()) return null;
        if (node instanceof BlockQuote) value = value.replaceAll("(?m)^> ?", "");
        return ContentBlock.text(node instanceof BlockQuote ? ContentBlockType.QUOTE
                : ContentBlockType.PARAGRAPH, value);
    }

    private String headingText(String body, Heading heading) {
        return slice(body, heading).replaceFirst("^#{1,6}\\s+", "")
                .replaceFirst("\\s+#+\\s*$", "").trim()
                .replaceFirst("^\\d+(?:\\.\\d+)*[.、) ]+", "").trim();
    }

    private String slice(String source, Node node) {
        if (node.getSourceSpans().isEmpty()) return "";
        var first = node.getSourceSpans().getFirst();
        var last = node.getSourceSpans().getLast();
        return source.substring(first.getInputIndex(), last.getInputIndex() + last.getLength());
    }

    private String newId(String prefix) { return prefix + UUID.randomUUID().toString().replace("-", ""); }
}
