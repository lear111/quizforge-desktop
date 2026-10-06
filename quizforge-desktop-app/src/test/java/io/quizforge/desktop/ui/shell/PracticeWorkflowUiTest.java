package io.quizforge.desktop.ui.shell;

import io.quizforge.core.question.content.TextContent;
import io.quizforge.core.question.model.Question;
import io.quizforge.core.question.model.QuestionBank;
import io.quizforge.core.question.content.QuestionText;
import io.quizforge.desktop.ui.file.FileMode;
import io.quizforge.desktop.ui.question.practice.QuestionBankPracticeView;
import io.quizforge.desktop.ui.question.shared.QuestionPracticeLayout;
import io.quizforge.infrastructure.filesystem.qbank.QuestionBankV2Codec;
import io.quizforge.infrastructure.testing.QBankTestPackageBuilder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class PracticeWorkflowUiTest extends WorkspaceUiTestSupport {
    @Test void summaryDisplaysWeightedPointsAndFullTotalWithoutPercentages() throws Exception {
        var reader=new io.quizforge.infrastructure.filesystem.qbank.QBankPackageReader();
        var path=fixture.alphaRoot.resolve("题库/Java集合.qbank");var bank=reader.read(path);
        var questions=new ArrayList<Question>();
        for(int i=0;i<bank.questions().size();i++){
            var q=bank.questions().get(i);
            questions.add(new Question(q.id(),q.type(),q.stimulusRefs(),q.prompt(),q.payload(),q.answerSpec(),
                    new io.quizforge.core.question.model.ScoreSpec(new java.math.BigDecimal(i==0?"2.5":"7.25")),q.evaluationSpec(),q.analysis(),q.sourceRefs()));
        }
        new io.quizforge.infrastructure.filesystem.qbank.QBankPackageWriter().write(path,
                new QuestionBank(bank.assetId(),bank.title(),bank.stimuli(),questions,bank.resources()),io.quizforge.infrastructure.filesystem.qbank.ResourceContentProvider.NONE);
        fx(()->{
            open("题库/Java集合.qbank");((RadioButton)shell.lookup("#option-0")).fire();submitAnswer();
            button("next-question").fire();button("next-question").fire();
            assertEquals("2.5",((Label)shell.lookup("#summary-score")).getText());
            assertEquals("/ 9.75",((Label)shell.lookup("#summary-max-score")).getText());
            assertNull(shell.lookup("#summary-percentage"));
            assertFalse(text(shell.lookup("#practice-summary")).contains("%"));
        });
    }

    @Test void questionBankDefaultsToPracticeAndReusesOneModeToggle() throws Exception {
        fx(() -> {
            open("题库/Java集合.qbank");
            assertEquals(FileMode.BROWSE, shell.filePane().mode());
            assertNotNull(shell.lookup("#question-practice"));
            assertNotNull(shell.lookup("#question-outline"));
            button("next-question").fire();
            Button toggle = button("file-mode-toggle");
            toggle.fire();
            shell.applyCss(); shell.layout();
            assertNotNull(shell.lookup("#question-bank-editor"));
            assertNull(shell.lookup("#question-practice"));
            assertNotNull(shell.lookup("#question-outline"));
            toggle.fire();
            assertSame(toggle, button("file-mode-toggle"));
            assertEquals("第 2 / 2 题", ((Label) shell.lookup("#question-position")).getText());
        });
    }

    @Test void submittingAnswersRevealsFeedbackAndPracticeIsSingleQuestion() throws Exception {
        fx(() -> {
            open("题库/Java集合.qbank");
            assertNull(shell.lookup("#answer-feedback"));
            assertFalse(text(shell.filePane()).contains("时间复杂度"));
            assertNull(shell.lookup("#qbank-source-0"));
            assertFalse(text(shell.filePane()).contains("以下哪些描述"));
            assertTrue(button("submit-answer").isDisabled());
            ((RadioButton) shell.lookup("#option-0")).fire();
            submitAnswer();
            assertTrue(text(shell.lookup("#answer-feedback")).contains("回答正确"));
            assertEquals("Java集合 · section_list", button("qbank-source-0").getText());
            button("next-question").fire();
            assertNull(shell.lookup("#answer-feedback"));
            ((CheckBox) shell.lookup("#option-0")).fire();
            ((CheckBox) shell.lookup("#option-1")).fire();
            submitAnswer();
            assertTrue(text(shell.lookup("#answer-feedback")).contains("回答正确"));
            button("previous-question").fire();
            assertTrue(text(shell.lookup("#answer-feedback")).contains("回答正确"));
        });
    }

    @Test void wrongAnswerDoesNotPassAndPracticeDoesNotChangeBankFile() throws Exception {
        fx(() -> {
            byte[] before = Files.readAllBytes(fixture.alphaRoot.resolve("题库/Java集合.qbank"));
            open("题库/Java集合.qbank");
            ((RadioButton) shell.lookup("#option-1")).fire();
            submitAnswer();
            assertTrue(text(shell.lookup("#answer-feedback")).contains("回答错误"));
            assertArrayEquals(before, Files.readAllBytes(fixture.alphaRoot.resolve("题库/Java集合.qbank")));
        });
    }

    @Test void practiceSummaryPersistsAndOutlineRemainsAvailable() throws Exception {
        fx(() -> {
            byte[] before = Files.readAllBytes(fixture.alphaRoot.resolve("题库/Java集合.qbank"));
            open("题库/Java集合.qbank");
            ((RadioButton) shell.lookup("#option-1")).fire();
            submitAnswer();
            button("next-question").fire();
            ((CheckBox) shell.lookup("#option-0")).fire();
            ((CheckBox) shell.lookup("#option-1")).fire();
            submitAnswer();
            button("next-question").fire();
            assertEquals("1", ((Label) shell.lookup("#summary-score")).getText());
            assertEquals("/ 2", ((Label) shell.lookup("#summary-max-score")).getText());
            assertEquals("1", ((Label) shell.lookup("#summary-correct-count")).getText());
            assertEquals("1", ((Label) shell.lookup("#summary-incorrect-count")).getText());
            assertEquals("0", ((Label) shell.lookup("#summary-unanswered-count")).getText());
            assertTrue(shell.lookup("#question-outline").isVisible());
            assertFalse(button("practice-restart").isDisabled());
            shell.tabs().closeAll();
            shell.tabs().openPreview(fixture.alpha.id(), "题库/Java集合.qbank");
            shell.applyCss(); shell.layout();
            assertNotNull(shell.lookup("#practice-summary"));
            assertEquals("1", ((Label) shell.lookup("#summary-score")).getText());
            assertEquals("/ 2", ((Label) shell.lookup("#summary-max-score")).getText());
            assertEquals("1", ((Label) shell.lookup("#summary-correct-count")).getText());
            assertTrue(shell.lookup("#question-outline").isVisible());
            assertArrayEquals(before, Files.readAllBytes(fixture.alphaRoot.resolve("题库/Java集合.qbank")));
        });
    }

    @Test void retryQuestionHidesFeedbackKeepsAttemptsAndUpdatesOutlineAfterRetrySubmit() throws Exception {
        fx(() -> {
            open("题库/Java集合.qbank");
            ((RadioButton) shell.lookup("#option-1")).fire(); submitAnswer();
            assertTrue(button("question-number-1").getStyleClass().contains("incorrect"));
            assertNotNull(shell.lookup("#answer-feedback"));
            assertNotNull(shell.lookup("#practice-retry"));
            button("practice-retry").fire();
            assertNull(shell.lookup("#answer-feedback"));
            assertNull(shell.lookup("#qbank-source-0"));
            assertTrue(button("question-number-1").getStyleClass().contains("unsubmitted"));
            assertFalse(((RadioButton) shell.lookup("#option-0")).isSelected());
            assertFalse(((RadioButton) shell.lookup("#option-1")).isSelected());
            var row = new io.quizforge.infrastructure.persistence.practice.SqlitePracticeSessionQuestionRepository(practiceDb())
                    .findBySessionIdAndQuestionId(practiceDbSession().id(), "q_one").orElseThrow();
            var attempts = new io.quizforge.infrastructure.persistence.practice.SqliteQuestionAttemptRepository(practiceDb());
            assertEquals(1, attempts.listBySessionQuestion(row.id()).size());
            shell.tabs().closeAll(); shell.tabs().openPreview(fixture.alpha.id(), "题库/Java集合.qbank");
            shell.applyCss(); shell.layout();
            assertNull(shell.lookup("#answer-feedback"));
            assertTrue(button("question-number-1").getStyleClass().contains("unsubmitted"));
            ((RadioButton) shell.lookup("#option-0")).fire(); submitAnswer();
            assertTrue(button("question-number-1").getStyleClass().contains("correct"));
            assertEquals(2, attempts.listBySessionQuestion(row.id()).size());
            assertEquals(io.quizforge.core.practice.QuestionAttempt.Mode.RETRY,
                    attempts.listBySessionQuestion(row.id()).getLast().attemptMode());
        });
    }

    @Test void incompletePracticeSummaryKeepsOutlineAndNavigatesBackToQuestions() throws Exception {
        fx(() -> {
            open("题库/Java集合.qbank");
            button("next-question").fire(); button("next-question").fire();
            assertEquals("0", ((Label) shell.lookup("#summary-score")).getText());
            assertEquals("/ 2", ((Label) shell.lookup("#summary-max-score")).getText());
            assertEquals("0", ((Label) shell.lookup("#summary-correct-count")).getText());
            assertEquals("0", ((Label) shell.lookup("#summary-incorrect-count")).getText());
            assertEquals("2", ((Label) shell.lookup("#summary-unanswered-count")).getText());
            assertTrue(shell.lookup("#question-outline").isVisible());
            assertFalse(button("question-number-1").getStyleClass().contains("current"));
            assertFalse(button("question-number-2").getStyleClass().contains("current"));
            button("summary-previous").fire();
            assertEquals("第 2 / 2 题", ((Label) shell.lookup("#question-position")).getText());
            assertTrue(button("question-number-2").getStyleClass().contains("current"));
            button("next-question").fire(); button("question-number-1").fire();
            assertEquals("第 1 / 2 题", ((Label) shell.lookup("#question-position")).getText());
            assertEquals(io.quizforge.core.practice.PracticeSession.View.QUESTION, practiceDbSession().currentView());
        });
    }

    @Test void restartConfirmationArchivesOldRoundAndKeepsCurrentTab() throws Exception {
        fx(() -> {
            open("题库/Java集合.qbank");
            var tab = shell.tabs().active();
            ((RadioButton) shell.lookup("#option-1")).fire(); submitAnswer();
            button("next-question").fire(); button("next-question").fire();
            String oldId = practiceDbSession().id();
            ((QuestionBankPracticeView) shell.lookup("#question-practice")).setRestartConfirmation(() -> false);
            button("practice-restart").fire();
            assertEquals(oldId, practiceDbSession().id());
            assertNotNull(shell.lookup("#practice-summary"));
            ((QuestionBankPracticeView) shell.lookup("#question-practice")).setRestartConfirmation(() -> true);
            button("practice-restart").fire();
            assertSame(tab, shell.tabs().active());
            assertNotEquals(oldId, practiceDbSession().id());
            assertEquals("第 1 / 2 题", ((Label) shell.lookup("#question-position")).getText());
            assertTrue(button("question-number-1").getStyleClass().contains("unsubmitted"));
            assertTrue(button("question-number-2").getStyleClass().contains("unsubmitted"));
            assertEquals(io.quizforge.core.practice.PracticeSession.Status.ARCHIVED,
                    new io.quizforge.infrastructure.persistence.practice.SqlitePracticeSessionRepository(practiceDb())
                            .findById(oldId).orElseThrow().status());
        });
    }

    @Test void failedRestartKeepsOldActiveSummaryAndShowsError() throws Exception {
        fx(() -> {
            open("题库/Java集合.qbank");
            button("next-question").fire(); button("next-question").fire();
            String oldId = practiceDbSession().id();
            try (var connection = practiceDb().openConnection(); var statement = connection.createStatement()) {
                statement.execute("CREATE TRIGGER fail_restart BEFORE INSERT ON practice_session WHEN NEW.status = 'ACTIVE' BEGIN SELECT RAISE(ABORT, 'forced failure'); END");
            }
            ((QuestionBankPracticeView) shell.lookup("#question-practice")).setRestartConfirmation(() -> true);
            button("practice-restart").fire();
            assertEquals(oldId, practiceDbSession().id());
            assertEquals(io.quizforge.core.practice.PracticeSession.Status.ACTIVE, practiceDbSession().status());
            assertNotNull(shell.lookup("#practice-summary"));
            assertNotNull(shell.lookup("#practice-error"));
            try (var connection = practiceDb().openConnection(); var statement = connection.createStatement()) {
                statement.execute("DROP TRIGGER fail_restart");
            }
        });
    }

    @Test void questionBankEditorSavesToRealFileAndReturnsToPractice() throws Exception {
        fx(() -> {
            open("题库/Java集合.qbank");
            var codec = new QuestionBankV2Codec();
            var oldBank = codec.parse(
                    QBankTestPackageBuilder.read(fixture.alphaRoot.resolve("题库/Java集合.qbank")));
            button("file-mode-toggle").fire();
            shell.applyCss(); shell.layout();
            assertNotNull(shell.lookup("#question-bank-editor"));
            ((TextArea) shell.lookup("#qbank-question-stem")).setText("New question stem?");
            ((TextField) shell.lookup("#qbank-option-0")).setText("Updated option");
            ((TextArea) shell.lookup("#qbank-analysis")).setText("Updated analysis");
            button("qbank-duplicate-question").fire();
            button("qbank-save").fire();
            assertEquals(FileMode.BROWSE, shell.filePane().mode());
            assertTrue(text(shell.filePane()).contains("New question stem?"));
            var saved = codec.parse(
                    QBankTestPackageBuilder.read(fixture.alphaRoot.resolve("题库/Java集合.qbank")));
            assertEquals(oldBank.assetId(), saved.assetId());
            assertEquals(3, saved.questions().size());
            assertNotEquals(saved.questions().get(0).id(), saved.questions().get(1).id());
            assertEquals("Updated option", QuestionText.option(saved.questions().getFirst().choicePayload().options().getFirst()));
            assertEquals("Updated analysis", QuestionText.analysis(saved.questions().getFirst()));
            assertNotEquals(codec.contentId(oldBank), codec.contentId(saved));
        });
    }

    @Test void practiceCardKeepsItsWidthGrowsForLongQuestionsAndPlacesControlsAroundIt() throws Exception {
        fx(() -> {
            stage.setWidth(1600);
            open("题库/Java集合.qbank");
            pulse(200);
            ScrollPane scroll = (ScrollPane) shell.lookup("#practice-scroll");
            Node practice = shell.lookup("#question-practice");
            var bounds = practice.getBoundsInParent();
            assertEquals(scroll.getViewportBounds().getHeight() / 2,
                    bounds.getMinY() + bounds.getHeight() / 2, 2);
            var navigation = (javafx.scene.layout.HBox) shell.lookup("#practice-navigation");
            assertEquals(List.of("previous-question", "practice-question-card", "next-question"),
                    navigation.getChildren().stream().map(Node::getId).toList());
            var card = (javafx.scene.layout.VBox) shell.lookup("#practice-question-card");
            double cardHeight = card.getHeight();
            assertEquals(720, card.getWidth(), 1);
            // Shadows extend beyond the boxes; verify the actual navigation layout.
            var cardBounds = card.localToScene(card.getLayoutBounds());
            assertFalse(button("previous-question").isVisible(), "The first card has no previous arrow");
            assertTrue(button("next-question").isVisible());
            var nextBounds = button("next-question").localToScene(button("next-question").getLayoutBounds());
            assertTrue(nextBounds.getMinX() > cardBounds.getMaxX(),
                    "The next arrow must sit outside the card: " + nextBounds + " / " + cardBounds);
            for (Button control : List.of(button("previous-question"), button("next-question"))) {
                assertEquals("", control.getText());
                assertNotNull(control.getTooltip());
                assertNotNull(control.getGraphic());
            }
            Button submit = button("submit-answer");
            assertEquals("提交答案", submit.getText());
            assertTrue(submit.getStyleClass().contains("primary"));
            assertEquals("practice-card-actions", submit.getParent().getId());
            assertSame(card.getChildren().getLast(), submit.getParent());
            ((Label) shell.lookup(".question-stem")).setText("这是一道包含大量背景信息的长题目，请阅读场景并选择正确答案。".repeat(90));
            shell.layout(); pulse(200);
            assertEquals(720, card.getWidth(), 1);
            assertTrue(card.getHeight() > cardHeight);
            assertTrue(scroll.getContent().getBoundsInLocal().getHeight() > scroll.getViewportBounds().getHeight());
            scroll.setVvalue(1); pulse(200);
            var viewport = scroll.lookup(".viewport");
            var visible = viewport.localToScene(viewport.getBoundsInLocal());
            var controls = submit.localToScene(submit.getBoundsInLocal());
            assertTrue(controls.getMinY() >= visible.getMinY());
            assertTrue(controls.getMaxY() <= visible.getMaxY() + 1);
        });
    }

    @Test void questionOutlineGroupsTypesWithGlobalNumbersInDocumentOrder() throws Exception {
        String path = outlineBank("SINGLE_CHOICE", "MULTIPLE_CHOICE", "SINGLE_CHOICE",
                "MULTIPLE_CHOICE", "SINGLE_CHOICE", "MULTIPLE_CHOICE");
        fx(() -> {
            shell.refresh(); open(path);
            assertNotNull(shell.lookup("#question-outline"));
            assertEquals(List.of("1"), outlineNumbers("single_choice"));
            assertEquals(List.of("2"), outlineNumbers("multiple_choice"));
            assertEquals(List.of("3"), outlineNumbers("single_choice-3"));
            assertEquals(List.of("4"), outlineNumbers("multiple_choice-4"));
            assertEquals(List.of("5"), outlineNumbers("single_choice-5"));
            assertEquals(List.of("6"), outlineNumbers("multiple_choice-6"));
            assertEquals(List.of("1", "2", "3", "4", "5", "6"),
                    nodes(shell.lookup("#question-outline"), Button.class).stream()
                            .filter(b -> b.getStyleClass().contains("question-number-cell"))
                            .map(Button::getText).toList());
            assertTrue(text(shell.lookup("#question-outline-single_choice")).contains("单选题"));
            assertTrue(text(shell.lookup("#question-outline-multiple_choice")).contains("多选题"));
            assertTrue(button("question-number-1").getStyleClass().containsAll(List.of("unsubmitted", "current")));
            assertFalse(button("question-number-2").getStyleClass().contains("current"));
            assertFalse(text(shell.lookup("#question-outline")).contains("点击题号"));
        });
    }

    @Test void questionOutlineOmitsAbsentTypes() throws Exception {
        String path = outlineBank("SINGLE_CHOICE", "SINGLE_CHOICE", "SINGLE_CHOICE");
        fx(() -> {
            shell.refresh(); open(path);
            assertEquals(List.of("1", "2", "3"), outlineNumbers("single_choice"));
            assertNull(shell.lookup("#question-outline-multiple_choice"));
        });
    }

    @Test void questionOutlineJumpPreservesUnsubmittedSelectionsAndPreviousNextSynchronize() throws Exception {
        String path = outlineBank("SINGLE_CHOICE", "MULTIPLE_CHOICE", "SINGLE_CHOICE",
                "MULTIPLE_CHOICE", "SINGLE_CHOICE");
        fx(() -> {
            shell.refresh(); open(path);
            ((RadioButton) shell.lookup("#option-1")).fire();
            assertTrue(button("question-number-1").getStyleClass().contains("draft"));
            button("question-number-5").fire();
            assertEquals("第 5 / 5 题", ((Label) shell.lookup("#question-position")).getText());
            assertNull(shell.lookup("#answer-feedback"));
            assertTrue(button("question-number-5").getStyleClass().contains("current"));
            button("previous-question").fire();
            assertTrue(button("question-number-4").getStyleClass().contains("current"));
            assertFalse(button("question-number-5").getStyleClass().contains("current"));
            button("next-question").fire();
            assertTrue(button("question-number-5").getStyleClass().contains("current"));
            button("question-number-1").fire();
            assertTrue(((RadioButton) shell.lookup("#option-1")).isSelected());
            assertFalse(button("submit-answer").isDisabled());
            assertNull(shell.lookup("#answer-feedback"));
            button("question-number-2").fire();
            ((CheckBox) shell.lookup("#option-0")).fire();
            ((CheckBox) shell.lookup("#option-1")).fire();
            button("question-number-3").fire();
            button("question-number-2").fire();
            assertTrue(((CheckBox) shell.lookup("#option-0")).isSelected());
            assertTrue(((CheckBox) shell.lookup("#option-1")).isSelected());
            assertTrue(button("question-number-2").getStyleClass().containsAll(List.of("draft", "current")));
        });
    }

    @Test void questionOutlineImmediatelyShowsResultsAndKeepsFeedbackOnRevisit() throws Exception {
        fx(() -> {
            open("题库/Java集合.qbank");
            byte[] before = Files.readAllBytes(fixture.alphaRoot.resolve("题库/Java集合.qbank"));
            ((RadioButton) shell.lookup("#option-1")).fire();
            submitAnswer();
            assertTrue(button("question-number-1").getStyleClass().containsAll(List.of("incorrect", "current")));
            assertFalse(button("question-number-1").getStyleClass().contains("unsubmitted"));
            button("question-number-2").fire();
            ((CheckBox) shell.lookup("#option-0")).fire();
            ((CheckBox) shell.lookup("#option-1")).fire();
            submitAnswer();
            assertTrue(button("question-number-2").getStyleClass().containsAll(List.of("correct", "current")));
            button("question-number-1").fire();
            assertTrue(button("question-number-1").getStyleClass().containsAll(List.of("incorrect", "current")));
            assertTrue(button("question-number-2").getStyleClass().contains("correct"));
            assertFalse(button("question-number-2").getStyleClass().contains("current"));
            assertTrue(((RadioButton) shell.lookup("#option-1")).isSelected());
            assertTrue(button("submit-answer").isDisabled());
            assertTrue(text(shell.lookup("#answer-feedback")).contains("回答错误"));
            assertTrue(text(shell.lookup("#answer-feedback")).contains("正确答案：A"));
            assertNotNull(shell.lookup("#qbank-source-0"));
            assertArrayEquals(before, Files.readAllBytes(fixture.alphaRoot.resolve("题库/Java集合.qbank")));
        });
    }

    @Test void questionOutlineScrollsIndependentlyWithoutGrowingThePracticePage() throws Exception {
        String[] types = new String[120];
        for (int i = 0; i < types.length; i++) types[i] = i % 2 == 0 ? "SINGLE_CHOICE" : "MULTIPLE_CHOICE";
        String path = outlineBank(types);
        fx(() -> {
            shell.refresh(); open(path); shell.applyCss(); shell.layout();
            ScrollPane outline = (ScrollPane) shell.lookup("#question-outline-scroll");
            ScrollPane main = (ScrollPane) shell.lookup("#practice-scroll");
            assertTrue(outline.getContent().getBoundsInLocal().getHeight() > outline.getViewportBounds().getHeight());
            assertTrue(outline.getHeight() <= shell.filePane().getHeight());
            double mainPosition = main.getVvalue(), mainHeight = main.getHeight();
            outline.setVvalue(1); shell.layout();
            assertEquals(mainPosition, main.getVvalue());
            assertEquals(mainHeight, main.getHeight());
            assertTrue(outline.getVvalue() > 0);
            button("question-number-120").fire();
            assertEquals("第 120 / 120 题", ((Label) shell.lookup("#question-position")).getText());
        });
    }

    @Test void questionOutlineReflowsWhileResizingWithoutLosingSelections() throws Exception {
        String[] types = new String[60];
        for (int i = 0; i < types.length; i++) types[i] = i < 30 ? "SINGLE_CHOICE" : "MULTIPLE_CHOICE";
        String path = outlineBank(types);
        fx(() -> {
            shell.refresh(); open(path); pulse(100);
            var layout = (QuestionPracticeLayout) shell.filePane().getCenter();
            var outline = layout.outline();
            var section = (javafx.scene.layout.VBox) shell.lookup("#question-outline-single_choice");
            var numbers = (javafx.scene.layout.FlowPane) section.getChildren().get(1);
            var firstCell = button("question-number-1");
            ((RadioButton) shell.lookup("#option-0")).fire();

            layout.setDividerPositions(0.45);
            shell.layout(); layout.layout(); pulse(100);
            long wideColumns = numbers.getChildren().stream().filter(node -> node.getLayoutY() == 0).count();
            double wideHeight = numbers.getHeight();
            assertTrue(outline.getWidth() > 280, "Outline must expand beyond its former fixed maximum");

            layout.setDividerPositions(0.8);
            shell.layout(); layout.layout(); pulse(100);
            long narrowColumns = numbers.getChildren().stream().filter(node -> node.getLayoutY() == 0).count();
            assertTrue(narrowColumns >= 1);
            assertTrue(narrowColumns < wideColumns, "A narrower outline must show fewer columns");
            assertTrue(numbers.getHeight() > wideHeight);
            var scroll = (ScrollPane) shell.lookup("#question-outline-scroll");
            assertTrue(numbers.getWidth() <= scroll.getViewportBounds().getWidth() + 1);
            assertSame(firstCell, button("question-number-1"));
            assertTrue(firstCell.getStyleClass().containsAll(List.of("draft", "current")));
            assertTrue(((RadioButton) shell.lookup("#option-0")).isSelected());
            assertNull(shell.lookup("#answer-feedback"));
            assertEquals(30, outlineNumbers("single_choice").size());
            assertEquals(30, outlineNumbers("multiple_choice").size());
            stage.setWidth(850); stage.setHeight(620);
            shell.layout(); pulse(100);
            assertTrue(numbers.getWidth() <= scroll.getViewportBounds().getWidth() + 1);
            assertTrue(outline.getWidth() >= outline.getMinWidth());
            assertTrue(outline.localToScene(0, 0).getX() >= layout.localToScene(320, 0).getX());
            button("question-number-60").fire();
            assertEquals("第 60 / 60 题", ((Label) shell.lookup("#question-position")).getText());
            button("question-number-1").fire();
            assertTrue(((RadioButton) shell.lookup("#option-0")).isSelected());
        });
    }

    @Test void questionOutlineDividerTracksSidebarAndRemainsInEditMode() throws Exception {
        fx(() -> {
            open("题库/Java集合.qbank"); pulse(100);
            var outer = (SplitPane) shell.getCenter();
            var layout = (QuestionPracticeLayout) shell.filePane().getCenter();
            var outline = layout.outline();
            var seam = shell.lookup("#workspace-outline-seam");
            var topDivider = shell.lookup("#window-outline-divider");
            assertNull(shell.filePane().getTop());
            var readerColumn = (BorderPane) layout.getItems().getFirst();
            assertSame(shell.lookup("#file-header"), readerColumn.getTop());
            assertEquals(outline.localToScene(0, 0).getY(), layout.localToScene(0, 0).getY(), 0.5);
            assertTrue(seam.isVisible());

            outer.setDividerPositions(0.35);
            outer.layout();
            assertEquals(divider(layout).localToScene(0, 0).getX(), seam.localToScene(0, 0).getX(), 1.0);
            layout.setDividerPositions(0.55);
            layout.layout();
            assertEquals(divider(layout).localToScene(0, 0).getX(), seam.localToScene(0, 0).getX(), 1.0);
            assertEquals(divider(layout).localToScene(0, 0).getX(), topDivider.localToScene(0, 0).getX(), 1.0);

            button("file-mode-toggle").fire();
            shell.applyCss(); shell.layout();
            assertTrue(seam.isVisible());
            assertNotNull(shell.lookup("#question-outline"));
            assertNotNull(shell.lookup("#question-bank-editor"));
            assertNull(shell.filePane().getTop());
            assertSame(shell.lookup("#file-header"), readerColumn.getTop());
            assertEquals(divider(layout).localToScene(0, 0).getX(), seam.localToScene(0, 0).getX(), 1.0);
            button("file-mode-toggle").fire(); pulse(100);
            assertTrue(seam.isVisible());
            assertNotNull(shell.lookup("#question-outline"));
            assertNull(shell.filePane().getTop());
            assertNull(shell.lookup("#markdown-outline"));
        });
    }

    @Test void questionBankEditPinsPreviewAndKeepsItsPracticeStateSeparate() throws Exception {
        fx(() -> {
            open("题库/Java集合.qbank");
            assertFalse(shell.tabs().active().pinned());
            button("file-mode-toggle").fire();
            assertTrue(shell.tabs().active().pinned());
            assertEquals(FileMode.EDIT, shell.filePane().mode());
            open("Java/Java集合.md");
            assertEquals(2, shell.tabs().tabs().size());
            open("题库/Java集合.qbank");
            assertEquals(FileMode.EDIT, shell.filePane().mode());
            assertNull(shell.lookup("#markdown-outline"));
            assertEquals(2, shell.tabs().tabs().size());
        });
    }

    @Test void persistentPracticeReopeningTabRestoresDraftCurrentAndOutlineWithoutSubmit() throws Exception {
        fx(() -> {
            open("题库/Java集合.qbank");
            ((RadioButton) shell.lookup("#option-1")).fire();
            button("next-question").fire();
            var before = practiceDbSession();
            shell.tabs().closeAll();
            shell.tabs().openPreview(fixture.alpha.id(), "题库/Java集合.qbank");
            shell.applyCss(); shell.layout();
            assertEquals(before.id(), practiceDbSession().id());
            assertEquals("第 2 / 2 题", ((Label) shell.lookup("#question-position")).getText());
            assertTrue(button("question-number-2").getStyleClass().contains("current"));
            button("previous-question").fire();
            assertTrue(((RadioButton) shell.lookup("#option-1")).isSelected());
            assertNull(shell.lookup("#answer-feedback"));
            assertTrue(button("question-number-1").getStyleClass().contains("draft"));
            button("question-number-2").fire();
            assertEquals("q_two", practiceDbSession().currentQuestionId());
        });
    }

    @Test void editSaveThenPracticeRunsRevisionSyncAndPreservesOnlyNonSemanticAnswers() throws Exception {
        fx(() -> {
            open("题库/Java集合.qbank");
            ((RadioButton) shell.lookup("#option-0")).fire(); submitAnswer();
            String id = practiceDbSession().id();
            button("file-mode-toggle").fire();
            shell.applyCss(); shell.layout();
            ((TextArea) shell.lookup("#qbank-analysis")).appendText(" Updated analysis.");
            button("qbank-save").fire();
            assertEquals(id, practiceDbSession().id());
            assertTrue(button("question-number-1").getStyleClass().contains("correct"));
            assertTrue(text(shell.lookup("#answer-feedback")).contains("Updated analysis."));
            button("file-mode-toggle").fire();
            shell.applyCss(); shell.layout();
            ((TextArea) shell.lookup("#qbank-question-stem")).appendText(" Updated stem.");
            button("qbank-save").fire();
            assertEquals(id, practiceDbSession().id());
            assertNull(shell.lookup("#answer-feedback"));
            assertTrue(button("question-number-1").getStyleClass().contains("unsubmitted"));
        });
    }

    @Test void leaveEditWithoutChangesReopensPracticeAndChecksActualFileRevision() throws Exception {
        fx(() -> {
            open("题库/Java集合.qbank");
            ((RadioButton) shell.lookup("#option-0")).fire(); submitAnswer();
            button("file-mode-toggle").fire();
            var file = fixture.alphaRoot.resolve("题库/Java集合.qbank");
            var codec = new QuestionBankV2Codec();
            var bank = codec.parse(QBankTestPackageBuilder.read(file));
            var q = bank.questions().getFirst();
            var changed = Question.choice(q.id(), q.type(), new TextContent(QuestionText.prompt(q) + " Changed"), q.analysis(), q.sourceRefs(), q.choicePayload(), q.choiceAnswerSpec());
            var edited = new QuestionBank(bank.assetId(), bank.title(), "2.0", List.of(), List.of(changed, bank.questions().get(1)), List.of());
            QBankTestPackageBuilder.write(file, codec.write(edited));
            button("file-mode-toggle").fire();
            shell.applyCss(); shell.layout();
            assertNull(shell.lookup("#answer-feedback"));
            assertTrue(text(shell.lookup("#question-practice")).contains("Changed"));
            assertEquals(codec.contentId(edited), practiceDbSession().questionBankContentId());
        });
    }

    @Test void submitPersistenceFailureKeepsSelectionDraftAndFeedbackHiddenThenDoubleConfirmIsSafe() throws Exception {
        fx(() -> {
            open("题库/Java集合.qbank");
            ((RadioButton) shell.lookup("#option-0")).fire();
            var db = practiceDb();
            try (var connection = db.openConnection(); var statement = connection.createStatement()) {
                statement.execute("CREATE TRIGGER fail_submit BEFORE UPDATE OF practice_state ON practice_session_question WHEN NEW.practice_state = 'SUBMITTED' BEGIN SELECT RAISE(ABORT, 'forced failure'); END");
            }
            submitAnswer();
            assertNull(shell.lookup("#answer-feedback"));
            assertNotNull(shell.lookup("#practice-error"));
            assertTrue(((RadioButton) shell.lookup("#option-0")).isSelected());
            assertFalse(button("submit-answer").isDisabled());
            assertTrue(button("question-number-1").getStyleClass().contains("draft"));
            var questions = new io.quizforge.infrastructure.persistence.practice.SqlitePracticeSessionQuestionRepository(db);
            var row = questions.findBySessionIdAndQuestionId(practiceDbSession().id(), "q_one").orElseThrow();
            assertEquals(io.quizforge.core.practice.PracticeSessionQuestion.State.DRAFT, row.practiceState());
            assertEquals(new io.quizforge.core.practice.PracticePayload(List.of("opt_a")), row.draftAnswer());
            var attempts = new io.quizforge.infrastructure.persistence.practice.SqliteQuestionAttemptRepository(db);
            assertTrue(attempts.listBySessionQuestion(row.id()).isEmpty());
            try (var connection = db.openConnection(); var statement = connection.createStatement()) { statement.execute("DROP TRIGGER fail_submit"); }
            Button confirm = button("submit-answer"); io.quizforge.desktop.testing.FxTestRuntime.acceptSubmission(confirm); confirm.fire();
            assertNotNull(shell.lookup("#answer-feedback"));
            assertEquals(1, attempts.listBySessionQuestion(row.id()).size());
        });
    }
}
