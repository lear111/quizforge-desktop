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
        context = new AnnotationConfigApplicationContext(DesktopConfiguration.class);
    }

    @Override
    public void start(Stage stage) {
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setTitle("QuizForge Desktop");
        stage.setScene(context.getBean(DesktopView.class).createScene(stage));
        stage.show();
    }

    @Override
    public void stop() {
        if (context != null) {
            context.close();
        }
    }
}
