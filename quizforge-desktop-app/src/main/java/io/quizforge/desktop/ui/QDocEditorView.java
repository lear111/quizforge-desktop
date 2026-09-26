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
        setMaxWidth(880);
        Button saveButton = UiTheme.button("Save", "check", "qdoc-save", this::save);
        saveButton.setId("qdoc-save");
        getChildren().addAll(saveButton, errors, body);
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
        title.setPromptText("Document title");
        title.setId("qdoc-document-title");
        title.textProperty().addListener((obs, old, value) -> model.setDocumentTitle(value));
        body.getChildren().add(title);
        if (!model.hasContent()) {
            VBox empty = UiTheme.quietState("Empty document", "Add a block to begin.");
            empty.setId("qdoc-empty-state");
            Button generate = UiTheme.button("Generate with AI", "spark", "qdoc-ai-action", ai);
            generate.setId("qdoc-empty-ai");
            empty.getChildren().add(generate);
            body.getChildren().add(empty);
        }
        body.getChildren().add(addMenu(List.of()));
        int number = 0;
        for (DocumentNode node : model.document().content()) {
            number++;
            body.getChildren().add(renderNode(node, List.of(number - 1), String.valueOf(number)));
        }
    }

    private VBox renderNode(DocumentNode node, List<Integer> path, String number) {
        VBox card = new VBox(8);
        card.getStyleClass().add("qdoc-block");
        card.setId("qdoc-node-" + key(path));
        TextField title = new TextField(node.title());
        title.setId("qdoc-node-title-" + key(path));
        title.textProperty().addListener((obs, old, value) -> model.setNodeTitle(path, value));
        HBox heading = new HBox(8, UiTheme.label(number, "qdoc-number"),
                UiTheme.label(node.type().name(), "qdoc-type"), title, addMenu(path), deleteMenu(path));
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
        HBox header = new HBox(8, UiTheme.label(block.type().name(), "qdoc-type"), deleteMenu(path));
        card.getChildren().add(header);
        if (block.type() == ContentBlockType.BULLET_LIST || block.type() == ContentBlockType.ORDERED_LIST) {
            for (int i = 0; i < block.items().size(); i++) {
                final int item = i;
                TextField field = new TextField(block.items().get(i));
                field.setId("qdoc-list-item-" + key(path) + "-" + i);
                field.textProperty().addListener((obs, old, value) -> model.setListItem(path, item, value));
                card.getChildren().add(new HBox(8, UiTheme.label(block.type() == ContentBlockType.BULLET_LIST
                        ? "•" : (i + 1) + ".", "qdoc-number"), field));
            }
            Button addItem = UiTheme.button("+ List item", "plus", "qdoc-add-item", () -> {
                model.addListItem(path); render();
            });
            addItem.setId("qdoc-add-item-" + key(path));
            card.getChildren().add(addItem);
        } else {
            if (block.type() == ContentBlockType.CODE_BLOCK) {
                TextField language = new TextField(block.language() == null ? "" : block.language());
                language.setPromptText("Language (optional)");
                language.textProperty().addListener((obs, old, value) -> model.setCodeLanguage(path, value));
                card.getChildren().add(language);
            }
            TextArea content = new TextArea(block.text());
            content.setId("qdoc-block-text-" + key(path));
            content.setPrefRowCount(block.type() == ContentBlockType.CODE_BLOCK ? 6 : 3);
            content.textProperty().addListener((obs, old, value) -> model.setBlockText(path, value));
            card.getChildren().add(content);
        }
        return card;
    }

    private MenuButton addMenu(List<Integer> parent) {
        MenuButton menu = new MenuButton("+");
        menu.setId("qdoc-add-" + key(parent));
        menu.setAccessibleText("Add Block");
        menu.getStyleClass().add("qdoc-add-block");
        for (DocumentNodeType type : DocumentNodeType.values()) {
            if (!model.allowedNodes(parent).contains(type)) continue;
            MenuItem item = new MenuItem(type.name());
            item.setOnAction(event -> { model.addNode(parent, type); render(); });
            menu.getItems().add(item);
        }
        for (ContentBlockType type : ContentBlockType.values()) {
            if (!model.allowedBlocks(parent).contains(type)) continue;
            MenuItem item = new MenuItem(type.name());
            item.setOnAction(event -> { model.addBlock(parent, type); render(); });
            menu.getItems().add(item);
        }
        return menu;
    }

    private MenuButton deleteMenu(List<Integer> path) {
        MenuButton menu = new MenuButton("⋯");
        menu.setId("qdoc-more-" + key(path));
        menu.setAccessibleText("Block actions");
        MenuItem delete = new MenuItem("Delete");
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
