package io.quizforge.desktop.ui;

import java.util.ArrayList;
import java.util.List;
import javafx.scene.control.Label;
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

/** Renders Markdown into JavaFX labels, without an HTML or JavaScript execution engine. */
final class SafeMarkdownPreview {
    record Block(String style, String text) { }
    private static final Parser MARKDOWN = Parser.builder().build();

    List<Block> project(String markdown) {
        Node root = MARKDOWN.parse(withoutFrontMatter(markdown));
        List<Block> blocks = new ArrayList<>();
        for (Node node = root.getFirstChild(); node != null; node = node.getNext()) collect(node, blocks, "");
        return List.copyOf(blocks);
    }

    javafx.scene.Node view(String markdown) {
        VBox preview = new VBox(12);
        preview.getStyleClass().add("markdown-preview");
        preview.setMaxWidth(820);
        preview.setId("markdown-browse-view");
        render(MARKDOWN.parse(withoutFrontMatter(markdown)), preview);
        javafx.scene.layout.StackPane centered = new javafx.scene.layout.StackPane(preview);
        centered.setAlignment(javafx.geometry.Pos.TOP_CENTER);
        return UiTheme.scroll(centered);
    }

    private void render(Node parent, VBox page) {
        for (Node node = parent.getFirstChild(); node != null; node = node.getNext()) {
            if (node instanceof HtmlBlock || node instanceof HtmlInline) continue;
            if (node instanceof Heading heading) {
                page.getChildren().add(UiTheme.label(text(node), "preview-heading-" + heading.getLevel()));
            } else if (node instanceof FencedCodeBlock || node instanceof IndentedCodeBlock) {
                String literal = node instanceof FencedCodeBlock code ? code.getLiteral() : ((IndentedCodeBlock) node).getLiteral();
                Label code = UiTheme.label(literal.stripTrailing(), "preview-code");
                code.setMaxWidth(Double.MAX_VALUE);
                page.getChildren().add(code);
            } else if (node instanceof org.commonmark.node.BlockQuote) {
                VBox quote = new VBox(8);
                quote.getStyleClass().add("preview-quote");
                render(node, quote);
                page.getChildren().add(quote);
            } else if (node instanceof org.commonmark.node.BulletList || node instanceof org.commonmark.node.OrderedList) {
                int number = node instanceof org.commonmark.node.OrderedList ordered ? ordered.getStartNumber() : 0;
                VBox list = new VBox(6);
                for (Node item = node.getFirstChild(); item != null; item = item.getNext()) {
                    VBox content = new VBox(6);
                    render(item, content);
                    javafx.scene.layout.HBox.setHgrow(content, javafx.scene.layout.Priority.ALWAYS);
                    content.setMinWidth(0);
                    Label marker = UiTheme.label(node instanceof org.commonmark.node.OrderedList ? number++ + "." : "•", "preview-list-marker");
                    list.getChildren().add(new javafx.scene.layout.HBox(10, marker, content));
                }
                page.getChildren().add(list);
            } else if (node instanceof org.commonmark.node.ThematicBreak) {
                page.getChildren().add(new javafx.scene.control.Separator());
            } else if (node instanceof Paragraph) {
                javafx.scene.text.TextFlow flow = new javafx.scene.text.TextFlow();
                flow.getStyleClass().add("preview-prose");
                flow.setLineSpacing(6);
                inline(node, flow, false, false, false);
                page.getChildren().add(flow);
            } else render(node, page);
        }
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
