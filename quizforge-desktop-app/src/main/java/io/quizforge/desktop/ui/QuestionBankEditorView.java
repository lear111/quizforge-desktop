package io.quizforge.desktop.ui;

import io.quizforge.core.asset.Asset;
import io.quizforge.core.question.QuestionBankEditorModel;
import io.quizforge.core.question.QuestionBankFile;
import io.quizforge.core.question.QuestionBankFileEditService;
import io.quizforge.core.question.QuestionSourceLinkService;
import io.quizforge.core.question.SourceDocumentSnapshot;
import io.quizforge.core.workspace.WorkspaceId;
import java.util.List;
import java.util.function.Consumer;
import javafx.collections.FXCollections;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.geometry.Pos;

/** One-question editor. Every control mutates the same portable bank model used by Practice. */
final class QuestionBankEditorView extends VBox {
    private final QuestionBankEditorModel model;
    private final WorkspaceId workspace;
    private final QuestionBankFileEditService edits;
    private final QuestionSourceLinkService sourceLinks;
    private final QuestionSourceNavigationAdapter sourceNavigation;
    private QuestionSourceListView sourceRows;
    private final Consumer<QuestionBankFile> save;
    private final VBox body = new VBox(14);
    private final VBox errors = new VBox(3);
    private int index;

    QuestionBankEditorView(QuestionBankFile bank, WorkspaceId workspace,
            QuestionBankFileEditService edits, QuestionSourceLinkService sourceLinks,
            QuestionSourceNavigationAdapter sourceNavigation, Consumer<QuestionBankFile> save) {
        model = new QuestionBankEditorModel(bank);
        this.workspace = workspace;
        this.edits = edits;
        this.sourceLinks = sourceLinks;
        this.sourceNavigation = sourceNavigation;
        this.save = save;
        setId("question-bank-editor");
        getStyleClass().add("qbank-editor");
        setMaxWidth(820);
        setSpacing(16);
        errors.managedProperty().bind(javafx.beans.binding.Bindings.isNotEmpty(errors.getChildren()));
        getChildren().addAll(EditorUi.toolbar("题库编辑", "qbank-save", this::save), errors, body);
        EditorUi.saveShortcut(this, this::save);
        render();
    }

    boolean dirty() { return model.dirty(); }
    void refreshSources() { if (sourceRows != null) sourceRows.refresh(); }

    private void save() {
        errors.getChildren().clear();
        try { save.accept(model.bank()); }
        catch (RuntimeException error) { showError(error.getMessage()); }
    }

    private void showError(String message) {
        errors.getChildren().setAll(UiTheme.label(message == null ? "Could not save QuestionBank" : message,
                "editor-error"));
    }

