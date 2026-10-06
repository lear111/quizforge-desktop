package io.quizforge.desktop.ui.shell;

import io.quizforge.core.question.content.BlockMathNode;
import io.quizforge.core.question.content.RichContent;
import io.quizforge.core.question.content.RichDocument;
import io.quizforge.core.question.content.TextContent;
import io.quizforge.core.question.model.Question;
import io.quizforge.core.question.model.QuestionBank;
import io.quizforge.core.question.model.choice.ChoiceAnswerSpec;
import io.quizforge.core.question.model.choice.ChoiceOption;
import io.quizforge.core.question.model.choice.ChoicePayload;
import io.quizforge.core.workspace.model.WorkspaceFileKind;
import io.quizforge.desktop.ui.file.FileMode;
import io.quizforge.infrastructure.filesystem.qbank.QuestionBankV2Codec;
import java.util.List;
import javafx.scene.control.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class QuestionBankEditorUiTest extends WorkspaceUiTestSupport {

    @Test void richQuestionBankShowsExplicitUnsupportedContentAndCannotEnterTextEditor() throws Exception {
        var q = Question.choice("q_rich_ui", "SINGLE_CHOICE",
                new RichContent(new RichDocument(List.of(new BlockMathNode("x^2")))), null, List.of(),
                new ChoicePayload(List.of(new ChoiceOption("opt_rich_a", new TextContent("A")),
                        new ChoiceOption("opt_rich_b", new TextContent("B")))), new ChoiceAnswerSpec(List.of("opt_rich_a")));
        var bank = new QuestionBank("qb_rich_ui", "Rich", List.of(), List.of(q), List.of());
        fixture.write("题库/Rich.qbank", new QuestionBankV2Codec().write(bank));
        fx(() -> {
            shell.refresh();
            open("题库/Rich.qbank");
            assertTrue(text(shell.filePane()).contains("暂不支持此题库内容"));
            assertTrue(button("file-mode-toggle").isDisabled());
            assertNull(shell.lookup("#question-practice"));
            assertEquals(FileMode.BROWSE, shell.filePane().mode());
        });
    }

    @Test void emptyQuestionBankHasNoGenerationEntry() throws Exception {
        fx(() -> {
            open("空草稿/新题库.qbank");
            assertTrue(shell.filePane().currentFile().draft());
            assertTrue(text(shell).contains("该题库暂无题目"));
            assertEquals(WorkspaceFileKind.INVALID_QUESTION_BANK, fixture.files.open(fixture.alpha.id(), "空草稿/新题库.qbank").entry().kind());
            assertNull(shell.lookup("#empty-asset-ai"));
        });
    }

    @Test void populatedQuestionBankHasNoAiAction() throws Exception {
        fx(() -> { open("题库/Java集合.qbank"); assertNull(shell.lookup("#empty-asset-ai")); });
    }

    @Test void emptyQuestionBankDraftCanEnterEditorAndAddQuestion() throws Exception {
        fx(() -> {
            open("空草稿/新题库.qbank");
            button("file-mode-toggle").fire();
            shell.applyCss(); shell.layout();
            assertNotNull(shell.lookup("#question-bank-editor"));
            MenuButton add = (MenuButton) shell.lookup("#qbank-add-question");
            add.getItems().getFirst().fire();
            shell.applyCss(); shell.layout();
            assertEquals("1 / 1", ((Label) shell.lookup("#qbank-editor-position")).getText());
            assertNull(shell.lookup("#qbank-add-source"));
            assertNotNull(shell.lookup("#qbank-source-link"));
            assertNotNull(shell.lookup("#qbank-use-source-link"));
        });
    }

    @Test void editPinsItsOwnTabAndPreservesDirtyEditorAcrossSwitches() throws Exception {
        fx(() -> {
            open("我的笔记/学习计划.md");
            button("file-mode-toggle").fire();
            assertTrue(shell.tabs().active().pinned());
            TextArea editor = (TextArea) shell.lookup("#markdown-source-text");
            editor.appendText("\nUncommitted tab content");
            open("Java/Java集合.md");
            assertEquals(2, shell.tabs().tabs().size());
            open("我的笔记/学习计划.md");
            assertSame(editor, shell.lookup("#markdown-source-text"));
            assertTrue(shell.filePane().hasUnsavedChanges());
            assertEquals(FileMode.EDIT, shell.filePane().mode());
            assertEquals(2, shell.tabs().tabs().size());
        });
    }
}
