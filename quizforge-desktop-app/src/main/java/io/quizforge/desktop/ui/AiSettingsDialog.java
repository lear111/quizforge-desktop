package io.quizforge.desktop.ui;

import io.quizforge.core.ai.AiConnectionService;
import io.quizforge.core.ai.AiSettingsService;
import io.quizforge.extensions.ai.deepseek.DeepSeekAiProvider;
import javafx.concurrent.Task;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

final class AiSettingsDialog {
    private final AiSettingsService settings;
    private final AiConnectionService connections;
    private final Stage owner;

    AiSettingsDialog(AiSettingsService settings, AiConnectionService connections, Stage owner) {
        this.settings = settings;
        this.connections = connections;
        this.owner = owner;
    }

    void show() {
        var current = settings.configuration();
        TextField base = new TextField(current.map(config -> config.baseUrl()).orElse(DeepSeekAiProvider.DEFAULT_BASE_URL));
        TextField model = new TextField(current.map(config -> config.model()).orElse(DeepSeekAiProvider.DEFAULT_MODEL));
        PasswordField key = new PasswordField();
        key.setPromptText("输入新密钥以替换已保存的密钥");
        Label status = UiTheme.label(settings.hasCredential() ? "API key 已配置" : "尚未配置 API key", "muted");
        Button save = new Button("保存");
        save.setOnAction(event -> {
            try {
                settings.save("deepseek", base.getText(), model.getText(), key.getText());
                key.clear();
                status.setText("配置已保存");
            } catch (RuntimeException error) { status.setText("无法保存：" + error.getMessage()); }
        });
        Button remove = new Button("移除密钥");
        remove.setOnAction(event -> {
            try { settings.removeApiKey(); key.clear(); status.setText("API key 已移除"); }
            catch (RuntimeException error) { status.setText("无法移除密钥"); }
        });
        Button test = new Button("测试连接");
        test.setOnAction(event -> {
            test.setDisable(true);
            status.setText("正在测试已保存的配置…");
            Task<Void> task = new Task<>() {
                @Override protected Void call() { connections.testConnection(); return null; }
            };
            task.setOnSucceeded(done -> { test.setDisable(false); status.setText("连接成功"); });
            task.setOnFailed(done -> { test.setDisable(false); status.setText("连接失败，请检查已保存的配置。"); });
            Thread thread = new Thread(task, "quizforge-ai-connection-test");
            thread.setDaemon(true);
            thread.start();
        });
        VBox form = new VBox(14, UiTheme.label("DeepSeek", "section-title"),
                new VBox(6, new Label("Base URL"), base), new VBox(6, new Label("Model"), model),
                new VBox(6, new Label("API key"), key),
                UiTheme.label("密钥仅在本机加密保存。请先保存配置，再测试连接。", "muted"),
                new HBox(8, save, test, remove), status);
        form.setPrefWidth(440);
        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(owner);
        UiTheme.apply(dialog);
        dialog.setTitle("Settings");
        dialog.setHeaderText("AI Provider");
        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }
}
