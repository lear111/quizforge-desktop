package io.quizforge.desktop.ui;

import io.quizforge.core.document.registered.RegisteredMarkdownDocument;
import io.quizforge.core.document.registered.NamedMarkdownAnchor;
import io.quizforge.core.document.registered.MarkdownSourceRange;
import io.quizforge.infrastructure.filesystem.RegisteredMarkdownCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.VBox;
import org.commonmark.node.Code;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.node.SourceSpan;

/** Renders Markdown into JavaFX labels, without an HTML or JavaScript execution engine. */
final class SafeMarkdownPreview {
    record Block(String style, String text) { }
    private record SourceLocation(int line, int column) { }
    interface SourceActions {
        void create(MarkdownSourceRange block);
        void copy(List<NamedMarkdownAnchor> anchors);
    }
    private static final Parser MARKDOWN = Parser.builder()
            .includeSourceSpans(IncludeSourceSpans.BLOCKS).build();
    private final RegisteredMarkdownCodec anchorCodec = new RegisteredMarkdownCodec();

    List<Block> project(String markdown) {
        Node root = MARKDOWN.parse(withoutFrontMatter(markdown));
        List<Block> blocks = new ArrayList<>();
        for (Node node = root.getFirstChild(); node != null; node = node.getNext()) collect(node, blocks, "");
        return List.copyOf(blocks);
    }

    javafx.scene.Node view(String markdown) {
        return view(markdown, null, null);
    }

    javafx.scene.Node view(String markdown, RegisteredMarkdownDocument registered,
            SourceActions actions) {
        VBox preview = new VBox(12);
        preview.getStyleClass().add("markdown-preview");
        preview.setMaxWidth(820);
        preview.setId("markdown-browse-view");
        Map<SourceLocation, List<NamedMarkdownAnchor>> anchors = new HashMap<>();
        List<NamedMarkdownAnchor> available = registered == null
                ? anchorCodec.inspectAnchors(markdown) : registered.anchors();
        if (!available.isEmpty()) {
            available.stream().filter(anchor -> !anchor.orphan()).forEach(anchor ->
                    anchors.computeIfAbsent(new SourceLocation(anchor.blockRange().startLine(),
                            anchor.blockRange().startColumn()), ignored -> new ArrayList<>()).add(anchor));
        }
        render(MARKDOWN.parse(withoutFrontMatter(markdown)), preview, anchors, actions);
        javafx.scene.layout.StackPane centered = new javafx.scene.layout.StackPane(preview);
        centered.setAlignment(javafx.geometry.Pos.TOP_CENTER);
        return UiTheme.scroll(centered);
    }

    private void render(Node parent, VBox page, Map<SourceLocation, List<NamedMarkdownAnchor>> anchors,
            SourceActions actions) {
        for (Node node = parent.getFirstChild(); node != null; node = node.getNext()) {
            if (node instanceof HtmlBlock || node instanceof HtmlInline) continue;
            if (node instanceof Heading heading) {
                add(page, UiTheme.label(text(node), "preview-heading-" + heading.getLevel()),
                        node, anchors, actions);
            } else if (node instanceof FencedCodeBlock || node instanceof IndentedCodeBlock) {
                String literal = node instanceof FencedCodeBlock code ? code.getLiteral() : ((IndentedCodeBlock) node).getLiteral();
                Label code = UiTheme.label(literal.stripTrailing(), "preview-code");
                code.setMaxWidth(Double.MAX_VALUE);
                add(page, code, node, anchors, actions);
            } else if (node instanceof org.commonmark.node.BlockQuote) {
                VBox quote = new VBox(8);
                quote.getStyleClass().add("preview-quote");
                render(node, quote, anchors, null);
                add(page, quote, node, anchors, actions);
            } else if (node instanceof org.commonmark.node.BulletList || node instanceof org.commonmark.node.OrderedList) {
                int number = node instanceof org.commonmark.node.OrderedList ordered ? ordered.getStartNumber() : 0;
                VBox list = new VBox(6);
                for (Node item = node.getFirstChild(); item != null; item = item.getNext()) {
                    VBox content = new VBox(6);
                    render(item, content, anchors, null);
                    javafx.scene.layout.HBox.setHgrow(content, javafx.scene.layout.Priority.ALWAYS);
                    content.setMinWidth(0);
                    Label marker = UiTheme.label(node instanceof org.commonmark.node.OrderedList ? number++ + "." : "•", "preview-list-marker");
                    list.getChildren().add(new javafx.scene.layout.HBox(10, marker, content));
                }
                add(page, list, node, anchors, actions);
            } else if (node instanceof org.commonmark.node.ThematicBreak) {
                page.getChildren().add(new javafx.scene.control.Separator());
            } else if (node instanceof Paragraph) {
                javafx.scene.text.TextFlow flow = new javafx.scene.text.TextFlow();
                flow.getStyleClass().add("preview-prose");
                flow.setLineSpacing(6);
                inline(node, flow, false, false, false);
                add(page, flow, node, anchors, actions);
            } else render(node, page, anchors, actions);
        }
    }

