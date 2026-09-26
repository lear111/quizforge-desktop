package io.quizforge.desktop.ui;

import static org.junit.jupiter.api.Assertions.*;

import io.quizforge.core.workspace.WorkspaceFileEntry;
import io.quizforge.core.workspace.WorkspaceFileKind;
import io.quizforge.infrastructure.filesystem.StandardKnowledgeDocumentV1;
import io.quizforge.infrastructure.filesystem.QDocV1Codec;
import io.quizforge.infrastructure.filesystem.QuestionBankV1Codec;
import io.quizforge.core.document.qdoc.*;
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
        });
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

    @Test void qdocBrowseRendersModelWithComputedNumberingInsteadOfJson() throws Exception {
        var section = new DocumentNode("section_one", DocumentNodeType.SECTION, "ArrayList",
                List.of(ContentBlock.text(ContentBlockType.PARAGRAPH, "数组支持按索引访问。")));
        var chapter = new DocumentNode("chapter_one", DocumentNodeType.CHAPTER, "Java 集合", List.of(section));
        var document = new QDocDocument("quizforge-document", "1.0", "doc_structured",
                DocumentTemplate.GENERAL_KNOWLEDGE.reference(), "集合知识", "zh-CN", List.of(chapter));
        fixture.write("Java/Structured.qdoc", new QDocV1Codec().write(document));
        fx(() -> {
            shell.refresh();
            open("Java/Structured.qdoc");
            assertNotNull(shell.lookup("#qdoc-browse-view"));
            String rendered = text(shell.filePane().getCenter());
            assertTrue(rendered.contains("1 Java 集合"));
            assertTrue(rendered.contains("1.1 ArrayList"));
            assertTrue(rendered.contains("数组支持按索引访问。"));
            assertFalse(rendered.contains("schemaVersion"));
            assertFalse(rendered.contains("doc_structured"));
        });
    }

    @Test void qdocEditUsesStructuredControlsAndSaveReturnsToFreshBrowse() throws Exception {
        var section = new DocumentNode("section_one", DocumentNodeType.SECTION, "ArrayList",
                List.of(ContentBlock.text(ContentBlockType.PARAGRAPH, "Old content")));
        var chapter = new DocumentNode("chapter_one", DocumentNodeType.CHAPTER, "Java", List.of(section));
        var document = new QDocDocument("quizforge-document", "1.0", "doc_edit_ui",
                DocumentTemplate.GENERAL_KNOWLEDGE.reference(), "Knowledge", "en-US", List.of(chapter));
        fixture.write("Java/Editable.qdoc", new QDocV1Codec().write(document));
        fx(() -> {
            shell.refresh(); open("Java/Editable.qdoc");
            String before = shell.filePane().currentFile().file().entry().contentId();
            button("file-mode-toggle").fire();
            shell.applyCss(); shell.layout();
            assertNotNull(shell.lookup("#qdoc-editor-view"));
            assertNull(shell.lookup("#editor-placeholder"));
            assertNull(shell.lookup("#qdoc-empty-ai"));
            ((TextArea) shell.lookup("#qdoc-block-text-0-0-0")).setText("New content");
            button("qdoc-save").fire();
            assertEquals(FileMode.BROWSE, shell.filePane().mode());
            assertTrue(text(shell.filePane()).contains("New content"));
            assertNotEquals(before, shell.filePane().currentFile().file().entry().contentId());
            assertEquals("doc_edit_ui", new QDocV1Codec().parse(Files.readString(
                    fixture.alphaRoot.resolve("Java/Editable.qdoc"))).id());
        });
    }

    @Test void emptyQDocOffersAiAndAddMenuHidesInvalidBlockTypes() throws Exception {
        var section = new DocumentNode("section_empty", DocumentNodeType.SECTION, "Empty", List.of());
        var chapter = new DocumentNode("chapter_empty", DocumentNodeType.CHAPTER, "Chapter", List.of(section));
        var document = new QDocDocument("quizforge-document", "1.0", "doc_empty_ui",
                DocumentTemplate.GENERAL_KNOWLEDGE.reference(), "Empty Knowledge", "en-US", List.of(chapter));
        fixture.write("Java/Empty.qdoc", new QDocV1Codec().write(document));
        fx(() -> {
            shell.refresh(); open("Java/Empty.qdoc");
            assertNotNull(shell.lookup("#empty-asset-ai"));
            button("file-mode-toggle").fire();
            shell.applyCss(); shell.layout();
            assertNotNull(shell.lookup("#qdoc-empty-state"));
            button("qdoc-empty-ai").fire();
            assertEquals(1, fixture.aiOpened.get());
            MenuButton root = (MenuButton) shell.lookup("#qdoc-add-root");
            assertEquals(List.of("CHAPTER"), root.getItems().stream().map(MenuItem::getText).toList());
            MenuButton chapterMenu = (MenuButton) shell.lookup("#qdoc-add-0");
            assertTrue(chapterMenu.getItems().stream().anyMatch(item -> item.getText().equals("SECTION")));
            assertFalse(chapterMenu.getItems().stream().anyMatch(item -> item.getText().equals("CODE_BLOCK")));
            MenuButton sectionMenu = (MenuButton) shell.lookup("#qdoc-add-0-0");
            assertTrue(sectionMenu.getItems().stream().anyMatch(item -> item.getText().equals("SUBSECTION")));
            assertFalse(sectionMenu.getItems().stream().anyMatch(item -> item.getText().equals("CHAPTER")));
            sectionMenu.getItems().stream().filter(item -> item.getText().equals("PARAGRAPH"))
                    .findFirst().orElseThrow().fire();
            shell.applyCss(); shell.layout();
            assertNotNull(shell.lookup("#qdoc-block-0-0-0"));
            assertNull(shell.lookup("#qdoc-empty-ai"));
            ((MenuButton) shell.lookup("#qdoc-more-0-0-0")).getItems().getFirst().fire();
            shell.applyCss(); shell.layout();
            assertNotNull(shell.lookup("#qdoc-empty-ai"));
        });
    }

    @Test void qdocInvalidEditShowsErrorAndLeavesFileIntact() throws Exception {
        var section = new DocumentNode("section_one", DocumentNodeType.SECTION, "Section",
                List.of(ContentBlock.text(ContentBlockType.PARAGRAPH, "Valid")));
        var chapter = new DocumentNode("chapter_one", DocumentNodeType.CHAPTER, "Chapter", List.of(section));
        var document = new QDocDocument("quizforge-document", "1.0", "doc_validation_ui",
                DocumentTemplate.GENERAL_KNOWLEDGE.reference(), "Knowledge", "en-US", List.of(chapter));
        String source = new QDocV1Codec().write(document);
        fixture.write("Java/InvalidEdit.qdoc", source);
        fx(() -> {
            shell.refresh(); open("Java/InvalidEdit.qdoc");
            button("file-mode-toggle").fire();
            shell.applyCss(); shell.layout();
            ((TextArea) shell.lookup("#qdoc-block-text-0-0-0")).setText("");
            button("qdoc-save").fire();
            assertTrue(text(shell.filePane()).contains("Document validation failed"));
            assertEquals(source, Files.readString(fixture.alphaRoot.resolve("Java/InvalidEdit.qdoc")));
            assertEquals(FileMode.EDIT, shell.filePane().mode());
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
            assertNotNull(shell.lookup("#editor-placeholder"));
            toggle.fire();
            assertEquals(FileMode.BROWSE, shell.filePane().mode());
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

    @Test void fileTreeKeepsCustomFilesEmptyFoldersAndHidesQuizforge() throws Exception {
        fx(() -> {
            var paths = paths(shell.sidebar().tree().getRoot());
            assertTrue(paths.contains("空目录"));
            assertTrue(paths.contains("原始材料/Java 官方文档.pdf"));
            assertTrue(paths.contains("Java/Java集合.md"));
            assertTrue(paths.stream().noneMatch(path -> path.contains(".quizforge")));
            var entries = shell.sidebar().tree().getRoot().getChildren().stream().map(TreeItem::getValue).toList();
            assertTrue(entries.getFirst().kind() == WorkspaceFileKind.DIRECTORY);
        });
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
        if (node instanceof ScrollPane scroll) return text(scroll.getContent());
        StringBuilder out = new StringBuilder(node instanceof Labeled label ? label.getText() + "\n" : "");
        if (node instanceof Parent parent) parent.getChildrenUnmodifiable().forEach(child -> out.append(text(child)));
        return out.toString();
    }

    private static void fx(CheckedRunnable action) throws Exception {
        FutureTask<Void> task = new FutureTask<>(() -> { action.run(); return null; });
        Platform.runLater(task);
        task.get(40, TimeUnit.SECONDS);
    }

    private interface CheckedRunnable { void run() throws Exception; }
}
