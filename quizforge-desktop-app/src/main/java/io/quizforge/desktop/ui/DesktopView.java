package io.quizforge.desktop.ui;

import io.quizforge.core.ai.AiConnectionService;
import io.quizforge.core.ai.AiSettingsService;
import io.quizforge.core.document.FileStandardDocumentGenerationService;
import io.quizforge.core.document.qdoc.QDocFileEditService;
import io.quizforge.core.document.MarkdownFileEditService;
import io.quizforge.core.material.MaterialService;
import io.quizforge.core.port.WorkspaceFileCatalog;
import io.quizforge.core.question.QuestionBankReferenceResolver;
import io.quizforge.core.question.QuestionBankFileEditService;
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
    private final QDocFileEditService qdocEdits;
    private final QuestionBankFileEditService bankEdits;
    private final MarkdownFileEditService markdownEdits;

    public DesktopView(WorkspaceService workspaces, WorkspaceFileService files, WorkspaceFileCatalog catalog,
            QuestionBankReferenceResolver references, MaterialService materials,
            FileStandardDocumentGenerationService documents, AiSettingsService settings,
            AiConnectionService connections, Path historyPath, QDocFileEditService qdocEdits,
            QuestionBankFileEditService bankEdits, MarkdownFileEditService markdownEdits) {
        this.workspaces = workspaces;
        this.files = files;
        this.catalog = catalog;
        this.references = references;
        this.materials = materials;
        this.documents = documents;
        this.settings = settings;
        this.connections = connections;
        this.historyPath = historyPath;
        this.qdocEdits = qdocEdits;
        this.bankEdits = bankEdits;
        this.markdownEdits = markdownEdits;
    }

    public Scene createScene(Stage stage) {
        AiSettingsDialog settingsDialog = new AiSettingsDialog(settings, connections, stage);
        EmptyAssetAiAction[] ai = new EmptyAssetAiAction[1];
        MainWorkspaceView shell = new MainWorkspaceView(workspaces, files, new WorkspaceHistory(historyPath),
                new FilePresentationLoader(files, catalog), references, stage, settingsDialog::show,
                (workspace, file) -> ai[0].run(workspace, file), qdocEdits, bankEdits, markdownEdits);
        ai[0] = new EmptyAssetAiAction(materials, documents, settings, stage,
                settingsDialog::show, shell::refreshAndOpen);
        Scene scene = new Scene(shell, 1180, 780);
        UiTheme.apply(scene);
        stage.setMinWidth(800);
        stage.setMinHeight(540);
        stage.setOnHidden(event -> shell.filePane().clear());
        return scene;
    }
}
