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
            button("next-question").fire();
            Button toggle = button("file-mode-toggle");
            toggle.fire();
            shell.applyCss(); shell.layout();
            assertNotNull(shell.lookup("#question-bank-editor"));
            assertNull(shell.lookup("#question-practice"));
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
            assertFalse(text(shell.filePane()).contains("来源："));
            assertFalse(text(shell.filePane()).contains("以下哪些描述"));
            assertTrue(button("submit-answer").isDisabled());
            ((RadioButton) shell.lookup("#option-0")).fire();
            button("submit-answer").fire();
            assertTrue(text(shell.lookup("#answer-feedback")).contains("回答正确"));
            assertTrue(text(shell.lookup("#answer-feedback")).contains("来源：Java 集合"));
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

    @Test void wrongAnswerDoesNotPassAndPracticeIsNotPersisted() throws Exception {
        fx(() -> {
            String before = Files.readString(fixture.alphaRoot.resolve("题库/Java集合.qbank"));
            open("题库/Java集合.qbank");
            ((RadioButton) shell.lookup("#option-1")).fire();
            button("submit-answer").fire();
            assertTrue(text(shell.lookup("#answer-feedback")).contains("回答错误"));
            assertEquals(before, Files.readString(fixture.alphaRoot.resolve("题库/Java集合.qbank")));
        });
    }

    @Test void practiceResultAndRestartStayTransient() throws Exception {
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
            assertTrue(text(shell.lookup("#practice-result")).contains("1 / 2"));
            assertTrue(text(shell.lookup("#practice-result")).contains("50%"));
            button("practice-restart").fire();
            assertNotNull(shell.lookup("#submit-answer"));
            assertNull(shell.lookup("#answer-feedback"));
            assertEquals(before, Files.readString(fixture.alphaRoot.resolve("题库/Java集合.qbank")));
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
            assertEquals(new QuizForgeNavigationLinkCodec().encode(
                    QuizForgeNavigationLink.asset(entry.assetId())), fixture.copiedText.get());
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
            assertEquals(new QuizForgeNavigationLinkCodec().encode(
                    QuizForgeNavigationLink.asset(registered.assetId())), fixture.copiedText.get());
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
            assertEquals(new QuizForgeNavigationLinkCodec().encode(
                    QuizForgeNavigationLink.heading(assetId, "示例", 2)), fixture.copiedText.get());
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
            assertEquals(new QuizForgeNavigationLinkCodec().encode(
                    QuizForgeNavigationLink.anchor(assetId, "定义", 2)), fixture.copiedText.get());
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
            assertEquals(new QuizForgeNavigationLinkCodec().encode(
                    QuizForgeNavigationLink.anchor(entry.assetId(), "手写来源", 1)),
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
            ScrollPane scroll = (ScrollPane) shell.filePane().getCenter();
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
