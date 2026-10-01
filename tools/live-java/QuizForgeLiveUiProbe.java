package io.quizforge.desktop.ui.content.document.canvas;

import io.quizforge.core.question.model.*;
import io.quizforge.core.question.content.*;
import io.quizforge.core.question.type.objective.choice.*;
import io.quizforge.desktop.ui.question.editor.QuestionBankEditorView;
import io.quizforge.desktop.ui.shared.UiTheme;
import io.quizforge.desktop.dev.LiveCssReloader;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import java.util.List;

/** Native scene smoke fixture, no workspace/services/database. Compiled only by the probe script. */
public final class QuizForgeLiveUiProbe {
    public static void main(String[] args) {
        Platform.startup(() -> {
            var question = Question.choice("q_probe", "SINGLE_CHOICE", new TextContent("Native reload probe"), null,
                    List.of(), new ChoicePayload(List.of(new ChoiceOption("opt_probe_a", new TextContent("A")),
                    new ChoiceOption("opt_probe_b", new TextContent("B")))), new ChoiceAnswerSpec(List.of("opt_probe_a")));
            var bank = new QuestionBank("qb_probe", "Probe", List.of(), List.of(question), List.of());
            var editor = new QuestionBankEditorView(bank, null, null, null, ignored -> {
                throw new AssertionError("Probe must never save");
            });
            CanvasEditorBridge canvas;
            if (System.getenv("QUIZFORGE_CANVAS_EDITOR_DEV_URL") != null) {
                var content = new TextContent("Canvas probe draft");
                var session = new ContentEditSession(content, List.of(), io.quizforge.core.port.QuestionResourceInput.NONE);
                canvas = new CanvasEditorBridge(content, session, error -> System.err.println("[LiveJava] CANVAS_ERROR " + error), () -> {});
                canvas.view().setPrefHeight(250);
            } else canvas = null;
            var root = canvas == null ? editor : new VBox(editor, canvas.view());
            var scene = new Scene(root, 850, 940);
            UiTheme.apply(scene);
            Runnable stopCss = LiveCssReloader.start(scene);
            if (UiTheme.liveCssEnabled()) System.out.println("[LiveJava] CSS_LIVE_ENABLED");
            var stage = new Stage(); stage.setOpacity(0); stage.setScene(scene); stage.show();
            ((TextArea)editor.lookup("#qbank-question-stem")).setText("LiveJava unsaved stem");
            String[] previous = {""};
            boolean[] webReady = {false};
            var observe = new Timeline(new KeyFrame(Duration.millis(300), event -> {
                String stop = System.getenv("QUIZFORGE_LIVE_PROBE_STOP");
                if (stop != null && java.nio.file.Files.exists(java.nio.file.Path.of(stop))) { stage.close(); return; }
                if (canvas != null && canvas.ready() && !webReady[0]) {
                    if (!QuestionContentData.plainText(canvas.getContent()).contains("Canvas probe draft")) throw new AssertionError("Canvas draft lost");
                    System.out.println("[LiveJava] CANVAS_READY " + canvas.view().getEngine().getLocation());
                    webReady[0] = true;
                }
                String text = labels(editor);
                String state = text.contains("[LiveJava native probe]") ? "updated" : "original";
                if (!state.equals(previous[0])) {
                    String stem = ((TextArea)editor.lookup("#qbank-question-stem")).getText();
                    if (!stem.equals("LiveJava unsaved stem")) throw new AssertionError("Unsaved input lost");
                    System.out.println("[LiveJava] NATIVE_SCENE " + state + " pid=" + ProcessHandle.current().pid() + " draft=retained");
                    previous[0] = state;
                }
            }));
            observe.setCycleCount(Animation.INDEFINITE); observe.play();
            stage.setOnHidden(event -> { observe.stop(); stopCss.run(); if (canvas != null) canvas.destroy(); Platform.exit(); });
        });
    }
    private static String labels(Node node) {
        String result = node instanceof Labeled label ? label.getText() : "";
        if (node instanceof Parent parent)
            for (Node child : parent.getChildrenUnmodifiable()) result += " " + labels(child);
        return result;
    }
}
