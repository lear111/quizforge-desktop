package io.quizforge.desktop.ui;

import io.quizforge.core.ai.AiConnectionService;
import io.quizforge.core.ai.AiSettingsService;
import io.quizforge.core.document.FileStandardDocumentGenerationService;
import io.quizforge.core.document.MarkdownFileEditService;
import io.quizforge.core.material.MaterialService;
import io.quizforge.core.port.WorkspaceFileCatalog;
import io.quizforge.core.port.MarkdownDocumentRegistration;
import io.quizforge.core.port.AssetIndexRepository;
import io.quizforge.core.question.QuestionBankReferenceResolver;
import io.quizforge.core.question.QuestionBankFileEditService;
import io.quizforge.core.question.QuestionSourceLinkService;
import io.quizforge.core.workspace.WorkspaceFileService;
import io.quizforge.core.workspace.WorkspaceService;
import java.nio.file.Path;
import javafx.scene.Scene;
import javafx.stage.Stage;

/** Desktop composition only; file navigation and viewers live in dedicated components. */
public final class DesktopView {
    private final WorkspaceService workspaces;
    private final WorkspaceFileService files;
    private final WorkspaceFileCatalog catalog;
    private final QuestionBankReferenceResolver references;
    private final MaterialService materials;
    private final FileStandardDocumentGenerationService documents;
    private final AiSettingsService settings;
    private final AiConnectionService connections;
    private final Path historyPath;
    private final QuestionBankFileEditService bankEdits;
    private final MarkdownFileEditService markdownEdits;
    private final MarkdownDocumentRegistration registration;
    private final AssetIndexRepository assetIndex;
    private final QuestionSourceLinkService sourceLinks;
    private final io.quizforge.core.port.PracticeRuntimeProvider practice;

    public DesktopView(WorkspaceService workspaces, WorkspaceFileService files, WorkspaceFileCatalog catalog,
            QuestionBankReferenceResolver references, MaterialService materials,
            FileStandardDocumentGenerationService documents, AiSettingsService settings,
            AiConnectionService connections, Path historyPath,
            QuestionBankFileEditService bankEdits, MarkdownFileEditService markdownEdits,
            MarkdownDocumentRegistration registration, AssetIndexRepository assetIndex,
            QuestionSourceLinkService sourceLinks, io.quizforge.core.port.PracticeRuntimeProvider practice) {
        this.workspaces = workspaces;
        this.files = files;
        this.catalog = catalog;
        this.references = references;
        this.materials = materials;
        this.documents = documents;
        this.settings = settings;
        this.connections = connections;
        this.historyPath = historyPath;
        this.bankEdits = bankEdits;
        this.markdownEdits = markdownEdits;
        this.registration = registration;
        this.assetIndex = assetIndex;
        this.sourceLinks = sourceLinks;
        this.practice = practice;
    }

    public Scene createScene(Stage stage) {
        AiSettingsDialog settingsDialog = new AiSettingsDialog(settings, connections, stage);
        EmptyAssetAiAction[] ai = new EmptyAssetAiAction[1];
        MainWorkspaceView shell = new MainWorkspaceView(workspaces, files, new WorkspaceHistory(historyPath),
                new FilePresentationLoader(files, catalog), references, stage, settingsDialog::show,
                (workspace, file) -> ai[0].run(workspace, file), bankEdits,
                markdownEdits, registration, TextClipboard.system(), assetIndex, sourceLinks, practice);
        ai[0] = new EmptyAssetAiAction(materials, documents, settings, stage,
                settingsDialog::show, shell::refreshAndOpen);
        Scene scene = new Scene(shell, 1180, 780);
        UiTheme.apply(scene);
        DevelopmentUiReloader.install(scene);
        if (UiTheme.liveCssEnabled()) stage.setTitle(stage.getTitle() + " · CSS Live");
        Runnable stopLiveCss = LiveCssReloader.start(scene);
        stage.setMinWidth(800);
        stage.setMinHeight(540);
        WindowChrome.installFrame(stage, scene);
        stage.setOnHidden(event -> {
            stopLiveCss.run();
            shell.tabs().closeAll();
        });
        return scene;
    }
}
