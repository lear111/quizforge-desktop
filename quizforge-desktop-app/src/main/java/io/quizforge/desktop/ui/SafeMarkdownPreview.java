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
        for (Block block : project(markdown)) {
            Label label = UiTheme.label(block.text(), block.style());
            label.setMaxWidth(Double.MAX_VALUE);
            preview.getChildren().add(label);
        }
        javafx.scene.layout.StackPane centered = new javafx.scene.layout.StackPane(preview);
        centered.setAlignment(javafx.geometry.Pos.TOP_CENTER);
        return UiTheme.scroll(centered);
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
