package io.quizforge.desktop.ui;

import io.quizforge.core.document.registered.RegisteredMarkdownDocument;
import io.quizforge.core.document.registered.NamedMarkdownAnchor;
import io.quizforge.core.document.registered.MarkdownSourceRange;
import io.quizforge.infrastructure.filesystem.RegisteredMarkdownCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.function.Consumer;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import org.commonmark.node.Code;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.ListItem;
import org.commonmark.node.Link;
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
    static final class BrowseLayout extends SplitPane {
        private final BorderPane readerColumn = new BorderPane();
        private final ScrollPane reader;
        private final MarkdownOutlineView outline;
        private boolean initialDividerSet;

        BrowseLayout(ScrollPane reader, MarkdownOutlineView outline) {
            this.reader = reader;
            this.outline = outline;
            setId("markdown-browse-layout");
            getStyleClass().add("markdown-browse-layout");
            setMinWidth(0);
            readerColumn.setMinWidth(320);
            readerColumn.setCenter(reader);
            getItems().addAll(readerColumn, outline);
            SplitPane.setResizableWithParent(outline, false);
            widthProperty().addListener((ignored, before, width) -> {
                if (initialDividerSet || width.doubleValue() <= 500) return;
                initialDividerSet = true;
                setDividerPositions((width.doubleValue() - 260) / width.doubleValue());
            });
        }

        void setHeader(javafx.scene.Node header) {
            readerColumn.setTop(header);
        }

        ScrollPane reader() { return reader; }
        MarkdownOutlineView outline() { return outline; }
        ScrollPane outlineScroll() { return outline.scroll(); }
    }

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
        return view(markdown, registered, actions, null);
    }

    javafx.scene.Node view(String markdown, RegisteredMarkdownDocument registered,
            SourceActions actions, Consumer<MarkdownOutline.Entry> copyLink) {
        return view(markdown, registered, actions, copyLink, null);
    }

    javafx.scene.Node view(String markdown, RegisteredMarkdownDocument registered,
            SourceActions actions, Consumer<MarkdownOutline.Entry> copyLink,
            Consumer<String> openLink) {
        VBox preview = new VBox();
        preview.getStyleClass().add("markdown-preview");
        // 940px including page padding leaves an 860px reading column.
        preview.setMaxWidth(940);
        preview.setMinWidth(0);
        preview.setId("markdown-browse-view");
        Map<SourceLocation, List<NamedMarkdownAnchor>> anchors = new HashMap<>();
        List<NamedMarkdownAnchor> available = anchors(markdown, registered);
        if (!available.isEmpty()) {
            available.stream().filter(anchor -> !anchor.orphan()).forEach(anchor ->
                    anchors.computeIfAbsent(new SourceLocation(anchor.blockRange().startLine(),
                            anchor.blockRange().startColumn()), ignored -> new ArrayList<>()).add(anchor));
        }
        Node parsed = MARKDOWN.parse(withoutFrontMatter(markdown));
        javafx.scene.layout.StackPane centered = new javafx.scene.layout.StackPane(preview);
        centered.setAlignment(javafx.geometry.Pos.TOP_CENTER);
        centered.setMinWidth(0);
        ScrollPane scroll = UiTheme.scroll(centered);
        scroll.setId("markdown-preview-scroll");
        MarkdownDocumentNavigator navigator = new MarkdownDocumentNavigator(scroll);
        render(parsed, preview, anchors, actions, navigator, openLink);
        List<MarkdownOutline.Entry> entries = MarkdownOutline.extract(parsed, available);
        MarkdownOutlineView outline = new MarkdownOutlineView(entries, navigator, copyLink);
        scroll.setMinWidth(0);
        BrowseLayout layout = new BrowseLayout(scroll, outline);
        layout.getProperties().put("quizforge.outlineEntries", entries);
        layout.getProperties().put("quizforge.navigator", navigator);
        return layout;
    }

    List<MarkdownOutline.Entry> outlineEntries(String markdown, RegisteredMarkdownDocument registered) {
        return MarkdownOutline.extract(MARKDOWN.parse(withoutFrontMatter(markdown)),
                anchors(markdown, registered));
    }

    private List<NamedMarkdownAnchor> anchors(String markdown, RegisteredMarkdownDocument registered) {
        return registered == null ? anchorCodec.inspectAnchors(markdown) : registered.anchors();
    }

    private void render(Node parent, VBox page, Map<SourceLocation, List<NamedMarkdownAnchor>> anchors,
            SourceActions actions, MarkdownDocumentNavigator navigator, Consumer<String> openLink) {
        for (Node node = parent.getFirstChild(); node != null; node = node.getNext()) {
            if (node instanceof HtmlBlock || node instanceof HtmlInline) continue;
            if (node instanceof Heading heading) {
                add(page, UiTheme.label(text(node), "preview-heading-" + heading.getLevel()),
                        node, anchors, actions, navigator);
            } else if (node instanceof FencedCodeBlock || node instanceof IndentedCodeBlock) {
                String literal = node instanceof FencedCodeBlock code ? code.getLiteral() : ((IndentedCodeBlock) node).getLiteral();
                Label code = UiTheme.label(literal.stripTrailing(), "preview-code-content");
                code.setWrapText(false);
                code.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
                ScrollPane codeScroll = new ScrollPane(code);
                codeScroll.getStyleClass().add("preview-code");
                codeScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
                codeScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
                codeScroll.setFitToHeight(true);
                codeScroll.setMinWidth(0);
                codeScroll.setMaxWidth(Double.MAX_VALUE);
                add(page, codeScroll, node, anchors, actions, navigator);
            } else if (node instanceof org.commonmark.node.BlockQuote) {
                VBox quote = new VBox();
                quote.getStyleClass().add("preview-quote");
                render(node, quote, anchors, null, navigator, openLink);
                add(page, quote, node, anchors, actions, navigator);
            } else if (node instanceof org.commonmark.node.BulletList || node instanceof org.commonmark.node.OrderedList) {
                int number = node instanceof org.commonmark.node.OrderedList ordered ? ordered.getStartNumber() : 0;
                VBox list = new VBox();
                list.getStyleClass().add("preview-list");
                for (Node item = node.getFirstChild(); item != null; item = item.getNext()) {
                    VBox content = new VBox();
                    content.getStyleClass().add("preview-list-content");
                    render(item, content, anchors, null, navigator, openLink);
                    javafx.scene.layout.HBox.setHgrow(content, javafx.scene.layout.Priority.ALWAYS);
                    content.setMinWidth(0);
                    Label marker = UiTheme.label(node instanceof org.commonmark.node.OrderedList ? number++ + "." : "•", "preview-list-marker");
                    javafx.scene.layout.HBox row = new javafx.scene.layout.HBox(marker, content);
                    row.getStyleClass().add("preview-list-row");
                    row.setMinWidth(0);
                    list.getChildren().add(row);
                }
                add(page, list, node, anchors, actions, navigator);
            } else if (node instanceof org.commonmark.node.ThematicBreak) {
                javafx.scene.control.Separator rule = new javafx.scene.control.Separator();
                rule.getStyleClass().add("preview-rule");
                page.getChildren().add(rule);
            } else if (node instanceof Paragraph) {
                javafx.scene.text.TextFlow flow = new javafx.scene.text.TextFlow();
                flow.getStyleClass().add("preview-prose");
                flow.setMinWidth(0);
                inline(node, flow, false, false, null, openLink);
                add(page, flow, node, anchors, actions, navigator);
            } else render(node, page, anchors, actions, navigator, openLink);
        }
    }

    private void add(VBox page, javafx.scene.Node rendered, Node source,
            Map<SourceLocation, List<NamedMarkdownAnchor>> anchors, SourceActions actions,
            MarkdownDocumentNavigator navigator) {
        page.getChildren().add(rendered);
        if (source.getSourceSpans().isEmpty()) return;
        SourceSpan start = source.getSourceSpans().getFirst();
        SourceSpan end = source.getSourceSpans().getLast();
        MarkdownSourceRange block = new MarkdownSourceRange(start.getLineIndex() + 1,
                start.getColumnIndex() + 1, end.getLineIndex() + 1,
                end.getColumnIndex() + end.getLength() + 1);
        navigator.register(block, rendered);
        if (actions == null) return;
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

    private void inline(Node node, javafx.scene.text.TextFlow flow, boolean bold, boolean italic,
            String destination, Consumer<String> openLink) {
        if (node instanceof HtmlBlock || node instanceof HtmlInline) return;
        if (node instanceof Link link) destination = link.getDestination();
        String literal = node instanceof Text text ? text.getLiteral() : node instanceof Code code ? code.getLiteral()
                : node instanceof SoftLineBreak ? " " : node instanceof HardLineBreak ? "\n" : null;
        if (literal != null) {
            if (node instanceof Code) {
                Label code = UiTheme.label(literal, "prose-code");
                code.setWrapText(false);
                if (destination != null) code.getStyleClass().add("prose-link");
                bindNavigationLink(code, destination, openLink);
                flow.getChildren().add(code);
            } else {
                javafx.scene.text.Text span = new javafx.scene.text.Text(literal);
                span.getStyleClass().add("prose-text");
                if (bold) span.getStyleClass().add("prose-strong");
                if (italic) span.getStyleClass().add("prose-emphasis");
                if (destination != null) span.getStyleClass().add("prose-link");
                bindNavigationLink(span, destination, openLink);
                flow.getChildren().add(span);
            }
        }
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            inline(child, flow, bold || node instanceof org.commonmark.node.StrongEmphasis,
                    italic || node instanceof org.commonmark.node.Emphasis, destination, openLink);
        }
    }

    private void bindNavigationLink(javafx.scene.Node rendered, String destination, Consumer<String> openLink) {
        // There is no WebView: only an explicit click on a QuizForge span can request navigation.
        if (openLink == null || destination == null || !destination.startsWith("quizforge://")) return;
        rendered.getStyleClass().add("prose-internal-link");
        rendered.getProperties().put("quizforge.linkHref", destination);
        rendered.setOnMouseClicked(event -> {
            if (event.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                openLink.accept(destination);
                event.consume();
            }
        });
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
