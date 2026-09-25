package io.quizforge.desktop.ui;

import io.quizforge.core.ai.AiSettingsService;
import io.quizforge.core.document.FileDocumentView;
import io.quizforge.core.document.FileStandardDocumentGenerationService;
import io.quizforge.core.material.MaterialService;
import io.quizforge.core.workspace.WorkspaceFileKind;
import io.quizforge.core.workspace.WorkspaceId;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import javafx.concurrent.Task;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/** Reuses document regeneration; unsupported draft filling is an explicit hook, never a second generator. */
final class EmptyAssetAiAction {
    private final MaterialService materials;
    private final FileStandardDocumentGenerationService documents;
    private final AiSettingsService settings;
    private final Stage owner;
    private final Runnable openSettings;
    private final BiConsumer<WorkspaceId, String> refresh;
    private boolean running;

    EmptyAssetAiAction(MaterialService materials, FileStandardDocumentGenerationService documents,
            AiSettingsService settings, Stage owner, Runnable openSettings, BiConsumer<WorkspaceId, String> refresh) {
        this.materials = materials;
        this.documents = documents;
        this.settings = settings;
        this.owner = owner;
        this.openSettings = openSettings;
        this.refresh = refresh;
    }

    void run(WorkspaceId workspace, FilePresentation file) {
        if (!file.offersAi()) return;
        if (file.draft() || file.kind() == WorkspaceFileKind.QUESTION_BANK) {
            info("AI 内容生成", "已预留此空文件的 AI 生成入口。现有生成服务暂不支持向空草稿原位填充内容。");
            return;
        }
        if (running) { info("AI 内容生成", "文档正在生成，请稍候。"); return; }
        if (!settings.hasCredential()) { openSettings.run(); return; }
        var available = materials.listMaterials(workspace);
        if (available.isEmpty()) { info("暂无可用材料", "当前生成流程需要已经导入的 Markdown 材料。"); return; }
        List<CheckBox> checks = new ArrayList<>();
        VBox choices = new VBox(8);
        available.forEach(material -> {
            CheckBox check = new CheckBox(material.originalFileName());
            check.setSelected(true);
            checks.add(check);
            choices.getChildren().add(check);
        });
        Dialog<ButtonType> choose = new Dialog<>();
        choose.initOwner(owner);
        UiTheme.apply(choose);
        choose.setTitle("选择生成材料");
        var scroll = UiTheme.scroll(choices);
        scroll.setPrefViewportWidth(420);
        scroll.setPrefViewportHeight(Math.min(360, checks.size() * 34));
        choose.getDialogPane().setContent(scroll);
        ButtonType generate = new ButtonType("生成", ButtonBar.ButtonData.OK_DONE);
        choose.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, generate);
        if (choose.showAndWait().orElse(ButtonType.CANCEL) != generate) return;
        List<io.quizforge.core.material.MaterialId> selected = new ArrayList<>();
        for (int i = 0; i < checks.size(); i++) if (checks.get(i).isSelected()) selected.add(available.get(i).id());
        if (selected.isEmpty()) { info("未选择材料", "请至少选择一份材料。"); return; }
        running = true;
        Label status = UiTheme.label("正在生成…", "muted");
        Dialog<Void> progress = new Dialog<>();
        progress.initOwner(owner);
        UiTheme.apply(progress);
        progress.setTitle("AI 内容生成");
        progress.getDialogPane().setContent(status);
        progress.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        progress.show();
        Task<FileDocumentView> task = new Task<>() {
            @Override protected FileDocumentView call() {
                return documents.regenerate(workspace, file.file().entry().assetId(), selected, this::updateMessage);
            }
        };
        status.textProperty().bind(task.messageProperty());
        task.setOnSucceeded(event -> {
            running = false;
            status.textProperty().unbind();
            progress.close();
            refresh.accept(workspace, task.getValue().asset().currentPath());
        });
        task.setOnFailed(event -> {
            running = false;
            status.textProperty().unbind();
            status.setText("生成失败，原文件已保留。请检查材料和 AI 配置。");
        });
        Thread worker = new Thread(task, "quizforge-document-generation");
        worker.setDaemon(true);
        worker.start();
    }

    private void info(String title, String message) {
        Alert dialog = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        dialog.initOwner(owner);
        UiTheme.apply(dialog);
        dialog.setTitle(title);
        dialog.setHeaderText(null);
        dialog.showAndWait();
    }
}
