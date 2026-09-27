package io.quizforge.desktop.ui;

import io.quizforge.core.document.qdoc.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.geometry.Pos;
import javafx.application.Platform;

/** A small block editor backed only by the structured QDoc model. */
final class QDocEditorView extends VBox {
    private final QDocEditorModel model;
    private final Consumer<QDocDocument> save;
    private final Runnable ai;
    private final VBox body = new VBox(10);
    private final VBox errors = new VBox(3);

    QDocEditorView(QDocDocument document, Consumer<QDocDocument> save, Runnable ai) {
        this.model = new QDocEditorModel(document, DocumentTemplate.GENERAL_KNOWLEDGE);
        this.save = save;
        this.ai = ai;
        setId("qdoc-editor-view");
        getStyleClass().add("qdoc-editor");
        setSpacing(16);
        setMaxWidth(820);
        errors.setManaged(false);
        errors.managedProperty().bind(javafx.beans.binding.Bindings.isNotEmpty(errors.getChildren()));
        getChildren().addAll(EditorUi.toolbar("模板文档 · 通用知识", "qdoc-save", this::save), errors, body);
        EditorUi.saveShortcut(this, this::save);
        render();
    }

    boolean dirty() { return model.dirty(); }

    private void save() {
        errors.getChildren().clear();
        try { save.accept(model.document()); }
        catch (IllegalArgumentException failure) {
            errors.getChildren().addAll(UiTheme.label("Document validation failed:", "qdoc-error"),
                    UiTheme.label("• " + validationMessage(failure.getMessage()), "qdoc-error"));
        } catch (RuntimeException failure) {
            errors.getChildren().add(UiTheme.label("Could not save document: " + failure.getMessage(),
                    "qdoc-error"));
        }
    }

    private String validationMessage(String code) {
        return switch (code == null ? "" : code) {
            case "INVALID_TEXT_BLOCK" -> "Content block requires text.";
            case "INVALID_LIST_BLOCK" -> "Every list item needs text.";
            case "INVALID_NODE" -> "A structure block needs a title.";
            case "MISSING_REQUIRED_CHILD" -> "Chapter requires a Section.";
            case "MISSING_CHAPTER" -> "Document requires a Chapter.";
            default -> code;
        };
    }

    private void render() {
        body.getChildren().clear();
        TextField title = new TextField(model.document().title());
        title.setPromptText("文档标题");
        title.getStyleClass().add("editor-document-title");
        title.setId("qdoc-document-title");
        title.textProperty().addListener((obs, old, value) -> model.setDocumentTitle(value));
        body.getChildren().add(title);
        if (!model.hasContent()) {
            VBox empty = UiTheme.quietState("开始组织你的文档", "点击章节旁的 + 添加模板块，再填写内容。样式由模板统一呈现。");
            empty.setId("qdoc-empty-state");
            Button generate = UiTheme.button("使用 AI 生成", "spark", "qdoc-ai-action", ai);
            generate.setId("qdoc-empty-ai");
            empty.getChildren().add(generate);
            body.getChildren().add(empty);
        }
        int number = 0;
        for (DocumentNode node : model.document().content()) {
            number++;
            body.getChildren().add(renderNode(node, List.of(number - 1), String.valueOf(number)));
        }
        body.getChildren().add(addMenu(List.of()));
    }

    private VBox renderNode(DocumentNode node, List<Integer> path, String number) {
        VBox card = new VBox(8);
        card.getStyleClass().addAll("qdoc-node", "qdoc-depth-" + path.size());
        card.setId("qdoc-node-" + key(path));
        TextField title = new TextField(node.title());
        title.setMinWidth(40);
        title.setPromptText(nodeLabel(node.type()) + "标题");
        title.getStyleClass().add("qdoc-heading-input");
        title.setId("qdoc-node-title-" + key(path));
        title.textProperty().addListener((obs, old, value) -> model.setNodeTitle(path, value));
        HBox heading = new HBox(6, UiTheme.label(number, "qdoc-number"), title, addMenu(path), deleteMenu(path));
        heading.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(title, Priority.ALWAYS);
        card.getChildren().add(heading);
        int childNumber = 0;
        for (int i = 0; i < node.children().size(); i++) {
            DocumentElement child = node.children().get(i);
            List<Integer> childPath = childPath(path, i);
            if (child instanceof DocumentNode nested) {
                childNumber++;
                card.getChildren().add(renderNode(nested, childPath, number + "." + childNumber));
            } else card.getChildren().add(renderBlock((ContentBlock) child, childPath));
        }
        return card;
    }

