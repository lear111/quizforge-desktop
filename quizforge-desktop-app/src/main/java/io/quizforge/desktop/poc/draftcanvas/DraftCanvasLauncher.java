package io.quizforge.desktop.poc.draftcanvas;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

/** Development POC entry; does not bootstrap Spring or open a workspace. */
public final class DraftCanvasLauncher {
    private DraftCanvasLauncher() { }
    public static void main(String[] args) { Application.launch(DraftCanvasWindow.class, args); }

    public static final class DraftCanvasWindow extends Application {
        private DraftCanvasWebView canvas;
        @Override public void start(Stage stage) {
            canvas = new DraftCanvasWebView();
            stage.setTitle("QuizForge — Draft Canvas Core v1 POC");
            stage.setScene(new Scene(canvas.view(), 1100, 760));
            stage.setMinWidth(720);
            stage.setMinHeight(500);
            stage.setOnCloseRequest(event -> canvas.destroy());
            canvas.ready().whenComplete((ignored, failure) -> {
                if (failure != null && stage.isShowing()) {
                    var alert = new Alert(Alert.AlertType.ERROR, failure.getMessage());
                    alert.initOwner(stage);
                    alert.show();
                }
            });
            stage.show();
        }
        @Override public void stop() { if (canvas != null) canvas.destroy(); }
    }
}
