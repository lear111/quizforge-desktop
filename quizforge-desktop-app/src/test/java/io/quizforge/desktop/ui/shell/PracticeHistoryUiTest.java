package io.quizforge.desktop.ui.shell;

import io.quizforge.core.question.content.TextContent;
import io.quizforge.core.question.model.Question;
import io.quizforge.core.question.model.QuestionBank;
import io.quizforge.core.question.source.QuestionBankReferenceResolver;
import io.quizforge.core.question.source.SourceRef;
import io.quizforge.desktop.ui.file.FileMode;
import io.quizforge.desktop.ui.question.history.PracticeHistoryView;
import io.quizforge.infrastructure.filesystem.qbank.QuestionBankV2Codec;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.control.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class PracticeHistoryUiTest extends WorkspaceUiTestSupport {

    @Test void qbankHistoryEntryKeepsTabShowsEmptyStateAndReturnsToPractice() throws Exception {
        fx(() -> {
            open("题库/Java集合.qbank");
            var tab = shell.tabs().active();
            Button history = button("qbank-history-entry");
            assertEquals("", history.getText());
            assertEquals("clock", history.getGraphic().getAccessibleText());
            assertEquals("历史记录", history.getTooltip().getText());
            assertEquals("历史记录", history.getAccessibleText());
            history.fire();
            shell.applyCss(); shell.layout();
            assertSame(tab, shell.tabs().active());
            assertNotNull(shell.lookup("#practice-history"));
            assertNotNull(shell.lookup("#history-empty"));
            assertNull(shell.lookup("#question-practice"));
            assertSame(shell.lookup("#file-header"), shell.filePane().getTop());
            button("history-back").fire();
            assertSame(tab, shell.tabs().active());
            assertNotNull(shell.lookup("#question-practice"));
            assertNull(shell.filePane().getTop());
            assertNotNull(shell.lookup("#file-header"));
            assertEquals(io.quizforge.core.practice.PracticeSession.Status.ACTIVE, practiceDbSession().status());
        });
    }

    @Test void qbankHistoryCardsExcludeActiveAndDeleteOnlyAfterConfirmation() throws Exception {
        fx(() -> {
            open("题库/Java集合.qbank");
            ((RadioButton) shell.lookup("#option-1")).fire(); submitAnswer();
            String archived = practiceDbSession().id();
            new io.quizforge.infrastructure.persistence.practice.SqlitePracticeSessionRepository(practiceDb())
                    .archive(archived, java.time.Instant.parse("2026-09-28T05:00:00Z"));
            shell.tabs().closeAll(); shell.tabs().openPreview(fixture.alpha.id(), "题库/Java集合.qbank");
            shell.applyCss(); shell.layout();
            String active = practiceDbSession().id();
            button("qbank-history-entry").fire();
            shell.applyCss(); shell.layout();
            var card = (javafx.scene.layout.VBox) shell.lookup("#history-card-" + archived);
            assertNotNull(card);
            assertTrue(text(card).contains("得分 0 / 2"));
            assertTrue(text(card).contains("2 道题"));
            assertEquals(1, ((javafx.scene.layout.TilePane) shell.lookup("#history-grid")).getChildren().size());
            assertNull(shell.lookup("#history-card-" + active));
            assertNotNull(card.getOnContextMenuRequested());
            var menu = (ContextMenu) card.getProperties().get("history.contextMenu");
            assertEquals("删除历史记录", menu.getItems().getFirst().getText());
            ((PracticeHistoryView) shell.lookup("#practice-history")).setDeleteConfirmation(entry -> false);
            menu.getItems().getFirst().fire();
            assertNotNull(shell.lookup("#history-card-" + archived));
            ((PracticeHistoryView) shell.lookup("#practice-history")).setDeleteConfirmation(entry -> true);
            menu.getItems().getFirst().fire();
            assertNull(shell.lookup("#history-card-" + archived));
            assertNotNull(shell.lookup("#history-empty"));
            assertTrue(new io.quizforge.infrastructure.persistence.practice.SqlitePracticeSessionRepository(practiceDb())
                    .findById(archived).isEmpty());
            assertEquals(active, practiceDbSession().id());
        });
    }

    @Test void qbankHistoryDeleteFailureKeepsCardAndShowsError() throws Exception {
        fx(() -> {
            open("题库/Java集合.qbank");
            String archived = practiceDbSession().id();
            new io.quizforge.infrastructure.persistence.practice.SqlitePracticeSessionRepository(practiceDb())
                    .archive(archived, java.time.Instant.parse("2026-09-28T05:00:00Z"));
            shell.tabs().closeAll(); shell.tabs().openPreview(fixture.alpha.id(), "题库/Java集合.qbank");
            button("qbank-history-entry").fire();
            shell.applyCss(); shell.layout();
            var card = (javafx.scene.layout.VBox) shell.lookup("#history-card-" + archived);
            var menu = (ContextMenu) card.getProperties().get("history.contextMenu");
            ((PracticeHistoryView) shell.lookup("#practice-history")).setDeleteConfirmation(entry -> true);
            try (var connection = practiceDb().openConnection(); var statement = connection.createStatement()) {
                statement.execute("CREATE TRIGGER fail_history_delete BEFORE DELETE ON practice_session BEGIN SELECT RAISE(ABORT, 'forced failure'); END");
            }
            menu.getItems().getFirst().fire();
            assertNotNull(shell.lookup("#history-card-" + archived));
            assertTrue(shell.lookup("#history-error").isVisible());
            try (var connection = practiceDb().openConnection(); var statement = connection.createStatement()) {
                statement.execute("DROP TRIGGER fail_history_delete");
            }
        });
    }

    @Test void qbankHistoryCardsShowNewestArchiveFirstAndExcludeCurrentRound() throws Exception {
        fx(() -> {
            open("题库/Java集合.qbank");
            var sessions = new io.quizforge.infrastructure.persistence.practice.SqlitePracticeSessionRepository(practiceDb());
            String first = practiceDbSession().id();
            sessions.archive(first, java.time.Instant.parse("2026-09-28T05:00:00Z"));
            shell.tabs().closeAll(); shell.tabs().openPreview(fixture.alpha.id(), "题库/Java集合.qbank");
            String second = practiceDbSession().id();
            sessions.archive(second, java.time.Instant.parse("2026-09-28T05:01:00Z"));
            shell.tabs().closeAll(); shell.tabs().openPreview(fixture.alpha.id(), "题库/Java集合.qbank");
            String active = practiceDbSession().id();
            button("qbank-history-entry").fire(); shell.applyCss(); shell.layout();
            var cards = ((javafx.scene.layout.TilePane) shell.lookup("#history-grid")).getChildren();
            assertEquals(2, cards.size());
            assertEquals("history-card-" + second, cards.get(0).getId());
            assertEquals("history-card-" + first, cards.get(1).getId());
            assertNull(shell.lookup("#history-card-" + active));
        });
    }

    @Test void historyCardOpensReadOnlyDetailWithQuestionAndAttemptAxes() throws Exception {
        fx(() -> {
            open("题库/Java集合.qbank");
            var tab = shell.tabs().active();
            ((RadioButton) shell.lookup("#option-1")).fire(); submitAnswer();
            button("practice-retry").fire();
            ((RadioButton) shell.lookup("#option-0")).fire(); submitAnswer();
            button("next-question").fire();
            ((javafx.scene.control.CheckBox) shell.lookup("#option-0")).fire();
            String archived = practiceDbSession().id();
            new io.quizforge.infrastructure.persistence.practice.SqlitePracticeSessionRepository(practiceDb())
                    .archive(archived, java.time.Instant.parse("2026-09-28T05:00:00Z"));
            shell.tabs().closeAll(); shell.tabs().openPreview(fixture.alpha.id(), "题库/Java集合.qbank");
            tab = shell.tabs().active();
            var archivedBefore = new io.quizforge.infrastructure.persistence.practice.SqlitePracticeSessionRepository(practiceDb())
                    .findById(archived).orElseThrow();
            button("qbank-history-entry").fire(); shell.applyCss(); shell.layout();
            Node card = shell.lookup("#history-card-" + archived);
            card.fireEvent(new javafx.scene.input.MouseEvent(javafx.scene.input.MouseEvent.MOUSE_CLICKED,
                    8, 8, 8, 8, javafx.scene.input.MouseButton.SECONDARY, 1,
                    false, false, false, false, false, false, false, false, false, true, null));
            assertNotNull(shell.lookup("#practice-history"));
            click(card, 1); shell.applyCss(); shell.layout();
            assertSame(tab, shell.tabs().active());
            assertNotNull(shell.lookup("#practice-history-detail"), text(shell.lookup("#history-error")));
            assertTrue(text(shell.lookup("#history-detail-question")).contains("第 1 / 2 题"));
            assertTrue(text(shell.lookup("#history-detail-question")).contains("第 2 / 2 次作答 · 重新答题"));
            assertTrue(text(shell.lookup("#history-detail-question")).contains("你的答案：A"));
            assertTrue(text(shell.lookup("#history-detail-question")).contains("正确答案：A"));
            assertTrue(text(shell.lookup("#history-detail-question")).contains("按索引访问的时间复杂度"));
            assertTrue(button("history-previous-question").isDisabled());
            assertTrue(button("history-next-attempt").isDisabled());
            assertTrue(button("history-question-number-1").getStyleClass().contains("correct"));
            assertTrue(button("history-question-number-1").getStyleClass().contains("current"));
            assertTrue(button("history-question-number-2").getStyleClass().contains("unsubmitted"));
            assertTrue(text(shell.lookup("#history-question-outline")).contains("单选题"));
            assertTrue(text(shell.lookup("#history-question-outline")).contains("多选题"));
            button("history-previous-attempt").fire();
            assertTrue(text(shell.lookup("#history-detail-question")).contains("第 1 / 2 次作答 · 首次作答"));
            assertTrue(text(shell.lookup("#history-detail-question")).contains("你的答案：B"));
            assertTrue(text(shell.lookup("#history-detail-question")).contains("错误"));
            assertTrue(button("history-previous-attempt").isDisabled());
            button("history-next-attempt").fire();
            assertTrue(text(shell.lookup("#history-detail-question")).contains("第 2 / 2 次作答"));
            button("history-question-number-2").fire();
            assertTrue(text(shell.lookup("#history-detail-question")).contains("第 2 / 2 题"));
            assertTrue(text(shell.lookup("#history-detail-question")).contains("本轮未提交"));
            assertTrue(text(shell.lookup("#history-detail-question")).contains("未提交选择：A"));
            assertFalse(text(shell.lookup("#history-detail-question")).contains("0 / 0"));
            assertTrue(button("history-next-question").isDisabled());
            button("history-previous-question").fire();
            assertTrue(text(shell.lookup("#history-detail-question")).contains("第 2 / 2 次作答"));
            assertNull(shell.lookup("#submit-answer"));
            assertNull(shell.lookup("#practice-retry"));
            assertNull(shell.lookup("#practice-restart"));
            button("history-detail-back").fire();
            assertNotNull(shell.lookup("#practice-history"));
            assertSame(tab, shell.tabs().active());
            assertEquals(archivedBefore, new io.quizforge.infrastructure.persistence.practice.SqlitePracticeSessionRepository(practiceDb())
                    .findById(archived).orElseThrow());
        });
    }

    @Test void historyOutlineUsesArchivedIncorrectState() throws Exception {
        fx(() -> {
            open("题库/Java集合.qbank");
            ((RadioButton) shell.lookup("#option-1")).fire(); submitAnswer();
            String archived = practiceDbSession().id();
            new io.quizforge.infrastructure.persistence.practice.SqlitePracticeSessionRepository(practiceDb())
                    .archive(archived, java.time.Instant.parse("2026-09-28T05:00:00Z"));
            shell.tabs().closeAll(); shell.tabs().openPreview(fixture.alpha.id(), "题库/Java集合.qbank");
            button("qbank-history-entry").fire(); shell.applyCss(); shell.layout();
            click(shell.lookup("#history-card-" + archived), 1); shell.applyCss(); shell.layout();
            assertTrue(button("history-question-number-1").getStyleClass().contains("incorrect"));
            assertTrue(button("history-question-number-1").getStyleClass().contains("current"));
            assertTrue(text(shell.lookup("#history-detail-question")).contains("你的答案：B"));
            assertTrue(text(shell.lookup("#history-detail-question")).contains("正确答案：A"));
            assertTrue(text(shell.lookup("#history-detail-question")).contains("ArrayList 基于可扩容数组"));
        });
    }

    @Test void historyDetailShowsIncorrectAndRetryingWithEarlierAttempt() throws Exception {
        fx(() -> {
            open("题库/Java集合.qbank");
            ((RadioButton) shell.lookup("#option-1")).fire(); submitAnswer();
            button("practice-retry").fire();
            ((RadioButton) shell.lookup("#option-0")).fire();
            String archived = practiceDbSession().id();
            new io.quizforge.infrastructure.persistence.practice.SqlitePracticeSessionRepository(practiceDb())
                    .archive(archived, java.time.Instant.parse("2026-09-28T05:00:00Z"));
            shell.tabs().closeAll(); shell.tabs().openPreview(fixture.alpha.id(), "题库/Java集合.qbank");
            button("qbank-history-entry").fire(); shell.applyCss(); shell.layout();
            click(shell.lookup("#history-card-" + archived), 1); shell.applyCss(); shell.layout();
            assertTrue(button("history-question-number-1").getStyleClass().contains("unsubmitted"));
            assertTrue(text(shell.lookup("#history-final-state")).contains("未完成"));
            assertTrue(text(shell.lookup("#history-detail-question")).contains("未提交选择：A"));
            assertTrue(text(shell.lookup("#history-detail-question")).contains("第 1 / 1 次作答"));
            assertTrue(text(shell.lookup("#history-detail-question")).contains("你的答案：B"));
            assertTrue(button("history-previous-attempt").isDisabled());
            assertTrue(button("history-next-attempt").isDisabled());
            button("history-question-number-2").fire();
            assertNotNull(shell.lookup("#history-no-attempt"));
        });
    }

    @Test void historySourceNavigationPreservesQuestionAttemptOutlineAndReusesMarkdownTabWithoutWrites() throws Exception {
        var sample = navigationBank(false);
        var archived = archiveSourceHistory(sample);
        fx(() -> {
            var targetTab = shell.tabs().openPinned(fixture.alpha.id(), sample.documentPath());
            shell.applyCss(); shell.layout();
            openSourceHistory(sample, archived);
            var historyTab = shell.tabs().active();
            button("history-question-number-2").fire();
            button("history-previous-attempt").fire();
            Node detail = shell.lookup("#practice-history-detail"), outline = shell.lookup("#history-question-outline");
            String position = ((Label) shell.lookup("#history-question-position")).getText();
            String attempt = ((Label) shell.lookup("#history-attempt-position")).getText();
            assertTrue(position.contains("第 2 / 2 题"));
            assertTrue(attempt.contains("第 1 / 2 次作答"));
            assertEquals("SourceNav · 定义", button("history-source-0").getText());
            assertEquals(QuestionBankReferenceResolver.Status.EXACT_MATCH,
                    button("history-source-0").getProperties().get("quizforge.sourceStatus"));
            var before = practiceRows();
            forbidPracticeWrites();
            button("history-source-0").fire();
            assertSame(targetTab, shell.tabs().active());
            assertEquals(2, shell.tabs().tabs().size());
            assertNotNull(shell.lookup("#markdown-outline"));
            shell.applyCss(); shell.layout();
            var reader = (ScrollPane) shell.lookup("#markdown-preview-scroll");
            assertTrue(reader.getVvalue() > 0.5, "History occurrence 2 must reach the second bound block");
            shell.tabs().activate(historyTab);
            assertSame(detail, shell.lookup("#practice-history-detail"));
            assertSame(outline, shell.lookup("#history-question-outline"));
            assertEquals(position, ((Label) shell.lookup("#history-question-position")).getText());
            assertEquals(attempt, ((Label) shell.lookup("#history-attempt-position")).getText());
            assertTrue(button("history-question-number-2").getStyleClass().contains("current"));
            assertEquals(before, practiceRows());
        });
    }

    @Test void historyChangedRevisionAndRenamedMovedSourceStayNavigableWithoutUpdatingArchivedContentId() throws Exception {
        var sample = navigationBank(false);
        String archived = archiveSourceHistory(sample);
        String moved = "Other/Java集合框架.md";
        fixture.write(moved, sample.markdown().replace("First definition.", "Changed definition."));
        Files.delete(fixture.alphaRoot.resolve(sample.documentPath()));
        fx(() -> {
            openSourceHistory(sample, archived);
            var tab = shell.tabs().active();
            assertEquals("Java集合框架 · 定义", button("history-source-0").getText());
            assertTrue(text(shell.lookup("#history-sources")).contains("来源已修改"));
            assertFalse(button("history-source-0").isDisabled());
            assertEquals(QuestionBankReferenceResolver.Status.DIFFERENT_REVISION,
                    button("history-source-0").getProperties().get("quizforge.sourceStatus"));
            var before = practiceRows();
            button("history-source-0").fire();
            assertEquals(moved, shell.tabs().active().path());
            shell.tabs().activate(tab);
            var detail = fixture.context.getBean(io.quizforge.core.port.PracticeRuntimeProvider.class)
                    .history(fixture.alpha.id()).loadArchivedSessionDetail(sample.bank().assetId(), archived);
            assertTrue(detail.questions().getFirst().sourceRefs().toString()
                    .contains(sample.bank().sourceDocuments().getFirst().contentId()));
            assertEquals(before, practiceRows());
        });
    }

    @Test void historyMultipleSourcesHaveIndependentLabelsAndMissingOrOrphanRowsHaveNoClickHandler() throws Exception {
        var sample = navigationBank(true);
        var archived = archiveSourceHistory(sample);
        fx(() -> {
            openSourceHistory(sample, archived);
            assertEquals(4, shell.lookupAll(".question-source-row").size());
            assertEquals(button("history-source-0").getText(), button("history-source-1").getText());
            assertFalse(button("history-source-1").getText().contains("#2"));
            var missing = button("history-source-2");
            var orphan = button("history-source-3");
            assertTrue(missing.isDisabled()); assertNull(missing.getOnAction());
            assertTrue(orphan.isDisabled()); assertNull(orphan.getOnAction());
            assertEquals(QuestionBankReferenceResolver.Status.MISSING_ANCHOR,
                    missing.getProperties().get("quizforge.sourceStatus"));
            assertEquals(QuestionBankReferenceResolver.Status.ORPHAN_ANCHOR,
                    orphan.getProperties().get("quizforge.sourceStatus"));
            assertTrue(text(shell.lookup("#history-sources")).contains("来源位置缺失"));
            assertTrue(text(shell.lookup("#history-sources")).contains("来源锚点无有效内容"));
            assertFalse(button("history-source-0").isDisabled());
        });
    }

    @Test void historyClickRechecksDeletedDocumentAndMissingStatusDoesNotInvalidateDetail() throws Exception {
        var sample = navigationBank(false);
        var archived = archiveSourceHistory(sample);
        fx(() -> {
            openSourceHistory(sample, archived);
            Node detail = shell.lookup("#practice-history-detail");
            Button stale = button("history-source-0");
            var before = practiceRows();
            Files.delete(fixture.alphaRoot.resolve(sample.documentPath()));
            assertDoesNotThrow(stale::fire);
            assertSame(detail, shell.lookup("#practice-history-detail"));
            assertTrue(text(shell.lookup(".workspace-navigation-status")).contains("来源文档缺失"));
            shell.filePane().refreshSourceStatus();
            Button missing = button("history-source-0");
            assertTrue(missing.isDisabled()); assertNull(missing.getOnMouseClicked());
            assertTrue(text(shell.lookup("#history-sources")).contains("来源文档缺失"));
            assertNotNull(shell.lookup("#history-attempt-position"));
            assertEquals(before, practiceRows());
        });
    }

    @Test void historyNavigationToEditingMarkdownKeepsUnsavedTextAndHistoryPosition() throws Exception {
        var sample = navigationBank(false);
        var archived = archiveSourceHistory(sample);
        fx(() -> {
            var target = shell.tabs().openPinned(fixture.alpha.id(), sample.documentPath());
            button("file-mode-toggle").fire();
            TextArea editor = (TextArea) shell.lookup("#markdown-source-text");
            editor.appendText("\nUnsaved changes.\n");
            String edited = editor.getText();
            openSourceHistory(sample, archived);
            var history = shell.tabs().active();
            button("history-question-number-2").fire(); button("history-previous-attempt").fire();
            String attempt = ((Label) shell.lookup("#history-attempt-position")).getText();
            var before = practiceRows();
            button("history-source-0").fire();
            assertSame(target, shell.tabs().active());
            assertSame(editor, shell.lookup("#markdown-source-text"));
            assertEquals(edited, editor.getText());
            assertEquals(FileMode.EDIT, target.pane().mode());
            assertTrue(text(shell.lookup(".workspace-navigation-status")).contains("正在编辑"));
            shell.tabs().activate(history);
            assertTrue(text(shell.lookup("#history-question-position")).contains("第 2 / 2 题"));
            assertEquals(attempt, ((Label) shell.lookup("#history-attempt-position")).getText());
            assertEquals(before, practiceRows());
        });
    }

    @Test void historySourcesUseArchivedSnapshotEvenAfterQuestionDeletedFromCurrentQBank() throws Exception {
        var sample = navigationBank(false);
        var archived = archiveSourceHistory(sample);
        var original = sample.bank().questions().getFirst();
        var replacement = Question.choice("q_replacement", original.type(), new TextContent("New question"), new TextContent("New analysis"), List.of(SourceRef.anchor(original.sourceRefs().getFirst().documentAssetId(),
                        original.sourceRefs().getFirst().documentContentId(), "Different current source", 1,
                        "Current document", "Current source")), original.choicePayload(), original.choiceAnswerSpec());
        fixture.write(sample.bankPath(), new QuestionBankV2Codec().write(new QuestionBank(sample.bank().assetId(), sample.bank().title(), "2.0", List.of(), List.of(replacement), List.of())));
        fx(() -> {
            openSourceHistory(sample, archived);
            assertTrue(text(shell.lookup("#history-detail-question")).contains("Test question"));
            assertFalse(text(shell.lookup("#history-detail-question")).contains("New question"));
            assertEquals("SourceNav · 定义", button("history-source-0").getText());
            assertEquals(QuestionBankReferenceResolver.Status.EXACT_MATCH,
                    button("history-source-0").getProperties().get("quizforge.sourceStatus"));
            button("history-source-0").fire();
            assertEquals(sample.documentPath(), shell.tabs().active().path());
        });
    }

    @Test void historyAnchorDeletionRefreshDisablesOnlyTheMissingOccurrenceAndRestorationWorks() throws Exception {
        var sample = navigationBank(true);
        var archived = archiveSourceHistory(sample);
        fx(() -> {
            openSourceHistory(sample, archived);
            fixture.write(sample.documentPath(), sample.markdown().replace(
                    "<!-- qf:anchor=定义 -->\nSecond definition.", "Second definition."));
            shell.filePane().refreshSourceStatus();
            assertFalse(button("history-source-0").isDisabled());
            assertTrue(button("history-source-1").isDisabled());
            assertNull(button("history-source-1").getOnAction());
            assertEquals(QuestionBankReferenceResolver.Status.MISSING_ANCHOR,
                    shell.lookup("#history-source-1").getProperties().get("quizforge.sourceStatus"));
            fixture.write(sample.documentPath(), sample.markdown());
            shell.filePane().refreshSourceStatus();
            assertEquals(QuestionBankReferenceResolver.Status.EXACT_MATCH,
                    button("history-source-1").getProperties().get("quizforge.sourceStatus"));
        });
    }

    @Test void archivedLegacySourceStillShowsItsStatusWithoutInventingAnAnchorButton() throws Exception {
        fx(() -> {
            open("题库/Java集合.qbank");
            var archived = practiceDbSession().id();
            // Frozen archived payload predates named anchors; current QBank writers never emit it.
            var ref = shell.filePane().currentFile().file().questionBank().questions().getFirst().sourceRefs().getFirst();
            String frozenRefs = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(List.of(
                    java.util.Map.of("documentAssetId", ref.documentAssetId(),
                            "documentContentId", ref.documentContentId(), "sectionId", "section_list",
                            "documentTitle", "Java 集合", "sectionTitle", "ArrayList")));
            try (var connection = practiceDb().openConnection(); var statement = connection.prepareStatement(
                    "UPDATE practice_session_question SET source_refs_snapshot_json = ? WHERE session_id = ?")) {
                statement.setString(1, frozenRefs);
                statement.setString(2, archived);
                statement.executeUpdate();
            }
            new io.quizforge.infrastructure.persistence.practice.SqlitePracticeSessionRepository(practiceDb())
                    .archive(archived, java.time.Instant.now());
            button("qbank-history-entry").fire(); shell.applyCss(); shell.layout();
            click(shell.lookup("#history-card-" + archived), 1); shell.applyCss(); shell.layout();
            Button source = button("history-source-0");
            assertEquals("Java集合 · ArrayList", source.getText());
            assertTrue(source.isDisabled()); assertNull(source.getOnMouseClicked());
            assertEquals(QuestionBankReferenceResolver.Status.EXACT_MATCH,
                    source.getProperties().get("quizforge.sourceStatus"));
            assertTrue(text(shell.lookup("#history-sources")).contains("旧版节点引用"));
        });
    }
}
