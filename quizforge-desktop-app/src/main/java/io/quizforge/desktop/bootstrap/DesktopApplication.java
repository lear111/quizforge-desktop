package io.quizforge.desktop.bootstrap;

import io.quizforge.desktop.config.DesktopConfiguration;
import io.quizforge.desktop.ui.shell.DesktopView;
import javafx.application.Application;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public final class DesktopApplication extends Application {
    private AnnotationConfigApplicationContext context;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void init() {
        io.quizforge.desktop.browser.webview2.WebView2TemporaryDirectories.initialize();
        context = new AnnotationConfigApplicationContext(DesktopConfiguration.class);
    }

    @Override
    public void start(Stage stage) {
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setTitle("QuizForge Desktop");
        var loading=new javafx.scene.Scene(io.quizforge.desktop.ui.shared.UiTheme.quietState("QuizForge", "正在加载工作区…"),1180,780);
        io.quizforge.desktop.ui.shared.UiTheme.apply(loading);
        stage.setScene(loading);stage.show();
        var root = context.getBean(io.quizforge.infrastructure.filesystem.QuizForgeDataDirectory.class).root();
        io.quizforge.desktop.extension.ExtensionManager.getDefault().initialize(root.resolve("extensions")).whenCompleteAsync((nothing,failure) -> {
            if(!stage.isShowing())return;
            stage.setScene(context.getBean(DesktopView.class).createScene(stage));
        },javafx.application.Platform::runLater);
    }

    @Override
    public void stop() {
        io.quizforge.desktop.extension.ExtensionManager.getDefault().close();
        if (context != null) {
            context.close();
        }
    }
}
