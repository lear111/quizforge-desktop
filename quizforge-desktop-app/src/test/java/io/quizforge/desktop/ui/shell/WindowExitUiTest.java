package io.quizforge.desktop.ui.shell;

import java.nio.file.Files;
import java.nio.file.Path;
import javafx.application.Platform;
import javafx.scene.control.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class WindowExitUiTest extends WorkspaceUiTestSupport {

    @Test void closeButtonCancelKeepsUnsavedMarkdownAndWindow() throws Exception {
        fx(()->{
            open("Java/Java集合.md");button("file-mode-toggle").fire();
            var editor=(TextArea)shell.lookup("#markdown-source-text");editor.appendText("\nunsaved review edit");
            DesktopView.installExitGuard(stage,shell);
            Platform.runLater(()->answerDialog(ButtonType.CANCEL.getText()));
            button("window-close").fire();
            assertTrue(stage.isShowing());assertTrue(shell.filePane().hasUnsavedChanges());
            assertTrue(editor.getText().contains("unsaved review edit"));
        });
    }

    @Test void closeButtonSavePublishesMarkdownBeforeWindowCloses() throws Exception {
        fx(()->{
            open("Java/Java集合.md");button("file-mode-toggle").fire();
            ((TextArea)shell.lookup("#markdown-source-text")).appendText("\nsaved review edit");
            DesktopView.installExitGuard(stage,shell);
            Platform.runLater(()->answerDialog("保存并退出"));button("window-close").fire();
            assertFalse(stage.isShowing());assertFalse(shell.filePane().hasUnsavedChanges());
            assertTrue(Files.readString(fixture.alphaRoot.resolve("Java/Java集合.md")).contains("saved review edit"));
        });
    }

    @Test void failedExitSaveKeepsEditorAndExternalMarkdown() throws Exception {
        fx(()->{
            open("Java/Java集合.md");button("file-mode-toggle").fire();
            var editor=(TextArea)shell.lookup("#markdown-source-text");editor.appendText("\nunsaved review edit");
            Path file=fixture.alphaRoot.resolve("Java/Java集合.md");Files.writeString(file,"external review version");
            DesktopView.installExitGuard(stage,shell);
            Platform.runLater(()->answerDialog("保存并退出"));button("window-close").fire();
            assertTrue(stage.isShowing());assertTrue(shell.filePane().hasUnsavedChanges());
            assertEquals("external review version",Files.readString(file));assertTrue(editor.getText().contains("unsaved review edit"));
        });
    }
}