    private void add(VBox page, javafx.scene.Node rendered, Node source,
            Map<SourceLocation, List<NamedMarkdownAnchor>> anchors, SourceActions actions) {
        page.getChildren().add(rendered);
        if (actions == null || source.getSourceSpans().isEmpty()) return;
        SourceSpan start = source.getSourceSpans().getFirst();
        SourceSpan end = source.getSourceSpans().getLast();
        MarkdownSourceRange block = new MarkdownSourceRange(start.getLineIndex() + 1,
                start.getColumnIndex() + 1, end.getLineIndex() + 1,
                end.getColumnIndex() + end.getLength() + 1);
        List<NamedMarkdownAnchor> existing = anchors.getOrDefault(new SourceLocation(
                block.startLine(), block.startColumn()), List.of());
        rendered.getProperties().put("quizforge.sourceBlock", block);
        MenuItem create = new MenuItem("Create Source Reference...");
        create.setId("create-source-reference");
        create.setOnAction(event -> actions.create(block));
        ContextMenu menu = new ContextMenu(create);
        if (!existing.isEmpty()) {
            MenuItem copy = new MenuItem("Copy Source Reference");
            copy.setId("copy-source-reference");
            copy.setOnAction(event -> actions.copy(existing));
            menu.getItems().add(copy);
        }
        rendered.getProperties().put("quizforge.sourceContextMenu", menu);
        rendered.setOnContextMenuRequested(event -> {
            menu.show(page, event.getScreenX(), event.getScreenY());
            event.consume();
        });
    }

    private void inline(Node node, javafx.scene.text.TextFlow flow, boolean bold, boolean italic, boolean link) {
        if (node instanceof HtmlBlock || node instanceof HtmlInline) return;
        String literal = node instanceof Text text ? text.getLiteral() : node instanceof Code code ? code.getLiteral()
                : node instanceof SoftLineBreak ? " " : node instanceof HardLineBreak ? "\n" : null;
        if (literal != null) {
            javafx.scene.text.Text span = new javafx.scene.text.Text(literal);
            span.getStyleClass().add("prose-text");
            if (bold) span.getStyleClass().add("prose-strong");
            if (italic) span.getStyleClass().add("prose-emphasis");
            if (link) span.getStyleClass().add("prose-link");
            if (node instanceof Code) span.getStyleClass().add("prose-code");
            flow.getChildren().add(span);
        }
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            inline(child, flow, bold || node instanceof org.commonmark.node.StrongEmphasis,
                    italic || node instanceof org.commonmark.node.Emphasis, link || node instanceof org.commonmark.node.Link);
        }
    }

    private void collect(Node node, List<Block> blocks, String prefix) {
        if (node instanceof HtmlBlock || node instanceof HtmlInline) return;
        if (node instanceof Heading heading) {
            add(blocks, "preview-heading-" + heading.getLevel(), prefix + text(node));
        } else if (node instanceof FencedCodeBlock code) {
            add(blocks, "preview-code", code.getLiteral());
        } else if (node instanceof IndentedCodeBlock code) {
            add(blocks, "preview-code", code.getLiteral());
        } else if (node instanceof Paragraph) {
            add(blocks, "preview-paragraph", prefix + text(node));
        } else if (node instanceof ListItem) {
            for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
                collect(child, blocks, "•  ");
            }
        } else {
            for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
                collect(child, blocks, prefix);
            }
        }
    }

    private void add(List<Block> blocks, String style, String text) {
        if (!text.isBlank()) blocks.add(new Block(style, text));
    }

    private String text(Node node) {
        StringBuilder value = new StringBuilder();
        append(node, value);
        return value.toString().trim();
    }

    private void append(Node node, StringBuilder value) {
        if (node instanceof HtmlBlock || node instanceof HtmlInline) return;
        if (node instanceof Text text) value.append(text.getLiteral());
        else if (node instanceof Code code) value.append(code.getLiteral());
        else if (node instanceof SoftLineBreak || node instanceof HardLineBreak) value.append('\n');
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) append(child, value);
    }

    private String withoutFrontMatter(String markdown) {
        String source = markdown.replace("\r\n", "\n").replace('\r', '\n');
        if (!source.startsWith("---\n")) return source;
        int end = source.indexOf("\n---\n", 4);
        return end < 0 ? source : source.substring(end + 5);
    }
}
