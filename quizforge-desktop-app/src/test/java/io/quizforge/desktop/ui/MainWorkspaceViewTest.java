package io.quizforge.desktop.ui;

import static org.junit.jupiter.api.Assertions.*;

import io.quizforge.core.workspace.WorkspaceFileEntry;
import io.quizforge.core.workspace.WorkspaceFileKind;
import io.quizforge.infrastructure.filesystem.StandardKnowledgeDocumentV1;
import io.quizforge.infrastructure.filesystem.QuestionBankV1Codec;
import io.quizforge.infrastructure.filesystem.RegisteredMarkdownCodec;
import io.quizforge.core.document.registered.QuizForgeReference;
import io.quizforge.core.document.registered.QuizForgeReferenceCodec;
import io.quizforge.core.document.navigation.QuizForgeNavigationLink;
import io.quizforge.core.document.navigation.QuizForgeNavigationLinkCodec;
import io.quizforge.core.question.QuestionBankFile;
import io.quizforge.core.question.QuestionBankReferenceResolver;
import io.quizforge.core.question.QuestionSourceLinkService;
import io.quizforge.core.port.WorkspaceAssetScanner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class MainWorkspaceViewTest {
    @TempDir Path temp;
    private ShellFixture fixture;
    private MainWorkspaceView shell;
    private Stage stage;

    @BeforeAll static void startFx() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        Platform.startup(() -> { Platform.setImplicitExit(false); started.countDown(); });
        assertTrue(started.await(20, TimeUnit.SECONDS));
    }

    @BeforeEach void setup() throws Exception {
        fixture = new ShellFixture(temp);
        fx(() -> {
            stage = new Stage();
            shell = fixture.shell(stage);
            Scene scene = new Scene(shell, 1100, 740);
            UiTheme.apply(scene);
            stage.setScene(scene);
            stage.setOpacity(0);
            stage.show();
        }, 120); // Native JavaFX window initialization can be slow on a busy Windows desktop.
    }

    @AfterEach void close() throws Exception {
        fx(() -> { if (shell != null) shell.filePane().clear(); if (stage != null) stage.close(); });
        if (fixture != null) fixture.close();
    }

    @AfterAll static void stopFx() { Platform.exit(); }

    @Test void switchingWorkspaceReplacesTreeAndClearsCurrentFile() throws Exception {
        fx(() -> {
            open("我的笔记/学习计划.md");
            assertNotNull(shell.filePane().currentFile());
            button("file-mode-toggle").fire();
            shell.switchWorkspace(fixture.beta);
            assertNull(shell.filePane().currentFile());
            assertEquals(FileMode.BROWSE, shell.filePane().mode());
            assertNull(shell.lookup("#file-header"));
            var paths = paths(shell.sidebar().tree().getRoot());
            assertTrue(paths.contains("Spring.md"));
            assertFalse(paths.contains("我的笔记/学习计划.md"));
        });
    }

    @Test void sidebarContainsSwitcherOneRealFileTreeAndFixedSettings() throws Exception {
        fx(() -> {
            assertEquals(3, shell.sidebar().getChildren().size());
            assertEquals(1, shell.lookupAll(".tree-view").size());
            assertTrue(shell.lookupAll(".tab-pane").isEmpty());
            assertNull(shell.lookup("#home-button"));
            assertNull(shell.getTop());
            assertNull(shell.getBottom());
            assertEquals("Java 学习库", shell.sidebar().switcher().getText());
            for (String obsolete : List.of("Home", "Workspace Files", "Generate Document", "Generate QuestionBank", "QuestionBank Practice")) {
                assertFalse(text(shell.sidebar()).contains(obsolete));
            }
        });
    }

    @Test void formalHeaderHidesIdsAndMetadataPopoverReReadsTheFile() throws Exception {
        fx(() -> {
            open("Java/Java集合.md");
            Parent header = (Parent) shell.lookup("#file-header");
            assertEquals("Java集合.md", ((Label) shell.lookup("#file-title")).getText());
            assertFalse(text(shell).contains("doc_java"));
            assertFalse(text(shell).contains("qfd:v1:"));
            assertEquals(2, header.lookupAll(".button").size());
            String updated = ShellFixture.document("Updated knowledge from disk.");
            fixture.write("Java/Java集合.md", updated);
            button("asset-info-button").fire();
            assertTrue(shell.filePane().details().isShowing());
            String metadata = text(shell.filePane().details().content());
            assertTrue(metadata.contains("doc_java"));
            assertTrue(metadata.contains(new StandardKnowledgeDocumentV1().parseIfStandard(updated).orElseThrow().contentId()));
            assertTrue(metadata.contains("Java/Java集合.md"));
            shell.filePane().details().hide();
            assertFalse(text(shell).contains("doc_java"));
        });
    }

    @Test void markdownUsesOneToggleWithCurrentModeIconAndNoPreviewSourceTabs() throws Exception {
        fx(() -> {
            open("我的笔记/学习计划.md");
            Button toggle = button("file-mode-toggle");
            assertEquals(FileMode.BROWSE, shell.filePane().mode());
            assertEquals("book", toggle.getGraphic().getAccessibleText());
            assertNull(shell.lookup("#asset-info-button"));
            assertTrue(shell.lookupAll(".tab-pane").isEmpty());
            assertTrue(text(shell).contains("本周学习计划"));
            toggle.fire();
            assertSame(toggle, button("file-mode-toggle"));
            assertEquals(FileMode.EDIT, shell.filePane().mode());
            assertEquals("book-pen", toggle.getGraphic().getAccessibleText());
            assertEquals("切换到浏览模式", toggle.getTooltip().getText());
            TextArea source = (TextArea) shell.lookup("#markdown-source-text");
            assertTrue(source.getText().contains("# 本周学习计划"));
            toggle.fire();
            assertEquals(FileMode.BROWSE, shell.filePane().mode());
        });
    }

    @Test void outlineJumpsToTheCorrectRepeatedAnchorAndResetsOnFileChange() throws Exception {
        StringBuilder markdown = new StringBuilder("# Java\n### Collection\n");
        markdown.append("<!-- qf:anchor=定义 -->\nFirst definition.\n");
        for (int index = 0; index < 65; index++)
            markdown.append("Paragraph ").append(index).append(" with enough content to scroll.\n\n");
        markdown.append("<!-- qf:anchor=定义 -->\nSecond definition.\n");
        markdown.append("<!-- qf:anchor=孤立锚点 -->\n");
        fixture.write("Java/Outline.md", markdown.toString());
        fx(() -> {
            shell.refresh();
            open("Java/Outline.md");
            pulse(100);
            assertNotNull(shell.lookup("#markdown-outline"));
            var first = button("qf-nav-2");
            var second = button("qf-nav-3");
            assertEquals("定义", first.getAccessibleText());
            assertEquals("定义", second.getAccessibleText());
            assertNotEquals(first.getId(), second.getId());
            second.fire();
            var scroll = (ScrollPane) shell.lookup("#markdown-preview-scroll");
            assertTrue(scroll.getVvalue() > 0.5, "Second anchor should scroll to its own paragraph");
            assertTrue(second.getStyleClass().contains("markdown-outline-selected"));
            button("qf-nav-1").fire();
            assertTrue(scroll.getVvalue() < 0.1, "Heading should navigate in the same preview");
            assertTrue(button("qf-nav-4").isDisabled());
            assertFalse(text(shell.lookup(".markdown-preview")).contains("qf:anchor"));
            open("我的笔记/学习计划.md");
            assertNull(shell.lookup("#qf-nav-3"));
            assertTrue(shell.lookupAll(".markdown-outline-selected").isEmpty());
            button("file-mode-toggle").fire();
            assertNull(shell.lookup("#markdown-outline"));
            ((TextArea) shell.lookup("#markdown-source-text")).setText("# Updated\n## New section\nText.\n");
            button("markdown-save").fire();
            assertNotNull(shell.lookup("#markdown-outline"));
            assertEquals("New section", button("qf-nav-1").getAccessibleText());
            open("题库/Java集合.qbank");
            assertNull(shell.lookup("#markdown-outline"));
        });
    }

    @Test void markdownSourceEditSavesRealFileAndReloadsBrowse() throws Exception {
        fx(() -> {
            open("我的笔记/学习计划.md");
            button("file-mode-toggle").fire();
            TextArea source = (TextArea) shell.lookup("#markdown-source-text");
            source.setText("# 更新后的计划\n\n```java\nclass Example {}\n```\n");
            button("markdown-save").fire();
            assertEquals(FileMode.BROWSE, shell.filePane().mode());
            assertTrue(text(shell).contains("更新后的计划"));
            assertEquals("# 更新后的计划\n\n```java\nclass Example {}\n```\n",
                    Files.readString(fixture.alphaRoot.resolve("我的笔记/学习计划.md")));
        });
    }

    @Test void formalMarkdownEditRetainsAssetIdAndRefreshesContentId() throws Exception {
        fx(() -> {
            open("Java/Java集合.md");
            String before = shell.filePane().currentFile().file().entry().contentId();
            button("file-mode-toggle").fire();
            TextArea source = (TextArea) shell.lookup("#markdown-source-text");
            source.setText(source.getText().replace("支持按索引访问", "可以按索引读取"));
            button("markdown-save").fire();
            assertEquals("doc_java", shell.filePane().currentFile().file().entry().assetId());
            assertNotEquals(before, shell.filePane().currentFile().file().entry().contentId());
            assertEquals("doc_java", fixture.files.open(fixture.alpha.id(), "Java/Java集合.md")
                    .entry().assetId());
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
            assertNull(shell.lookup("#question-outline"));
            toggle.fire();
            assertSame(toggle, button("file-mode-toggle"));
            assertEquals("Question 2 / 2", ((Label) shell.lookup("#question-position")).getText());
        });
    }

    @Test void emptyStandardDocumentShowsAiHookWithoutChangingFormalValidity() throws Exception {
        fx(() -> {
            open("空草稿/新文档.md");
            assertTrue(shell.filePane().currentFile().draft());
            assertTrue(text(shell).contains("此文档暂无内容"));
            assertEquals(WorkspaceFileKind.INVALID_STANDARD_DOCUMENT, fixture.files.open(fixture.alpha.id(), "空草稿/新文档.md").entry().kind());
            button("empty-asset-ai").fire();
            assertEquals(1, fixture.aiOpened.get());
            button("asset-info-button").fire();
            assertTrue(text(shell.filePane().details().content()).contains("未通过正式格式校验"));
        });
    }

    @Test void populatedStandardDocumentHasNoAiAction() throws Exception {
        fx(() -> { open("Java/Java集合.md"); assertNull(shell.lookup("#empty-asset-ai")); });
    }

    @Test void emptyQuestionBankShowsAiHookWithoutChangingCodecValidity() throws Exception {
        fx(() -> {
            open("空草稿/新题库.qbank");
            assertTrue(shell.filePane().currentFile().draft());
            assertTrue(text(shell).contains("该题库暂无题目"));
            assertEquals(WorkspaceFileKind.INVALID_QUESTION_BANK, fixture.files.open(fixture.alpha.id(), "空草稿/新题库.qbank").entry().kind());
            button("empty-asset-ai").fire();
            assertEquals(1, fixture.aiOpened.get());
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
            assertNotNull(shell.lookup("#qbank-add-source"));
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
            button("submit-answer").fire();
            assertTrue(text(shell.lookup("#answer-feedback")).contains("回答正确"));
            assertEquals("Java集合 · ArrayList", button("qbank-source-0").getText());
            button("next-question").fire();
            assertNull(shell.lookup("#answer-feedback"));
            ((CheckBox) shell.lookup("#option-0")).fire();
            ((CheckBox) shell.lookup("#option-1")).fire();
            button("submit-answer").fire();
            assertTrue(text(shell.lookup("#answer-feedback")).contains("回答正确"));
            button("previous-question").fire();
            assertTrue(text(shell.lookup("#answer-feedback")).contains("回答正确"));
        });
    }

    @Test void wrongAnswerDoesNotPassAndPracticeDoesNotChangeBankFile() throws Exception {
        fx(() -> {
            String before = Files.readString(fixture.alphaRoot.resolve("题库/Java集合.qbank"));
            open("题库/Java集合.qbank");
            ((RadioButton) shell.lookup("#option-1")).fire();
            button("submit-answer").fire();
            assertTrue(text(shell.lookup("#answer-feedback")).contains("回答错误"));
            assertEquals(before, Files.readString(fixture.alphaRoot.resolve("题库/Java集合.qbank")));
        });
    }

    @Test void practiceSummaryPersistsAndOutlineRemainsAvailable() throws Exception {
        fx(() -> {
            String before = Files.readString(fixture.alphaRoot.resolve("题库/Java集合.qbank"));
            open("题库/Java集合.qbank");
            ((RadioButton) shell.lookup("#option-1")).fire();
            button("submit-answer").fire();
            button("next-question").fire();
            ((CheckBox) shell.lookup("#option-0")).fire();
            ((CheckBox) shell.lookup("#option-1")).fire();
            button("submit-answer").fire();
            button("next-question").fire();
            assertTrue(text(shell.lookup("#practice-summary")).contains("已提交：2 / 2"));
            assertTrue(text(shell.lookup("#practice-summary")).contains("50%"));
            assertTrue(shell.lookup("#question-outline").isVisible());
            assertFalse(button("practice-restart").isDisabled());
            shell.tabs().closeAll();
            shell.tabs().openPreview(fixture.alpha.id(), "题库/Java集合.qbank");
            shell.applyCss(); shell.layout();
            assertNotNull(shell.lookup("#practice-summary"));
            assertTrue(text(shell.lookup("#practice-summary")).contains("已提交：2 / 2"));
            assertTrue(shell.lookup("#question-outline").isVisible());
            assertEquals(before, Files.readString(fixture.alphaRoot.resolve("题库/Java集合.qbank")));
        });
    }

    @Test void retryQuestionHidesFeedbackKeepsAttemptsAndUpdatesOutlineAfterRetrySubmit() throws Exception {
        fx(() -> {
            open("题库/Java集合.qbank");
            ((RadioButton) shell.lookup("#option-1")).fire(); button("submit-answer").fire();
            assertTrue(button("question-number-1").getStyleClass().contains("incorrect"));
            assertNotNull(shell.lookup("#answer-feedback"));
            assertNotNull(shell.lookup("#practice-retry"));
            button("practice-retry").fire();
            assertNull(shell.lookup("#answer-feedback"));
            assertNull(shell.lookup("#qbank-source-0"));
            assertTrue(button("question-number-1").getStyleClass().contains("unsubmitted"));
            assertFalse(((RadioButton) shell.lookup("#option-0")).isSelected());
            assertFalse(((RadioButton) shell.lookup("#option-1")).isSelected());
            var row = new io.quizforge.infrastructure.persistence.SqlitePracticeSessionQuestionRepository(practiceDb())
                    .findBySessionIdAndQuestionId(practiceDbSession().id(), "q_one").orElseThrow();
            var attempts = new io.quizforge.infrastructure.persistence.SqliteQuestionAttemptRepository(practiceDb());
            assertEquals(1, attempts.listBySessionQuestion(row.id()).size());
            shell.tabs().closeAll(); shell.tabs().openPreview(fixture.alpha.id(), "题库/Java集合.qbank");
            shell.applyCss(); shell.layout();
            assertNull(shell.lookup("#answer-feedback"));
            assertTrue(button("question-number-1").getStyleClass().contains("unsubmitted"));
            ((RadioButton) shell.lookup("#option-0")).fire(); button("submit-answer").fire();
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
            assertTrue(text(shell.lookup("#practice-summary")).contains("已提交：0 / 2"));
            assertTrue(text(shell.lookup("#practice-summary")).contains("正确率：—"));
            assertTrue(text(shell.lookup("#practice-summary")).contains("未完成：2"));
            assertTrue(shell.lookup("#question-outline").isVisible());
            assertFalse(button("question-number-1").getStyleClass().contains("current"));
            assertFalse(button("question-number-2").getStyleClass().contains("current"));
            button("summary-previous").fire();
            assertEquals("Question 2 / 2", ((Label) shell.lookup("#question-position")).getText());
            assertTrue(button("question-number-2").getStyleClass().contains("current"));
            button("next-question").fire(); button("question-number-1").fire();
            assertEquals("Question 1 / 2", ((Label) shell.lookup("#question-position")).getText());
            assertEquals(io.quizforge.core.practice.PracticeSession.View.QUESTION, practiceDbSession().currentView());
        });
    }

    @Test void summaryRestoresAfterTabCloseAndLastSubmitDoesNotAutoOpenIt() throws Exception {
        fx(() -> {
            open("题库/Java集合.qbank");
            button("next-question").fire();
            ((CheckBox) shell.lookup("#option-0")).fire(); button("submit-answer").fire();
            assertNull(shell.lookup("#practice-summary"));
            button("next-question").fire();
            assertTrue(text(shell.lookup("#practice-summary")).contains("已提交：1 / 2"));
            assertTrue(text(shell.lookup("#practice-summary")).contains("正确率：0%"));
            String id = practiceDbSession().id();
            shell.tabs().closeAll(); shell.tabs().openPreview(fixture.alpha.id(), "题库/Java集合.qbank");
            shell.applyCss(); shell.layout();
            assertEquals(id, practiceDbSession().id());
            assertNotNull(shell.lookup("#practice-summary"));
            assertTrue(shell.lookup("#question-outline").isVisible());
        });
    }

    @Test void restartConfirmationArchivesOldRoundAndKeepsCurrentTab() throws Exception {
        fx(() -> {
            open("题库/Java集合.qbank");
            var tab = shell.tabs().active();
            ((RadioButton) shell.lookup("#option-1")).fire(); button("submit-answer").fire();
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
            assertEquals("Question 1 / 2", ((Label) shell.lookup("#question-position")).getText());
            assertTrue(button("question-number-1").getStyleClass().contains("unsubmitted"));
            assertTrue(button("question-number-2").getStyleClass().contains("unsubmitted"));
            assertEquals(io.quizforge.core.practice.PracticeSession.Status.ARCHIVED,
                    new io.quizforge.infrastructure.persistence.SqlitePracticeSessionRepository(practiceDb())
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
            var codec = new QuestionBankV1Codec();
            var oldBank = codec.parse(
                    Files.readString(fixture.alphaRoot.resolve("题库/Java集合.qbank")));
            button("file-mode-toggle").fire();
            shell.applyCss(); shell.layout();
            assertNotNull(shell.lookup("#question-bank-editor"));
            ((TextArea) shell.lookup("#qbank-question-stem")).setText("New question stem?");
            ((TextField) shell.lookup("#qbank-option-0")).setText("Updated option");
            ((TextArea) shell.lookup("#qbank-analysis")).setText("Updated analysis");
            ((MenuButton) shell.lookup("#qbank-question-actions")).getItems().getFirst().fire();
            button("qbank-save").fire();
            assertEquals(FileMode.BROWSE, shell.filePane().mode());
            assertTrue(text(shell.filePane()).contains("New question stem?"));
            var saved = codec.parse(
                    Files.readString(fixture.alphaRoot.resolve("题库/Java集合.qbank")));
            assertEquals(oldBank.id(), saved.id());
            assertEquals(3, saved.questions().size());
            assertNotEquals(saved.questions().get(0).id(), saved.questions().get(1).id());
            assertEquals("Updated option", saved.questions().getFirst().data().options().getFirst().content());
            assertEquals("Updated analysis", saved.questions().getFirst().analysis());
            assertNotEquals(codec.contentId(oldBank), codec.contentId(saved));
        });
    }

    @Test void fileTreeKeepsFoldersAndOnlyThreeSupportedExtensions() throws Exception {
        fx(() -> {
            var paths = paths(shell.sidebar().tree().getRoot());
            assertTrue(paths.contains("空目录"));
            assertTrue(paths.contains("原始材料"));
            assertFalse(paths.contains("原始材料/Java 官方文档.pdf"));
            assertTrue(paths.contains("Java/Java集合.md"));
            assertTrue(paths.stream().noneMatch(path -> path.contains(".quizforge")));
            var entries = shell.sidebar().tree().getRoot().getChildren().stream().map(TreeItem::getValue).toList();
            assertTrue(entries.getFirst().kind() == WorkspaceFileKind.DIRECTORY);
        });
    }

    @Test void folderFileAndWorkspaceMenusExposeOnlyRelevantActions() throws Exception {
        fx(() -> {
            open("Java/Java集合.md");
            var folderItems = folderCell("Java").getContextMenu().getItems();
            assertEquals(java.util.Arrays.asList("folder-new-folder", "folder-new-md", "folder-new-qbank",
                            null, "copy-file-path", null,
                            "rename-file-entry", "delete-file-entry"),
                    folderItems.stream().map(MenuItem::getId).toList());
            var fileItems = folderCell("Java/Java集合.md").getContextMenu().getItems();
            assertEquals("copy-link", fileItems.getFirst().getId());
            assertTrue(fileItems.stream().noneMatch(item -> item.getId() != null
                    && item.getId().startsWith("folder-new-")));
            Menu copy = (Menu) fileItems.stream().filter(item ->
                    "copy-file-path".equals(item.getId())).findFirst().orElseThrow();
            assertEquals(List.of("copy-relative-path", "copy-absolute-path"),
                    copy.getItems().stream().map(MenuItem::getId).toList());
            var switcher = shell.sidebar().switcher().getItems();
            assertTrue(switcher.stream().anyMatch(item -> "workspace-new-folder".equals(item.getId())));
            Menu newFile = (Menu) switcher.stream().filter(item -> "workspace-new-file".equals(item.getId()))
                    .findFirst().orElseThrow();
            assertEquals(List.of(".md", ".qbank"),
                    newFile.getItems().stream().map(MenuItem::getText).toList());
            assertTrue(switcher.stream().anyMatch(item -> "refresh-workspace".equals(item.getId())));
        });
    }

    @Test void fileTreeCopyLinkWritesTheRevisionAgnosticAssetUri() throws Exception {
        fx(() -> {
            open("Java/Java集合.md");
            var entry = shell.filePane().currentFile().file().entry();
            assertEquals("copy-link",
                    folderCell("Java/Java集合.md").getContextMenu().getItems().getFirst().getId());
            folderCell("Java/Java集合.md").getContextMenu().getItems().getFirst().fire();
            assertEquals(new io.quizforge.core.document.navigation.MarkdownNavigationLinkCodec().format(
                    "Java/Java集合.md", QuizForgeNavigationLink.asset(entry.assetId())), fixture.copiedText.get());
        });
    }

    @Test void sourceLinkOnlyChangesExplicitSourceAndOrdinaryFieldsKeepMarkdownText() throws Exception {
        String path = "Java/Source.md";
        var prepared = new RegisteredMarkdownCodec().prepareRegistration(
                "# Source\n\n<!-- qf:anchor=定义 -->\nCurrent content.\n", path);
        fixture.write(path, prepared.source());
        String link = new io.quizforge.core.document.navigation.MarkdownNavigationLinkCodec().format(path,
                QuizForgeNavigationLink.anchor(prepared.document().documentAssetId(), "定义", 1));
        fx(() -> {
            shell.refresh();
            open("题库/Java集合.qbank");
            button("file-mode-toggle").fire();
            shell.applyCss(); shell.layout();
            ((TextArea) shell.lookup("#qbank-question-stem")).setText(link);
            ((TextField) shell.lookup("#qbank-option-0")).setText(link);
            ((TextArea) shell.lookup("#qbank-analysis")).setText(link);
            assertEquals(1, shell.filePane().currentFile().file().questionBank()
                    .questions().getFirst().sourceRefs().size());
            ((TextField) shell.lookup("#qbank-source-link")).setText(link);
            button("qbank-use-source-link").fire();
            shell.applyCss(); shell.layout();
            assertNotNull(shell.lookup("#qbank-source-1"));
            assertEquals("Source · 定义", ((Button) shell.lookup("#qbank-source-1")).getText());
            button("qbank-save").fire();
            var saved = new QuestionBankV1Codec().parse(Files.readString(
                    fixture.alphaRoot.resolve("题库/Java集合.qbank")));
            var question = saved.questions().getFirst();
            assertEquals(link, question.stem());
            assertEquals(link, question.data().options().getFirst().content());
            assertEquals(link, question.analysis());
            assertEquals(2, question.sourceRefs().size());
            var source = question.sourceRefs().get(1);
            assertEquals(prepared.document().documentAssetId(), source.documentAssetId());
            assertEquals(prepared.document().contentId(), source.documentContentId());
            assertEquals("定义", source.anchorName());
            assertEquals(1, source.occurrence());
            assertFalse(Files.readString(fixture.alphaRoot.resolve("题库/Java集合.qbank"))
                    .contains("\"displayLabel\""));
        });
    }

    @Test void rejectedSourceLinkDoesNotRemoveExistingReferences() throws Exception {
        fx(() -> {
            open("题库/Java集合.qbank");
            button("file-mode-toggle").fire();
            shell.applyCss(); shell.layout();
            ((TextField) shell.lookup("#qbank-source-link")).setText(
                    "[Heading](quizforge://asset/doc_java/heading/ArrayList?occurrence=1)");
            button("qbank-use-source-link").fire();
            assertNotNull(shell.lookup("#qbank-source-0"));
            assertNull(shell.lookup("#qbank-source-1"));
            assertTrue(text(shell.lookup("#question-bank-editor")).contains("Source Anchor"));
        });
    }

    @Test void formalMarkdownNamedAnchorCanBecomeSourceButLegacyIdCannot() throws Exception {
        String path = "Java/Named.md";
        String markdown = ShellFixture.document("A formal document section.").replace("doc_java", "doc_named")
                + "\n<!-- qf:anchor=命名来源 -->\nNamed anchor body.\n";
        fixture.write(path, markdown);
        fx(() -> {
            shell.refresh();
            open("题库/Java集合.qbank");
            button("file-mode-toggle").fire();
            shell.applyCss(); shell.layout();
            var links = new io.quizforge.core.document.navigation.MarkdownNavigationLinkCodec();
            ((TextField) shell.lookup("#qbank-source-link")).setText(links.format(path,
                    QuizForgeNavigationLink.anchor("doc_named", "section_list", 1)));
            button("qbank-use-source-link").fire();
            assertNull(shell.lookup("#qbank-source-1"));
            assertTrue(text(shell.lookup("#question-bank-editor")).contains("Source Anchor is missing"));
            ((TextField) shell.lookup("#qbank-source-link")).setText(links.format(path,
                    QuizForgeNavigationLink.anchor("doc_named", "命名来源", 1)));
            button("qbank-use-source-link").fire();
            shell.applyCss(); shell.layout();
            assertEquals("Named · 命名来源", ((Button) shell.lookup("#qbank-source-1")).getText());
            button("qbank-save").fire();
            var saved = new QuestionBankV1Codec().parse(Files.readString(
                    fixture.alphaRoot.resolve("题库/Java集合.qbank")));
            var ref = saved.questions().getFirst().sourceRefs().get(1);
            assertEquals("命名来源", ref.anchorName());
            assertEquals(new StandardKnowledgeDocumentV1().parseIfStandard(markdown)
                    .orElseThrow().contentId(), ref.documentContentId());
        });
    }

    @Test void ordinaryMarkdownFileTreeCopyLinkRegistersWithoutOpeningIt() throws Exception {
        String path = "我的笔记/学习计划.md";
        String before = Files.readString(fixture.alphaRoot.resolve(path));
        fx(() -> {
            open("Java/Java集合.md");
            folderCell(path).getContextMenu().getItems().getFirst().fire();
            assertEquals("Java/Java集合.md", shell.filePane().currentFile()
                    .file().entry().relativePath());
            var registered = fixture.files.open(fixture.alpha.id(), path).entry();
            assertEquals(WorkspaceFileKind.STANDARD_DOCUMENT, registered.kind());
            assertEquals(new io.quizforge.core.document.navigation.MarkdownNavigationLinkCodec().format(
                    path, QuizForgeNavigationLink.asset(registered.assetId())), fixture.copiedText.get());
            assertTrue(Files.readString(fixture.alphaRoot.resolve(path)).endsWith(before));
            assertFalse(Files.readString(fixture.alphaRoot.resolve(path)).contains("qf:anchor="));
        });
    }

    @Test void outlineCopyLinkUsesFinalOccurrenceAndRegistersOnlyDocumentIdentity() throws Exception {
        String path = "Java/navigation-links.md";
        String source = "---\ntitle: User note\n---\n# Java\n\n## 示例\n正文 A。\n\n"
                + "## 示例\n正文 B。\n\n<!-- qf:anchor=定义 -->\n内容 A。\n\n"
                + "<!-- qf:anchor=定义 -->\n内容 B。\n\n<!-- qf:anchor=孤立 -->\n";
        fixture.write(path, source);
        fx(() -> {
            shell.refresh();
            open(path);
            assertEquals(WorkspaceFileKind.MARKDOWN, shell.filePane().currentFile().kind());
            assertEquals("示例", button("qf-nav-2").getAccessibleText());
            assertNull(button("qf-nav-5").getContextMenu());
            assertTrue(button("qf-nav-5").isDisabled());
            button("qf-nav-2").getContextMenu().getItems().getFirst().fire();
            var registered = shell.filePane().currentFile();
            String assetId = registered.file().entry().assetId();
            assertTrue(assetId.startsWith("doc_"));
            assertEquals(path, registered.file().entry().relativePath());
            assertEquals(new io.quizforge.core.document.navigation.MarkdownNavigationLinkCodec().format(
                    path, QuizForgeNavigationLink.heading(assetId, "示例", 2)), fixture.copiedText.get());
            String saved = Files.readString(fixture.alphaRoot.resolve(path));
            assertTrue(saved.contains("title: User note"));
            assertTrue(saved.endsWith(source.substring(source.indexOf("# Java"))));
            assertFalse(saved.contains("qf:id="));
            assertEquals(2, saved.split("qf:anchor=定义", -1).length - 1);
            var previewLayout = (javafx.scene.layout.HBox) shell.filePane().getCenter();
            var reader = (ScrollPane) previewLayout.getChildren().getFirst();
            var page = (javafx.scene.layout.StackPane) reader.getContent();
            assertTrue(text(page).contains("内容 B"));
            assertFalse(text(page).contains("qf:anchor"));
            assertEquals("示例", button("qf-nav-2").getAccessibleText());
            button("qf-nav-4").getContextMenu().getItems().getFirst().fire();
            assertEquals(new io.quizforge.core.document.navigation.MarkdownNavigationLinkCodec().format(
                    path, QuizForgeNavigationLink.anchor(assetId, "定义", 2)), fixture.copiedText.get());
            assertEquals(saved, Files.readString(fixture.alphaRoot.resolve(path)));
            assertEquals(assetId, shell.filePane().currentFile().file().entry().assetId());
        });
    }

    @Test void handwrittenAnchorCopyLinkRegistersWithoutAddingAnotherAnchor() throws Exception {
        String path = "Java/handwritten-navigation.md";
        String source = "# H\n\n<!-- qf:anchor=手写来源 -->\nParagraph.\n";
        fixture.write(path, source);
        fx(() -> {
            shell.refresh();
            open(path);
            button("qf-nav-1").getContextMenu().getItems().getFirst().fire();
            var entry = shell.filePane().currentFile().file().entry();
            assertEquals(new io.quizforge.core.document.navigation.MarkdownNavigationLinkCodec().format(
                    path, QuizForgeNavigationLink.anchor(entry.assetId(), "手写来源", 1)),
                    fixture.copiedText.get());
            String saved = Files.readString(fixture.alphaRoot.resolve(path));
            assertTrue(saved.endsWith(source));
            assertEquals(1, saved.split("qf:anchor=手写来源", -1).length - 1);
        });
    }

    @Test void rightClickDoesNotDiscardTheOpenFile() throws Exception {
        fx(() -> {
            open("Java/Java集合.md");
            TreeCell<?> target = folderCell("我的笔记/学习计划.md");
            for (var type : List.of(javafx.scene.input.MouseEvent.MOUSE_PRESSED,
                    javafx.scene.input.MouseEvent.MOUSE_RELEASED,
                    javafx.scene.input.MouseEvent.MOUSE_CLICKED)) {
                target.fireEvent(new javafx.scene.input.MouseEvent(type, 8, 8, 8, 8,
                        javafx.scene.input.MouseButton.SECONDARY, 1, false, false, false, false,
                        type == javafx.scene.input.MouseEvent.MOUSE_PRESSED,
                        false, false, false, false, true, null));
            }
            assertEquals("Java/Java集合.md", shell.filePane().currentFile().file().entry().relativePath());
        });
    }

    @Test void sourceReferenceCreationRegistersOnlyTheSelectedMarkdownBlock() throws Exception {
        String path = "我的笔记/学习计划.md";
        String original = Files.readString(fixture.alphaRoot.resolve(path));
        fx(() -> {
            open(path);
            assertNull(folderCell(path).getGraphic().lookup(".reference-link-indicator"));
            assertTrue(folderCell(path).getContextMenu().getItems().stream()
                    .noneMatch(item -> "register-markdown-document".equals(item.getId())));
            Node paragraph = shell.lookup(".preview-prose");
            ContextMenu menu = (ContextMenu) paragraph.getProperties().get("quizforge.sourceContextMenu");
            assertEquals("create-source-reference", menu.getItems().getFirst().getId());
            Platform.runLater(() -> answerDialog(ButtonType.CANCEL.getText()));
            menu.getItems().getFirst().fire();
            assertEquals(original, Files.readString(fixture.alphaRoot.resolve(path)));
            assertEquals(WorkspaceFileKind.MARKDOWN, shell.filePane().currentFile().kind());

            Platform.runLater(() -> {
                DialogPane pane = currentDialog();
                ((javafx.scene.control.TextField) pane.lookup(".text-field")).setText("学习计划来源");
                answerDialog("Create and Copy");
            });
            menu.getItems().getFirst().fire();
            assertEquals(path, shell.filePane().currentFile().file().entry().relativePath());
            var copiedDocument = shell.filePane().currentFile().registeredMarkdown();
            assertNotNull(copiedDocument);
            assertEquals(new QuizForgeReferenceCodec().encode(QuizForgeReference.anchor(
                    copiedDocument.documentAssetId(), copiedDocument.contentId(), "学习计划来源", 1)),
                    fixture.copiedText.get());
            assertEquals(WorkspaceFileKind.STANDARD_DOCUMENT, shell.filePane().currentFile().kind());
            assertNotNull(shell.filePane().currentFile().registeredMarkdown());
            assertNotNull(folderCell(path).getGraphic().lookup(".reference-link-indicator"));
            assertTrue(Files.readString(fixture.alphaRoot.resolve(path)).contains("quizforge:"));
            assertTrue(Files.readString(fixture.alphaRoot.resolve(path)).contains("<!-- qf:anchor=学习计划来源 -->"));
            assertFalse(Files.readString(fixture.alphaRoot.resolve(path)).contains("qf:id=node_"));
            shell.refresh();
            open(path);
            assertNotNull(folderCell(path).getGraphic().lookup(".reference-link-indicator"));
            assertNotNull(shell.filePane().currentFile().registeredMarkdown());
            assertEquals("copy-link", folderCell(path).getContextMenu().getItems()
                    .getFirst().getId());
            assertEquals("quizforge://document/" + shell.filePane().currentFile()
                    .registeredMarkdown().documentAssetId(),
                    new QuizForgeReferenceCodec().encode(QuizForgeReference.document(
                            shell.filePane().currentFile().registeredMarkdown().documentAssetId())));
            button("file-mode-toggle").fire();
            assertTrue(((TextArea) shell.lookup("#markdown-source-text")).getText()
                    .contains("<!-- qf:anchor=学习计划来源 -->"));
        });
    }

    @Test void aBlockWithTwoAnchorsOffersAChoiceBeforeCopying() throws Exception {
        String path = "Java/two-anchors.md";
        fixture.write(path, "---\nquizforge:\n  format: document\n  version: 1\n"
                + "  assetId: doc_two_anchors\n---\n# H\n\n<!-- qf:anchor=first -->\n"
                + "<!-- qf:anchor=second -->\nParagraph.\n");
        fx(() -> {
            shell.refresh();
            open(path);
            Node paragraph = shell.lookup(".preview-prose");
            ContextMenu menu = (ContextMenu) paragraph.getProperties().get("quizforge.sourceContextMenu");
            assertEquals(List.of("create-source-reference", "copy-source-reference"),
                    menu.getItems().stream().map(MenuItem::getId).toList());
            Platform.runLater(() -> {
                DialogPane pane = currentDialog();
                ((javafx.scene.control.ComboBox<?>) pane.lookup(".combo-box"))
                        .getSelectionModel().select(1);
                answerDialog(ButtonType.OK.getText());
            });
            menu.getItems().get(1).fire();
            assertEquals(new QuizForgeReferenceCodec().encode(QuizForgeReference.anchor(
                            "doc_two_anchors", shell.filePane().currentFile().registeredMarkdown()
                                    .contentId(), "second", 1)),
                    fixture.copiedText.get());
        });
    }

    @Test void handWrittenAnchorInOrdinaryMarkdownCanBeCopiedOnDemand() throws Exception {
        String path = "Java/manual-anchor.md";
        fixture.write(path, "# H\n\n<!-- qf:anchor=手写来源 -->\nParagraph.\n");
        fx(() -> {
            shell.refresh();
            open(path);
            assertEquals(WorkspaceFileKind.MARKDOWN, shell.filePane().currentFile().kind());
            Node paragraph = shell.lookup(".preview-prose");
            ContextMenu menu = (ContextMenu) paragraph.getProperties().get("quizforge.sourceContextMenu");
            assertEquals(List.of("create-source-reference", "copy-source-reference"),
                    menu.getItems().stream().map(MenuItem::getId).toList());
            menu.getItems().get(1).fire();
            assertEquals(WorkspaceFileKind.STANDARD_DOCUMENT, shell.filePane().currentFile().kind());
            assertNotNull(folderCell(path).getGraphic().lookup(".reference-link-indicator"));
            String saved = Files.readString(fixture.alphaRoot.resolve(path));
            assertEquals(1, saved.split("qf:anchor=手写来源", -1).length - 1);
            assertTrue(saved.contains("quizforge:"));
            var document = shell.filePane().currentFile().registeredMarkdown();
            assertEquals(new QuizForgeReferenceCodec().encode(QuizForgeReference.anchor(
                    document.documentAssetId(), document.contentId(), "手写来源", 1)),
                    fixture.copiedText.get());
        });
    }

    @Test void fileTreeMarksOnlyReferenceEnabledMarkdown() throws Exception {
        fixture.write("Java/registered-in-name.md", "# Ordinary Markdown\n");
        fixture.write("Java/addressable.md", new RegisteredMarkdownCodec()
                .prepare("# Registered\n\nAddressable paragraph.\n", "Java/addressable.md").source());
        fx(() -> {
            shell.refresh();
            Node ordinary = folderCell("Java/registered-in-name.md").getGraphic();
            assertNotNull(ordinary.lookup(".icon-markdown"));
            assertNull(ordinary.lookup(".reference-link-indicator"));
            Node registered = folderCell("Java/addressable.md").getGraphic();
            assertNotNull(registered.lookup(".icon-markdown"));
            assertNotNull(registered.lookup(".reference-link-indicator"));
            assertEquals("可引用文档", registered.getAccessibleText());
            assertTrue(registered.getProperties().values().stream().anyMatch(value ->
                    value instanceof Tooltip tooltip && "可引用文档".equals(tooltip.getText())));
        });
    }

    @Test void richMarkdownUsesOneBoundedReaderForOrdinaryAndReferenceEnabledFiles() throws Exception {
        String source = """
                # Knowledge Notes

                ## Concepts

                ### Details

                #### Fourth level

                ##### Fifth level

                ###### Sixth level

                <!-- qf:anchor=Definition -->
                中文 English 123 with **strong**, *emphasis*, `inline code`, and [a link](https://example.com).

                - First item
                  - Nested item
                - Second item

                1. Ordered item
                2. Another item

                > A short quotation.

                ---

                ```java
                LONG_CODE
                ```
                """.replace("LONG_CODE", "System.out.println(\"QuizForge\");".repeat(25));
        fixture.write("Java/Reader.md", source);
        fixture.write("Java/Reader-reference.md", """
                ---
                quizforge:
                  format: document
                  version: 1
                  assetId: doc_reader_reference
                ---
                """ + source);
        fx(() -> {
            assertTrue(stage.getScene().getStylesheets().contains(UiTheme.markdownStylesheet()));
            shell.refresh();
            stage.setWidth(1500);
            for (String path : List.of("Java/Reader.md", "Java/Reader-reference.md")) {
                open(path);
                pulse(150);
                var page = (javafx.scene.layout.VBox) shell.lookup(".markdown-preview");
                assertNotNull(page);
                assertTrue(page.getWidth() <= 940.5, "Reading column must stay bounded on a wide window");
                for (int level = 1; level <= 6; level++)
                    assertNotNull(shell.lookup(".preview-heading-" + level));
                assertEquals(3, shell.lookupAll(".preview-list").size());
                assertNotNull(shell.lookup(".preview-quote"));
                assertNotNull(shell.lookup(".preview-rule"));
                assertFalse(shell.lookupAll(".prose-code").isEmpty());
                assertFalse(shell.lookupAll(".prose-link").isEmpty());
                assertNotNull(((Label) shell.lookup(".prose-code")).getBackground());
                var code = (ScrollPane) shell.lookup(".preview-code");
                assertNotNull(code);
                assertEquals(ScrollPane.ScrollBarPolicy.AS_NEEDED, code.getHbarPolicy());
                assertTrue(code.getContent().prefWidth(-1) > code.getViewportBounds().getWidth());
                assertFalse(text(shell.filePane().getCenter()).contains("qf:anchor"));
            }
            assertNotNull(shell.filePane().currentFile().registeredMarkdown());
            stage.setWidth(650);
            shell.applyCss(); shell.layout(); pulse(150);
            var page = (javafx.scene.layout.VBox) shell.lookup(".markdown-preview");
            var reader = (ScrollPane) shell.lookup("#markdown-preview-scroll");
            assertTrue(page.getWidth() <= reader.getViewportBounds().getWidth() + 1,
                    "Reading column must shrink with a compact window");
            button("file-mode-toggle").fire();
            assertTrue(((TextArea) shell.lookup("#markdown-source-text")).getText()
                    .contains("<!-- qf:anchor=Definition -->"));
        });
    }

    @Test void previewCopiesNamedAnchorOnTheCorrectRepeatedParagraph() throws Exception {
        String source = "# Heading\n\nSame text.\n\nSame text.\n\n- Item\n\n> Quote\n\n```java\nint x = 1;\n```\n";
        var prepared = new RegisteredMarkdownCodec().prepareAnchor(source,
                "Java/References.md", 5, 1, "Repeated source");
        fixture.write("Java/References.md", prepared.source());
        fx(() -> {
            shell.refresh();
            open("Java/References.md");
            var registered = shell.filePane().currentFile().registeredMarkdown();
            assertNotNull(registered);
            var paragraphs = shell.lookupAll(".preview-prose").stream()
                    .filter(node -> "Same text.".equals(text(node).trim())).toList();
            assertEquals(2, paragraphs.size());
            assertNotNull(paragraphs.get(0).getProperties().get("quizforge.sourceBlock"));
            assertNotNull(paragraphs.get(1).getProperties().get("quizforge.sourceBlock"));
            assertTrue(shell.lookupAll(".preview-heading-1").stream().anyMatch(node ->
                    node.getProperties().containsKey("quizforge.sourceBlock")));
            assertTrue(shell.lookupAll(".preview-code").stream().anyMatch(node ->
                    node.getProperties().containsKey("quizforge.sourceBlock")));
            assertTrue(shell.lookupAll(".preview-quote").stream().anyMatch(node ->
                    node.getProperties().containsKey("quizforge.sourceBlock")));
            assertTrue(shell.lookupAll(".markdown-preview").stream().flatMap(node ->
                    ((Parent) node).getChildrenUnmodifiable().stream()).anyMatch(node ->
                    node.getProperties().containsKey("quizforge.sourceBlock")
                            && node.getStyleClass().contains("preview-list")));
            ContextMenu menu = (ContextMenu) paragraphs.get(1).getProperties()
                    .get("quizforge.sourceContextMenu");
            assertEquals("copy-source-reference", menu.getItems().get(1).getId());
            menu.getItems().get(1).fire();
            assertEquals(new QuizForgeReferenceCodec().encode(QuizForgeReference.anchor(
                    registered.documentAssetId(), registered.contentId(), "Repeated source", 1)),
                    fixture.copiedText.get());
            open("我的笔记/学习计划.md");
            assertTrue(shell.lookupAll(".preview-prose").stream().anyMatch(node ->
                    node.getProperties().containsKey("quizforge.sourceContextMenu")));
        });
    }

    private static void answerDialog(String label) {
        DialogPane pane = currentDialog();
        ButtonType choice = pane.getButtonTypes().stream()
                .filter(type -> label.equals(type.getText())).findFirst().orElseThrow();
        ((Button) pane.lookupButton(choice)).fire();
    }

    private static DialogPane currentDialog() {
        return javafx.stage.Window.getWindows().stream()
                .filter(javafx.stage.Window::isShowing)
                .map(window -> window.getScene() == null ? null : window.getScene().getRoot())
                .filter(DialogPane.class::isInstance).map(DialogPane.class::cast)
                .findFirst().orElseThrow();
    }

    @Test void refreshLoadsChangedRealMarkdownWithoutLegacyTables() throws Exception {
        fx(() -> {
            open("我的笔记/学习计划.md");
            fixture.write("我的笔记/学习计划.md", "# New content\nRead from the real file.");
            shell.refresh();
            assertTrue(text(shell).contains("Read from the real file."));
            assertFalse(text(shell).contains("本周学习计划"));
        });
    }

    @Test void malformedAssetsAreNotPromotedToEmptyDrafts() throws Exception {
        fx(() -> {
            open("错误文件/broken.qbank");
            assertFalse(shell.filePane().currentFile().draft());
            assertNull(shell.lookup("#empty-asset-ai"));
            fixture.write("空草稿/新题库.qbank", "{\"format\":\"quizforge-question-bank\",\"schemaVersion\":\"9.0\",\"id\":\"qb_x\",\"title\":\"Bad\",\"sourceDocuments\":[],\"questions\":[]}");
            shell.refresh();
            open("空草稿/新题库.qbank");
            assertFalse(shell.filePane().currentFile().draft());
            assertNull(shell.lookup("#empty-asset-ai"));
        });
    }

    @Test void referencesAreResolvedWhenOpeningDetailsAndReflectChangedOrMissingFiles() throws Exception {
        fx(() -> {
            open("题库/Java集合.qbank");
            button("asset-info-button").fire();
            assertTrue(text(shell.filePane().details().content()).contains("Exact"));
            shell.filePane().details().hide();
            fixture.write("Java/Java集合.md", ShellFixture.document("Different revision."));
            button("asset-info-button").fire();
            assertTrue(text(shell.filePane().details().content()).contains("Changed"));
            shell.filePane().details().hide();
            Files.delete(fixture.alphaRoot.resolve("Java/Java集合.md"));
            button("asset-info-button").fire();
            assertTrue(text(shell.filePane().details().content()).contains("Missing"));
        });
    }

    @Test void switcherTracksRecentWorkspacesAndSettingsStaysAtSidebarBottom() throws Exception {
        fx(() -> {
            shell.switchWorkspace(fixture.beta);
            assertEquals(fixture.beta.id(), new WorkspaceHistory(fixture.history).order(fixture.workspaces.listWorkspaces()).getFirst().id());
            var menu = shell.sidebar().switcher();
            assertEquals("Spring 学习", menu.getText());
            assertNotNull(menu.getItems().getFirst().getGraphic());
            Button settings = button("settings-button");
            settings.fire();
            assertEquals(1, fixture.settingsOpened.get());
            assertTrue(menu.getItems().stream().noneMatch(item -> "workspace-settings".equals(item.getId())));
            Node footer = shell.lookup("#workspace-settings-area");
            assertSame(footer, shell.sidebar().getChildren().getLast());
            assertEquals(shell.sidebar().getHeight() - 10, footer.getBoundsInParent().getMaxY(), 1);
            assertEquals("", settings.getText());
            assertEquals("设置", settings.getTooltip().getText());
            menu.getItems().stream().filter(item -> fixture.alpha.id().equals(item.getUserData())).findFirst().orElseThrow().fire();
            assertEquals(fixture.alpha.id(), shell.currentWorkspace().id());
        });
    }

    @Test void sidebarResizesAndLongNamesNeverRequireHorizontalScrolling() throws Exception {
        String longPath = "我的笔记/这是一个用于检查侧栏收缩后依然能够正常选择和打开的非常长的学习笔记文件名称.md";
        fixture.write(longPath, "# Long filename remains accessible");
        for (int i = 0; i < 35; i++) fixture.write("我的笔记/笔记-" + i + ".md", "# Note");
        fx(() -> {
            shell.refresh();
            open(longPath);
            SplitPane split = (SplitPane) shell.lookup("#workspace-split");
            split.setDividerPositions(0.4);
            shell.layout();
            double wide = shell.sidebar().getWidth();
            split.setDividerPositions(0.18);
            shell.layout();
            assertTrue(wide - shell.sidebar().getWidth() > 150);
            assertTrue(shell.sidebar().getWidth() >= 190);
            var tree = shell.sidebar().tree();
            tree.scrollTo(tree.getSelectionModel().getSelectedIndex());
            tree.layout();
            var bars = tree.lookupAll(".scroll-bar").stream().map(ScrollBar.class::cast).toList();
            assertTrue(bars.stream().anyMatch(bar -> bar.getOrientation() == javafx.geometry.Orientation.VERTICAL && bar.isVisible()));
            assertTrue(bars.stream().noneMatch(bar -> bar.getOrientation() == javafx.geometry.Orientation.HORIZONTAL && bar.isVisible()));
            assertEquals(longPath, shell.filePane().currentFile().file().entry().relativePath());
            assertTrue(text(shell.filePane()).contains("Long filename remains accessible"));
            Button settings = button("settings-button");
            assertEquals(shell.sidebar().getHeight() - 10, shell.lookup("#workspace-settings-area").getBoundsInParent().getMaxY(), 1);
            settings.fire();
            assertEquals(1, fixture.settingsOpened.get());
        });
    }

    @Test void folderRowAndArrowToggleOncePerClickAndArrowAnimates() throws Exception {
        fx(() -> {
            open("Java/Java集合.md");
            pulse(200);
            var tree = shell.sidebar().tree();
            TreeCell<?> cell = folderCell("Java");
            var folder = cell.getTreeItem();
            Node arrow = cell.lookup(".folder-chevron");
            List<Double> angles = new ArrayList<>();
            arrow.rotateProperty().addListener((obs, before, after) -> angles.add(after.doubleValue()));
            assertTrue(folder.isExpanded());
            assertEquals(90, arrow.getRotate(), 0.01);
            click(cell, 1);
            assertFalse(folder.isExpanded());
            assertEquals(90, arrow.getRotate(), 0.01); // The chevron does not jump to its endpoint.
            pulse(240);
            assertTrue(angles.stream().anyMatch(angle -> angle > 0 && angle < 90));
            cell = folderCell("Java");
            assertEquals(0, cell.lookup(".folder-chevron").getRotate(), 0.01);
            click(cell.getGraphic(), 1);
            assertTrue(folder.isExpanded());
            pulse(200);
            cell = folderCell("Java");
            assertEquals(90, cell.lookup(".folder-chevron").getRotate(), 0.01);
            click(cell.getDisclosureNode(), 1);
            assertFalse(folder.isExpanded());
            click(cell, 2); // The second click toggles once, without the default double-click handler.
            assertTrue(folder.isExpanded());
            assertEquals("Java/Java集合.md", shell.filePane().currentFile().file().entry().relativePath());
            folder.setExpanded(false); // Keyboard/programmatic changes share the animation.
            pulse(200);
            assertEquals(0, folderCell("Java").lookup(".folder-chevron").getRotate(), 0.01);
        });
    }

    @Test void folderIconKeepsItsStyledGraphicWhenCellsAreRecycled() throws Exception {
        fx(() -> {
            open("Java/Java集合.md");
            pulse(200);
            TreeCell<?> cell = folderCell("Java");
            int row = cell.getIndex();
            Node icon = cell.getGraphic();
            var stroke = ((javafx.scene.shape.SVGPath) icon.lookup(".line-icon")).getStroke();
            assertNotNull(stroke);
            for (int i = 0; i < 6; i++) {
                // TreeView reuses rows during expansion. Verify the icon is ready before the next CSS pulse.
                cell.updateIndex(-1);
                assertNull(cell.getGraphic());
                cell.updateIndex(row);
                assertSame(icon, cell.getGraphic());
                assertEquals(stroke, ((javafx.scene.shape.SVGPath) cell.getGraphic().lookup(".line-icon")).getStroke());
                assertEquals(20, cell.getGraphic().getLayoutBounds().getWidth(), 0.01);
            }
        });
    }

    @Test void practiceCentersShortQuestionsAndScrollsLongOnesWithSymbolControls() throws Exception {
        fx(() -> {
            open("题库/Java集合.qbank");
            pulse(200);
            ScrollPane scroll = (ScrollPane) shell.lookup("#practice-scroll");
            Node practice = shell.lookup("#question-practice");
            var bounds = practice.getBoundsInParent();
            assertEquals(scroll.getViewportBounds().getHeight() / 2,
                    bounds.getMinY() + bounds.getHeight() / 2, 2);
            var navigation = (javafx.scene.layout.HBox) shell.lookup("#practice-navigation");
            assertEquals(List.of("previous-question", "submit-answer", "next-question"),
                    navigation.getChildren().stream().map(Node::getId).toList());
            for (Node child : navigation.getChildren()) {
                Button control = (Button) child;
                assertEquals("", control.getText());
                assertNotNull(control.getTooltip());
                assertNotNull(control.getGraphic());
            }
            ((Label) shell.lookup(".question-stem")).setText("这是一道包含大量背景信息的长题目，请阅读场景并选择正确答案。".repeat(90));
            shell.layout(); pulse(200);
            assertTrue(scroll.getContent().getBoundsInLocal().getHeight() > scroll.getViewportBounds().getHeight());
            scroll.setVvalue(1); pulse(200);
            var viewport = scroll.lookup(".viewport");
            var visible = viewport.localToScene(viewport.getBoundsInLocal());
            var controls = navigation.localToScene(navigation.getBoundsInLocal());
            assertTrue(controls.getMinY() >= visible.getMinY());
            assertTrue(controls.getMaxY() <= visible.getMaxY() + 1);
        });
    }

    @Test void fileTreePreviewReplacesOnlyPreviewAndDoubleClickPins() throws Exception {
        fx(() -> {
            open("我的笔记/学习计划.md");
            assertEquals(1, shell.tabs().tabs().size());
            assertFalse(shell.tabs().active().pinned());
            open("Java/Java集合.md");
            assertEquals(1, shell.tabs().tabs().size());
            assertEquals("Java/Java集合.md", shell.tabs().active().path());
            shell.applyCss(); shell.layout();
            click(fileCell("Java/Java集合.md"), 2);
            assertTrue(shell.tabs().active().pinned());
            open("我的笔记/学习计划.md");
            assertEquals(2, shell.tabs().tabs().size());
            assertEquals("我的笔记/学习计划.md", shell.tabs().active().path());
            open("Java/Java集合.md");
            assertEquals(2, shell.tabs().tabs().size());
            assertTrue(shell.tabs().active().pinned());
            assertNotNull(shell.lookup("#workspace-tab-bar"));
            assertEquals(1, shell.lookupAll(".workspace-tab-selected").size());
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

    @Test void closingActiveTabSelectsNeighborAndRestoresItsOutline() throws Exception {
        fx(() -> {
            open("我的笔记/学习计划.md");
            shell.tabs().openPinned(fixture.alpha.id(), "Java/Java集合.md");
            assertNotNull(shell.lookup("#markdown-outline"));
            assertTrue(shell.tabs().close(shell.tabs().active()));
            assertEquals("我的笔记/学习计划.md", shell.tabs().active().path());
            assertNotNull(shell.lookup("#markdown-outline"));
            assertTrue(shell.tabs().close(shell.tabs().active()));
            assertNull(shell.tabs().active());
            assertNull(shell.lookup("#markdown-outline"));
        });
    }

    @Test void navigationResolvesRegistryPathAndPreservesSourceTab() throws Exception {
        fx(() -> {
            shell.tabs().openPinned(fixture.alpha.id(), "我的笔记/学习计划.md");
            assertEquals(WorkspaceNavigationService.Result.OPENED,
                    shell.navigate(QuizForgeNavigationLink.asset("doc_java")));
            assertEquals("Java/Java集合.md", shell.tabs().active().path());
            assertTrue(shell.tabs().active().pinned());
            assertEquals(2, shell.tabs().tabs().size());
            assertEquals(WorkspaceNavigationService.Result.OPENED,
                    shell.navigate(QuizForgeNavigationLink.heading("doc_java", "ArrayList", 1)));
            assertEquals(2, shell.tabs().tabs().size());
            assertEquals(WorkspaceNavigationService.Result.MISSING_ASSET,
                    shell.navigate(QuizForgeNavigationLink.asset("doc_missing")));
            assertEquals(2, shell.tabs().tabs().size());
            assertEquals(WorkspaceNavigationService.Result.MISSING_TARGET,
                    shell.navigate(QuizForgeNavigationLink.heading("doc_java", "Missing", 1)));
            assertEquals(WorkspaceNavigationService.Result.MISSING_TARGET,
                    shell.navigate(QuizForgeNavigationLink.heading("doc_java", "ArrayList", 2)));
            assertEquals(WorkspaceNavigationService.Result.MISSING_TARGET,
                    shell.navigate(QuizForgeNavigationLink.anchor("doc_java", "Missing", 1)));
            fixture.write("Java/Java集合.md", ShellFixture.document("A changed revision remains navigable."));
            shell.refresh();
            assertEquals(WorkspaceNavigationService.Result.OPENED,
                    shell.navigate(QuizForgeNavigationLink.heading("doc_java", "ArrayList", 1)));
        });
    }

    @Test void practiceSourceClickNavigatesSecondAnchorReusesTabAndNeverWritesBank() throws Exception {
        var sample = navigationBank(false);
        fx(() -> {
            shell.refresh();
            open(sample.bankPath());
            assertNull(shell.lookup("#qbank-source-0"));
            ((RadioButton) shell.lookup("#option-0")).fire();
            button("submit-answer").fire();
            Button source = button("qbank-source-0");
            assertEquals("SourceNav · 定义", source.getText());
            assertEquals(QuestionBankReferenceResolver.Status.EXACT_MATCH,
                    source.getProperties().get("quizforge.sourceStatus"));
            assertFalse(source.isDisabled());
            var bankTab = shell.tabs().active();
            source.fire();
            shell.applyCss(); shell.layout();
            assertEquals(sample.documentPath(), shell.tabs().active().path());
            assertTrue(shell.tabs().active().pinned());
            assertEquals(2, shell.tabs().tabs().size());
            var documentTab = shell.tabs().active();
            shell.tabs().activate(bankTab);
            assertNotNull(shell.lookup("#answer-feedback"));
            assertTrue(button("question-number-1").getStyleClass().containsAll(List.of("correct", "current")));
            button("qbank-source-0").fire();
            assertSame(documentTab, shell.tabs().active());
            assertEquals(2, shell.tabs().tabs().size());
            shell.applyCss(); shell.layout();
            var reader = (ScrollPane) shell.lookup("#markdown-preview-scroll");
            assertTrue(reader.getVvalue() > 0.5, "Second occurrence must scroll to its own body: " + reader.getVvalue());
            assertEquals(sample.json(), Files.readString(fixture.alphaRoot.resolve(sample.bankPath())));
        });
    }

    @Test void questionOutlineGroupsTypesWithGlobalNumbersInDocumentOrder() throws Exception {
        String path = outlineBank("SINGLE_CHOICE", "MULTIPLE_CHOICE", "SINGLE_CHOICE",
                "MULTIPLE_CHOICE", "SINGLE_CHOICE", "MULTIPLE_CHOICE");
        fx(() -> {
            shell.refresh(); open(path);
            assertNotNull(shell.lookup("#question-outline"));
            assertEquals(List.of("1", "3", "5"), outlineNumbers("single_choice"));
            assertEquals(List.of("2", "4", "6"), outlineNumbers("multiple_choice"));
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
            assertTrue(button("question-number-1").getStyleClass().contains("unsubmitted"));
            button("question-number-5").fire();
            assertEquals("Question 5 / 5", ((Label) shell.lookup("#question-position")).getText());
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
            assertTrue(button("question-number-2").getStyleClass().containsAll(List.of("unsubmitted", "current")));
        });
    }

    @Test void questionOutlineImmediatelyShowsResultsAndKeepsFeedbackOnRevisit() throws Exception {
        fx(() -> {
            open("题库/Java集合.qbank");
            String before = Files.readString(fixture.alphaRoot.resolve("题库/Java集合.qbank"));
            ((RadioButton) shell.lookup("#option-1")).fire();
            button("submit-answer").fire();
            assertTrue(button("question-number-1").getStyleClass().containsAll(List.of("incorrect", "current")));
            assertFalse(button("question-number-1").getStyleClass().contains("unsubmitted"));
            button("question-number-2").fire();
            ((CheckBox) shell.lookup("#option-0")).fire();
            ((CheckBox) shell.lookup("#option-1")).fire();
            button("submit-answer").fire();
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
            assertEquals(before, Files.readString(fixture.alphaRoot.resolve("题库/Java集合.qbank")));
        });
    }

    @Test void questionOutlineStaysWithItsPracticeAcrossFileAndTabSwitches() throws Exception {
        fx(() -> {
            open("题库/Java集合.qbank");
            var bankTab = shell.tabs().active();
            var outline = shell.lookup("#question-outline");
            button("question-number-2").fire();
            ((CheckBox) shell.lookup("#option-0")).fire();
            shell.tabs().openPinned(fixture.alpha.id(), "Java/Java集合.md");
            assertNull(shell.lookup("#question-outline"));
            shell.tabs().activate(bankTab);
            assertSame(outline, shell.lookup("#question-outline"));
            assertEquals("Question 2 / 2", ((Label) shell.lookup("#question-position")).getText());
            assertTrue(button("question-number-2").getStyleClass().contains("current"));
            assertTrue(((CheckBox) shell.lookup("#option-0")).isSelected());
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
            assertEquals("Question 120 / 120", ((Label) shell.lookup("#question-position")).getText());
        });
    }

    private List<String> outlineNumbers(String type) {
        var section = (javafx.scene.layout.VBox) shell.lookup("#question-outline-" + type);
        var numbers = (javafx.scene.layout.FlowPane) section.getChildren().get(1);
        return numbers.getChildren().stream().map(node -> ((Button) node).getText()).toList();
    }

    private String outlineBank(String... types) throws Exception {
        var template = new QuestionBankV1Codec().parse(Files.readString(fixture.alphaRoot.resolve("题库/Java集合.qbank")));
        List<QuestionBankFile.Entry> questions = new ArrayList<>();
        for (int i = 0; i < types.length; i++) {
            String type = types[i];
            var original = template.questions().stream().filter(question -> question.type().equals(type)).findFirst().orElseThrow();
            List<QuestionBankFile.Option> options = new ArrayList<>();
            List<String> correct = new ArrayList<>();
            for (int j = 0; j < original.data().options().size(); j++) {
                var old = original.data().options().get(j);
                String id = "opt_outline_" + i + "_" + j;
                options.add(new QuestionBankFile.Option(id, old.content()));
                if (original.data().correctOptionIds().contains(old.id())) correct.add(id);
            }
            questions.add(new QuestionBankFile.Entry("q_outline_" + i, types[i], original.stem(), original.analysis(),
                    original.sourceRefs(), new QuestionBankFile.Data(options, correct)));
        }
        String path = "题库/Outline.qbank";
        fixture.write(path, new QuestionBankV1Codec().write(new QuestionBankFile(template.format(), template.schemaVersion(),
                "qb_outline", "Outline practice", template.sourceDocuments(), questions)));
        return path;
    }

    @Test void changedRevisionAfterRenameShowsWarningAndNavigatesWithoutUpdatingRecordedRevision() throws Exception {
        var sample = navigationBank(false);
        String moved = "Renamed/Collections.md";
        Files.createDirectories(fixture.alphaRoot.resolve("Renamed"));
        Files.move(fixture.alphaRoot.resolve(sample.documentPath()), fixture.alphaRoot.resolve(moved));
        fixture.write(moved, sample.markdown().replace("Second definition.", "Changed current definition."));
        fx(() -> {
            shell.refresh();
            open(sample.bankPath());
            button("file-mode-toggle").fire();
            assertEquals("Collections · 定义", button("qbank-source-0").getText());
            assertTrue(text(shell.lookup("#question-bank-editor")).contains("来源已修改"));
            assertFalse(button("qbank-source-0").isDisabled());
            button("qbank-source-0").fire();
            assertEquals(moved, shell.tabs().active().path());
            assertTrue(text(shell.filePane().getCenter()).contains("Changed current definition."));
            assertEquals(sample.json(), Files.readString(fixture.alphaRoot.resolve(sample.bankPath())));
            var reread = new QuestionBankV1Codec().parse(sample.json());
            assertEquals(sample.bank().questions().getFirst().sourceRefs().getFirst().documentContentId(),
                    reread.questions().getFirst().sourceRefs().getFirst().documentContentId());
            assertEquals("1.2", reread.schemaVersion());
            assertFalse(sample.json().contains("displayName"));
        });
    }

    @Test void sourceRefreshPreservesDirtyEditorAndIndependentlyDisablesBrokenReferences() throws Exception {
        var sample = navigationBank(true);
        fx(() -> {
            shell.refresh(); open(sample.bankPath());
            button("file-mode-toggle").fire();
            assertFalse(button("qbank-source-0").isDisabled());
            assertFalse(button("qbank-source-1").isDisabled());
            assertTrue(button("qbank-source-2").isDisabled());
            assertTrue(button("qbank-source-3").isDisabled());
            assertTrue(text(shell.lookup("#question-bank-editor")).contains("来源位置缺失"));
            assertTrue(text(shell.lookup("#question-bank-editor")).contains("来源锚点无有效内容"));
            assertEquals(button("qbank-source-0").getText(), button("qbank-source-1").getText());
            TextField title = (TextField) shell.lookup("#qbank-title");
            TextArea stem = (TextArea) shell.lookup("#qbank-question-stem");
            title.setText("Unsaved bank title"); stem.appendText(" Unsaved stem");
            var bankTab = shell.tabs().active();
            button("qbank-source-1").fire();
            assertTrue(bankTab.pinned()); assertTrue(bankTab.pane().hasUnsavedChanges());
            String changed = sample.markdown().replace("First definition.", "Fresh current content.")
                    .replace("<!-- qf:anchor=定义 -->\nSecond definition.",
                            "<!-- qf:anchor=新名字 -->\nSecond definition.");
            fixture.write(sample.documentPath(), changed);
            shell.tabs().activate(bankTab);
            assertSame(title, shell.lookup("#qbank-title"));
            assertSame(stem, shell.lookup("#qbank-question-stem"));
            assertEquals("Unsaved bank title", title.getText());
            assertTrue(stem.getText().endsWith("Unsaved stem"));
            assertEquals(QuestionBankReferenceResolver.Status.DIFFERENT_REVISION,
                    button("qbank-source-0").getProperties().get("quizforge.sourceStatus"));
            assertFalse(button("qbank-source-0").isDisabled());
            assertEquals(QuestionBankReferenceResolver.Status.MISSING_ANCHOR,
                    button("qbank-source-1").getProperties().get("quizforge.sourceStatus"));
            assertTrue(button("qbank-source-1").isDisabled());
            button("qbank-source-0").fire();
            assertEquals(2, shell.tabs().tabs().size());
            assertTrue(text(shell.filePane().getCenter()).contains("Fresh current content."));
            assertEquals(sample.json(), Files.readString(fixture.alphaRoot.resolve(sample.bankPath())));
        });
    }

    @Test void sourceClickRechecksMissingDocumentWithoutLosingTheBankEditor() throws Exception {
        var sample = navigationBank(false);
        fx(() -> {
            shell.refresh(); open(sample.bankPath()); button("file-mode-toggle").fire();
            Button stale = button("qbank-source-0");
            assertFalse(stale.isDisabled());
            Files.delete(fixture.alphaRoot.resolve(sample.documentPath()));
            stale.fire();
            assertEquals(sample.bankPath(), shell.tabs().active().path());
            assertTrue(text(shell.tabs().getBottom()).contains("来源文档缺失"));
            assertEquals(1, shell.tabs().tabs().size());
            assertEquals(sample.json(), Files.readString(fixture.alphaRoot.resolve(sample.bankPath())));
        });
    }

    @Test void sourceNavigationLastMomentRaceUsesExistingNavigationFeedback() throws Exception {
        var sample = navigationBank(false);
        fx(() -> {
            shell.refresh(); open(sample.bankPath());
            var adapter = new QuestionSourceNavigationAdapter(
                    fixture.context.getBean(QuestionBankReferenceResolver.class),
                    fixture.context.getBean(QuestionSourceLinkService.class), link -> {
                        try { Files.delete(fixture.alphaRoot.resolve(sample.documentPath())); }
                        catch (Exception error) { throw new RuntimeException(error); }
                        fixture.context.getBean(WorkspaceAssetScanner.class).scan(fixture.alpha.id());
                        assertEquals(WorkspaceNavigationService.Result.MISSING_ASSET, shell.navigate(link));
                    }, message -> fail("Source should be valid immediately before navigation"));
            adapter.open(fixture.alpha.id(), sample.bank().questions().getFirst().sourceRefs().getFirst());
            assertTrue(text(shell.tabs().getBottom()).contains("找不到这个文档资产"));
            assertEquals(sample.bankPath(), shell.tabs().active().path());
            assertEquals(sample.json(), Files.readString(fixture.alphaRoot.resolve(sample.bankPath())));
        });
    }

    @Test void legacySourceUiRetainsReadOnlyResolutionAndExplicitReplacement() throws Exception {
        fx(() -> {
            open("题库/Java集合.qbank"); button("file-mode-toggle").fire();
            assertEquals(QuestionBankReferenceResolver.Status.EXACT_MATCH,
                    button("qbank-source-0").getProperties().get("quizforge.sourceStatus"));
            assertTrue(button("qbank-source-0").isDisabled());
            assertTrue(text(shell.lookup("#question-bank-editor")).contains("旧版节点引用"));
            assertFalse(button("qbank-change-source-0").isDisabled());
            assertFalse(shell.filePane().hasUnsavedChanges());
        });
    }

    private record NavigationBank(String documentPath, String bankPath, String markdown,
            QuestionBankFile bank, String json) { }

    private NavigationBank navigationBank(boolean multiple) throws Exception {
        String documentPath = "Java/SourceNav.md", bankPath = "题库/SourceNav.qbank";
        StringBuilder text = new StringBuilder("# Source navigation\n\n<!-- qf:anchor=定义 -->\nFirst definition.\n\n");
        for (int i = 0; i < 65; i++) text.append("Paragraph ").append(i).append(" for scrolling.\n\n");
        text.append("<!-- qf:anchor=定义 -->\nSecond definition.\n\n<!-- qf:anchor=孤立 -->\n");
        var prepared = new RegisteredMarkdownCodec().prepareRegistration(text.toString(), documentPath);
        var document = prepared.document();
        var first = QuestionBankFile.SourceRef.anchor(document.documentAssetId(), document.contentId(),
                "定义", 1, "Historical document title", "Historical section title");
        var second = QuestionBankFile.SourceRef.anchor(document.documentAssetId(), document.contentId(),
                "定义", 2, "Historical document title", "Historical section title");
        var missing = QuestionBankFile.SourceRef.anchor(document.documentAssetId(), document.contentId(),
                "不存在", 1, "Historical document title", "Historical section title");
        var orphan = QuestionBankFile.SourceRef.anchor(document.documentAssetId(), document.contentId(),
                "孤立", 1, "Historical document title", "Historical section title");
        var question = new QuestionBankFile.Entry("q_navigation", "SINGLE_CHOICE", "Test question", "Analysis",
                multiple ? List.of(first, second, missing, orphan) : List.of(second),
                new QuestionBankFile.Data(List.of(new QuestionBankFile.Option("opt_nav_a", "Correct"),
                        new QuestionBankFile.Option("opt_nav_b", "Incorrect")), List.of("opt_nav_a")));
        var bank = new QuestionBankFile("quizforge-question-bank", "1.2", "qb_navigation", "Navigation bank",
                List.of(new QuestionBankFile.SourceDocument(document.documentAssetId(), document.contentId(),
                        "Historical document title")), List.of(question));
        String json = new QuestionBankV1Codec().write(bank);
        fixture.write(documentPath, prepared.source()); fixture.write(bankPath, json);
        return new NavigationBank(documentPath, bankPath, prepared.source(), bank, json);
    }

    @Test void previewLinksNavigateCurrentFileByUriIncludingDuplicateTargets() throws Exception {
        String path = "Java/CurrentLinks.md";
        String assetId = "doc_current_links";
        var uri = new QuizForgeNavigationLinkCodec();
        String heading = uri.encode(QuizForgeNavigationLink.heading(assetId, "Same", 2));
        String anchor = uri.encode(QuizForgeNavigationLink.anchor(assetId, "定义", 2));
        String markdown = "# Current\n\n[随便写的别名](" + heading + ")\n\n"
                + "[第二个定义](" + anchor + ")\n\n## Same\nFirst.\n\n## Same\nSecond.\n\n"
                + "<!-- qf:anchor=定义 -->\nFirst definition.\n\n"
                + "<!-- qf:anchor=定义 -->\nSecond definition.\n";
        var prepared = new RegisteredMarkdownCodec().prepareRegistration(markdown, path);
        fixture.write(path, prepared.source().replace(prepared.document().documentAssetId(), assetId));
        fx(() -> {
            shell.refresh();
            shell.tabs().openPinned(fixture.alpha.id(), path);
            shell.applyCss(); shell.layout();
            Node headingText = previewLink(heading);
            assertEquals("随便写的别名", text(headingText));
            assertEquals(heading, headingText.getProperties().get("quizforge.linkHref"));
            click(headingText, 1);
            assertEquals(path, shell.tabs().active().path());
            assertEquals(1, shell.tabs().tabs().size());
            click(previewLink(anchor), 1);
            assertEquals(path, shell.tabs().active().path());
            assertEquals(1, shell.tabs().tabs().size());
            assertNull(shell.tabs().getBottom());
        });
    }

    @Test void previewLinksOpenOrFocusCrossFileTargetsAndKeepSourceTab() throws Exception {
        String sourcePath = "Java/LinkSource.md";
        String targetPath = "Java/LinkTarget.md";
        var codec = new QuizForgeNavigationLinkCodec();
        var target = new RegisteredMarkdownCodec().prepareRegistration("# Target\n\n"
                + "## Same\nFirst.\n\n## Same\nSecond.\n\n"
                + "<!-- qf:anchor=定义 -->\nFirst definition.\n\n"
                + "<!-- qf:anchor=定义 -->\nSecond definition.\n", targetPath);
        String id = target.document().documentAssetId();
        String asset = codec.encode(QuizForgeNavigationLink.asset(id));
        String heading = codec.encode(QuizForgeNavigationLink.heading(id, "Same", 2));
        String anchor = codec.encode(QuizForgeNavigationLink.anchor(id, "定义", 2));
        fixture.write(targetPath, target.source());
        fixture.write(sourcePath, "# Source\n\n[打开 B](" + asset + ")\n\n"
                + "[跳到 Heading](" + heading + ")\n\n[跳到 Anchor](" + anchor + ")\n");
        fx(() -> {
            shell.refresh();
            shell.tabs().openPinned(fixture.alpha.id(), sourcePath);
            shell.applyCss(); shell.layout();
            click(previewLink(heading), 1);
            assertEquals(targetPath, shell.tabs().active().path());
            assertEquals(2, shell.tabs().tabs().size());
            assertEquals(sourcePath, shell.tabs().tabs().getFirst().path());
            @SuppressWarnings("unchecked")
            List<MarkdownOutline.Entry> targetEntries = (List<MarkdownOutline.Entry>)
                    ((javafx.scene.layout.HBox) shell.filePane().getCenter())
                            .getProperties().get("quizforge.outlineEntries");
            assertTrue(targetEntries.stream().anyMatch(entry -> "Same".equals(entry.label())
                    && entry.occurrence() == 2));
            assertEquals(targetPath, shell.sidebar().tree().getSelectionModel()
                    .getSelectedItem().getValue().relativePath());
            shell.tabs().activate(shell.tabs().findOpenTab(sourcePath));
            click(previewLink(anchor), 1);
            assertEquals(targetPath, shell.tabs().active().path());
            assertEquals(2, shell.tabs().tabs().size());
            shell.tabs().activate(shell.tabs().findOpenTab(sourcePath));
            click(previewLink(asset), 1);
            assertEquals(targetPath, shell.tabs().active().path());
            assertEquals(2, shell.tabs().tabs().size());
        });
    }

    @Test void previewLinksFailClosedAndLeaveExternalLinksUntouched() throws Exception {
        String path = "Java/FailureLinks.md";
        String targetPath = "Java/EditTarget.md";
        var codec = new QuizForgeNavigationLinkCodec();
        var target = new RegisteredMarkdownCodec().prepareRegistration("# Target\n\n## Present\nBody.\n",
                targetPath);
        String id = target.document().documentAssetId();
        String missingHeading = codec.encode(QuizForgeNavigationLink.heading(id, "Missing", 1));
        String missingAnchor = codec.encode(QuizForgeNavigationLink.anchor(id, "Missing", 1));
        String existing = codec.encode(QuizForgeNavigationLink.heading(id, "Present", 1));
        fixture.write(targetPath, target.source());
        fixture.write(path, "# Source\n\n[外部](https://example.com)\n\n"
                + "[损坏](quizforge://invalid)\n\n"
                + "[资产缺失](quizforge://asset/doc_missing)\n\n"
                + "[标题缺失](" + missingHeading + ")\n\n"
                + "[锚点缺失](" + missingAnchor + ")\n\n"
                + "[编辑中](" + existing + ")\n");
        fx(() -> {
            shell.refresh();
            shell.tabs().openPinned(fixture.alpha.id(), path);
            shell.applyCss(); shell.layout();
            Node external = shell.lookupAll(".prose-link").stream()
                    .filter(node -> "外部".equals(text(node))).findFirst().orElseThrow();
            assertNull(external.getOnMouseClicked());
            click(external, 1);
            assertEquals(path, shell.tabs().active().path());
            assertNull(shell.tabs().getBottom());
            click(previewLink("quizforge://invalid"), 1);
            assertEquals(path, shell.tabs().active().path());
            assertTrue(text(shell.tabs().getBottom()).contains("链接格式无效"));
            click(previewLink("quizforge://asset/doc_missing"), 1);
            assertEquals(path, shell.tabs().active().path());
            assertTrue(text(shell.tabs().getBottom()).contains("找不到这个文档资产"));
            click(previewLink(missingHeading), 1);
            assertTrue(text(shell.tabs().getBottom()).contains("找不到指定位置"));
            shell.tabs().activate(shell.tabs().findOpenTab(path));
            click(previewLink(missingAnchor), 1);
            assertTrue(text(shell.tabs().getBottom()).contains("找不到指定位置"));
            shell.tabs().openPinned(fixture.alpha.id(), targetPath);
            button("file-mode-toggle").fire();
            TextArea editor = (TextArea) shell.lookup("#markdown-source-text");
            editor.appendText("\nUnsaved content");
            shell.tabs().activate(shell.tabs().findOpenTab(path));
            click(previewLink(existing), 1);
            assertEquals(targetPath, shell.tabs().active().path());
            assertTrue(text(shell.tabs().getBottom()).contains("正在编辑"));
            assertSame(editor, shell.lookup("#markdown-source-text"));
            assertTrue(editor.getText().contains("Unsaved content"));
        });
    }

    @Test void duplicateHeadingAndAnchorNavigationUsesExactOccurrenceAndBoundBlock() throws Exception {
        String source = "# A\n## Same\nFirst.\n## Same\nSecond.\n"
                + "<!-- qf:anchor=定义 -->\nFirst definition.\n"
                + "<!-- qf:anchor=定义 -->\nSecond definition.\n"
                + "<!-- qf:anchor=孤立 -->\n";
        var prepared = new RegisteredMarkdownCodec().prepareRegistration(source, "Java/Navigation.md");
        fixture.write("Java/Navigation.md", prepared.source());
        fx(() -> {
            shell.refresh();
            String assetId = prepared.document().documentAssetId();
            assertEquals(WorkspaceNavigationService.Result.OPENED,
                    shell.navigate(QuizForgeNavigationLink.heading(assetId, "Same", 2)));
            assertEquals(WorkspaceNavigationService.Result.OPENED,
                    shell.navigate(QuizForgeNavigationLink.anchor(assetId, "定义", 2)));
            var layout = (javafx.scene.layout.HBox) shell.filePane().getCenter();
            @SuppressWarnings("unchecked")
            List<MarkdownOutline.Entry> entries = (List<MarkdownOutline.Entry>)
                    layout.getProperties().get("quizforge.outlineEntries");
            var second = entries.stream().filter(entry -> entry.kind() == MarkdownOutline.Kind.ANCHOR
                    && entry.label().equals("定义") && entry.occurrence() == 2).findFirst().orElseThrow();
            assertEquals(9, second.target().startLine());
            assertEquals(WorkspaceNavigationService.Result.MISSING_TARGET,
                    shell.navigate(QuizForgeNavigationLink.anchor(assetId, "定义", 3)));
            assertEquals(WorkspaceNavigationService.Result.MISSING_TARGET,
                    shell.navigate(QuizForgeNavigationLink.anchor(assetId, "孤立", 1)));
            assertEquals(1, shell.tabs().tabs().size());
        });
    }

    @Test void crossFileNavigationWaitsForRenderedPreviewAndScrollsToAnchorBody() throws Exception {
        StringBuilder source = new StringBuilder("# Long note\n\n");
        for (int i = 0; i < 90; i++) source.append("Paragraph ").append(i).append(".\n\n");
        source.append("<!-- qf:anchor=末尾 -->\nTarget paragraph.\n");
        var prepared = new RegisteredMarkdownCodec().prepareRegistration(source.toString(),
                "Java/LongNavigation.md");
        fixture.write("Java/LongNavigation.md", prepared.source());
        fx(() -> {
            shell.refresh();
            shell.tabs().openPinned(fixture.alpha.id(), "我的笔记/学习计划.md");
            assertEquals(WorkspaceNavigationService.Result.OPENED,
                    shell.navigate(QuizForgeNavigationLink.anchor(
                            prepared.document().documentAssetId(), "末尾", 1)));
            shell.applyCss(); shell.layout(); pulse(120);
            ScrollPane reader = (ScrollPane) shell.lookup("#markdown-preview-scroll");
            assertNotNull(reader);
            assertTrue(reader.getVvalue() > 0.5, "Anchor target must scroll after the preview is laid out: v="
                    + reader.getVvalue() + " content=" + reader.getContent().getLayoutBounds().getHeight()
                    + " viewport=" + reader.getViewportBounds().getHeight());
            assertEquals(2, shell.tabs().tabs().size());
        });
    }

    @Test void qbankTabHasNoMarkdownOutlineAndNavigationNeverDiscardsEdit() throws Exception {
        fx(() -> {
            open("Java/Java集合.md");
            button("file-mode-toggle").fire();
            TextArea editor = (TextArea) shell.lookup("#markdown-source-text");
            editor.appendText("\nNot saved");
            assertEquals(WorkspaceNavigationService.Result.EDIT_MODE,
                    shell.navigate(QuizForgeNavigationLink.heading("doc_java", "ArrayList", 1)));
            assertTrue(editor.getText().contains("Not saved"));
            shell.tabs().openPinned(fixture.alpha.id(), "题库/Java集合.qbank");
            shell.applyCss(); shell.layout();
            assertNotNull(shell.lookup("#question-practice"));
            assertNull(shell.lookup("#markdown-outline"));
            shell.tabs().activate(shell.tabs().findOpenTab("Java/Java集合.md"));
            assertSame(editor, shell.lookup("#markdown-source-text"));
        });
    }

    @Test void outlineBelongsToActiveMarkdownTabAndRefreshKeepsPinnedTabs() throws Exception {
        fx(() -> {
            shell.tabs().openPinned(fixture.alpha.id(), "我的笔记/学习计划.md");
            shell.applyCss(); shell.layout();
            FilePane first = shell.filePane();
            assertTrue(text(shell.lookup("#markdown-outline")).contains("今天"));
            shell.tabs().openPinned(fixture.alpha.id(), "Java/Java集合.md");
            shell.applyCss(); shell.layout();
            assertTrue(text(shell.lookup("#markdown-outline")).contains("ArrayList"));
            assertFalse(text(shell.lookup("#markdown-outline")).contains("今天"));
            shell.tabs().activate(shell.tabs().findOpenTab("我的笔记/学习计划.md"));
            shell.applyCss(); shell.layout();
            assertSame(first, shell.filePane());
            assertEquals("我的笔记/学习计划.md", shell.sidebar().tree().getSelectionModel()
                    .getSelectedItem().getValue().relativePath());
            assertTrue(text(shell.lookup("#markdown-outline")).contains("今天"));
            shell.refresh();
            assertEquals(2, shell.tabs().tabs().size());
            assertTrue(shell.tabs().tabs().stream().allMatch(WorkspaceTab::pinned));
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
            assertEquals("Question 2 / 2", ((Label) shell.lookup("#question-position")).getText());
            assertTrue(button("question-number-2").getStyleClass().contains("current"));
            button("previous-question").fire();
            assertTrue(((RadioButton) shell.lookup("#option-1")).isSelected());
            assertNull(shell.lookup("#answer-feedback"));
            assertTrue(button("question-number-1").getStyleClass().contains("unsubmitted"));
            button("question-number-2").fire();
            assertEquals("q_two", practiceDbSession().currentQuestionId());
        });
    }

    @Test void recreatedShellRestoresSubmittedFeedbackSourcesAndOutlineFromAttempt() throws Exception {
        fx(() -> {
            open("题库/Java集合.qbank");
            ((RadioButton) shell.lookup("#option-1")).fire();
            button("submit-answer").fire();
            button("next-question").fire();
            ((CheckBox) shell.lookup("#option-0")).fire();
            var id = practiceDbSession().id();
            shell.tabs().closeAll();
            shell = fixture.shell(stage);
            Scene scene = new Scene(shell, 1100, 740); UiTheme.apply(scene); stage.setScene(scene);
            open("题库/Java集合.qbank");
            assertEquals(id, practiceDbSession().id());
            assertEquals("Question 2 / 2", ((Label) shell.lookup("#question-position")).getText());
            assertTrue(((CheckBox) shell.lookup("#option-0")).isSelected());
            assertNull(shell.lookup("#answer-feedback"));
            assertTrue(button("question-number-1").getStyleClass().contains("incorrect"));
            button("question-number-1").fire();
            assertTrue(((RadioButton) shell.lookup("#option-1")).isSelected());
            assertTrue(text(shell.lookup("#answer-feedback")).contains("回答错误"));
            assertNotNull(shell.lookup("#qbank-source-0"));
            assertTrue(button("submit-answer").isDisabled());
        });
    }

    @Test void editSaveThenPracticeRunsRevisionSyncAndPreservesOnlyNonSemanticAnswers() throws Exception {
        fx(() -> {
            open("题库/Java集合.qbank");
            ((RadioButton) shell.lookup("#option-0")).fire(); button("submit-answer").fire();
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
            ((RadioButton) shell.lookup("#option-0")).fire(); button("submit-answer").fire();
            button("file-mode-toggle").fire();
            var file = fixture.alphaRoot.resolve("题库/Java集合.qbank");
            var codec = new QuestionBankV1Codec();
            var bank = codec.parse(Files.readString(file));
            var q = bank.questions().getFirst();
            var changed = new QuestionBankFile.Entry(q.id(), q.type(), q.stem() + " Changed", q.analysis(), q.sourceRefs(), q.data());
            var edited = new QuestionBankFile(bank.format(), bank.schemaVersion(), bank.id(), bank.title(), bank.sourceDocuments(), List.of(changed, bank.questions().get(1)));
            Files.writeString(file, codec.write(edited));
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
            button("submit-answer").fire();
            assertNull(shell.lookup("#answer-feedback"));
            assertNotNull(shell.lookup("#practice-error"));
            assertTrue(((RadioButton) shell.lookup("#option-0")).isSelected());
            assertFalse(button("submit-answer").isDisabled());
            assertTrue(button("question-number-1").getStyleClass().contains("unsubmitted"));
            var questions = new io.quizforge.infrastructure.persistence.SqlitePracticeSessionQuestionRepository(db);
            var row = questions.findBySessionIdAndQuestionId(practiceDbSession().id(), "q_one").orElseThrow();
            assertEquals(io.quizforge.core.practice.PracticeSessionQuestion.State.DRAFT, row.practiceState());
            assertEquals(new io.quizforge.core.practice.PracticePayload(List.of("opt_a")), row.draftAnswer());
            var attempts = new io.quizforge.infrastructure.persistence.SqliteQuestionAttemptRepository(db);
            assertTrue(attempts.listBySessionQuestion(row.id()).isEmpty());
            try (var connection = db.openConnection(); var statement = connection.createStatement()) { statement.execute("DROP TRIGGER fail_submit"); }
            Button confirm = button("submit-answer"); confirm.fire(); confirm.fire();
            assertNotNull(shell.lookup("#answer-feedback"));
            assertEquals(1, attempts.listBySessionQuestion(row.id()).size());
        });
    }

    @Test void qbankHistoryEntryKeepsTabShowsEmptyStateAndReturnsToPractice() throws Exception {
        fx(() -> {
            open("题库/Java集合.qbank");
            var tab = shell.tabs().active();
            assertNotNull(button("qbank-history-entry"));
            button("qbank-history-entry").fire();
            shell.applyCss(); shell.layout();
            assertSame(tab, shell.tabs().active());
            assertNotNull(shell.lookup("#practice-history"));
            assertNotNull(shell.lookup("#history-empty"));
            assertNull(shell.lookup("#question-practice"));
            button("history-back").fire();
            assertSame(tab, shell.tabs().active());
            assertNotNull(shell.lookup("#question-practice"));
            assertEquals(io.quizforge.core.practice.PracticeSession.Status.ACTIVE, practiceDbSession().status());
        });
    }

    @Test void qbankHistoryCardsExcludeActiveAndDeleteOnlyAfterConfirmation() throws Exception {
        fx(() -> {
            open("题库/Java集合.qbank");
            ((RadioButton) shell.lookup("#option-1")).fire(); button("submit-answer").fire();
            String archived = practiceDbSession().id();
            new io.quizforge.infrastructure.persistence.SqlitePracticeSessionRepository(practiceDb())
                    .archive(archived, java.time.Instant.parse("2026-09-28T05:00:00Z"));
            shell.tabs().closeAll(); shell.tabs().openPreview(fixture.alpha.id(), "题库/Java集合.qbank");
            shell.applyCss(); shell.layout();
            String active = practiceDbSession().id();
            button("qbank-history-entry").fire();
            shell.applyCss(); shell.layout();
            var card = (javafx.scene.layout.VBox) shell.lookup("#history-card-" + archived);
            assertNotNull(card);
            assertTrue(text(card).contains("正确率 0%"));
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
            assertTrue(new io.quizforge.infrastructure.persistence.SqlitePracticeSessionRepository(practiceDb())
                    .findById(archived).isEmpty());
            assertEquals(active, practiceDbSession().id());
        });
    }

    @Test void qbankHistoryDeleteFailureKeepsCardAndShowsError() throws Exception {
        fx(() -> {
            open("题库/Java集合.qbank");
            String archived = practiceDbSession().id();
            new io.quizforge.infrastructure.persistence.SqlitePracticeSessionRepository(practiceDb())
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
            var sessions = new io.quizforge.infrastructure.persistence.SqlitePracticeSessionRepository(practiceDb());
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
            ((RadioButton) shell.lookup("#option-1")).fire(); button("submit-answer").fire();
            button("practice-retry").fire();
            ((RadioButton) shell.lookup("#option-0")).fire(); button("submit-answer").fire();
            button("next-question").fire();
            ((javafx.scene.control.CheckBox) shell.lookup("#option-0")).fire();
            String archived = practiceDbSession().id();
            new io.quizforge.infrastructure.persistence.SqlitePracticeSessionRepository(practiceDb())
                    .archive(archived, java.time.Instant.parse("2026-09-28T05:00:00Z"));
            shell.tabs().closeAll(); shell.tabs().openPreview(fixture.alpha.id(), "题库/Java集合.qbank");
            tab = shell.tabs().active();
            var archivedBefore = new io.quizforge.infrastructure.persistence.SqlitePracticeSessionRepository(practiceDb())
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
            assertEquals(archivedBefore, new io.quizforge.infrastructure.persistence.SqlitePracticeSessionRepository(practiceDb())
                    .findById(archived).orElseThrow());
        });
    }

    @Test void historyOutlineUsesArchivedIncorrectState() throws Exception {
        fx(() -> {
            open("题库/Java集合.qbank");
            ((RadioButton) shell.lookup("#option-1")).fire(); button("submit-answer").fire();
            String archived = practiceDbSession().id();
            new io.quizforge.infrastructure.persistence.SqlitePracticeSessionRepository(practiceDb())
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
            ((RadioButton) shell.lookup("#option-1")).fire(); button("submit-answer").fire();
            button("practice-retry").fire();
            ((RadioButton) shell.lookup("#option-0")).fire();
            String archived = practiceDbSession().id();
            new io.quizforge.infrastructure.persistence.SqlitePracticeSessionRepository(practiceDb())
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

    private io.quizforge.infrastructure.persistence.SqliteDatabase practiceDb() {
        return new io.quizforge.infrastructure.persistence.SqliteDatabase(fixture.alphaRoot.resolve(".quizforge/quizforge.db"));
    }
    private io.quizforge.core.practice.PracticeSession practiceDbSession() {
        return new io.quizforge.infrastructure.persistence.SqlitePracticeSessionRepository(practiceDb())
                .findActiveByQuestionBankAssetId("qb_java").orElseThrow();
    }

    private TreeCell<?> fileCell(String path) {
        shell.applyCss(); shell.layout();
        return shell.sidebar().tree().lookupAll(".tree-cell").stream().filter(TreeCell.class::isInstance)
                .map(TreeCell.class::cast).filter(row -> row.getItem() instanceof WorkspaceFileEntry entry
                        && entry.relativePath().equals(path)).findFirst().orElseThrow();
    }

    private TreeCell<?> folderCell(String path) {
        shell.applyCss(); shell.layout();
        return shell.sidebar().tree().lookupAll(".tree-cell").stream().filter(TreeCell.class::isInstance)
                .map(TreeCell.class::cast).filter(row -> row.getItem() instanceof WorkspaceFileEntry entry
                        && entry.relativePath().equals(path)).findFirst().orElseThrow();
    }

    private static void click(Node target, int count) {
        for (var type : List.of(javafx.scene.input.MouseEvent.MOUSE_PRESSED,
                javafx.scene.input.MouseEvent.MOUSE_RELEASED, javafx.scene.input.MouseEvent.MOUSE_CLICKED)) {
            target.fireEvent(new javafx.scene.input.MouseEvent(type, 8, 8, 8, 8,
                    javafx.scene.input.MouseButton.PRIMARY, count, false, false, false, false,
                    type == javafx.scene.input.MouseEvent.MOUSE_PRESSED, false, false, false, false, true, null));
        }
    }

    private Node previewLink(String href) {
        return shell.lookupAll(".prose-internal-link").stream()
                .filter(node -> href.equals(node.getProperties().get("quizforge.linkHref")))
                .findFirst().orElseThrow();
    }

    private static void pulse(int millis) {
        Object token = new Object();
        var timer = new javafx.animation.PauseTransition(javafx.util.Duration.millis(millis));
        timer.setOnFinished(event -> Platform.exitNestedEventLoop(token, null));
        timer.play();
        Platform.enterNestedEventLoop(token);
    }

    private void open(String path) { shell.selectPath(path); shell.applyCss(); shell.layout(); }
    private Button button(String id) { shell.applyCss(); shell.layout(); return (Button) shell.lookup("#" + id); }

    private static List<String> paths(TreeItem<WorkspaceFileEntry> root) {
        List<String> paths = new ArrayList<>();
        if (root.getValue() != null) paths.add(root.getValue().relativePath());
        root.getChildren().forEach(child -> paths.addAll(paths(child)));
        return paths;
    }

    static String text(Node node) {
        if (node == null) return "";
        if (node instanceof javafx.scene.text.Text span) return span.getText();
        if (node instanceof ScrollPane scroll) return text(scroll.getContent());
        StringBuilder out = new StringBuilder(node instanceof Labeled label ? label.getText() + "\n" : "");
        if (node instanceof Parent parent) parent.getChildrenUnmodifiable().forEach(child -> out.append(text(child)));
        return out.toString();
    }

    private static void fx(CheckedRunnable action) throws Exception {
        fx(action, 40);
    }

    private static void fx(CheckedRunnable action, int timeoutSeconds) throws Exception {
        FutureTask<Void> task = new FutureTask<>(() -> { action.run(); return null; });
        Platform.runLater(task);
        task.get(timeoutSeconds, TimeUnit.SECONDS);
    }

    private interface CheckedRunnable { void run() throws Exception; }
}
