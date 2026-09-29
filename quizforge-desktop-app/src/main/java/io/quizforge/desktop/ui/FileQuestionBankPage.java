package io.quizforge.desktop.ui;

import io.quizforge.core.question.*;

import io.quizforge.core.QuizForgeException;
import io.quizforge.core.ai.AiSettingsService;
import io.quizforge.core.asset.Asset;
import io.quizforge.core.workspace.Workspace;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/** Minimal file-backed surface; all displayed questions are parsed from .qbank. */
final class FileQuestionBankPage {
    private final FileQuestionBankGenerationService questions;
    private final QuestionBankReferenceResolver references;
    private final AiSettingsService settings;
    private final Stage stage;
    private boolean running;

    FileQuestionBankPage(FileQuestionBankGenerationService questions,
            QuestionBankReferenceResolver references, AiSettingsService settings, Stage stage) {
        this.questions = questions;
        this.references = references;
        this.settings = settings;
        this.stage = stage;
    }

    Node content(Workspace workspace, Runnable refresh) {
        VBox page = new VBox(18);
        page.getStyleClass().add("content-page");
        Button generate = new Button("Generate .qbank", UiTheme.icon("spark"));
        generate.getStyleClass().add("primary");
        generate.setDisable(running);
        Label progress = UiTheme.label(running ? "Generating..." : "", "muted");
        generate.setOnAction(event -> generate(workspace, generate, progress, refresh));
        page.getChildren().addAll(new HBox(12, UiTheme.label("Question banks", "page-title"), generate),
                UiTheme.label("Portable QuestionBank files in this workspace", "muted"));
        List<Asset> banks = questions.listBanks(workspace.id());
        if (banks.isEmpty()) {
            page.getChildren().add(UiTheme.emptyState("book", "No QuestionBank files yet",
                    "Generate from one or more formal knowledge documents."));
        }
        for (Asset asset : banks) {
            try { showBank(page, workspace, asset); }
            catch (RuntimeException error) {
                page.getChildren().add(UiTheme.label(asset.currentPath() + ": invalid .qbank", "warning"));
            }
        }
        page.getChildren().add(progress);
        return UiTheme.scroll(page);
    }

    private void showBank(VBox page, Workspace workspace, Asset asset) {
        QuestionBank bank = questions.read(workspace.id(), asset.assetId());
        page.getChildren().addAll(UiTheme.label(bank.title(), "section-title"),
                UiTheme.label(bank.assetId() + "  ·  " + asset.currentPath(), "muted"),
                UiTheme.label(bank.questions().size() + " questions  ·  "
                        + bank.sourceDocuments().size() + " source documents", "muted"));
        for (var resolution : references.resolve(workspace.id(), bank)) {
            page.getChildren().add(UiTheme.label(resolution.source().title() + ": " + resolution.status()
                    + (resolution.ambiguous() ? " (ambiguous: " + resolution.candidates().size() + ")" : ""),
                    "muted"));
        }
        int index = 1;
        for (Question question : bank.questions()) {
            VBox card = new VBox(10, UiTheme.label(String.format("%02d  /  %s", index++, question.type()), "eyebrow"),
                    wrapping(QuestionText.prompt(question)));
            card.getStyleClass().add("question-card");
            List<String> correct = new ArrayList<>();
            for (int i = 0; i < question.choicePayload().options().size(); i++) {
                ChoiceOption option = question.choicePayload().options().get(i);
                String label = Character.toString('A' + i);
                card.getChildren().add(UiTheme.label(label + ".  " + QuestionText.option(option), "question-option"));
                if (question.choiceAnswerSpec().correctOptionIds().contains(option.id())) correct.add(label);
            }
            TitledPane reveal = new TitledPane("Answer & explanation", new VBox(8,
                    UiTheme.label(String.join(", ", correct), "answer"), wrapping(QuestionText.analysis(question))));
            reveal.setExpanded(false);
            card.getChildren().add(reveal);
            page.getChildren().add(card);
        }
    }

