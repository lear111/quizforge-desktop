package io.quizforge.desktop.ui;

import io.quizforge.core.QuizForgeException;
import io.quizforge.core.ai.AiSettingsService;
import io.quizforge.core.question.GenerationScopeType;
import io.quizforge.core.question.Question;
import io.quizforge.core.question.QuestionBank;
import io.quizforge.core.question.QuestionBankView;
import io.quizforge.core.question.QuestionGenerationCommand;
import io.quizforge.core.question.QuestionGenerationOutcome;
import io.quizforge.core.question.QuestionGenerationService;
import io.quizforge.core.question.QuestionOption;
import io.quizforge.core.question.QuestionType;
import io.quizforge.core.workspace.Workspace;
import io.quizforge.extension.document.StandardDocumentStructure;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

final class QuestionBankPage {
    private final QuestionGenerationService questions;
    private final AiSettingsService settings;
    private final Stage stage;
    private boolean running;

    QuestionBankPage(QuestionGenerationService questions, AiSettingsService settings, Stage stage) {
        this.questions = questions;
        this.settings = settings;
        this.stage = stage;
    }

    Node content(Workspace workspace, Runnable refresh) {
        VBox body = new VBox(18);
        body.getStyleClass().add("content-page");
        Optional<QuestionBankView> current = questions.findByWorkspace(workspace.id());
        Label progress = UiTheme.label("", "muted");
        Button generate = new Button(current.isPresent() ? "Regenerate" : "Generate questions", UiTheme.icon("spark"));
        generate.getStyleClass().add("primary");
        generate.setDisable(running);
        if (running) progress.setText("Generating question bank...");
        generate.setOnAction(event -> generate(workspace, generate, progress, refresh));
        Region space = new Region();
        HBox.setHgrow(space, Priority.ALWAYS);
        HBox header = new HBox(12, UiTheme.label("Question bank", "page-title"), space, generate);
        header.setAlignment(Pos.CENTER_LEFT);
        body.getChildren().addAll(header, UiTheme.label("Make what you’ve learned stick.", "muted"));
        if (current.isEmpty()) {
            body.getChildren().add(UiTheme.emptyState("book", "A little practice goes a long way",
                    "Generate a standard document first, then create questions from any chapter or section."));
        } else {
            renderBank(body, current.get());
        }
        body.getChildren().add(progress);
        return UiTheme.scroll(body);
    }

    private void renderBank(VBox body, QuestionBankView view) {
        QuestionBank bank = view.bank();
        Label name = UiTheme.label(bank.name(), "section-title");
        long single = bank.questions().stream().filter(q -> q.type() == QuestionType.SINGLE_CHOICE).count();
        long multiple = bank.questions().size() - single;
        body.getChildren().addAll(name,
                UiTheme.label(bank.questions().size() + " questions  ·  " + single
                        + " single choice  ·  " + multiple + " multiple choice", "muted"),
                UiTheme.label("Source: " + scopeLabel(bank) + "  ·  Generated " + bank.generatedAt(), "muted"));
        if (view.outdated()) {
            Label warning = new Label("The standard document has changed since this question bank was generated.\n"
                    + "This question bank may be outdated.");
            warning.setWrapText(true);
            warning.getStyleClass().add("warning");
            body.getChildren().add(warning);
        }
        for (Question question : bank.questions()) {
            VBox card = new VBox(12);
            card.getStyleClass().add("question-card");
            card.getChildren().addAll(UiTheme.label(String.format("%02d", question.sortOrder()) + "  /  "
                    + (question.type() == QuestionType.SINGLE_CHOICE ? "SINGLE CHOICE" : "MULTIPLE CHOICE"), "eyebrow"),
                    UiTheme.label(question.stem(), "question-stem"));
            List<String> answers = new ArrayList<>();
            for (QuestionOption option : question.options()) {
                card.getChildren().add(UiTheme.label(option.key() + ".   " + option.content(), "question-option"));
                if (option.correct()) answers.add(option.key());
            }
            VBox explanation = new VBox(10, UiTheme.label("Correct answer: " + String.join(", ", answers), "answer"),
                    wrapping(question.analysis()));
            TitledPane reveal = new TitledPane("Answer & explanation", explanation);
            reveal.setExpanded(false);
            reveal.setAnimated(false);
            card.getChildren().addAll(reveal,
                    UiTheme.label(question.sourceChapter() + " / " + question.sourceSection(), "muted"));
            body.getChildren().add(card);
        }
    }

    private Label wrapping(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        return label;
    }

    private String scopeLabel(QuestionBank bank) {
        return switch (bank.generationScopeType()) {
            case DOCUMENT -> "Entire Document";
            case CHAPTER -> bank.sourceChapter();
            case SECTION -> bank.sourceChapter() + " / " + bank.sourceSection();
            case SUBSECTION -> bank.sourceChapter() + " / " + bank.sourceSection();
        };
    }

