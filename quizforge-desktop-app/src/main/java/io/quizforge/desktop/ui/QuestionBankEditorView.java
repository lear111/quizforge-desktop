package io.quizforge.desktop.ui;

import io.quizforge.core.asset.Asset;
import io.quizforge.core.question.QuestionBankEditorModel;
import io.quizforge.core.question.QuestionBankFile;
import io.quizforge.core.question.QuestionBankFileEditService;
import io.quizforge.core.question.SourceDocumentSnapshot;
import io.quizforge.core.workspace.WorkspaceId;
import java.util.List;
import java.util.function.Consumer;
import javafx.collections.FXCollections;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** One-question editor. Every control mutates the same portable bank model used by Practice. */
final class QuestionBankEditorView extends VBox {
    private final QuestionBankEditorModel model;
    private final WorkspaceId workspace;
    private final QuestionBankFileEditService edits;
    private final Consumer<QuestionBankFile> save;
    private final VBox body = new VBox(14);
    private final VBox errors = new VBox(3);
    private int index;

    QuestionBankEditorView(QuestionBankFile bank, WorkspaceId workspace,
            QuestionBankFileEditService edits, Consumer<QuestionBankFile> save) {
        model = new QuestionBankEditorModel(bank);
        this.workspace = workspace;
        this.edits = edits;
        this.save = save;
        setId("question-bank-editor");
        getStyleClass().add("qbank-editor");
        setMaxWidth(820);
        setSpacing(16);
        Button saveButton = UiTheme.button("Save QuestionBank", "check", "primary-button", this::save);
        saveButton.setId("qbank-save");
        getChildren().addAll(saveButton, errors, body);
        render();
    }

    boolean dirty() { return model.dirty(); }

    private void save() {
        errors.getChildren().clear();
        try { save.accept(model.bank()); }
        catch (RuntimeException error) { showError(error.getMessage()); }
    }

    private void showError(String message) {
        errors.getChildren().setAll(UiTheme.label(message == null ? "Could not save QuestionBank" : message,
                "qdoc-error"));
    }

    private void render() {
        body.getChildren().clear();
        TextField bankTitle = new TextField(model.bank().title());
        bankTitle.setId("qbank-title");
        bankTitle.setPromptText("QuestionBank title");
        bankTitle.textProperty().addListener((obs, old, text) -> model.setTitle(text));
        body.getChildren().add(bankTitle);

        Button previous = new Button("←");
        previous.setId("qbank-editor-previous");
        previous.setDisable(index == 0);
        previous.setOnAction(event -> { index--; render(); });
        Button next = new Button("→");
        next.setId("qbank-editor-next");
        next.setDisable(index >= model.bank().questions().size() - 1);
        next.setOnAction(event -> { index++; render(); });
        MenuButton add = new MenuButton("+");
        add.setId("qbank-add-question");
        for (String type : List.of("SINGLE_CHOICE", "MULTIPLE_CHOICE")) {
            MenuItem item = new MenuItem(type.equals("SINGLE_CHOICE") ? "Single Choice" : "Multiple Choice");
            item.setOnAction(event -> { index = model.addQuestion(type); render(); });
            add.getItems().add(item);
        }
        Label position = UiTheme.label(model.bank().questions().isEmpty() ? "0 / 0"
                : (index + 1) + " / " + model.bank().questions().size(), "muted");
        position.setId("qbank-editor-position");
        body.getChildren().add(new HBox(12, previous, position, next, add));
        if (model.bank().questions().isEmpty()) {
            body.getChildren().add(UiTheme.quietState("No questions", "Add a question to begin."));
            return;
        }
        QuestionBankFile.Entry question = model.bank().questions().get(index);
        MenuButton actions = new MenuButton("⋯");
        actions.setId("qbank-question-actions");
        MenuItem duplicate = new MenuItem("Duplicate Question");
        duplicate.setOnAction(event -> { index = model.duplicateQuestion(index); render(); });
        MenuItem delete = new MenuItem("Delete Question");
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
        type.setValue(question.type());
        type.valueProperty().addListener((obs, old, value) -> { model.setType(index, value); render(); });
        body.getChildren().add(new HBox(12, UiTheme.label("Type", "field-label"), type, actions));

        TextArea stem = area(question.stem(), "qbank-question-stem", 3);
        stem.textProperty().addListener((obs, old, value) -> model.setStem(index, value));
        body.getChildren().addAll(UiTheme.label("Question", "field-label"), stem,
                UiTheme.label("Options", "field-label"));
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
            Button remove = new Button("×");
            remove.setId("qbank-remove-option-" + i);
            remove.setOnAction(event -> { model.deleteOption(index, optionIndex); render(); });
            options.getChildren().add(new HBox(8, UiTheme.label(String.valueOf((char) ('A' + i)), "muted"),
                    content, correct, remove));
        }
        Button addOption = new Button("+ Option");
        addOption.setId("qbank-add-option");
        addOption.setOnAction(event -> { model.addOption(index); render(); });
        options.getChildren().add(addOption);
        body.getChildren().add(options);

        TextArea analysis = area(question.analysis(), "qbank-analysis", 4);
        analysis.textProperty().addListener((obs, old, value) -> model.setAnalysis(index, value));
        body.getChildren().addAll(UiTheme.label("Analysis", "field-label"), analysis,
                UiTheme.label("Source", "field-label"));
        VBox refs = new VBox(6);
        for (int i = 0; i < question.sourceRefs().size(); i++) {
            final int refIndex = i;
            var ref = question.sourceRefs().get(i);
            Button change = new Button(ref.documentTitle() + " → " + ref.sectionTitle());
            change.setId("qbank-source-" + i);
            change.setOnAction(event -> chooseSource(refIndex));
            Button remove = new Button("×");
            remove.setId("qbank-remove-source-" + i);
            remove.setOnAction(event -> { model.deleteSourceRef(index, refIndex); render(); });
            refs.getChildren().add(new HBox(8, change, remove));
        }
        Button addSource = new Button("+ Source");
        addSource.setId("qbank-add-source");
        addSource.setOnAction(event -> chooseSource(-1));
        refs.getChildren().add(addSource);
        body.getChildren().add(refs);
    }

    private TextArea area(String value, String id, int rows) {
        TextArea area = new TextArea(value);
        area.setId(id);
        area.setPrefRowCount(rows);
        area.setWrapText(true);
        return area;
    }

    private void chooseSource(int replaceIndex) {
        try {
            List<Asset> available = edits.availableSources(workspace);
            if (available.isEmpty()) { showError("No valid QDoc source is available."); return; }
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
            dialog.setTitle("Select QDoc source");
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
            var ref = new QuestionBankFile.SourceRef(snapshot.assetId(), snapshot.contentId(),
                    section.id(), snapshot.title(), section.title());
            if (replaceIndex < 0) model.addSourceRef(index, ref);
            else model.replaceSourceRef(index, replaceIndex, ref);
            errors.getChildren().clear();
            render();
        } catch (RuntimeException error) { showError(error.getMessage()); }
    }
}
