package io.quizforge.desktop.poc.sharedpractice;

import java.nio.file.Path;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

/** Development entry for an actual example qbank and an isolated temporary Practice database. */
public final class SharedPracticeCanvasLauncher {
    private SharedPracticeCanvasLauncher() { }
    public static void main(String[] args) { Application.launch(SharedPracticeWindow.class, args); }

    public static final class SharedPracticeWindow extends Application {
        private SharedPracticeCanvasWebView canvas;
        private SharedPracticeExample.Context context;
        @Override public void start(Stage stage) throws Exception {
            var arguments = getParameters().getRaw();
            var qbank = arguments.isEmpty() ? Path.of("examples/step7-practice/Java集合练习.qbank") : Path.of(arguments.getFirst());
            context = SharedPracticeExample.openTemporary(qbank.toAbsolutePath());
            canvas = new SharedPracticeCanvasWebView(context.adapter());
            stage.setTitle("QuizForge — Shared Practice UI v1 / SINGLE_CHOICE");
            stage.setScene(new Scene(canvas.view(), 1100, 760));
            stage.setMinWidth(720);
            stage.setMinHeight(500);
            stage.setOnCloseRequest(event -> canvas.destroy());
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