    private void render() {
        sourceRows = null;
        body.getChildren().clear();
        TextField bankTitle = new TextField(model.bank().title());
        bankTitle.setId("qbank-title");
        bankTitle.setPromptText("题库标题");
        bankTitle.getStyleClass().add("editor-document-title");
        bankTitle.textProperty().addListener((obs, old, text) -> model.setTitle(text));
        body.getChildren().add(bankTitle);

        Button previous = UiTheme.iconButton("arrow-left", "上一题", () -> { });
        previous.setId("qbank-editor-previous");
        previous.setDisable(index == 0);
        previous.setOnAction(event -> { index--; render(); });
        Button next = UiTheme.iconButton("arrow", "下一题", () -> { });
        next.setId("qbank-editor-next");
        next.setDisable(index >= model.bank().questions().size() - 1);
        next.setOnAction(event -> { index++; render(); });
        MenuButton add = EditorUi.menu("plus", "添加题目");
        add.setId("qbank-add-question");
        for (String type : List.of("SINGLE_CHOICE", "MULTIPLE_CHOICE")) {
            MenuItem item = new MenuItem(type.equals("SINGLE_CHOICE") ? "单选题" : "多选题");
            item.setOnAction(event -> { index = model.addQuestion(type); render(); });
            add.getItems().add(item);
        }
        Label position = UiTheme.label(model.bank().questions().isEmpty() ? "0 / 0"
                : (index + 1) + " / " + model.bank().questions().size(), "muted");
        position.setId("qbank-editor-position");
        HBox navigation = new HBox(10, previous, position, next, add);
        navigation.setAlignment(Pos.CENTER_LEFT);
        navigation.getStyleClass().add("qbank-edit-navigation");
        body.getChildren().add(navigation);
        if (model.bank().questions().isEmpty()) {
            body.getChildren().add(UiTheme.quietState("还没有题目", "点击 +，选择单选题或多选题开始编辑。"));
            return;
        }
        QuestionBankFile.Entry question = model.bank().questions().get(index);
        MenuButton actions = EditorUi.menu("more", "题目操作");
        actions.setId("qbank-question-actions");
        MenuItem duplicate = new MenuItem("复制题目");
        duplicate.setOnAction(event -> { index = model.duplicateQuestion(index); render(); });
        MenuItem delete = new MenuItem("删除题目");
        delete.setOnAction(event -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "This question and its options will be removed.", ButtonType.CANCEL, ButtonType.OK);
            confirm.setHeaderText("Delete this question?");
            UiTheme.apply(confirm);
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
            model.deleteQuestion(index);
            index = Math.min(index, Math.max(0, model.bank().questions().size() - 1));
            render();
        });
        actions.getItems().addAll(duplicate, delete);
        ChoiceBox<String> type = new ChoiceBox<>(FXCollections.observableArrayList("SINGLE_CHOICE", "MULTIPLE_CHOICE"));
        type.setId("qbank-question-type");
        type.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(String value) { return "SINGLE_CHOICE".equals(value) ? "单选题" : "多选题"; }
            @Override public String fromString(String value) { return "单选题".equals(value) ? "SINGLE_CHOICE" : "MULTIPLE_CHOICE"; }
        });
        type.setValue(question.type());
        type.valueProperty().addListener((obs, old, value) -> { model.setType(index, value); render(); });
        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        navigation.getChildren().addAll(spacer, type, actions);

        TextArea stem = area(question.stem(), "qbank-question-stem", 3);
        stem.textProperty().addListener((obs, old, value) -> model.setStem(index, value));
        stem.setPromptText("输入题干…");
        body.getChildren().addAll(UiTheme.label("题干", "editor-caption"), stem,
                UiTheme.label("选项 · 勾选正确答案", "editor-caption"));
        ToggleGroup correctGroup = new ToggleGroup();
        VBox options = new VBox(8);
        for (int i = 0; i < question.data().options().size(); i++) {
            final int optionIndex = i;
            var option = question.data().options().get(i);
            TextField content = new TextField(option.content());
            content.setId("qbank-option-" + i);
            content.textProperty().addListener((obs, old, value) -> model.setOptionContent(index, optionIndex, value));
            HBox.setHgrow(content, Priority.ALWAYS);
            javafx.scene.control.ButtonBase correct;
            if ("SINGLE_CHOICE".equals(question.type())) {
                RadioButton radio = new RadioButton();
                radio.setToggleGroup(correctGroup);
                radio.setSelected(question.data().correctOptionIds().contains(option.id()));
                radio.setOnAction(event -> model.setCorrect(index, option.id(), true));
                correct = radio;
            } else {
                CheckBox check = new CheckBox();
                check.setSelected(question.data().correctOptionIds().contains(option.id()));
                check.setOnAction(event -> model.setCorrect(index, option.id(), check.isSelected()));
                correct = check;
            }
            correct.setId("qbank-correct-" + i);
            correct.setAccessibleText("将选项 " + (char) ('A' + i) + " 设为正确答案");
            Button remove = UiTheme.iconButton("close", "删除选项", () -> { });
            remove.setId("qbank-remove-option-" + i);
            remove.setOnAction(event -> { model.deleteOption(index, optionIndex); render(); });
            HBox row = new HBox(10, correct, UiTheme.label(String.valueOf((char) ('A' + i)), "muted"), content, remove);
            row.setAlignment(Pos.CENTER_LEFT);
            content.setMinWidth(40);
            options.getChildren().add(row);
        }
        Button addOption = UiTheme.button("添加选项", "plus", "text-action", () -> { });
        addOption.setId("qbank-add-option");
        addOption.setOnAction(event -> { model.addOption(index); render(); });
        options.getChildren().add(addOption);
        body.getChildren().add(options);

        TextArea analysis = area(question.analysis(), "qbank-analysis", 4);
        analysis.textProperty().addListener((obs, old, value) -> model.setAnalysis(index, value));
        body.getChildren().addAll(UiTheme.label("解析", "editor-caption"), analysis,
                UiTheme.label("引用来源", "editor-caption"));
        VBox refs = new VBox(6);
        sourceRows = new QuestionSourceListView(question.sourceRefs(), workspace, sourceNavigation, (refIndex, row) -> {
            Button change = UiTheme.button("更换", "refresh", "text-action", () -> { });
            change.setId("qbank-change-source-" + refIndex);
            change.setOnAction(event -> chooseSource(refIndex));
            Button remove = UiTheme.iconButton("close", "移除引用", () -> { });
            remove.setId("qbank-remove-source-" + refIndex);
            remove.setOnAction(event -> { model.deleteSourceRef(index, refIndex); render(); });
            row.getChildren().addAll(change, remove);
        });
        refs.getChildren().add(sourceRows);
        Button addSource = UiTheme.button("添加来源", "plus", "text-action", () -> { });
        addSource.setId("qbank-add-source");
        addSource.setOnAction(event -> chooseSource(-1));
        refs.getChildren().add(addSource);
        TextField sourceLink = new TextField();
        sourceLink.setId("qbank-source-link");
        sourceLink.setPromptText("粘贴 Source Anchor 链接…");
        Button useLink = UiTheme.button("使用链接", "plus", "text-action", () -> { });
        useLink.setId("qbank-use-source-link");
        useLink.setOnAction(event -> {
            try {
                var ref = sourceLinks.resolve(workspace, sourceLink.getText());
                model.addSourceRef(index, ref);
                errors.getChildren().clear();
                render();
            } catch (RuntimeException error) { showError(error.getMessage()); }
        });
        HBox linkInput = new HBox(8, sourceLink, useLink);
        HBox.setHgrow(sourceLink, Priority.ALWAYS);
        refs.getChildren().add(linkInput);
        body.getChildren().add(refs);
    }

    private TextArea area(String value, String id, int rows) {
        return EditorUi.content(value, id, false);
    }

    private void chooseSource(int replaceIndex) {
        try {
            List<Asset> available = edits.availableSources(workspace);
            if (available.isEmpty()) { showError("No valid Markdown source is available."); return; }
            ChoiceBox<Asset> documents = new ChoiceBox<>(FXCollections.observableArrayList(available));
            documents.setConverter(new javafx.util.StringConverter<>() {
                @Override public String toString(Asset asset) { return asset == null ? "" : asset.title(); }
                @Override public Asset fromString(String text) { return null; }
            });
            ChoiceBox<SourceDocumentSnapshot.Chapter> chapters = new ChoiceBox<>();
            chapters.setConverter(new javafx.util.StringConverter<>() {
                @Override public String toString(SourceDocumentSnapshot.Chapter chapter) {
                    return chapter == null ? "" : chapter.title();
                }
                @Override public SourceDocumentSnapshot.Chapter fromString(String text) { return null; }
            });
            ChoiceBox<SourceDocumentSnapshot.Section> sections = new ChoiceBox<>();
            sections.setConverter(new javafx.util.StringConverter<>() {
                @Override public String toString(SourceDocumentSnapshot.Section section) {
                    return section == null ? "" : section.title();
                }
                @Override public SourceDocumentSnapshot.Section fromString(String text) { return null; }
            });
            documents.valueProperty().addListener((obs, old, selected) -> {
                chapters.getItems().clear();
                sections.getItems().clear();
                if (selected != null) {
                    chapters.getItems().setAll(edits.source(workspace, selected.assetId()).chapters());
                    chapters.getSelectionModel().selectFirst();
                }
            });
            chapters.valueProperty().addListener((obs, old, selected) -> {
                sections.getItems().clear();
                if (selected != null) {
                    sections.getItems().setAll(selected.sections());
                    sections.getSelectionModel().selectFirst();
                }
            });
            documents.getSelectionModel().selectFirst();
            VBox form = new VBox(8, UiTheme.label("Document", "field-label"), documents,
                    UiTheme.label("Chapter", "field-label"), chapters,
                    UiTheme.label("Section", "field-label"), sections);
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle("Select Markdown source");
            UiTheme.apply(dialog);
            dialog.getDialogPane().setContent(form);
            ButtonType choose = new ButtonType("Use source", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, choose);
            if (dialog.showAndWait().orElse(ButtonType.CANCEL) != choose) return;
            Asset asset = documents.getValue();
            SourceDocumentSnapshot.Section section = sections.getValue();
            if (asset == null || section == null) { showError("Select a document and Section."); return; }
            SourceDocumentSnapshot snapshot = edits.source(workspace, asset.assetId());
            boolean valid = snapshot.chapters().stream().flatMap(chapter -> chapter.sections().stream())
                    .anyMatch(candidate -> candidate.id().equals(section.id()));
            if (!valid) { showError("Selected Section is no longer available."); return; }
            var ref = QuestionBankFile.SourceRef.anchor(snapshot.assetId(), snapshot.contentId(),
                    section.id(), 1, snapshot.title(), section.title());
            if (replaceIndex < 0) model.addSourceRef(index, ref);
            else model.replaceSourceRef(index, replaceIndex, ref);
            errors.getChildren().clear();
            render();
        } catch (RuntimeException error) { showError(error.getMessage()); }
    }
}
