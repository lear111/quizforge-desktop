package io.quizforge.desktop.ui.shell;

import io.quizforge.desktop.dev.DevelopmentUiReloader;
import io.quizforge.desktop.dev.LiveCssReloader;
import io.quizforge.desktop.ui.shared.UiTheme;
import io.quizforge.infrastructure.filesystem.qbank.QuestionBankV2Codec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class DevelopmentRefreshUiTest extends WorkspaceUiTestSupport {

    @Test void liveCssReappliesChangedStylesToAnOpenWindow() throws Exception {
        Path styles = Files.createDirectory(temp.resolve("live-css"));
        Path workspaceCss = styles.resolve("workspace.css");
        Files.writeString(workspaceCss, ".live-css-sample { -fx-background-color: #b93131; }");
        Files.writeString(styles.resolve("markdown-preview.css"), "");
        String previous = System.getProperty("quizforge.ui.liveCssDir");
        Stage[] preview = new Stage[1];
        Runnable[] stop = new Runnable[1];
        CountDownLatch changed = new CountDownLatch(1);
        try {
            System.setProperty("quizforge.ui.liveCssDir", styles.toString());
            fx(() -> {
                var sample = new javafx.scene.layout.StackPane();
                sample.getStyleClass().add("live-css-sample");
                Scene scene = new Scene(sample, 80, 80);
                UiTheme.apply(scene);
                scene.getStylesheets().addListener((javafx.collections.ListChangeListener<String>) ignored ->
                        changed.countDown());
                preview[0] = new Stage();
                preview[0].setScene(scene);
                preview[0].setOpacity(0);
                preview[0].show();
                stop[0] = LiveCssReloader.start(scene);
            });

            Path replacement = styles.resolve("replacement.css");
            Files.writeString(replacement, ".live-css-sample { -fx-background-color: #276fc2; }");
            Files.move(replacement, workspaceCss, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            assertTrue(changed.await(10, TimeUnit.SECONDS));
            fx(() -> {
                var sample = (javafx.scene.layout.StackPane) preview[0].getScene().getRoot();
                sample.applyCss();
                assertEquals(javafx.scene.paint.Color.web("#276fc2"),
                        sample.getBackground().getFills().getFirst().getFill());
            });
        } finally {
            fx(() -> {
                if (stop[0] != null) stop[0].run();
                if (preview[0] != null) preview[0].close();
            });
            if (previous == null) System.clearProperty("quizforge.ui.liveCssDir");
            else System.setProperty("quizforge.ui.liveCssDir", previous);
        }
    }

    @Test void developmentRefreshKeepsPracticeDraftAndDatabaseRows() throws Exception {
        fx(() -> {
            open("题库/Java集合.qbank");
            ((RadioButton) shell.lookup("#option-0")).fire();
            var session = practiceDbSession();
            var before = practiceRows();
            var view = shell.lookup("#question-practice");
            DevelopmentUiReloader.refreshNode(view);
            assertSame(view, shell.lookup("#question-practice"));
            assertTrue(((RadioButton) shell.lookup("#option-0")).isSelected());
            assertFalse(button("submit-answer").isDisabled());
            assertEquals(session, practiceDbSession());
            assertEquals(before, practiceRows());
            button("submit-answer").fire();
            var submitted = practiceRows();
            DevelopmentUiReloader.refreshNode(view);
            assertNotNull(shell.lookup("#answer-feedback"));
            assertEquals(submitted, practiceRows());
        });
    }

    @Test void developmentRefreshKeepsUnsavedEditorAndInvalidInput() throws Exception {
        fixture.write("题库/Essay.qbank", new QuestionBankV2Codec().write(io.quizforge.infrastructure.testing.EssayTestBanks.bank()));
        byte[] original = Files.readAllBytes(fixture.alphaRoot.resolve("题库/Essay.qbank"));
        fx(() -> {
            shell.refresh(); open("题库/Essay.qbank"); button("file-mode-toggle").fire(); shell.applyCss(); shell.layout();
            TextArea guidance = (TextArea) shell.lookup("#essay-evaluation-guidance");
            guidance.setText("Unsaved guidance"); guidance.selectRange(2, 8);
            ((TextField) shell.lookup("#essay-max-score")).setText("invalid");
            var editor = shell.lookup("#question-bank-editor");
            DevelopmentUiReloader.refreshNode(editor);
            assertSame(editor, shell.lookup("#question-bank-editor"));
            assertEquals("Unsaved guidance", ((TextArea) shell.lookup("#essay-evaluation-guidance")).getText());
            assertEquals("invalid", ((TextField) shell.lookup("#essay-max-score")).getText());
            assertEquals(2, ((TextArea) shell.lookup("#essay-evaluation-guidance")).getAnchor());
            assertEquals(8, ((TextArea) shell.lookup("#essay-evaluation-guidance")).getCaretPosition());
            assertTrue(shell.filePane().hasUnsavedChanges());
            assertArrayEquals(original, Files.readAllBytes(fixture.alphaRoot.resolve("题库/Essay.qbank")));
        });
    }

    @Test void productionSceneHasNoDevelopmentRefreshHandler() throws Exception {
        fx(() -> {
            String previous = System.getProperty("quizforge.liveJava.enabled");
            try {
                System.clearProperty("quizforge.liveJava.enabled");
                var scene = new Scene(new BorderPane());
                DevelopmentUiReloader.install(scene);
                assertNull(scene.getOnKeyPressed());
            } finally {
                if (previous != null) System.setProperty("quizforge.liveJava.enabled", previous);
            }
        });
    }
}