    private void generate(Workspace workspace, Button button, Label progress, Runnable refresh) {
        if (running) return;
        if (!settings.hasCredential()) {
            info("AI Provider required", "Configure an AI Provider in AI Settings first.");
            return;
        }
        StandardDocumentStructure structure;
        try { structure = questions.structure(workspace.id()); }
        catch (RuntimeException failure) { failure(failure); return; }

        TextField name = new TextField(structure.title() + "题库");
        RadioButton whole = new RadioButton("Entire Document");
        RadioButton chapterRadio = new RadioButton("Chapter");
        RadioButton sectionRadio = new RadioButton("Section");
        ToggleGroup scopes = new ToggleGroup();
        whole.setToggleGroup(scopes);
        chapterRadio.setToggleGroup(scopes);
        sectionRadio.setToggleGroup(scopes);
        whole.setSelected(true);
        ComboBox<Choice> chapters = new ComboBox<>();
        for (StandardDocumentStructure.Chapter chapter : structure.chapters()) {
            chapters.getItems().add(new Choice(chapter.id(), chapter.title()));
        }
        if (!chapters.getItems().isEmpty()) chapters.getSelectionModel().selectFirst();
        ComboBox<Choice> sections = new ComboBox<>();
        Runnable fillSections = () -> {
            sections.getItems().clear();
            Choice selected = chapters.getValue();
            if (selected != null) {
                structure.chapters().stream().filter(c -> c.id().equals(selected.id())).findFirst()
                        .ifPresent(c -> c.sections().forEach(s -> sections.getItems().add(new Choice(s.id(), s.title()))));
            }
            if (!sections.getItems().isEmpty()) sections.getSelectionModel().selectFirst();
        };
        chapters.valueProperty().addListener((obs, oldValue, newValue) -> fillSections.run());
        fillSections.run();
        Runnable updateScope = () -> {
            chapters.setDisable(whole.isSelected());
            sections.setDisable(!sectionRadio.isSelected());
        };
        scopes.selectedToggleProperty().addListener((obs, oldValue, newValue) -> updateScope.run());
        updateScope.run();
        CheckBox single = new CheckBox("Single Choice");
        CheckBox multiple = new CheckBox("Multiple Choice");
        single.setSelected(true);
        multiple.setSelected(true);
        TextField count = new TextField("10");
        VBox form = new VBox(8, new Label("Question Bank Name"), name,
                new Label("Generation Scope"), whole, chapterRadio, sectionRadio,
                new Label("Chapter"), chapters, new Label("Section"), sections,
                new Label("Question Types"), single, multiple,
                new Label("Question Count (1–50)"), count);
        form.setPadding(new Insets(10));
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(stage);
        UiTheme.apply(dialog);
        dialog.setTitle("Generate Question Bank");
        ScrollPane formScroll = UiTheme.scroll(form);
        formScroll.setPrefViewportHeight(460);
        formScroll.setPrefViewportWidth(440);
        dialog.getDialogPane().setContent(formScroll);
        ButtonType submit = new ButtonType("Generate");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, submit);
        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != submit) return;
        int amount;
        try { amount = Integer.parseInt(count.getText().trim()); }
        catch (NumberFormatException error) { info("Invalid count", "Enter a number from 1 to 50."); return; }
        Set<QuestionType> types = EnumSet.noneOf(QuestionType.class);
        if (single.isSelected()) types.add(QuestionType.SINGLE_CHOICE);
        if (multiple.isSelected()) types.add(QuestionType.MULTIPLE_CHOICE);
        GenerationScopeType scope = whole.isSelected() ? GenerationScopeType.DOCUMENT
                : chapterRadio.isSelected() ? GenerationScopeType.CHAPTER : GenerationScopeType.SECTION;
        QuestionGenerationCommand command = new QuestionGenerationCommand(name.getText(), scope,
                chapters.getValue() == null ? null : chapters.getValue().id(),
                sections.getValue() == null ? null : sections.getValue().id(), types, amount);
        running = true;
        button.setDisable(true);
        progress.setText("Generating question bank...");
        Task<QuestionGenerationOutcome> task = new Task<>() {
            @Override protected QuestionGenerationOutcome call() {
                return questions.generate(workspace.id(), command, this::updateMessage);
            }
        };
        progress.textProperty().bind(task.messageProperty());
        task.setOnSucceeded(event -> {
            running = false;
            progress.textProperty().unbind();
            QuestionGenerationOutcome outcome = task.getValue();
            refresh.run();
            if (outcome.rejected() > 0 || outcome.accepted() < outcome.requested()) {
                info("Question bank generated", outcome.accepted() + " questions generated successfully.\n"
                        + outcome.rejected() + " invalid questions were discarded.\n"
                        + "Requested: " + outcome.requested() + ", returned: " + outcome.generated() + ".");
            }
        });
        task.setOnFailed(event -> {
            running = false;
            button.setDisable(false);
            progress.textProperty().unbind();
            progress.setText("Generation failed. Existing question bank was kept.");
            failure(task.getException());
        });
        Thread worker = new Thread(task, "quizforge-question-generation");
        worker.setDaemon(true);
        worker.start();
    }

    private void failure(Throwable failure) {
        if (failure instanceof QuizForgeException known) info("Could not generate question bank",
                known.code() + ": " + known.getMessage());
        else info("Could not generate question bank", "An unexpected error occurred.");
    }

    private void info(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        alert.initOwner(stage);
        UiTheme.apply(alert);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private record Choice(String id, String title) {
        @Override public String toString() { return title; }
    }
}