    private Node renderBlock(ContentBlock block, List<Integer> path) {
        VBox card = new VBox(5);
        card.getStyleClass().add("qdoc-block");
        card.setId("qdoc-block-" + key(path));
        var caption = UiTheme.label(blockLabel(block.type()), "qdoc-type");
        caption.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(caption, Priority.ALWAYS);
        HBox header = new HBox(8, caption, deleteMenu(path));
        header.setAlignment(Pos.CENTER_LEFT);
        card.getChildren().add(header);
        if (block.type() == ContentBlockType.BULLET_LIST || block.type() == ContentBlockType.ORDERED_LIST) {
            for (int i = 0; i < block.items().size(); i++) {
                final int item = i;
                TextField field = new TextField(block.items().get(i));
                field.setId("qdoc-list-item-" + key(path) + "-" + i);
                field.setMinWidth(40);
                HBox.setHgrow(field, Priority.ALWAYS);
                field.textProperty().addListener((obs, old, value) -> model.setListItem(path, item, value));
                card.getChildren().add(new HBox(8, UiTheme.label(block.type() == ContentBlockType.BULLET_LIST
                        ? "•" : (i + 1) + ".", "qdoc-number"), field));
            }
            Button addItem = UiTheme.button("添加列表项", "plus", "qdoc-add-item", () -> {
                model.addListItem(path); render();
            });
            addItem.setId("qdoc-add-item-" + key(path));
            card.getChildren().add(addItem);
        } else {
            if (block.type() == ContentBlockType.CODE_BLOCK) {
                TextField language = new TextField(block.language() == null ? "" : block.language());
                language.setPromptText("代码语言（可选）");
                language.getStyleClass().add("qdoc-code-language");
                language.textProperty().addListener((obs, old, value) -> model.setCodeLanguage(path, value));
                card.getChildren().add(language);
            }
            TextArea content = EditorUi.content(block.text(), "qdoc-block-text-" + key(path), block.type() == ContentBlockType.CODE_BLOCK);
            content.setPromptText("填写" + blockLabel(block.type()) + "内容…");
            content.textProperty().addListener((obs, old, value) -> model.setBlockText(path, value));
            card.getChildren().add(content);
        }
        return card;
    }

    private MenuButton addMenu(List<Integer> parent) {
        MenuButton menu = EditorUi.menu("plus", parent.isEmpty() ? "添加章节" : "添加模板块");
        if (parent.isEmpty()) menu.setText("添加章节");
        menu.setId("qdoc-add-" + key(parent));
        menu.getStyleClass().add("qdoc-add-block");
        for (DocumentNodeType type : DocumentNodeType.values()) {
            if (!model.allowedNodes(parent).contains(type)) continue;
            MenuItem item = new MenuItem(nodeLabel(type));
            item.setUserData(type.name());
            item.setOnAction(event -> {
                int next = parent.isEmpty() ? model.document().content().size() : ((DocumentNode) model.element(parent)).children().size();
                model.addNode(parent, type); render();
                focus("qdoc-node-title-" + key(childPath(parent, next)));
            });
            menu.getItems().add(item);
        }
        for (ContentBlockType type : ContentBlockType.values()) {
            if (!model.allowedBlocks(parent).contains(type)) continue;
            MenuItem item = new MenuItem(blockLabel(type));
            item.setUserData(type.name());
            item.setOnAction(event -> {
                int next = ((DocumentNode) model.element(parent)).children().size();
                model.addBlock(parent, type); render();
                String path = key(childPath(parent, next));
                focus(type == ContentBlockType.BULLET_LIST || type == ContentBlockType.ORDERED_LIST
                        ? "qdoc-list-item-" + path + "-0" : "qdoc-block-text-" + path);
            });
            menu.getItems().add(item);
        }
        return menu;
    }

    private MenuButton deleteMenu(List<Integer> path) {
        MenuButton menu = EditorUi.menu("more", "模板块操作");
        menu.setId("qdoc-more-" + key(path));
        MenuItem delete = new MenuItem("删除此块");
        delete.setOnAction(event -> {
            DocumentElement target = model.element(path);
            if (target instanceof DocumentNode node && !node.children().isEmpty()) {
                int[] counts = countChildren(node);
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                        "This will remove " + counts[0] + " nested nodes and " + counts[1]
                                + " content blocks.", ButtonType.CANCEL, ButtonType.OK);
                confirm.setHeaderText("Delete this " + node.type().name() + "?");
                UiTheme.apply(confirm);
                if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
            }
            model.delete(path);
            render();
        });
        menu.getItems().add(delete);
        return menu;
    }

    private void focus(String id) {
        Platform.runLater(() -> {
            Node target = lookup("#" + id);
            if (target instanceof TextInputControl input) { input.requestFocus(); input.selectAll(); }
        });
    }

    private String nodeLabel(DocumentNodeType type) {
        return switch (type) { case CHAPTER -> "章节"; case SECTION -> "小节"; case SUBSECTION -> "子节"; };
    }

    private String blockLabel(ContentBlockType type) {
        return switch (type) {
            case PARAGRAPH -> "正文"; case BULLET_LIST -> "无序列表"; case ORDERED_LIST -> "有序列表";
            case CODE_BLOCK -> "代码"; case QUOTE -> "引用";
        };
    }

    private int[] countChildren(DocumentNode node) {
        int[] counts = new int[2];
        for (DocumentElement child : node.children()) {
            if (child instanceof DocumentNode nested) {
                counts[0]++;
                int[] inside = countChildren(nested);
                counts[0] += inside[0];
                counts[1] += inside[1];
            } else counts[1]++;
        }
        return counts;
    }

    private List<Integer> childPath(List<Integer> parent, int index) {
        List<Integer> path = new ArrayList<>(parent);
        path.add(index);
        return List.copyOf(path);
    }

    private String key(List<Integer> path) {
        return path.isEmpty() ? "root" : path.stream().map(String::valueOf)
                .reduce((left, right) -> left + "-" + right).orElse("root");
    }
}
