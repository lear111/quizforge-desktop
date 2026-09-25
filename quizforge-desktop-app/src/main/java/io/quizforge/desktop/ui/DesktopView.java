package io.quizforge.desktop.ui;

import io.quizforge.core.QuizForgeException;
import io.quizforge.core.ai.AiConnectionService;
import io.quizforge.core.ai.AiSettingsService;
import io.quizforge.core.document.DocumentNormalizationService;
import io.quizforge.core.document.StandardDocumentView;
import io.quizforge.core.material.Material;
import io.quizforge.core.material.MaterialService;
import io.quizforge.core.workspace.Workspace;
import io.quizforge.core.workspace.WorkspaceService;
import io.quizforge.extensions.ai.deepseek.DeepSeekAiProvider;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public final class DesktopView {
    private final WorkspaceService workspaces;
    private final MaterialService materials;
    private final DocumentNormalizationService documents;
    private final AiSettingsService settings;
    private final AiConnectionService connections;
    private BorderPane root;
    private Stage stage;
    private boolean generationRunning;

    public DesktopView(WorkspaceService workspaces, MaterialService materials,
            DocumentNormalizationService documents, AiSettingsService settings,
            AiConnectionService connections) {
        this.workspaces = workspaces;
        this.materials = materials;
        this.documents = documents;
        this.settings = settings;
        this.connections = connections;
    }

    public Scene createScene(Stage stage) {
        this.stage = stage;
        root = new BorderPane();
        root.setPadding(new Insets(24));
        showHome();
        return new Scene(root, 820, 600);
    }

    private void showHome() {
        Label title = heading("QuizForge");
        Label section = new Label("Workspaces");
        Button settingsButton = new Button("AI Settings");
        settingsButton.setOnAction(event -> showSettings());
        Button create = new Button("+ New Workspace");
        create.setOnAction(event -> createWorkspace());
        HBox header = new HBox(16, section, spacer(), settingsButton, create);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox body = new VBox(14, title, header);
        body.setPadding(new Insets(8));
        List<Workspace> entries = workspaces.listWorkspaces();
        if (entries.isEmpty()) {
            body.getChildren().add(new Label("No workspaces yet. Create one to begin."));
        } else {
            for (Workspace workspace : entries) {
                Button open = new Button(workspace.name());
                open.setMaxWidth(Double.MAX_VALUE);
                open.setAlignment(Pos.CENTER_LEFT);
                open.setOnAction(event -> showWorkspace(workspace));
                body.getChildren().add(open);
            }
        }
        root.setCenter(body);
    }

    private void createWorkspace() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.initOwner(stage);
        dialog.setTitle("New Workspace");
        dialog.setHeaderText("Create a workspace");
        dialog.setContentText("Workspace Name:");
        dialog.showAndWait().ifPresent(name -> {
            try {
                workspaces.createWorkspace(name);
                showHome();
            } catch (QuizForgeException e) {
                showError(e);
            }
        });
    }

    private void showWorkspace(Workspace workspace) {
        showWorkspace(workspace, false);
    }

    private void showWorkspace(Workspace workspace, boolean selectDocument) {
        Button back = new Button("← Workspaces");
        back.setOnAction(event -> showHome());
        Label title = heading("Workspace: " + workspace.name());
        Button settingsButton = new Button("AI Settings");
        settingsButton.setOnAction(event -> showSettings());
        HBox navigation = new HBox(12, back, spacer(), settingsButton);
        VBox header = new VBox(12, navigation, title);

        Tab materialsTab = new Tab("Materials", materialsPage(workspace));
        Tab documentTab = new Tab("Standard Document", standardDocumentPage(workspace));
        Tab questionTab = new Tab("Question Bank", placeholder("Coming later"));
        TabPane tabs = new TabPane(materialsTab, documentTab, questionTab);
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        if (selectDocument) {
            tabs.getSelectionModel().select(documentTab);
        }

        VBox page = new VBox(18, header, tabs);
        VBox.setVgrow(tabs, Priority.ALWAYS);
        root.setCenter(page);
    }

    private VBox materialsPage(Workspace workspace) {
        Label title = new Label("Materials");
        Button importButton = new Button("+ Import Markdown");
        importButton.setOnAction(event -> importMarkdown(workspace));
        HBox header = new HBox(12, title, spacer(), importButton);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox page = new VBox(12, header);
        page.setPadding(new Insets(16));
        List<Material> entries = materials.listMaterials(workspace.id());
        if (entries.isEmpty()) {
            page.getChildren().add(new Label("No materials yet. Import Markdown files to begin."));
        } else {
            for (Material material : entries) {
                page.getChildren().add(materialRow(workspace, material));
            }
        }
        return page;
    }

    private HBox materialRow(Workspace workspace, Material material) {
        Label name = new Label(material.originalFileName());
        String size = material.fileSize() < 1024
                ? material.fileSize() + " B"
                : String.format(Locale.ROOT, "%.1f KB", material.fileSize() / 1024.0);
        Label details = new Label(size + " · " + material.status());
        VBox text = new VBox(4, name, details);
        Button preview = new Button("Preview");
        preview.setOnAction(event -> preview(material));
        Button delete = new Button("Delete");
        delete.setOnAction(event -> delete(workspace, material));
        HBox row = new HBox(12, text, spacer(), preview, delete);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(8));
        return row;
    }

    private void importMarkdown(Workspace workspace) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import Markdown");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Markdown (*.md, *.markdown)", "*.md", "*.markdown"));
        List<File> selected = chooser.showOpenMultipleDialog(stage);
        if (selected == null || selected.isEmpty()) {
            return;
        }
        int imported = 0;
        List<String> failures = new ArrayList<>();
        for (File file : selected) {
            try {
                materials.importMaterial(workspace.id(), file.getAbsolutePath());
                imported++;
            } catch (QuizForgeException e) {
                failures.add(file.getName() + ": " + e.code() + " — " + e.getMessage());
            }
        }
        showWorkspace(workspace);
        if (failures.isEmpty()) {
            showInfo("Import complete", "Imported " + imported + " file(s).");
        } else {
            showInfo("Import results", "Imported " + imported + " file(s).\n\n"
                    + String.join("\n", failures));
        }
    }

    private void preview(Material material) {
        try {
            TextArea contents = new TextArea(materials.readMaterial(material.id()));
            contents.setEditable(false);
            contents.setWrapText(false);
            contents.setPrefSize(700, 500);
            Dialog<Void> dialog = new Dialog<>();
            dialog.initOwner(stage);
            dialog.setTitle(material.originalFileName());
            dialog.getDialogPane().setContent(contents);
            dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
            dialog.showAndWait();
        } catch (QuizForgeException e) {
            showError(e);
        }
    }

    private void delete(Workspace workspace, Material material) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete the QuizForge copy of " + material.originalFileName() + "?",
                ButtonType.CANCEL, ButtonType.OK);
        confirm.initOwner(stage);
        confirm.setTitle("Delete Material");
        Optional<ButtonType> choice = confirm.showAndWait();
        if (choice.isEmpty() || choice.get() != ButtonType.OK) {
            return;
        }
        try {
            materials.deleteMaterial(material.id());
            showWorkspace(workspace);
        } catch (QuizForgeException e) {
            showError(e);
        }
    }

    private VBox standardDocumentPage(Workspace workspace) {
        VBox page = new VBox(12);
        page.setPadding(new Insets(16));
        Optional<StandardDocumentView> current = documents.findByWorkspace(workspace.id());
        if (current.isEmpty()) {
            page.getChildren().addAll(new Label("No standard document yet."),
                    new Label("Select materials and generate one."));
        } else {
            StandardDocumentView view = current.get();
            var document = view.document();
            page.getChildren().addAll(heading("Standard Document"),
                    new Label("Title: " + document.title()),
                    new Label("Format: QuizForge Standard Markdown v1"),
                    new Label("Status: " + document.status()),
                    new Label("Generated from: " + document.sourceMaterialIds().size() + " materials"),
                    new Label("Generated at: " + document.updatedAt()));
            TextArea raw = new TextArea(view.content());
            raw.setEditable(false);
            raw.setWrapText(false);
            VBox.setVgrow(raw, Priority.ALWAYS);
            page.getChildren().add(raw);
        }
        Button generate = new Button(current.isEmpty() ? "Generate Standard Document" : "Regenerate");
        generate.setDisable(generationRunning);
        Label status = new Label();
        generate.setOnAction(event -> chooseMaterialsAndGenerate(workspace, status, generate));
        page.getChildren().addAll(generate, status);
        return page;
    }

    private void chooseMaterialsAndGenerate(Workspace workspace, Label status, Button generate) {
        if (generationRunning) {
            return;
        }
        if (!settings.hasCredential()) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                    "Configure an AI Provider first.", ButtonType.CANCEL,
                    new ButtonType("Open AI Settings"));
            alert.initOwner(stage);
            alert.setTitle("AI Provider required");
            if (alert.showAndWait().map(ButtonType::getText).orElse("")
                    .equals("Open AI Settings")) {
                showSettings();
            }
            return;
        }
        List<Material> entries = materials.listMaterials(workspace.id());
        if (entries.isEmpty()) {
            showInfo("No materials", "Import Markdown materials first.");
            return;
        }
        List<CheckBox> checks = new ArrayList<>();
        List<io.quizforge.core.material.MaterialId> previous = documents.findByWorkspace(workspace.id())
                .map(view -> view.document().sourceMaterialIds()).orElse(List.of());
        VBox choices = new VBox(8);
        for (Material material : entries) {
            CheckBox check = new CheckBox(material.originalFileName());
            check.setSelected(previous.isEmpty() || previous.contains(material.id()));
            checks.add(check);
            choices.getChildren().add(check);
        }
        Label selected = new Label();
        Runnable count = () -> selected.setText("Selected: "
                + checks.stream().filter(CheckBox::isSelected).count());
        checks.forEach(check -> check.selectedProperty().addListener((obs, oldValue, newValue) -> count.run()));
        count.run();
        choices.getChildren().add(selected);
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(stage);
        dialog.setTitle("Select Materials");
        dialog.setHeaderText("Generate Standard Document");
        dialog.getDialogPane().setContent(choices);
        ButtonType submit = new ButtonType("Generate");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, submit);
        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != submit) {
            return;
        }
        List<io.quizforge.core.material.MaterialId> ids = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            if (checks.get(i).isSelected()) {
                ids.add(entries.get(i).id());
            }
        }
        if (ids.isEmpty()) {
            showInfo("No materials selected", "Select at least one material.");
            return;
        }
        generationRunning = true;
        generate.setDisable(true);
        Task<StandardDocumentView> task = new Task<>() {
            @Override
            protected StandardDocumentView call() {
                return documents.generate(workspace.id(), ids, this::updateMessage);
            }
        };
        status.textProperty().bind(task.messageProperty());
        task.setOnSucceeded(event -> {
            generationRunning = false;
            status.textProperty().unbind();
            showWorkspace(workspace, true);
        });
        task.setOnFailed(event -> {
            generationRunning = false;
            generate.setDisable(false);
            status.textProperty().unbind();
            status.setText("Generation failed. Existing document was kept.");
            showFailure(task.getException());
        });
        Thread worker = new Thread(task, "quizforge-document-generation");
        worker.setDaemon(true);
        worker.start();
    }

    private void showSettings() {
        Button back = new Button("← Workspaces");
        back.setOnAction(event -> showHome());
        ComboBox<String> provider = new ComboBox<>();
        provider.getItems().add("DeepSeek");
        provider.setValue("DeepSeek");
        var current = settings.configuration();
        TextField baseUrl = new TextField(current.map(config -> config.baseUrl())
                .orElse(DeepSeekAiProvider.DEFAULT_BASE_URL));
        TextField model = new TextField(current.map(config -> config.model())
                .orElse(DeepSeekAiProvider.DEFAULT_MODEL));
        PasswordField key = new PasswordField();
        key.setPromptText("Enter a new API key to replace the saved key");
        Label keyStatus = new Label(settings.hasCredential()
                ? "API Key configured ✓" : "API Key not configured");
        Label connectionStatus = new Label();
        Button save = new Button("Save");
        save.setOnAction(event -> {
            try {
                settings.save("deepseek", baseUrl.getText(), model.getText(), key.getText());
                key.clear();
                keyStatus.setText(settings.hasCredential()
                        ? "API Key configured ✓" : "API Key not configured");
                showInfo("AI Settings", "Configuration saved.");
            } catch (RuntimeException failure) {
                showFailure(failure);
            }
        });
        Button remove = new Button("Remove API Key");
        remove.setOnAction(event -> {
            try {
                settings.removeApiKey();
                key.clear();
                keyStatus.setText("API Key not configured");
            } catch (RuntimeException failure) {
                showFailure(failure);
            }
        });
        Button test = new Button("Test Connection");
        test.setOnAction(event -> {
            test.setDisable(true);
            connectionStatus.setText("Testing saved AI Provider configuration...");
            Task<Void> task = new Task<>() {
                @Override
                protected Void call() {
                    connections.testConnection();
                    return null;
                }
            };
            task.setOnSucceeded(done -> {
                test.setDisable(false);
                connectionStatus.setText("Connection succeeded.");
            });
            task.setOnFailed(done -> {
                test.setDisable(false);
                connectionStatus.setText("Connection failed.");
                showFailure(task.getException());
            });
            Thread worker = new Thread(task, "quizforge-ai-connection-test");
            worker.setDaemon(true);
            worker.start();
        });
        VBox page = new VBox(12, back, heading("AI Provider Settings"),
                new Label("Provider"), provider, new Label("Base URL"), baseUrl,
                new Label("Model"), model, new Label("API Key"), key, keyStatus,
                new HBox(12, save, test, remove), connectionStatus);
        page.setPadding(new Insets(16));
        root.setCenter(page);
    }

    private void showFailure(Throwable failure) {
        if (failure instanceof QuizForgeException known) {
            showError(known);
        } else if (failure instanceof IllegalArgumentException) {
            showInfo("Could not complete action", failure.getMessage());
        } else {
            showInfo("Could not complete action", "An unexpected error occurred.");
        }
    }

    private VBox placeholder(String message) {
        VBox box = new VBox(new Label(message));
        box.setPadding(new Insets(24));
        return box;
    }

    private Label heading(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");
        return label;
    }

    private Region spacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    private void showError(QuizForgeException error) {
        showInfo("Could not complete action", error.code() + ": " + error.getMessage());
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        alert.initOwner(stage);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}