    private void generate(Workspace workspace, Button button, Label progress, Runnable refresh) {
        if (running) return;
        if (!settings.hasCredential()) { info("AI Provider required", "Configure an AI Provider first."); return; }
        List<Asset> available = questions.listDocuments(workspace.id());
        if (available.isEmpty()) { info("No documents", "Add a valid StandardDocument .md first."); return; }
        TextField title = new TextField("New QuestionBank");
        TextField count = new TextField("10");
        CheckBox single = new CheckBox("Single Choice");
        CheckBox multiple = new CheckBox("Multiple Choice");
        single.setSelected(true);
        multiple.setSelected(true);
        VBox form = new VBox(9, new Label("Title"), title, new Label("Documents and scopes"));
        List<DocumentRow> rows = new ArrayList<>();
        for (Asset asset : available) {
            try {
                DocumentRow row = new DocumentRow(asset, questions.inspectDocument(workspace.id(), asset.assetId()));
                rows.add(row);
                form.getChildren().add(new HBox(10, row.enabled, row.scope));
            } catch (RuntimeException ignored) { /* A changed or invalid document is unavailable. */ }
        }
        if (rows.isEmpty()) { info("No valid documents", "All discovered documents failed validation."); return; }
        form.getChildren().addAll(new Label("Question types"), single, multiple,
                new Label("Count (1–50)"), count);
        form.setPadding(new Insets(10));
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(stage);
        UiTheme.apply(dialog);
        dialog.setTitle("Generate QuestionBank file");
        ScrollPane scroll = UiTheme.scroll(form);
        scroll.setPrefViewportHeight(460);
        scroll.setPrefViewportWidth(520);
        dialog.getDialogPane().setContent(scroll);
        ButtonType submit = new ButtonType("Generate");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, submit);
        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != submit) return;
        int amount;
        try { amount = Integer.parseInt(count.getText().trim()); }
        catch (NumberFormatException error) { info("Invalid count", "Enter a number from 1 to 50."); return; }
        Set<QuestionType> types = EnumSet.noneOf(QuestionType.class);
        if (single.isSelected()) types.add(QuestionType.SINGLE_CHOICE);
        if (multiple.isSelected()) types.add(QuestionType.MULTIPLE_CHOICE);
        List<StandardDocumentSelection> selections = rows.stream().filter(row -> row.enabled.isSelected())
                .map(row -> row.scope.getValue().selection(row.asset.assetId())).toList();
        running = true;
        button.setDisable(true);
        Task<FileQuestionBankGenerationService.Outcome> task = new Task<>() {
            @Override protected FileQuestionBankGenerationService.Outcome call() {
                return questions.create(workspace.id(), title.getText(), selections, types, amount, this::updateMessage);
            }
        };
        progress.textProperty().bind(task.messageProperty());
        task.setOnSucceeded(event -> {
            running = false;
            progress.textProperty().unbind();
            var result = task.getValue();
            refresh.run();
            info("QuestionBank saved", result.bank().title() + "\n" + result.asset().assetId()
                    + "\n" + result.asset().currentPath() + "\nAccepted: " + result.accepted()
                    + "; rejected: " + result.rejected() + "; requested: " + result.requested());
        });
        task.setOnFailed(event -> {
            running = false;
            button.setDisable(false);
            progress.textProperty().unbind();
            progress.setText("Generation failed. Existing files were kept.");
            Throwable error = task.getException();
            info("Could not generate QuestionBank", error instanceof QuizForgeException known
                    ? known.code() + ": " + known.getMessage() : "An unexpected error occurred.");
        });
        Thread worker = new Thread(task, "quizforge-file-question-generation");
        worker.setDaemon(true);
        worker.start();
    }

    private Label wrapping(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        return label;
    }

    private void info(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        alert.initOwner(stage);
        UiTheme.apply(alert);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private record ScopeChoice(String label, GenerationScopeType type, String chapterId, String sectionId) {
        StandardDocumentSelection selection(String assetId) {
            return new StandardDocumentSelection(assetId, type, chapterId, sectionId);
        }
        @Override public String toString() { return label; }
    }

    private static final class DocumentRow {
        private final Asset asset;
        private final CheckBox enabled;
        private final ComboBox<ScopeChoice> scope = new ComboBox<>();

        private DocumentRow(Asset asset, SourceDocumentSnapshot document) {
            this.asset = asset;
            enabled = new CheckBox(document.title());
            enabled.setSelected(true);
            scope.getItems().add(new ScopeChoice("Entire document", GenerationScopeType.DOCUMENT, null, null));
            for (SourceDocumentSnapshot.Chapter chapter : document.chapters()) {
                scope.getItems().add(new ScopeChoice("Chapter: " + chapter.title(),
                        GenerationScopeType.CHAPTER, chapter.id(), null));
                for (SourceDocumentSnapshot.Section section : chapter.sections()) {
                    scope.getItems().add(new ScopeChoice("Section: " + section.title(),
                            GenerationScopeType.SECTION, chapter.id(), section.id()));
                }
            }
            scope.getSelectionModel().selectFirst();
        }
    }
}
