package io.quizforge.desktop.poc.sharedpractice;

import java.nio.file.Path;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

/** Development entry: actual QBank plus durable isolated Practice DB; no Workspace is created. */
public final class SharedPracticeCanvasLauncher {
    private SharedPracticeCanvasLauncher() { }
    public static void main(String[] args) { Application.launch(SharedPracticeWindow.class, args); }

    public static final class SharedPracticeWindow extends Application {
        private SharedPracticeCanvasWebView canvas;
        private SharedPracticeExample.Context context;
        @Override public void start(Stage stage) throws Exception {
            var arguments = getParameters().getRaw();
            var qbank = arguments.isEmpty() ? Path.of("examples/step7-practice/Java集合练习.qbank") : Path.of(arguments.getFirst());
            var database = arguments.size() > 1 ? Path.of(arguments.get(1)) : Path.of("target/draft-persistence-acceptance/practice.db");
            java.nio.file.Files.createDirectories(database.toAbsolutePath().getParent());
            context = SharedPracticeExample.open(qbank.toAbsolutePath(), database.toAbsolutePath());
            canvas = new SharedPracticeCanvasWebView(context.adapter());
            stage.setTitle("QuizForge — Shared Practice UI v1 / SINGLE_CHOICE");
            stage.setScene(new Scene(canvas.view(), 1100, 760));
            stage.setMinWidth(720);
            stage.setMinHeight(500);
            stage.setOnCloseRequest(event -> {
                try { canvas.destroy(); }
                catch (RuntimeException failure) {
                    event.consume();
                    var alert = new Alert(Alert.AlertType.ERROR, "草稿保存失败，窗口保持打开：" + failure.getMessage());
                    alert.initOwner(stage); alert.show();
                }
            });
            canvas.ready().whenComplete((ignored, failure) -> {
                if (failure != null) Platform.runLater(() -> {
                    if (!stage.isShowing()) return;
                    var alert = new Alert(Alert.AlertType.ERROR, failure.getMessage());
                    alert.initOwner(stage);
                    alert.show();
                });
            });
            stage.show();
        }
        @Override public void stop() throws Exception {
            if (canvas != null) canvas.destroy();
            if (context != null) context.close();
        }
    }
}
