package io.quizforge.desktop.ui;

import io.quizforge.core.QuizForgeException;
import io.quizforge.core.ai.AiConnectionService;
import io.quizforge.core.ai.AiSettingsService;
import io.quizforge.core.document.DocumentNormalizationService;
import io.quizforge.core.document.FileDocumentView;
import io.quizforge.core.document.FileStandardDocumentGenerationService;
import io.quizforge.core.document.StandardDocumentView;
import io.quizforge.core.asset.Asset;
import io.quizforge.core.material.Material;
import io.quizforge.core.material.MaterialService;
import io.quizforge.core.question.QuestionGenerationService;
import io.quizforge.core.question.FileQuestionBankGenerationService;
import io.quizforge.core.question.QuestionBankReferenceResolver;
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
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public final class DesktopView {
    private final WorkspaceService workspaces;
    private final MaterialService materials;
    private final DocumentNormalizationService documents;
    private final FileStandardDocumentGenerationService fileDocuments;
    private final AiSettingsService settings;
    private final AiConnectionService connections;
    private final QuestionGenerationService questions;
    private final FileQuestionBankGenerationService fileQuestions;
    private final QuestionBankReferenceResolver references;
    private FileQuestionBankPage questionBankPage;
    private BorderPane root;
    private Stage stage;
    private boolean generationRunning;
    private String selectedDocumentAssetId;
    private VBox sidebar;
    private Label location;
    private Label statusContext;
    private Workspace activeWorkspace;
    private TabPane workspaceTabs;
    private final List<Button> sectionButtons = new ArrayList<>();

    public DesktopView(WorkspaceService workspaces, MaterialService materials,
            DocumentNormalizationService documents, FileStandardDocumentGenerationService fileDocuments,
            AiSettingsService settings,
            AiConnectionService connections, QuestionGenerationService questions,
            FileQuestionBankGenerationService fileQuestions, QuestionBankReferenceResolver references) {
        this.workspaces = workspaces;
        this.materials = materials;
        this.documents = documents;
        this.fileDocuments = fileDocuments;
        this.settings = settings;
        this.connections = connections;
        this.questions = questions;
        this.fileQuestions = fileQuestions;
        this.references = references;
    }

    public Scene createScene(Stage stage) {
        this.stage = stage;
        questionBankPage = new FileQuestionBankPage(fileQuestions, references, settings, stage);
        root = new BorderPane();
        root.getStyleClass().add("workspace-shell");
        sidebar = new VBox(14);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(232);
        sidebar.setMinWidth(232);
        VBox rail = new VBox(10);
        rail.getStyleClass().add("ribbon");
        Button home = UiTheme.iconButton("grid", "All workspaces", this::showHome);
        home.setId("home-button");
        Button toggle = UiTheme.iconButton("panel", "Toggle sidebar", () -> {
            sidebar.setVisible(!sidebar.isVisible());
            sidebar.setManaged(sidebar.isVisible());
        });
        toggle.setId("sidebar-toggle");
        Region verticalSpace = new Region();
        VBox.setVgrow(verticalSpace, Priority.ALWAYS);
        Button settingsButton = UiTheme.iconButton("settings", "AI settings", this::showSettings);
        settingsButton.setId("settings-button");
        rail.getChildren().addAll(home, toggle, verticalSpace, settingsButton);
        root.setLeft(new HBox(rail, sidebar));
        location = UiTheme.label("", "muted");
        statusContext = UiTheme.label("", "muted");
        HBox status = new HBox(8, UiTheme.label("●", "local-dot"),
                UiTheme.label("Local workspace", "muted"), spacer(), statusContext);
        status.getStyleClass().add("status-bar");
        status.setAlignment(Pos.CENTER_LEFT);
        root.setBottom(status);
        showHome();
        Scene scene = new Scene(root, 1180, 780);
        UiTheme.apply(scene);
        stage.setMinWidth(880);
        stage.setMinHeight(600);
        return scene;
    }

    private void showHome() {
        activeWorkspace = null;
        workspaceTabs = null;
        refreshSidebar();
        VBox body = new VBox(18, UiTheme.label("YOUR LEARNING WORKSPACE", "eyebrow"),
                heading("A little space to think."),
                UiTheme.label("Bring your materials together. Turn what you read into what you know.", "intro"));
        body.getStyleClass().add("home-content");
        body.setMaxWidth(650);
        Button create = UiTheme.button("New workspace", "plus", "primary", this::createWorkspace);
        create.setId("create-workspace");
        HBox header = new HBox(12, UiTheme.label("Workspaces", "section-title"), spacer(), create);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(24, 0, 0, 0));
        body.getChildren().add(header);
        List<Workspace> entries = workspaces.listWorkspaces();
        if (entries.isEmpty()) {
            body.getChildren().add(UiTheme.emptyState("folder", "Make room for your next idea",
                    "Create a workspace, then import your Markdown materials."));
        } else {
            for (Workspace workspace : entries) {
                Button open = UiTheme.button(workspace.name(), "folder", "workspace-row",
                        () -> showWorkspace(workspace));
                open.setMaxWidth(Double.MAX_VALUE);
                open.setAlignment(Pos.CENTER_LEFT);
                body.getChildren().add(open);
            }
        }
        body.getChildren().add(UiTheme.label("Stored on your device. Use your own AI provider when you need it.", "muted"));
        StackPane centered = new StackPane(body);
        centered.setPadding(new Insets(48, 40, 48, 40));
        StackPane.setAlignment(body, Pos.TOP_CENTER);
        showContent("Welcome", UiTheme.scroll(centered));
        statusContext.setText(entries.size() + " workspaces");
    }

    private void refreshSidebar() {
        sidebar.getChildren().clear();
        sectionButtons.clear();
        HBox brand = new HBox(8, UiTheme.icon("book"), UiTheme.label("QuizForge", "brand"));
        brand.setAlignment(Pos.CENTER_LEFT);
        HBox header = new HBox(UiTheme.label("WORKSPACES", "eyebrow"), spacer(),
                UiTheme.iconButton("plus", "New workspace", this::createWorkspace));
        header.setAlignment(Pos.CENTER_LEFT);
        TextField search = new TextField();
        search.setPromptText("Find a workspace…");
        search.setId("workspace-search");
        search.getStyleClass().add("sidebar-search");
        VBox entries = new VBox(4);
        Runnable populate = () -> {
            entries.getChildren().clear();
            sectionButtons.clear();
            String query = search.getText().strip().toLowerCase(Locale.ROOT);
            for (Workspace workspace : workspaces.listWorkspaces()) {
                if (!workspace.name().toLowerCase(Locale.ROOT).contains(query)) continue;
                Button open = UiTheme.button(workspace.name(), "folder", "nav-item", () -> showWorkspace(workspace));
                open.setMaxWidth(Double.MAX_VALUE);
                open.setAlignment(Pos.CENTER_LEFT);
                entries.getChildren().add(open);
                if (activeWorkspace != null && activeWorkspace.id().equals(workspace.id())) {
                    open.getStyleClass().add("active-workspace");
                    VBox children = new VBox(3);
                    children.getStyleClass().add("workspace-tree");
                    String[] titles = {"Materials", "Standard document", "Question bank"};
                    String[] icons = {"folder", "file", "book"};
                    for (int i = 0; i < titles.length; i++) {
                        final int index = i;
                        Button item = UiTheme.button(titles[i], icons[i], "nav-item", () -> {
                            if (workspaceTabs != null) workspaceTabs.getSelectionModel().select(index);
                            else showWorkspace(workspace, index);
                        });
                        item.setAlignment(Pos.CENTER_LEFT);
                        item.setMaxWidth(Double.MAX_VALUE);
                        children.getChildren().add(item);
                        sectionButtons.add(item);
                    }
                    entries.getChildren().add(children);
                }
            }
            if (entries.getChildren().isEmpty()) {
                entries.getChildren().add(UiTheme.label(query.isEmpty()
                        ? "Your workspaces will appear here." : "No matching workspaces", "muted"));
            }
            updateSectionSelection();
        };
        search.textProperty().addListener((obs, oldValue, newValue) -> populate.run());
        populate.run();
        ScrollPane list = UiTheme.scroll(entries);
        list.getStyleClass().add("sidebar-scroll");
        VBox.setVgrow(list, Priority.ALWAYS);
        sidebar.getChildren().addAll(brand, header, search, list,
                UiTheme.label("A quieter place to learn.", "sidebar-footer"));
    }

    private void updateSectionSelection() {
        for (int i = 0; i < sectionButtons.size(); i++) {
            Button button = sectionButtons.get(i);
            button.getStyleClass().remove("selected");
            if (workspaceTabs != null && workspaceTabs.getSelectionModel().getSelectedIndex() == i) {
                button.getStyleClass().add("selected");
            }
        }
    }

    private void showContent(String title, Node content) {
        location.setText(title);
        HBox breadcrumb = new HBox(10, UiTheme.label("QuizForge", "muted"),
                UiTheme.label("/", "muted"), location);
        breadcrumb.setAlignment(Pos.CENTER_LEFT);
        breadcrumb.getStyleClass().add("breadcrumb");
        BorderPane main = new BorderPane(content);
        main.setTop(breadcrumb);
        root.setCenter(main);
    }

    private void createWorkspace() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.initOwner(stage);
        UiTheme.apply(dialog);
        dialog.setTitle("New Workspace");
        dialog.setHeaderText("Create a workspace");
        dialog.setContentText("Workspace Name:");
        dialog.showAndWait().ifPresent(name -> {
            try {
                showWorkspace(workspaces.createWorkspace(name));
            } catch (QuizForgeException e) {
                showError(e);
            }
        });
    }

    private void showWorkspace(Workspace workspace) {
        showWorkspace(workspace, 0);
    }

    private void showWorkspace(Workspace workspace, boolean selectDocument) {
        showWorkspace(workspace, selectDocument ? 1 : 0);
    }

    private void showWorkspace(Workspace workspace, int selectedTab) {
        activeWorkspace = workspace;
        Tab materialsTab = new Tab("Materials", UiTheme.scroll(materialsPage(workspace)));
        Tab documentTab = new Tab("Standard Document", standardDocumentPage(workspace));
        Tab questionTab = new Tab("Question Bank",
                questionBankPage.content(workspace, () -> showWorkspace(workspace, 2)));
        TabPane tabs = new TabPane(materialsTab, documentTab, questionTab);
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getStyleClass().add("workspace-tabs");
        tabs.setId("workspace-tabs");
        materialsTab.setGraphic(UiTheme.icon("folder"));
        documentTab.setGraphic(UiTheme.icon("file"));
        questionTab.setGraphic(UiTheme.icon("book"));
        tabs.getSelectionModel().select(selectedTab);
        workspaceTabs = tabs;
        tabs.getSelectionModel().selectedIndexProperty().addListener((obs, before, after) -> updateSectionSelection());
        refreshSidebar();
        showContent(workspace.name(), tabs);
        statusContext.setText(materials.listMaterials(workspace.id()).size() + " materials  ·  " + workspace.name());
    }

    private VBox materialsPage(Workspace workspace) {
        Label title = heading("Materials");
        Button importButton = UiTheme.button("Import Markdown", "upload", "primary", () -> importMarkdown(workspace));
        HBox header = new HBox(12, title, spacer(), importButton);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox page = new VBox(16, header, UiTheme.label("The source material for everything you’ll learn here.", "muted"));
        page.getStyleClass().add("content-page");
        List<Material> entries = materials.listMaterials(workspace.id());
        if (entries.isEmpty()) {
            page.getChildren().add(UiTheme.emptyState("file", "Start with something worth learning",
                    "Import a Markdown note, chapter or article to get started."));
        } else {
            page.getChildren().add(UiTheme.label(entries.size() + " FILES", "eyebrow"));
            for (Material material : entries) {
                page.getChildren().add(materialRow(workspace, material));
            }
        }
        return page;
    }

    private HBox materialRow(Workspace workspace, Material material) {
        Label name = UiTheme.label(material.originalFileName(), "file-name");
        String size = material.fileSize() < 1024
                ? material.fileSize() + " B"
                : String.format(Locale.ROOT, "%.1f KB", material.fileSize() / 1024.0);
        Label details = UiTheme.label(size + "  ·  " + material.status(), "muted");
        VBox text = new VBox(4, name, details);
        text.setMinWidth(0);
        HBox.setHgrow(text, Priority.ALWAYS);
        Button preview = new Button("Preview");
        preview.setOnAction(event -> preview(material));
        Button delete = new Button("Delete");
        delete.getStyleClass().add("quiet-danger");
        delete.setOnAction(event -> delete(workspace, material));
        HBox row = new HBox(14, UiTheme.icon("file"), text, preview, delete);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("material-row");
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
            contents.getStyleClass().add("document-editor");
            contents.setPrefSize(700, 500);
            Dialog<Void> dialog = new Dialog<>();
            dialog.initOwner(stage);
            UiTheme.apply(dialog);
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
        UiTheme.apply(confirm);
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
        VBox page = new VBox(16);
        page.getStyleClass().add("content-page");
        List<Asset> available = fileDocuments.list(workspace.id());
        Button generate = UiTheme.button("Create document", "spark", "primary", () -> { });
        Button regenerate = UiTheme.button("Regenerate selected", "arrow", "quiet", () -> { });
        regenerate.setDisable(generationRunning || available.isEmpty());
        HBox header = new HBox(12, heading("Standard document"), spacer(), regenerate, generate);
        header.setAlignment(Pos.CENTER_LEFT);
        page.getChildren().add(header);
        ComboBox<String> selection = new ComboBox<>();
        for (Asset asset : available) {
            selection.getItems().add(asset.title() + "  ·  " + asset.currentPath());
        }
        if (!available.isEmpty()) {
            int selected = 0;
            for (int i = 0; i < available.size(); i++) {
                if (available.get(i).assetId().equals(selectedDocumentAssetId)) selected = i;
            }
            selection.getSelectionModel().select(selected);
            page.getChildren().add(selection);
            VBox preview = new VBox(8);
            VBox.setVgrow(preview, Priority.ALWAYS);
            Runnable showSelected = () -> {
                preview.getChildren().clear();
                int index = selection.getSelectionModel().getSelectedIndex();
                if (index < 0) return;
                Asset asset = available.get(index);
                selectedDocumentAssetId = asset.assetId();
                FileDocumentView view = fileDocuments.findById(workspace.id(), asset.assetId())
                        .orElseThrow();
                preview.getChildren().addAll(UiTheme.label(view.asset().title(), "section-title"),
                        UiTheme.label("assetId: " + view.asset().assetId(), "muted"),
                        UiTheme.label("contentId: " + view.asset().contentId(), "muted"),
                        UiTheme.label("Path: " + view.asset().currentPath(), "muted"));
                TextArea raw = new TextArea(view.markdown());
                raw.setEditable(false);
                raw.setWrapText(false);
                raw.getStyleClass().add("document-editor");
                raw.setId("document-editor");
                VBox.setVgrow(raw, Priority.ALWAYS);
                preview.getChildren().add(raw);
            };
            selection.setOnAction(event -> showSelected.run());
            showSelected.run();
            page.getChildren().add(preview);
        } else {
            Optional<StandardDocumentView> legacy = documents.findByWorkspace(workspace.id());
            if (legacy.isPresent()) {
                page.getChildren().add(UiTheme.label("Existing Draft document: "
                        + legacy.get().document().title(), "muted"));
            } else {
                page.getChildren().add(UiTheme.emptyState("file", "From scattered notes to a clear structure",
                        "Choose your materials and let your AI provider organize them into a study document."));
            }
        }
        generate.setDisable(generationRunning);
        Label status = UiTheme.label(generationRunning ? "Generating document…" : "", "muted");
        generate.setOnAction(event -> chooseMaterialsAndGenerate(workspace, status, generate, null));
        regenerate.setOnAction(event -> {
            int selected = selection.getSelectionModel().getSelectedIndex();
            if (selected >= 0) chooseMaterialsAndGenerate(workspace, status, regenerate,
                    available.get(selected).assetId());
        });
        page.getChildren().add(status);
        return page;
    }

    private void chooseMaterialsAndGenerate(Workspace workspace, Label status, Button generate,
            String existingAssetId) {
        if (generationRunning) {
            return;
        }
        if (!settings.hasCredential()) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                    "Configure an AI Provider first.", ButtonType.CANCEL,
                    new ButtonType("Open AI Settings"));
            alert.initOwner(stage);
            UiTheme.apply(alert);
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
        VBox choices = new VBox(8);
        for (Material material : entries) {
            CheckBox check = new CheckBox(material.originalFileName());
            check.setSelected(true);
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
        UiTheme.apply(dialog);
        dialog.setTitle("Select Materials");
        dialog.setHeaderText("Generate Standard Document");
        ScrollPane choicesScroll = UiTheme.scroll(choices);
        choicesScroll.setPrefViewportHeight(Math.min(360, entries.size() * 34 + 40));
        choicesScroll.setPrefViewportWidth(420);
        dialog.getDialogPane().setContent(choicesScroll);
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
        Task<FileDocumentView> task = new Task<>() {
            @Override
            protected FileDocumentView call() {
                return existingAssetId == null
                        ? fileDocuments.create(workspace.id(), ids, this::updateMessage)
                        : fileDocuments.regenerate(workspace.id(), existingAssetId, ids,
                                this::updateMessage);
            }
        };
        status.textProperty().bind(task.messageProperty());
        task.setOnSucceeded(event -> {
            generationRunning = false;
            status.textProperty().unbind();
            selectedDocumentAssetId = task.getValue().asset().assetId();
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
        workspaceTabs = null;
        updateSectionSelection();
        Button back = new Button(activeWorkspace == null ? "← Workspaces" : "← Back to workspace");
        back.setOnAction(event -> {
            if (activeWorkspace == null) showHome();
            else showWorkspace(activeWorkspace);
        });
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
        save.getStyleClass().add("primary");
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
        remove.getStyleClass().add("quiet-danger");
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
        provider.setMaxWidth(Double.MAX_VALUE);
        VBox page = new VBox(20, back, heading("Your AI, your choice."),
                UiTheme.label("Connect a provider to organize materials and generate questions.", "intro"),
                field("Provider", provider), field("Base URL", baseUrl),
                field("Model", model), field("API key", key), keyStatus,
                UiTheme.label("Your key is encrypted on this device. Save changes before testing the connection.", "muted"),
                new HBox(10, save, test, remove), connectionStatus);
        page.getStyleClass().add("settings-page");
        page.setMaxWidth(620);
        StackPane centered = new StackPane(page);
        centered.setPadding(new Insets(32, 40, 40, 40));
        StackPane.setAlignment(page, Pos.TOP_CENTER);
        showContent("Settings / AI provider", UiTheme.scroll(centered));
        statusContext.setText("AI provider settings");
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

    private VBox field(String title, Node input) {
        return new VBox(8, UiTheme.label(title, "field-label"), input);
    }

    private Label heading(String text) {
        return UiTheme.label(text, "page-title");
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
        UiTheme.apply(alert);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}
