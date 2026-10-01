package io.quizforge.desktop.ui.shell;

import io.quizforge.core.ai.AiConnectionService;
import io.quizforge.core.ai.AiSettingsService;
import io.quizforge.core.document.MarkdownFileEditService;
import io.quizforge.core.port.AssetIndexRepository;
import io.quizforge.core.port.MarkdownDocumentRegistration;
import io.quizforge.core.port.WorkspaceFileCatalog;
import io.quizforge.core.question.service.QuestionBankFileEditService;
import io.quizforge.core.question.source.QuestionBankReferenceResolver;
import io.quizforge.core.question.source.QuestionSourceLinkService;
import io.quizforge.core.workspace.service.WorkspaceFileService;
import io.quizforge.core.workspace.service.WorkspaceService;
import io.quizforge.desktop.dev.DevelopmentUiReloader;
import io.quizforge.desktop.dev.LiveCssReloader;
import io.quizforge.desktop.ui.ai.AiSettingsDialog;
import io.quizforge.desktop.ui.file.FilePresentationLoader;
import io.quizforge.desktop.ui.shared.TextClipboard;
import io.quizforge.desktop.ui.shared.UiTheme;
import io.quizforge.desktop.ui.workspace.WorkspaceHistory;
import java.nio.file.Path;
import javafx.scene.Scene;
import javafx.stage.Stage;

/** Desktop composition only; file navigation and viewers live in dedicated components. */
public final class DesktopView {
    private final WorkspaceService workspaces;
    private final WorkspaceFileService files;
    private final WorkspaceFileCatalog catalog;
    private final QuestionBankReferenceResolver references;
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
            QuestionBankReferenceResolver references,
            AiSettingsService settings,
            AiConnectionService connections, Path historyPath,
            QuestionBankFileEditService bankEdits, MarkdownFileEditService markdownEdits,
            MarkdownDocumentRegistration registration, AssetIndexRepository assetIndex,
            QuestionSourceLinkService sourceLinks, io.quizforge.core.port.PracticeRuntimeProvider practice) {
        this.workspaces = workspaces;
        this.files = files;
        this.catalog = catalog;
        this.references = references;
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
        MainWorkspaceView shell = new MainWorkspaceView(workspaces, files, new WorkspaceHistory(historyPath),
                new FilePresentationLoader(files, catalog), references, stage, settingsDialog::show,
                bankEdits,
                markdownEdits, registration, TextClipboard.system(), assetIndex, sourceLinks, practice);
        Scene scene = new Scene(shell, 1180, 780);
        UiTheme.apply(scene);
        DevelopmentUiReloader.install(scene);
        if (UiTheme.liveCssEnabled()) stage.setTitle(stage.getTitle() + " · CSS Live");
        Runnable stopLiveCss = LiveCssReloader.start(scene);
        stage.setMinWidth(800);
        stage.setMinHeight(540);
        WindowChrome.installFrame(stage, scene);
        installExitGuard(stage,shell);
        stage.setOnHidden(event -> {
            stopLiveCss.run();
            shell.tabs().closeAll();
        });
        return scene;
    }

    static void installExitGuard(Stage stage,MainWorkspaceView shell) {
        stage.setOnCloseRequest(event -> { if(!shell.prepareExit())event.consume(); });
    }
}
