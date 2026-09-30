package io.quizforge.desktop.ui;

import io.quizforge.core.question.*;

import static org.junit.jupiter.api.Assertions.*;

import io.quizforge.core.workspace.WorkspaceFileEntry;
import io.quizforge.core.workspace.WorkspaceFileKind;
import io.quizforge.infrastructure.filesystem.StandardKnowledgeDocumentV1;
import io.quizforge.infrastructure.filesystem.QuestionBankV2Codec;
import io.quizforge.infrastructure.filesystem.RegisteredMarkdownCodec;
import io.quizforge.core.document.registered.QuizForgeReference;
import io.quizforge.core.document.registered.QuizForgeReferenceCodec;
import io.quizforge.core.document.navigation.QuizForgeNavigationLink;
import io.quizforge.core.document.navigation.QuizForgeNavigationLinkCodec;
import io.quizforge.core.port.WorkspaceAssetScanner;
import java.nio.file.Files;
import io.quizforge.infrastructure.testing.QBankTestPackageBuilder;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class MainWorkspaceViewTest {
    @TempDir Path temp;
    private ShellFixture fixture;
    private MainWorkspaceView shell;
    private Stage stage;

    @BeforeAll static void startFx() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try { Platform.startup(() -> { Platform.setImplicitExit(false); started.countDown(); }); }
        catch (IllegalStateException alreadyStarted) { Platform.runLater(started::countDown); }
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

    @Test void selectedWorkspaceLocationReopensThroughDesktopFileTree() throws Exception {
        Path parent = Files.createDirectory(temp.resolve("chosen-location"));
        var external = fixture.workspaces.createWorkspace("外部学习库", parent);
        Path root = parent.resolve("外部学习库");
        Files.writeString(root.resolve("笔记.md"), "# 外部工作区笔记\n");
        fx(() -> {
            shell.switchWorkspace(external);
            assertEquals("外部学习库", shell.sidebar().switcher().getText());
            assertTrue(paths(shell.sidebar().tree().getRoot()).contains("笔记.md"));
            open("笔记.md");
            assertEquals("笔记.md", shell.filePane().currentFile().file().entry().relativePath());
        });
    }

    @Test void sidebarContainsSwitcherOneRealFileTreeAndFixedSettings() throws Exception {
        fx(() -> {
            assertEquals(4, shell.sidebar().getChildren().size());
            assertEquals(1, shell.lookupAll(".tree-view").size());
            assertTrue(shell.lookupAll(".tab-pane").isEmpty());
            assertNull(shell.lookup("#home-button"));
            assertEquals("window-chrome", shell.getTop().getId());
            assertNull(shell.getBottom());
            assertEquals("Java 学习库", shell.sidebar().switcher().getText());
            for (String obsolete : List.of("Home", "Workspace Files", "Generate Document", "Generate QuestionBank", "QuestionBank Practice")) {
                assertFalse(text(shell.sidebar()).contains(obsolete));
            }
        });
    }

    @Test void topTabRowCanHideAndRestoreTheWorkspaceFileList() throws Exception {
        fx(() -> {
            open("我的笔记/学习计划.md");
            var currentFile = shell.filePane().currentFile();
            var split = (SplitPane) shell.getCenter();
            assertSame(shell.tabs().tabBar(), shell.lookup("#workspace-tab-bar"));
            assertNotNull(shell.lookup("#window-minimize"));
            assertNotNull(shell.lookup("#window-maximize"));
            assertNotNull(shell.lookup("#window-close"));
            assertEquals(2, split.getItems().size());
            button("toggle-file-list").fire();
            assertEquals(1, split.getItems().size());
            assertNull(shell.lookup("#workspace-sidebar"));
            assertSame(currentFile, shell.filePane().currentFile());
            button("toggle-file-list").fire();
            assertEquals(2, split.getItems().size());
            assertSame(shell.sidebar(), split.getItems().getFirst());
            assertSame(currentFile, shell.filePane().currentFile());
        });
    }

    @Test void topRowSeparatorsFollowTheSidebarAndMarkdownOutline() throws Exception {
        fx(() -> {
            open("Java/Java集合.md");
            pulse(100);
            var outer = (SplitPane) shell.getCenter();
            var markdown = (SplitPane) shell.filePane().getCenter();
            var outline = (MarkdownOutlineView) markdown.getItems().get(1);
            var sidebarCell = shell.lookup("#window-sidebar-cell");
            var outlineCell = shell.lookup("#window-outline-cell");
            var sidebarDivider = shell.lookup("#window-sidebar-divider");
            var outlineDivider = shell.lookup("#window-outline-divider");
            var headerSeam = shell.lookup("#workspace-header-seam");
            var headerSeamRight = shell.lookup("#workspace-header-seam-right");
            var sidebarSeam = shell.lookup("#workspace-sidebar-seam");
            var outlineSeam = shell.lookup("#workspace-outline-seam");
            var tabBar = shell.tabs().tabBar();
            assertTrue(headerSeam.isMouseTransparent());
            assertTrue(headerSeamRight.isMouseTransparent());
            assertTrue(sidebarSeam.isMouseTransparent());
            assertTrue(outlineSeam.isMouseTransparent());
            assertEquals(0.75, headerSeam.getBoundsInParent().getHeight(), 0.01);
            assertEquals(0.75, headerSeamRight.getBoundsInParent().getHeight(), 0.01);
            assertEquals(outer.localToScene(0, 0).getY(), headerSeam.localToScene(0, 0.75).getY(), 0.5);
            var activeTabNode = shell.tabs().activeTabNode();
            assertEquals(activeTabNode.localToScene(0, 0).getX(),
                    headerSeam.localToScene(headerSeam.getBoundsInLocal().getWidth(), 0).getX(), 0.5);
            assertEquals(activeTabNode.localToScene(activeTabNode.getBoundsInLocal().getWidth(), 0).getX(),
                    headerSeamRight.localToScene(0, 0).getX(), 0.5);
            assertEquals(shell.getWidth(), headerSeamRight.getBoundsInParent().getMaxX(), 0.5);
            assertEquals(shell.getHeight(), sidebarSeam.getBoundsInParent().getHeight(), 0.5);
            assertEquals(shell.getHeight(), outlineSeam.getBoundsInParent().getHeight(), 0.5);
            assertEquals(tabBar.getBackground().getFills().getFirst().getFill(),
                    ((javafx.scene.layout.Region) sidebarCell).getBackground().getFills().getFirst().getFill());
            assertEquals(tabBar.getBackground().getFills().getFirst().getFill(),
                    ((javafx.scene.layout.Region) outlineCell).getBackground().getFills().getFirst().getFill());
            var activeTab = (javafx.scene.layout.Region) shell.lookup(".workspace-tab-active");
            assertEquals(0.75, activeTab.getBorder().getStrokes().getFirst().getWidths().getTop(), 0.01);
            assertEquals(0.75, sidebarSeam.getBoundsInParent().getWidth(), 0.01);
            assertEquals(0.75, outlineSeam.getBoundsInParent().getWidth(), 0.01);
            assertEquals(javafx.scene.paint.Color.web("#ded9d0"),
                    ((javafx.scene.layout.Region) sidebarSeam).getBackground().getFills().getFirst().getFill());
            assertEquals(((javafx.scene.layout.Region) sidebarSeam).getBackground().getFills().getFirst().getFill(),
                    ((javafx.scene.layout.Region) headerSeam).getBackground().getFills().getFirst().getFill());
            assertEquals(shell.sidebar().getWidth(), sidebarCell.getBoundsInParent().getWidth(), 2);
            assertEquals(outline.getWidth(), outlineCell.getBoundsInParent().getWidth(), 2);
            assertEquals(divider(outer).localToScene(0, 0).getX(), sidebarDivider.localToScene(0, 0).getX(), 0.5);
            assertEquals(divider(markdown).localToScene(0, 0).getX(), outlineDivider.localToScene(0, 0).getX(), 0.5);
            assertEquals(38, sidebarDivider.getBoundsInParent().getHeight(), 0.5);
            assertEquals(38, outlineDivider.getBoundsInParent().getHeight(), 0.5);
            assertEquals(divider(outer).localToScene(0, 0).getY(),
                    sidebarDivider.localToScene(0, 38).getY(), 0.5);
            assertEquals(divider(markdown).localToScene(0, 0).getY(),
                    outlineDivider.localToScene(0, 38).getY(), 0.5);
            assertEquals(divider(outer).localToScene(5.25, 0).getX(), sidebarSeam.localToScene(0, 0).getX(), 0.5);
            assertEquals(divider(markdown).localToScene(0, 0).getX(), outlineSeam.localToScene(0, 0).getX(), 1.0);
            assertEquals(sidebarDivider.localToScene(6, 0).getX(), tabBar.localToScene(0, 0).getX(), 0.5);
            assertEquals(outline.localToScene(0, 0).getX(), outlineCell.localToScene(0, 0).getX(), 2);

            javafx.event.Event.fireEvent(divider(outer), mouseMoved());
            assertEquals(6, sidebarSeam.getBoundsInParent().getWidth(), 0.5);
            assertEquals(0.75, outlineSeam.getBoundsInParent().getWidth(), 0.5);
            assertEquals(divider(outer).localToScene(0, 0).getX(), sidebarSeam.localToScene(0, 0).getX(), 0.5);
            sidebarSeam.applyCss();
            assertEquals(javafx.scene.paint.Color.web("#e3dfd8"),
                    ((javafx.scene.layout.Region) sidebarSeam).getBackground().getFills().getFirst().getFill());
            javafx.event.Event.fireEvent(divider(outer), mouseEvent(javafx.scene.input.MouseEvent.MOUSE_DRAGGED));
            assertEquals(6, sidebarSeam.getBoundsInParent().getWidth(), 0.5);
            javafx.event.Event.fireEvent(divider(markdown), mouseMoved());
            assertEquals(0.75, sidebarSeam.getBoundsInParent().getWidth(), 0.5);
            assertEquals(6, outlineSeam.getBoundsInParent().getWidth(), 0.5);
            javafx.event.Event.fireEvent(shell, mouseMoved());
            assertEquals(0.75, sidebarSeam.getBoundsInParent().getWidth(), 0.5);
            assertEquals(0.75, outlineSeam.getBoundsInParent().getWidth(), 0.5);

            outer.setDividerPositions(0.35);
            outer.layout();
            assertEquals(divider(outer).localToScene(0, 0).getX(), sidebarDivider.localToScene(0, 0).getX(), 0.5);
            assertEquals(divider(outer).localToScene(5.25, 0).getX(), sidebarSeam.localToScene(0, 0).getX(), 0.5);
            assertEquals(divider(markdown).localToScene(0, 0).getX(), outlineSeam.localToScene(0, 0).getX(), 1.0);
            assertEquals(divider(markdown).localToScene(0, 0).getX(), outlineDivider.localToScene(0, 0).getX(), 0.5);
            markdown.setDividerPositions(0.65);
            markdown.layout();
            assertEquals(divider(markdown).localToScene(0, 0).getX(), outlineDivider.localToScene(0, 0).getX(), 0.5);
            assertEquals(divider(markdown).localToScene(0, 0).getX(), outlineSeam.localToScene(0, 0).getX(), 0.5);
            shell.layout(); pulse(100);
            assertEquals(divider(outer).localToScene(0, 0).getX(), sidebarDivider.localToScene(0, 0).getX(), 0.5);
            assertEquals(divider(markdown).localToScene(0, 0).getX(), outlineDivider.localToScene(0, 0).getX(), 0.5);
            assertEquals(outline.localToScene(0, 0).getX(), outlineCell.localToScene(0, 0).getX(), 2);

            button("toggle-file-list").fire();
            shell.layout(); pulse(100);
            assertEquals(42, sidebarCell.getBoundsInParent().getWidth(), 2);
            assertFalse(sidebarDivider.isManaged());
            assertFalse(sidebarSeam.isVisible());
            button("toggle-file-list").fire();
            pulse(100); shell.layout(); pulse(100);
            assertEquals(divider(outer).localToScene(0, 0).getX(), sidebarDivider.localToScene(0, 0).getX(), 0.5);
            assertTrue(sidebarSeam.isVisible());

            button("file-mode-toggle").fire();
            assertFalse(outlineDivider.isManaged());
            assertFalse(outlineSeam.isVisible());
            button("file-mode-toggle").fire();
            assertTrue(outlineDivider.isManaged());
            assertTrue(outlineSeam.isVisible());
        });
    }

    @Test void headerSeparatorGapFollowsTheActiveTab() throws Exception {
        fx(() -> {
            open("Java/Java集合.md");
            var first = shell.tabs().active();
            shell.tabs().openPinned(fixture.alpha.id(), "我的笔记/学习计划.md");
            shell.applyCss(); shell.layout(); pulse(100);
            assertHeaderGapUnderActiveTab();

            shell.tabs().activate(first);
            shell.applyCss(); shell.layout(); pulse(100);
            assertHeaderGapUnderActiveTab();

            shell.tabs().closeAll();
            shell.applyCss(); shell.layout(); pulse(100);
            assertEquals(shell.getWidth(), shell.lookup("#workspace-header-seam").getBoundsInParent().getWidth(), 0.5);
            assertEquals(0, shell.lookup("#workspace-header-seam-right").getBoundsInParent().getWidth(), 0.01);
        });
    }

    @Test void tabsShrinkBeforeScrollingAndKeepTheirCloseButtons() throws Exception {
        String first = "Java/这是第一份用于检查标签宽度缩小的长文件名称.md";
        String second = "Java/这是第二份用于检查标签宽度缩小的长文件名称.md";
        fixture.write(first, "# 第一份文档\n");
        fixture.write(second, "# 第二份文档\n");
        fx(() -> {
            shell.refresh();
            shell.tabs().openPinned(fixture.alpha.id(), first);
            shell.tabs().openPinned(fixture.alpha.id(), second);
            shell.applyCss(); shell.layout(); pulse(100);
            var bar = shell.tabs().tabBar();
            var row = (javafx.scene.layout.HBox) bar.getContent();
            var item = (javafx.scene.layout.HBox) row.getChildren().getFirst();
            var select = (Button) item.getChildren().getFirst();
            var close = (Button) item.getChildren().get(1);
            double wideWidth = item.getWidth(), closeWidth = close.getWidth();
            assertEquals(first.substring(first.indexOf('/') + 1), select.getTooltip().getText());

            var outer = (SplitPane) shell.getCenter();
            outer.setDividerPositions(0.45);
            outer.layout(); shell.layout(); pulse(100);
            assertTrue(item.getWidth() < wideWidth, "Tabs must shrink with the available strip width");
            assertTrue(row.getWidth() <= bar.getViewportBounds().getWidth() + 0.5,
                    "Two tabs must fit instead of requiring horizontal scrolling");
            assertEquals(closeWidth, close.getWidth(), 0.01);
            assertTrue(select.getWidth() > 0);
            assertTrue(close.getBoundsInParent().getMaxX() <= item.getWidth() + 0.5);
            assertHeaderGapUnderActiveTab();
            close.fire();
            assertEquals(1, shell.tabs().tabs().size());
            assertEquals(second, shell.tabs().active().path());
            assertTrue(text(shell.lookup("#markdown-preview-scroll")).contains("第二份文档"));
        });
    }

    @Test void tabsKeepTheirMinimumWidthWhenScrollingIsNecessary() throws Exception {
        List<String> files = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            String path = "Java/这是用于检查多标签滚动的长文件名" + i + ".md";
            fixture.write(path, "# 文档 " + i + "\n");
            files.add(path);
        }
        fx(() -> {
            shell.refresh();
            files.forEach(path -> shell.tabs().openPinned(fixture.alpha.id(), path));
            shell.applyCss(); shell.layout(); pulse(100);
            var bar = shell.tabs().tabBar();
            var row = (javafx.scene.layout.HBox) bar.getContent();
            assertTrue(row.getWidth() > bar.getViewportBounds().getWidth(),
                    "The strip must scroll after tabs reach their minimum width");
            var sidebarCell = (javafx.scene.layout.Region) shell.lookup("#window-sidebar-cell");
            var outlineCell = (javafx.scene.layout.Region) shell.lookup("#window-outline-cell");
            assertEquals(shell.sidebar().getWidth(), sidebarCell.getWidth(), 0.5);
            assertEquals(shell.filePane().visibleOutline().getWidth(), outlineCell.getWidth(), 0.5);
            for (Node child : row.getChildren()) {
                var item = (javafx.scene.layout.HBox) child;
                var close = (Button) item.getChildren().get(1);
                assertTrue(item.getWidth() >= item.getMinWidth() - 0.5);
                assertTrue(close.getWidth() > 0);
                assertTrue(close.getBoundsInParent().getMaxX() <= item.getWidth() + 0.5);
            }
            assertEquals(files.size(), shell.tabs().tabs().size());
            assertEquals(ScrollPane.ScrollBarPolicy.AS_NEEDED, bar.getHbarPolicy());
            bar.setHvalue(1); pulse(100);
            assertHeaderGapUnderActiveTab();
        });
    }

    private void assertHeaderGapUnderActiveTab() {
        var tab = shell.tabs().activeTabNode();
        var left = shell.lookup("#workspace-header-seam");
        var right = shell.lookup("#workspace-header-seam-right");
        assertEquals(tab.localToScene(0, 0).getX(),
                left.localToScene(left.getBoundsInLocal().getWidth(), 0).getX(), 0.5);
        assertEquals(tab.localToScene(tab.getBoundsInLocal().getWidth(), 0).getX(),
                right.localToScene(0, 0).getX(), 0.5);
    }

    private Node divider(SplitPane pane) {
        return pane.getChildrenUnmodifiable().stream()
                .filter(child -> child.getStyleClass().contains("split-pane-divider"))
                .findFirst().orElseThrow();
    }

    private static javafx.scene.input.MouseEvent mouseMoved() {
        return mouseEvent(javafx.scene.input.MouseEvent.MOUSE_MOVED);
    }

    private static javafx.scene.input.MouseEvent mouseEvent(
            javafx.event.EventType<? extends javafx.scene.input.MouseEvent> type) {
        return new javafx.scene.input.MouseEvent(type,
                0, 0, 0, 0, javafx.scene.input.MouseButton.NONE, 0,
                false, false, false, false, false, false, false, false, false, false, null);
    }

    @Test void customWindowButtonsControlTheStage() throws Exception {
        fx(() -> {
            button("window-maximize").fire();
            assertTrue(stage.isMaximized());
            button("window-maximize").fire();
            assertFalse(stage.isMaximized());
            button("window-minimize").fire();
            assertTrue(stage.isIconified());
            stage.setIconified(false);
            button("window-close").fire();
            assertFalse(stage.isShowing());
        });
    }

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

    @Test void formalHeadersHideMetadataAndKeepOnlyRelevantActions() throws Exception {
        fx(() -> {
            open("Java/Java集合.md");
            Parent header = (Parent) shell.lookup("#file-header");
            assertEquals("Java集合.md", ((Label) shell.lookup("#file-title")).getText());
            assertFalse(text(shell).contains("doc_java"));
            assertFalse(text(shell).contains("qfd:v1:"));
            assertEquals(1, header.lookupAll(".button").size());
            assertNull(shell.lookup("#asset-info-button"));
            open("题库/Java集合.qbank");
            assertEquals(2, shell.lookup("#file-header").lookupAll(".button").size());
            assertNull(shell.lookup("#asset-info-button"));
            assertFalse(text(shell).contains("doc_java"));
            assertFalse(text(shell).contains("qfd:v1:"));
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

    @Test void markdownOutlineFillsTheHeaderHeightAndItsDividerCanMove() throws Exception {
        fx(() -> {
            open("Java/Java集合.md");
            shell.applyCss(); shell.layout();
            FilePane pane = shell.filePane();
            var layout = (SplitPane) pane.getCenter();
            var reader = (BorderPane) layout.getItems().getFirst();
            var outline = (MarkdownOutlineView) layout.getItems().get(1);
            assertNull(pane.getTop());
            assertSame(shell.lookup("#file-header"), reader.getTop());
            assertEquals(0, outline.getLayoutY(), 1);
            assertEquals(shell.sidebar().getBackground().getFills().getFirst().getFill(),
                    outline.getBackground().getFills().getFirst().getFill());
            assertNotNull(layout.lookup(".split-pane-divider"));
            double originalWidth = outline.getWidth();
            layout.setDividerPositions(0.6);
            shell.layout(); pulse(100);
            assertTrue(outline.getWidth() > originalWidth + 20);

            button("file-mode-toggle").fire();
            assertSame(shell.lookup("#file-header"), pane.getTop());
            assertNull(shell.lookup("#markdown-outline"));
            button("file-mode-toggle").fire();
            assertNull(pane.getTop());
            assertSame(shell.lookup("#file-header"), reader.getTop());
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
            shell.applyCss(); shell.layout(); pulse(100);
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
            assertNotNull(shell.lookup("#question-outline"));
            toggle.fire();
            assertSame(toggle, button("file-mode-toggle"));
            assertEquals("第 2 / 2 题", ((Label) shell.lookup("#question-position")).getText());
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
            assertNull(shell.lookup("#asset-info-button"));
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
            assertNull(shell.lookup("#qbank-add-source"));
            assertNotNull(shell.lookup("#qbank-source-link"));
            assertNotNull(shell.lookup("#qbank-use-source-link"));
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
            assertEquals("Java集合 · section_list", button("qbank-source-0").getText());
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
            byte[] before = Files.readAllBytes(fixture.alphaRoot.resolve("题库/Java集合.qbank"));
            open("题库/Java集合.qbank");
            ((RadioButton) shell.lookup("#option-1")).fire();
            button("submit-answer").fire();
            assertTrue(text(shell.lookup("#answer-feedback")).contains("回答错误"));
            assertArrayEquals(before, Files.readAllBytes(fixture.alphaRoot.resolve("题库/Java集合.qbank")));
        });
    }

    @Test void practiceSummaryPersistsAndOutlineRemainsAvailable() throws Exception {
        fx(() -> {
            byte[] before = Files.readAllBytes(fixture.alphaRoot.resolve("题库/Java集合.qbank"));
            open("题库/Java集合.qbank");
            ((RadioButton) shell.lookup("#option-1")).fire();
            button("submit-answer").fire();
            button("next-question").fire();
            ((CheckBox) shell.lookup("#option-0")).fire();
            ((CheckBox) shell.lookup("#option-1")).fire();
            button("submit-answer").fire();
            button("next-question").fire();
            assertEquals("50%", ((Label) shell.lookup("#summary-percentage")).getText());
            assertEquals("1", ((Label) shell.lookup("#summary-correct-count")).getText());
            assertEquals("1", ((Label) shell.lookup("#summary-incorrect-count")).getText());
            assertEquals("0", ((Label) shell.lookup("#summary-unanswered-count")).getText());
            assertTrue(shell.lookup("#question-outline").isVisible());
            assertFalse(button("practice-restart").isDisabled());
            shell.tabs().closeAll();
            shell.tabs().openPreview(fixture.alpha.id(), "题库/Java集合.qbank");
            shell.applyCss(); shell.layout();
            assertNotNull(shell.lookup("#practice-summary"));
            assertEquals("50%", ((Label) shell.lookup("#summary-percentage")).getText());
            assertEquals("1", ((Label) shell.lookup("#summary-correct-count")).getText());
            assertTrue(shell.lookup("#question-outline").isVisible());
            assertArrayEquals(before, Files.readAllBytes(fixture.alphaRoot.resolve("题库/Java集合.qbank")));
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
            assertEquals("0%", ((Label) shell.lookup("#summary-percentage")).getText());
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

    @Test void summaryRestoresAfterTabCloseAndLastSubmitDoesNotAutoOpenIt() throws Exception {
        fx(() -> {
            open("题库/Java集合.qbank");
            button("next-question").fire();
            ((CheckBox) shell.lookup("#option-0")).fire(); button("submit-answer").fire();
            assertNull(shell.lookup("#practice-summary"));
            button("next-question").fire();
            assertEquals("0%", ((Label) shell.lookup("#summary-percentage")).getText());
            assertEquals("1", ((Label) shell.lookup("#summary-incorrect-count")).getText());
            assertEquals("1", ((Label) shell.lookup("#summary-unanswered-count")).getText());
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
            assertEquals("第 1 / 2 题", ((Label) shell.lookup("#question-position")).getText());
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
            assertEquals(java.util.Arrays.asList("folder-new-folder", "folder-new-file",
                            null, "copy-file-path", null,
                            "rename-file-entry", "delete-file-entry"),
                    folderItems.stream().map(MenuItem::getId).toList());
            Menu folderFiles = (Menu) folderItems.get(1);
            assertEquals(List.of("folder-new-md", "folder-new-qbank"),
                    folderFiles.getItems().stream().map(MenuItem::getId).toList());
            assertEquals(List.of("Markdown 文件 (.md)", "题库文件 (.qbank)"),
                    folderFiles.getItems().stream().map(MenuItem::getText).toList());
            var fileItems = folderCell("Java/Java集合.md").getContextMenu().getItems();
            assertEquals("copy-link", fileItems.getFirst().getId());
            assertEquals("复制链接", fileItems.getFirst().getText());
            assertTrue(fileItems.stream().noneMatch(item -> item.getId() != null
                    && item.getId().startsWith("folder-new-")));
            Menu copy = (Menu) fileItems.stream().filter(item ->
                    "copy-file-path".equals(item.getId())).findFirst().orElseThrow();
            assertEquals(List.of("copy-relative-path", "copy-absolute-path"),
                    copy.getItems().stream().map(MenuItem::getId).toList());
            var switcher = shell.sidebar().switcher().getItems();
            assertEquals(java.util.Arrays.asList("workspace-history", null, "new-workspace", "open-workspace-folder"),
                    switcher.stream().map(MenuItem::getId).toList());
            assertEquals(List.of("refresh-workspace", "workspace-new-folder", "workspace-new-file"),
                    shell.sidebar().fileActions().getChildren().stream().map(Node::getId).toList());
            Button newFile = button("workspace-new-file");
            assertEquals(List.of("Markdown 文件 (.md)", "题库文件 (.qbank)"),
                    newFile.getContextMenu().getItems().stream().map(MenuItem::getText).toList());
            var bankItems = folderCell("题库/Java集合.qbank").getContextMenu().getItems();
            assertEquals(java.util.Arrays.asList("copy-file-path", null, "rename-file-entry", "delete-file-entry"),
                    bankItems.stream().map(MenuItem::getId).toList());
            for (var items : List.of(folderItems, fileItems, bankItems)) {
                for (MenuItem item : items) {
                    if (item instanceof javafx.scene.control.SeparatorMenuItem) continue;
                    assertNotNull(item.getGraphic(), item.getText());
                    if (item instanceof Menu submenu) {
                        submenu.getItems().forEach(child -> assertNull(child.getGraphic(), child.getText()));
                    }
                }
            }
            assertFalse(folderCell("Java").getContextMenu().getStyleClass().contains("workspace-submenu"));
            assertEquals("", newFile.getText());
            assertEquals("新建文件", newFile.getTooltip().getText());
        });
    }

    @Test void workspaceMenuUsesChineseGroupsAndKeepsEveryActionCallback() throws Exception {
        fx(() -> {
            var calls = new ArrayList<String>();
            var menu = new WorkspaceSwitcher();
            menu.update(fixture.alpha, List.of(fixture.alpha, fixture.beta),
                    workspace -> calls.add("select:" + workspace.id()),
                    () -> calls.add("open-folder"), () -> calls.add("new"));
            assertEquals(List.of("", "|", "新建工作区…", "打开文件夹…"),
                    menu.getItems().stream().map(item -> item instanceof SeparatorMenuItem ? "|" : item.getText()).toList());
            assertEquals(2, menu.historyItems().getChildren().size());
            assertTrue(menu.historyItems().getChildren().getFirst().getStyleClass().contains("current"));
            ((Button) menu.historyItems().getChildren().get(1)).fire();
            for (String id : List.of("new-workspace", "open-workspace-folder"))
                menu.getItems().stream().filter(item -> id.equals(item.getId())).findFirst().orElseThrow().fire();
            assertEquals(List.of("select:" + fixture.beta.id(), "new", "open-folder"), calls);
            menu.update(null, List.of(), ignored -> {}, () -> {}, () -> {});
            assertEquals("尚无已打开的工作区", ((Label) menu.historyItems().getChildren().getFirst()).getText());
            assertEquals("open-workspace-folder", menu.getItems().getLast().getId());
        });
    }

    @Test void workspaceHistoryScrollsWhileCreateAndOpenStayOutsideTheList() throws Exception {
        fx(() -> {
            var recent = new ArrayList<io.quizforge.core.workspace.Workspace>();
            for (int index = 0; index < 12; index++) {
                recent.add(new io.quizforge.core.workspace.Workspace(
                        io.quizforge.core.workspace.WorkspaceId.newId(), "Workspace " + index,
                        java.time.Instant.EPOCH, java.time.Instant.EPOCH));
            }
            var menu = new WorkspaceSwitcher();
            menu.update(recent.getFirst(), recent, ignored -> {}, () -> {}, () -> {});

            assertEquals(12, menu.historyItems().getChildren().size());
            assertEquals(7 * 32, menu.historyScroll().getPrefViewportHeight());
            assertEquals("new-workspace", menu.getItems().get(2).getId());
            assertEquals("open-workspace-folder", menu.getItems().get(3).getId());
        });
    }

    @Test void folderAndFileMenuCallbacksKeepTheirOriginalTargets() throws Exception {
        fx(() -> {
            var calls = new ArrayList<String>();
            var tree = new WorkspaceFileTree((entry, pinned) -> {}, new WorkspaceFileTree.FileActions() {
                public void createFolder(String path) { calls.add("folder:" + path); }
                public void createFile(String path, io.quizforge.core.workspace.WorkspaceFileType type) {
                    calls.add("file:" + path + ":" + type.extension());
                }
                public void copyPath(String path, boolean absolute) { calls.add("copy:" + path + ":" + absolute); }
                public void rename(WorkspaceFileEntry entry) { calls.add("rename:" + entry.relativePath()); }
                public void delete(WorkspaceFileEntry entry) { calls.add("delete:" + entry.relativePath()); }
                public void copyLink(WorkspaceFileEntry entry) { calls.add("link:" + entry.relativePath()); }
            });
            tree.setRoot(shell.sidebar().tree().getRoot());
            Stage review = new Stage();
            Scene scene = new Scene(tree, 300, 900); UiTheme.apply(scene);
            review.setScene(scene); review.setOpacity(0); review.show();
            try {
                tree.applyCss(); tree.layout();
                for (String path : List.of("Java", "Java/Java集合.md", "题库/Java集合.qbank")) {
                    calls.clear();
                    TreeCell<?> cell = tree.lookupAll(".tree-cell").stream().filter(TreeCell.class::isInstance)
                            .map(TreeCell.class::cast).filter(row -> row.getItem() instanceof WorkspaceFileEntry entry
                                    && path.equals(entry.relativePath())).findFirst().orElseThrow();
                    for (MenuItem item : cell.getContextMenu().getItems()) {
                        if (item instanceof Menu submenu) submenu.getItems().forEach(MenuItem::fire);
                        else if (!(item instanceof SeparatorMenuItem)) item.fire();
                    }
                    var expected = new ArrayList<String>();
                    if (path.equals("Java")) expected.addAll(List.of("folder:Java", "file:Java:.md", "file:Java:.qbank"));
                    if (path.endsWith(".md")) expected.add("link:" + path);
                    expected.addAll(List.of("copy:" + path + ":false", "copy:" + path + ":true", "rename:" + path, "delete:" + path));
                    assertEquals(expected, calls);
                }
            } finally { review.close(); }
        });
    }

    @Test void newMarkdownFileIsNamedInTreeAndDuplicateNameStaysEditable() throws Exception {
        fx(() -> {
            Menu newFile = (Menu) folderCell("Java").getContextMenu().getItems().stream()
                    .filter(item -> "folder-new-file".equals(item.getId())).findFirst().orElseThrow();
            newFile.getItems().getFirst().fire();
            shell.applyCss(); shell.layout();
            TextField name = (TextField) shell.lookup("#inline-new-file-name");
            assertNotNull(name);
            assertFalse(Files.exists(fixture.alphaRoot.resolve("Java/新建文件.md")));
            name.setText("Java集合");
            name.fireEvent(new javafx.event.ActionEvent());
            assertSame(name, shell.lookup("#inline-new-file-name"));
            assertTrue(name.getPseudoClassStates().contains(javafx.css.PseudoClass.getPseudoClass("invalid-name")));
            assertEquals("当前文件夹中已存在同名文件或文件夹", name.getTooltip().getText());
            name.setText("新建笔记");
            name.fireEvent(new javafx.event.ActionEvent());
            assertTrue(Files.exists(fixture.alphaRoot.resolve("Java/新建笔记.md")));
            assertNull(shell.lookup("#inline-new-file-name"));
            assertTrue(paths(shell.sidebar().tree().getRoot()).contains("Java/新建笔记.md"));
        });
    }

    @Test void unnamedNewFileCancelsAndWorkspaceMenuCanCreateQuestionBankAtRoot() throws Exception {
        fx(() -> {
            Button newFile = button("workspace-new-file");
            newFile.getContextMenu().getItems().getFirst().fire();
            shell.applyCss(); shell.layout();
            TextField name = (TextField) shell.lookup("#inline-new-file-name");
            assertNotNull(name);
            name.fireEvent(new javafx.event.ActionEvent());
            shell.applyCss(); shell.layout();
            assertNull(shell.lookup("#inline-new-file-name"));
            assertFalse(Files.exists(fixture.alphaRoot.resolve("新建文件.md")));
            newFile.getContextMenu().getItems().get(1).fire();
            shell.applyCss(); shell.layout();
            name = (TextField) shell.lookup("#inline-new-file-name");
            assertNotNull(name);
            name.setText("取消的题库");
            name.fireEvent(new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED,
                    "", "", javafx.scene.input.KeyCode.ESCAPE, false, false, false, false));
            shell.applyCss(); shell.layout();
            assertNull(shell.lookup("#inline-new-file-name"));
            assertFalse(Files.exists(fixture.alphaRoot.resolve("取消的题库.qbank")));
            newFile.getContextMenu().getItems().get(1).fire();
            shell.applyCss(); shell.layout();
            name = (TextField) shell.lookup("#inline-new-file-name");
            name.setText("根目录题库");
            name.fireEvent(new javafx.event.ActionEvent());
            assertTrue(Files.exists(fixture.alphaRoot.resolve("根目录题库.qbank")));
            assertTrue(paths(shell.sidebar().tree().getRoot()).contains("根目录题库.qbank"));
        });
    }

    @Test void newFolderIsNamedInTreeAndRejectsSiblingDuplicate() throws Exception {
        fx(() -> {
            button("workspace-new-folder").fire();
            shell.applyCss(); shell.layout();
            TextField name = (TextField) shell.lookup("#inline-new-folder-name");
            assertNotNull(name);
            assertFalse(Files.exists(fixture.alphaRoot.resolve("新文件夹")));
            name.setText("Java");
            name.fireEvent(new javafx.event.ActionEvent());
            assertSame(name, shell.lookup("#inline-new-folder-name"));
            assertTrue(name.getPseudoClassStates().contains(javafx.css.PseudoClass.getPseudoClass("invalid-name")));
            name.setText("新的资料");
            name.fireEvent(new javafx.event.ActionEvent());
            assertTrue(Files.isDirectory(fixture.alphaRoot.resolve("新的资料")));
            assertTrue(paths(shell.sidebar().tree().getRoot()).contains("新的资料"));
            assertNull(shell.lookup("#inline-new-folder-name"));
            folderCell("Java").getContextMenu().getItems().stream()
                    .filter(item -> "folder-new-folder".equals(item.getId())).findFirst().orElseThrow().fire();
            shell.applyCss(); shell.layout();
            name = (TextField) shell.lookup("#inline-new-folder-name");
            assertNotNull(name);
            name.fireEvent(new javafx.event.ActionEvent());
            shell.applyCss(); shell.layout();
            assertNull(shell.lookup("#inline-new-folder-name"));
            assertFalse(Files.exists(fixture.alphaRoot.resolve("Java/新文件夹")));
        });
    }

    @Test void fileAndFolderRenameHappenInTreeWithoutOverwritingSiblings() throws Exception {
        fx(() -> {
            folderCell("Java").getContextMenu().getItems().stream()
                    .filter(item -> "rename-file-entry".equals(item.getId())).findFirst().orElseThrow().fire();
            shell.applyCss(); shell.layout();
            TextField name = (TextField) shell.lookup("#inline-rename-name");
            assertEquals("Java", name.getText());
            name.setText("题库");
            name.fireEvent(new javafx.event.ActionEvent());
            assertSame(name, shell.lookup("#inline-rename-name"));
            assertTrue(name.getPseudoClassStates().contains(javafx.css.PseudoClass.getPseudoClass("invalid-name")));
            name.setText("Java新");
            name.fireEvent(new javafx.event.ActionEvent());
            assertTrue(Files.exists(fixture.alphaRoot.resolve("Java新/Java集合.md")));
            assertFalse(Files.exists(fixture.alphaRoot.resolve("Java")));
            assertTrue(paths(shell.sidebar().tree().getRoot()).contains("Java新/Java集合.md"));
            folderCell("Java新/Java集合.md").getContextMenu().getItems().stream()
                    .filter(item -> "rename-file-entry".equals(item.getId())).findFirst().orElseThrow().fire();
            shell.applyCss(); shell.layout();
            name = (TextField) shell.lookup("#inline-rename-name");
            assertEquals("Java集合.md", name.getText());
            name.setText("集合笔记.md");
            name.fireEvent(new javafx.event.ActionEvent());
            assertTrue(Files.exists(fixture.alphaRoot.resolve("Java新/集合笔记.md")));
            assertFalse(Files.exists(fixture.alphaRoot.resolve("Java新/Java集合.md")));
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
            var saved = new QuestionBankV2Codec().parse(QBankTestPackageBuilder.read(
                    fixture.alphaRoot.resolve("题库/Java集合.qbank")));
            var question = saved.questions().getFirst();
            assertEquals(link, QuestionText.prompt(question));
            assertEquals(link, QuestionText.option(question.choicePayload().options().getFirst()));
            assertEquals(link, QuestionText.analysis(question));
            assertEquals(2, question.sourceRefs().size());
            var source = question.sourceRefs().get(1);
            assertEquals(prepared.document().documentAssetId(), source.documentAssetId());
            assertEquals(prepared.document().contentId(), source.documentContentId());
            assertEquals("定义", source.anchorName());
            assertEquals(1, source.occurrence());
            assertFalse(QBankTestPackageBuilder.read(fixture.alphaRoot.resolve("题库/Java集合.qbank"))
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
                .replace("<!-- qf:anchor=section_list -->\n", "")
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
            var saved = new QuestionBankV2Codec().parse(QBankTestPackageBuilder.read(
                    fixture.alphaRoot.resolve("题库/Java集合.qbank")));
            var ref = saved.questions().getFirst().sourceRefs().get(1);
            assertEquals("命名来源", ref.anchorName());
            assertEquals(new StandardKnowledgeDocumentV1().parseIfStandard(markdown)
                    .orElseThrow().contentId(), ref.documentContentId());
        });
    }

    @Test void ordinaryMarkdownFileTreeCopyLinkRegistersWithoutOpeningIt() throws Exception {
        String path = "我的笔记/学习计划.md";
        String before = QBankTestPackageBuilder.read(fixture.alphaRoot.resolve(path));
        fx(() -> {
            open("Java/Java集合.md");
            folderCell(path).getContextMenu().getItems().getFirst().fire();
            assertEquals("Java/Java集合.md", shell.filePane().currentFile()
                    .file().entry().relativePath());
            var registered = fixture.files.open(fixture.alpha.id(), path).entry();
            assertEquals(WorkspaceFileKind.STANDARD_DOCUMENT, registered.kind());
            assertEquals(new io.quizforge.core.document.navigation.MarkdownNavigationLinkCodec().format(
                    path, QuizForgeNavigationLink.asset(registered.assetId())), fixture.copiedText.get());
            assertTrue(QBankTestPackageBuilder.read(fixture.alphaRoot.resolve(path)).endsWith(before));
            assertFalse(QBankTestPackageBuilder.read(fixture.alphaRoot.resolve(path)).contains("qf:anchor="));
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
            String saved = QBankTestPackageBuilder.read(fixture.alphaRoot.resolve(path));
            assertTrue(saved.contains("title: User note"));
            assertTrue(saved.endsWith(source.substring(source.indexOf("# Java"))));
            assertFalse(saved.contains("qf:id="));
            assertEquals(2, saved.split("qf:anchor=定义", -1).length - 1);
            var previewLayout = (SafeMarkdownPreview.BrowseLayout) shell.filePane().getCenter();
            var reader = previewLayout.reader();
            var page = (javafx.scene.layout.StackPane) reader.getContent();
            assertTrue(text(page).contains("内容 B"));
            assertFalse(text(page).contains("qf:anchor"));
            assertEquals("示例", button("qf-nav-2").getAccessibleText());
            button("qf-nav-4").getContextMenu().getItems().getFirst().fire();
            assertEquals(new io.quizforge.core.document.navigation.MarkdownNavigationLinkCodec().format(
                    path, QuizForgeNavigationLink.anchor(assetId, "定义", 2)), fixture.copiedText.get());
            assertEquals(saved, QBankTestPackageBuilder.read(fixture.alphaRoot.resolve(path)));
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
            String saved = QBankTestPackageBuilder.read(fixture.alphaRoot.resolve(path));
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
        String original = QBankTestPackageBuilder.read(fixture.alphaRoot.resolve(path));
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
            assertEquals(original, QBankTestPackageBuilder.read(fixture.alphaRoot.resolve(path)));
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
            assertNull(folderCell(path).getGraphic().lookup(".reference-link-indicator"));
            assertTrue(QBankTestPackageBuilder.read(fixture.alphaRoot.resolve(path)).contains("quizforge:"));
            assertTrue(QBankTestPackageBuilder.read(fixture.alphaRoot.resolve(path)).contains("<!-- qf:anchor=学习计划来源 -->"));
            assertFalse(QBankTestPackageBuilder.read(fixture.alphaRoot.resolve(path)).contains("qf:id=node_"));
            shell.refresh();
            open(path);
            assertNull(folderCell(path).getGraphic().lookup(".reference-link-indicator"));
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
            assertNull(folderCell(path).getGraphic().lookup(".reference-link-indicator"));
            String saved = QBankTestPackageBuilder.read(fixture.alphaRoot.resolve(path));
            assertEquals(1, saved.split("qf:anchor=手写来源", -1).length - 1);
            assertTrue(saved.contains("quizforge:"));
            var document = shell.filePane().currentFile().registeredMarkdown();
            assertEquals(new QuizForgeReferenceCodec().encode(QuizForgeReference.anchor(
                    document.documentAssetId(), document.contentId(), "手写来源", 1)),
                    fixture.copiedText.get());
        });
    }

    @Test void fileTreeUsesPlainMarkdownIconsForOrdinaryAndRegisteredDocuments() throws Exception {
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
            assertNull(registered.lookup(".reference-link-indicator"));
            assertEquals(ordinary.getAccessibleText(), registered.getAccessibleText());
            assertEquals(((javafx.scene.shape.SVGPath) ordinary.lookup(".line-icon")).getContent(),
                    ((javafx.scene.shape.SVGPath) registered.lookup(".line-icon")).getContent());
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
            fixture.write("空草稿/新题库.qbank", "{\"schemaVersion\":\"9.0\",\"assetId\":\"qb_x\",\"title\":\"Bad\",\"stimuli\":[],\"resources\":[],\"questions\":[]}");
            shell.refresh();
            open("空草稿/新题库.qbank");
            assertFalse(shell.filePane().currentFile().draft());
            assertNull(shell.lookup("#empty-asset-ai"));
        });
    }

    @Test void switcherTracksRecentWorkspacesAndSettingsStaysAtSidebarBottom() throws Exception {
        fx(() -> {
            shell.switchWorkspace(fixture.beta);
            assertEquals(fixture.beta.id(), new WorkspaceHistory(fixture.history).order(fixture.workspaces.listWorkspaces()).getFirst().id());
            var menu = shell.sidebar().switcher();
            assertEquals("Spring 学习", menu.getText());
            assertNotNull(menu.historyItems().getChildren().getFirst());
            Button settings = button("settings-button");
            settings.fire();
            assertEquals(1, fixture.settingsOpened.get());
            assertTrue(menu.getItems().stream().noneMatch(item -> "workspace-settings".equals(item.getId())));
            Node footer = shell.lookup("#workspace-settings-area");
            assertSame(footer, shell.sidebar().getChildren().getLast());
            assertEquals(shell.sidebar().getHeight() - 10, footer.getBoundsInParent().getMaxY(), 1);
            assertEquals("", settings.getText());
            assertEquals("设置", settings.getTooltip().getText());
            ((Button) menu.historyItems().getChildren().stream()
                    .filter(item -> fixture.alpha.id().equals(item.getUserData())).findFirst().orElseThrow()).fire();
            assertEquals(fixture.alpha.id(), shell.currentWorkspace().id());
        });
    }

    @Test void workspaceSwitcherArrowRotatesDownWhenOpenAndUpWhenClosed() throws Exception {
        fx(() -> {
            var menu = shell.sidebar().switcher();
            shell.applyCss();
            shell.layout();
            Node arrow = menu.lookup(".arrow-button .arrow");
            assertNotNull(arrow);
            assertNotNull(((javafx.scene.layout.Region) arrow).getShape());
            assertEquals(0, arrow.getRotate(), 0.1);

            menu.show();
            pulse(240);
            assertEquals(180, arrow.getRotate(), 0.1);

            menu.hide();
            pulse(240);
            assertEquals(0, arrow.getRotate(), 0.1);
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
            double iconWidth = icon.getLayoutBounds().getWidth();
            var stroke = ((javafx.scene.shape.SVGPath) icon.lookup(".line-icon")).getStroke();
            assertNotNull(stroke);
            for (int i = 0; i < 6; i++) {
                // TreeView reuses rows during expansion. Verify the icon is ready before the next CSS pulse.
                cell.updateIndex(-1);
                assertNull(cell.getGraphic());
                cell.updateIndex(row);
                assertSame(icon, cell.getGraphic());
                assertEquals(stroke, ((javafx.scene.shape.SVGPath) cell.getGraphic().lookup(".line-icon")).getStroke());
                assertEquals(iconWidth, cell.getGraphic().getLayoutBounds().getWidth(), 0.01);
            }
        });
    }

    @Test void rootFilesKeepDedicatedIconsAndQuestionBankActivation() throws Exception {
        fixture.write("Root.md", "# Root Markdown");
        fixture.write("Root.qbank", QBankTestPackageBuilder.read(fixture.alphaRoot.resolve("题库/Java集合.qbank")));
        fx(() -> {
            shell.refresh();
            TreeCell<?> markdown = folderCell("Root.md");
            assertNotNull(markdown.getGraphic().lookup(".icon-markdown"));
            TreeCell<?> bank = folderCell("Root.qbank");
            var entry = (WorkspaceFileEntry) bank.getItem();
            assertEquals(WorkspaceFileKind.QUESTION_BANK, entry.kind());
            assertNotNull(bank.getGraphic().lookup(".icon-qbank"));
            assertNull(bank.getGraphic().lookup(".icon-file"));
            assertEquals("题库文件", bank.getGraphic().getAccessibleText());
            open("Root.qbank");
            assertEquals("Root.qbank", shell.tabs().active().path());
            assertEquals(entry, shell.sidebar().tree().getSelectionModel().getSelectedItem().getValue());
            assertNotNull(shell.lookup("#question-practice"));
            assertTrue(paths(shell.sidebar().tree().getRoot()).contains("空目录"));
        });
    }

    @Test void sharedAnsweringRendererPreservesChoiceControlsAndOnlyCallsSelectionAction() throws Exception {
        fx(() -> {
            var chosen = new ArrayList<String>();
            var single = QuestionCardView.answering(QuestionPresentationMapperTest.content("SINGLE_CHOICE"),
                    0, 2, java.util.Set.of("opt_b"), chosen::add);
            assertEquals("第 1 / 2 题", single.position().getText());
            assertTrue(((RadioButton) single.lookup("#option-1")).isSelected());
            ((RadioButton) single.lookup("#option-0")).fire();
            assertEquals(List.of("opt_a"), chosen);
            assertTrue(single.resultPresentation().isEmpty());
            assertNull(single.lookup("#answer-feedback"));
            var multiple = QuestionCardView.answering(QuestionPresentationMapperTest.content("MULTIPLE_CHOICE"),
                    1, 2, java.util.Set.of("opt_a", "opt_b"), chosen::add);
            assertTrue(((CheckBox) multiple.lookup("#option-0")).isSelected());
            ((CheckBox) multiple.lookup("#option-2")).fire();
            assertEquals(List.of("opt_a", "opt_c"), chosen);
            assertEquals("第 2 / 2 题", multiple.position().getText());
        });
    }

    @Test void sharedResultRendererDistinguishesSelectedCorrectMissedWrongAndNeutralOptions() throws Exception {
        fx(() -> {
            var result = new QuestionResultPresentation(QuestionPresentationMapperTest.content("MULTIPLE_CHOICE"),
                    java.util.Set.of("opt_a", "opt_c"), QuestionResultPresentation.Result.INCORRECT, true, true, true);
            var card = QuestionCardView.result(result, 0, 1, "history-", null);
            assertEquals("回答错误", card.resultLabel().getText());
            assertTrue(text(card).contains("你的答案：A、C"));
            assertTrue(text(card).contains("正确答案：A、B"));
            assertTrue(text(card).contains("题目解析"));
            var correctSelected = (CheckBox) card.lookup("#history-option-0");
            var correctMissed = (CheckBox) card.lookup("#history-option-1");
            var wrongSelected = (CheckBox) card.lookup("#history-option-2");
            var neutral = (CheckBox) card.lookup("#history-option-3");
            assertTrue(correctSelected.isSelected());
            assertTrue(correctSelected.getStyleClass().contains("correct-option"));
            assertFalse(correctMissed.isSelected());
            assertTrue(correctMissed.getStyleClass().contains("correct-option"));
            assertTrue(wrongSelected.isSelected());
            assertTrue(wrongSelected.getStyleClass().contains("incorrect-option"));
            assertFalse(neutral.isSelected());
            assertFalse(neutral.getStyleClass().contains("correct-option"));
            assertFalse(neutral.getStyleClass().contains("incorrect-option"));
            for (var option : List.of(correctSelected, correctMissed, wrongSelected, neutral)) {
                assertTrue(option.isDisabled());
                assertNull(option.getOnAction());
            }
            wrongSelected.fire();
            assertEquals(java.util.Set.of("opt_a", "opt_c"), result.userAnswer());
        });
    }

    @Test void sharedResultRendererHonorsVisibilityFlagsAndDoesNotShowEmptySourceHeading() throws Exception {
        fx(() -> {
            Label source = new Label("测试来源");
            var hidden = new QuestionResultPresentation(QuestionPresentationMapperTest.content("SINGLE_CHOICE"),
                    java.util.Set.of("opt_a"), QuestionResultPresentation.Result.CORRECT, false, false, false);
            var card = QuestionCardView.result(hidden, 0, 1, "", source);
            assertFalse(text(card).contains("正确答案："));
            assertFalse(text(card).contains("题目解析"));
            assertFalse(text(card).contains("测试来源"));
            assertFalse(card.lookup("#option-0").getStyleClass().contains("correct-option"));
            var emptySources = new javafx.scene.layout.VBox();
            emptySources.setVisible(false); emptySources.setManaged(false);
            var shown = new QuestionResultPresentation(hidden.question(), hidden.userAnswer(), hidden.result(), true, true, true);
            var visibleCard = QuestionCardView.result(shown, 0, 1, "history-", emptySources);
            Label heading = visibleCard.lookupAll(".editor-caption").stream().map(Label.class::cast)
                    .filter(label -> label.getText().equals("来源")).findFirst().orElseThrow();
            assertFalse(heading.isVisible());
            assertFalse(heading.isManaged());
        });
    }

    @Test void sharedReadOnlyPreviewShowsDraftWithoutResultOrFictitiousAttempt() throws Exception {
        fx(() -> {
            var card = QuestionCardView.readOnly(QuestionPresentationMapperTest.content("SINGLE_CHOICE"),
                    0, 1, "history-", java.util.Set.of("opt_b"), null);
            assertTrue(card.resultPresentation().isEmpty());
            assertNull(card.resultLabel());
            assertNull(card.lookup("#history-question-result"));
            assertFalse(text(card).contains("你的答案："));
            assertFalse(text(card).contains("回答正确"));
            RadioButton draft = (RadioButton) card.lookup("#history-option-1");
            assertTrue(draft.isSelected());
            assertTrue(draft.isDisabled());
            assertNull(draft.getOnAction());
            assertFalse(draft.getStyleClass().contains("incorrect-option"));
        });
    }

    @Test void practiceAndHistoryUseSameResultRendererWhileHistoryShellKeepsItsOwnNavigation() throws Exception {
        fx(() -> {
            open("题库/Java集合.qbank");
            assertInstanceOf(QuestionCardView.class, shell.lookup("#practice-question-card"));
            ((RadioButton) shell.lookup("#option-1")).fire(); button("submit-answer").fire();
            shell.applyCss(); shell.layout();
            var practice = (QuestionCardView) shell.lookup("#practice-question-card");
            var presentation = practice.resultPresentation().orElseThrow();
            String archived = practiceDbSession().id();
            new io.quizforge.infrastructure.persistence.SqlitePracticeSessionRepository(practiceDb())
                    .archive(archived, java.time.Instant.parse("2026-09-28T05:00:00Z"));
            shell.tabs().closeAll(); shell.tabs().openPreview(fixture.alpha.id(), "题库/Java集合.qbank");
            button("qbank-history-entry").fire(); shell.applyCss(); shell.layout();
            click(shell.lookup("#history-card-" + archived), 1); shell.applyCss(); shell.layout();
            var before = practiceRows();
            forbidPracticeWrites();
            var history = (QuestionCardView) shell.lookup("#history-question-card");
            assertEquals(presentation, history.resultPresentation().orElseThrow());
            assertEquals(practice.getPadding(), history.getPadding());
            assertEquals(practice.getSpacing(), history.getSpacing());
            assertEquals(1, shell.lookupAll(".shared-question-card").size());
            assertNotNull(shell.lookup("#history-final-state"));
            assertNotNull(shell.lookup("#history-attempt-position"));
            assertNotNull(shell.lookup("#history-detail-back"));
            assertNull(shell.lookup("#submit-answer"));
            button("history-next-question").fire();
            history = (QuestionCardView) shell.lookup("#history-question-card");
            assertTrue(history.resultPresentation().isEmpty());
            assertNotNull(shell.lookup("#history-no-attempt"));
            button("history-previous-question").fire();
            assertEquals(before, practiceRows());
        });
    }

    @Test void sharedSourceRowsKeepChangedRevisionNavigableAndMissingSourceWithoutAction() throws Exception {
        fx(() -> {
            var clicks = new java.util.concurrent.atomic.AtomicInteger();
            var changed = QuestionSourceVisuals.row(new QuestionSourceVisuals.Presentation("来源", "来源已修改",
                    QuestionBankReferenceResolver.Status.DIFFERENT_REVISION, true, clicks::incrementAndGet), "changed");
            Button link = (Button) changed.lookup("#changed");
            assertFalse(link.isDisabled()); link.fire();
            assertEquals(1, clicks.get());
            assertNotNull(changed.lookup(".question-source-warning"));
            var missing = QuestionSourceVisuals.row(new QuestionSourceVisuals.Presentation("来源", "来源文档不存在",
                    QuestionBankReferenceResolver.Status.MISSING_DOCUMENT, false, clicks::incrementAndGet), "missing");
            Button disabled = (Button) missing.lookup("#missing");
            assertTrue(disabled.isDisabled());
            assertNull(disabled.getOnAction()); disabled.fire();
            assertEquals(1, clicks.get());
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
            var cardBounds = card.localToScene(card.getBoundsInLocal());
            assertTrue(button("previous-question").localToScene(button("previous-question").getBoundsInLocal())
                    .getMaxX() < cardBounds.getMinX());
            assertTrue(button("next-question").localToScene(button("next-question").getBoundsInLocal())
                    .getMinX() > cardBounds.getMaxX());
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
            shell.applyCss(); shell.layout(); pulse(100);
            assertNotNull(shell.lookup("#markdown-outline"));
            assertTrue(shell.tabs().close(shell.tabs().active()));
            assertEquals("我的笔记/学习计划.md", shell.tabs().active().path());
            shell.applyCss(); shell.layout(); pulse(100);
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
            assertEquals(sample.json(), QBankTestPackageBuilder.read(fixture.alphaRoot.resolve(sample.bankPath())));
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
            assertTrue(button("question-number-2").getStyleClass().containsAll(List.of("unsubmitted", "current")));
        });
    }

    @Test void questionOutlineImmediatelyShowsResultsAndKeepsFeedbackOnRevisit() throws Exception {
        fx(() -> {
            open("题库/Java集合.qbank");
            byte[] before = Files.readAllBytes(fixture.alphaRoot.resolve("题库/Java集合.qbank"));
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
            assertArrayEquals(before, Files.readAllBytes(fixture.alphaRoot.resolve("题库/Java集合.qbank")));
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
            assertEquals("第 2 / 2 题", ((Label) shell.lookup("#question-position")).getText());
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
            assertEquals("第 120 / 120 题", ((Label) shell.lookup("#question-position")).getText());
        });
    }

    @Test void questionOutlineReflowsWhileResizingWithoutLosingSelections() throws Exception {
        String[] types = new String[60];
        for (int i = 0; i < types.length; i++) types[i] = i % 2 == 0 ? "SINGLE_CHOICE" : "MULTIPLE_CHOICE";
        String path = outlineBank(types);
        fx(() -> {
            shell.refresh(); open(path); pulse(100);
            var layout = (FileViewerRouter.PracticeLayout) shell.filePane().getCenter();
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
            assertTrue(firstCell.getStyleClass().containsAll(List.of("unsubmitted", "current")));
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
            var layout = (FileViewerRouter.PracticeLayout) shell.filePane().getCenter();
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

    private List<String> outlineNumbers(String type) {
        var section = (javafx.scene.layout.VBox) shell.lookup("#question-outline-" + type);
        var numbers = (javafx.scene.layout.FlowPane) section.getChildren().get(1);
        return numbers.getChildren().stream().map(node -> ((Button) node).getText()).toList();
    }

    private String outlineBank(String... types) throws Exception {
        var template = new QuestionBankV2Codec().parse(QBankTestPackageBuilder.read(fixture.alphaRoot.resolve("题库/Java集合.qbank")));
        List<Question> questions = new ArrayList<>();
        for (int i = 0; i < types.length; i++) {
            String type = types[i];
            var original = template.questions().stream().filter(question -> question.type().equals(type)).findFirst().orElseThrow();
            List<ChoiceOption> options = new ArrayList<>();
            List<String> correct = new ArrayList<>();
            for (int j = 0; j < original.choicePayload().options().size(); j++) {
                var old = original.choicePayload().options().get(j);
                String id = "opt_outline_" + i + "_" + j;
                options.add(new ChoiceOption(id, old.content()));
                if (original.choiceAnswerSpec().correctOptionIds().contains(old.id())) correct.add(id);
            }
            questions.add(Question.choice("q_outline_" + i, types[i], original.prompt(), original.analysis(), original.sourceRefs(), new ChoicePayload(options), new ChoiceAnswerSpec(correct)));
        }
        String path = "题库/Outline.qbank";
        fixture.write(path, new QuestionBankV2Codec().write(new QuestionBank("qb_outline", "Outline practice", "2.0", List.of(), questions, List.of())));
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
            assertEquals(sample.json(), QBankTestPackageBuilder.read(fixture.alphaRoot.resolve(sample.bankPath())));
            var reread = new QuestionBankV2Codec().parse(sample.json());
            assertEquals(sample.bank().questions().getFirst().sourceRefs().getFirst().documentContentId(),
                    reread.questions().getFirst().sourceRefs().getFirst().documentContentId());
            assertEquals("2.0", reread.schemaVersion());
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
            TextArea stem = (TextArea) shell.lookup("#qbank-question-stem");
            stem.appendText(" Unsaved stem");
            var bankTab = shell.tabs().active();
            button("qbank-source-1").fire();
            assertTrue(bankTab.pinned()); assertTrue(bankTab.pane().hasUnsavedChanges());
            String changed = sample.markdown().replace("First definition.", "Fresh current content.")
                    .replace("<!-- qf:anchor=定义 -->\nSecond definition.",
                            "<!-- qf:anchor=新名字 -->\nSecond definition.");
            fixture.write(sample.documentPath(), changed);
            shell.tabs().activate(bankTab);
            assertSame(stem, shell.lookup("#qbank-question-stem"));
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
            assertEquals(sample.json(), QBankTestPackageBuilder.read(fixture.alphaRoot.resolve(sample.bankPath())));
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
            assertEquals(sample.json(), QBankTestPackageBuilder.read(fixture.alphaRoot.resolve(sample.bankPath())));
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
            assertEquals(sample.json(), QBankTestPackageBuilder.read(fixture.alphaRoot.resolve(sample.bankPath())));
        });
    }

    @Test void leavingUnsavedBankEditUsesOwnedDialogAndCancelKeepsDraft() throws Exception {
        fx(()->{
            open("题库/Java集合.qbank");button("file-mode-toggle").fire();
            shell.applyCss();shell.layout();
            var stem=(TextArea)shell.lookup("#qbank-question-stem");
            stem.setText("Draft retained after cancel");
            var dialogFailure=new AtomicReference<Throwable>();
            Platform.runLater(()->{
                try {
                    var dialog=(Stage)currentDialog().getScene().getWindow();
                    assertSame(stage,dialog.getOwner());
                    assertEquals(javafx.stage.Modality.WINDOW_MODAL,dialog.getModality());
                }catch(Throwable error){dialogFailure.set(error);}
                finally{answerDialog(ButtonType.CANCEL.getText());}
            });
            button("file-mode-toggle").fire();
            if(dialogFailure.get()!=null)throw new AssertionError("Edit confirmation must stay with its owner",dialogFailure.get());
            assertSame(stem,shell.lookup("#qbank-question-stem"));
            assertEquals("Draft retained after cancel",stem.getText());
            assertTrue(shell.filePane().hasUnsavedChanges());
            button("qbank-save").fire();
            assertFalse(shell.filePane().hasUnsavedChanges());
            assertNull(shell.lookup("#question-bank-editor"));
        });
    }

    @Test void namedSourceUiResolvesAndCanBeRemovedInTheV2Editor() throws Exception {
        fx(() -> {
            open("题库/Java集合.qbank"); button("file-mode-toggle").fire();
            assertEquals(QuestionBankReferenceResolver.Status.EXACT_MATCH,
                    button("qbank-source-0").getProperties().get("quizforge.sourceStatus"));
            assertFalse(button("qbank-source-0").isDisabled());
            assertFalse(text(shell.lookup("#question-bank-editor")).contains("旧版节点引用"));
            assertNull(shell.lookup("#qbank-change-source-0"));
            assertFalse(button("qbank-remove-source-0").isDisabled());
            assertFalse(shell.filePane().hasUnsavedChanges());
            button("qbank-remove-source-0").fire();
            assertTrue(shell.filePane().hasUnsavedChanges());
            assertNull(shell.lookup("#qbank-source-0"));
        });
    }

    private record NavigationBank(String documentPath, String bankPath, String markdown,
            QuestionBank bank, String json) { }

    private NavigationBank navigationBank(boolean multiple) throws Exception {
        String documentPath = "Java/SourceNav.md", bankPath = "题库/SourceNav.qbank";
        StringBuilder text = new StringBuilder("# Source navigation\n\n<!-- qf:anchor=定义 -->\nFirst definition.\n\n");
        for (int i = 0; i < 65; i++) text.append("Paragraph ").append(i).append(" for scrolling.\n\n");
        text.append("<!-- qf:anchor=定义 -->\nSecond definition.\n\n<!-- qf:anchor=孤立 -->\n");
        var prepared = new RegisteredMarkdownCodec().prepareRegistration(text.toString(), documentPath);
        var document = prepared.document();
        var first = SourceRef.anchor(document.documentAssetId(), document.contentId(),
                "定义", 1, "Historical document title", "Historical section title");
        var second = SourceRef.anchor(document.documentAssetId(), document.contentId(),
                "定义", 2, "Historical document title", "Historical section title");
        var missing = SourceRef.anchor(document.documentAssetId(), document.contentId(),
                "不存在", 1, "Historical document title", "Historical section title");
        var orphan = SourceRef.anchor(document.documentAssetId(), document.contentId(),
                "孤立", 1, "Historical document title", "Historical section title");
        var question = Question.choice("q_navigation", "SINGLE_CHOICE", new TextContent("Test question"), new TextContent("Analysis"), multiple ? List.of(first, second, missing, orphan) : List.of(second), new ChoicePayload(List.of(new ChoiceOption("opt_nav_a", new TextContent("Correct")),
                        new ChoiceOption("opt_nav_b", new TextContent("Incorrect")))), new ChoiceAnswerSpec(List.of("opt_nav_a")));
        var bank = new QuestionBank("qb_navigation", "Navigation bank", "2.0", List.of(), List.of(question), List.of());
        String json = new QuestionBankV2Codec().write(bank);
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
                    shell.filePane().getCenter()
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
            var layout = shell.filePane().getCenter();
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
            assertEquals("第 2 / 2 题", ((Label) shell.lookup("#question-position")).getText());
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
            assertEquals("第 2 / 2 题", ((Label) shell.lookup("#question-position")).getText());
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
            new io.quizforge.infrastructure.persistence.SqlitePracticeSessionRepository(practiceDb())
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

    private String archiveSourceHistory(NavigationBank sample) throws Exception {
        var first = sample.bank().questions().getFirst();
        var second = Question.choice("q_history_second", first.type(), new TextContent("Archived second question"), first.analysis(), first.sourceRefs(), new ChoicePayload(List.of(
                        new ChoiceOption("opt_second_a", new TextContent("Correct")),
                        new ChoiceOption("opt_second_b", new TextContent("Incorrect")))), new ChoiceAnswerSpec(List.of("opt_second_a")));
        var bank = new QuestionBank(sample.bank().assetId(), sample.bank().title(), "2.0", List.of(), List.of(first, second), List.of());
        fixture.write(sample.bankPath(), new QuestionBankV2Codec().write(bank));
        var runtime = fixture.context.getBean(io.quizforge.core.port.PracticeRuntimeProvider.class).open(fixture.alpha.id(), bank);
        for (int i = 0; i < 2; i++) {
            String prefix = i == 0 ? "opt_nav_" : "opt_second_";
            runtime.select(prefix + "b"); runtime.submit(); runtime.retry();
            runtime.select(prefix + "a"); runtime.submit();
            if (i == 0) runtime.next();
        }
        String id = runtime.sessionId();
        new io.quizforge.infrastructure.persistence.SqlitePracticeSessionRepository(practiceDb()).archive(id, java.time.Instant.now());
        return id;
    }

    private void openSourceHistory(NavigationBank sample, String archived) {
        shell.tabs().openPinned(fixture.alpha.id(), sample.bankPath());
        button("qbank-history-entry").fire(); shell.applyCss(); shell.layout();
        click(shell.lookup("#history-card-" + archived), 1); shell.applyCss(); shell.layout();
        assertNotNull(shell.lookup("#practice-history-detail"));
    }

    private List<String> practiceRows() throws Exception {
        List<String> rows = new ArrayList<>();
        try (var connection = practiceDb().openConnection(); var statement = connection.createStatement()) {
            for (String table : List.of("practice_session", "practice_session_question", "question_attempt")) {
                try (var result = statement.executeQuery("SELECT * FROM " + table + " ORDER BY id")) {
                    int columns = result.getMetaData().getColumnCount();
                    while (result.next()) {
                        StringBuilder row = new StringBuilder(table);
                        for (int i = 1; i <= columns; i++) row.append('|').append(result.getString(i));
                        rows.add(row.toString());
                    }
                }
            }
        }
        return rows;
    }

    private void forbidPracticeWrites() throws Exception {
        try (var connection = practiceDb().openConnection(); var statement = connection.createStatement()) {
            for (String table : List.of("practice_session", "practice_session_question", "question_attempt"))
                for (String operation : List.of("INSERT", "UPDATE", "DELETE")) statement.execute(
                        "CREATE TRIGGER no_" + table + "_" + operation + " BEFORE " + operation + " ON " + table
                                + " BEGIN SELECT RAISE(ABORT, 'History navigation must be read-only'); END");
        }
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
        if (node instanceof SplitPane split) return split.getItems().stream()
                .map(MainWorkspaceViewTest::text).collect(java.util.stream.Collectors.joining("\n"));
        StringBuilder out = new StringBuilder(node instanceof Labeled label ? label.getText() + "\n" : "");
        if (node instanceof Parent parent) parent.getChildrenUnmodifiable().forEach(child -> out.append(text(child)));
        return out.toString();
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

    @Test void mixedBankUsesSharedChoicePracticeAndKeepsEssayPreviewReadOnly() throws Exception {
        var choiceBank = new QuestionBankV2Codec().parse(QBankTestPackageBuilder.read(fixture.alphaRoot.resolve("题库/Java集合.qbank")));
        var essay = io.quizforge.infrastructure.testing.EssayTestBanks.bank().questions().getFirst();
        var mixed = new QuestionBank("qb_mixed_ui", "Mixed", List.of(),
                List.of(essay, choiceBank.questions().get(0), choiceBank.questions().get(1)), List.of());
        fixture.write("题库/Mixed.qbank", new QuestionBankV2Codec().write(mixed));
        fx(() -> {
            shell.refresh(); open("题库/Mixed.qbank");
            assertNotNull(shell.lookup("#authoring-question-card"));
            assertNull(shell.lookup("#submit-answer"));
            assertNotNull(shell.lookup("#qbank-history-entry"));
            button("authoring-question-2").fire();
            shell.applyCss(); shell.layout();
            assertTrue(shell.lookup("#practice-question-card") instanceof QuestionCardView, text(shell));
            assertEquals("第 2 / 3 题", ((Label)shell.lookup("#question-position")).getText());
            ((RadioButton)shell.lookup("#option-0")).fire();
            assertFalse(button("submit-answer").isDisabled()); button("submit-answer").fire();
            shell.applyCss(); shell.layout();
            assertEquals("回答正确", ((Label)shell.lookup("#question-result")).getText());
            assertNotNull(shell.lookup("#practice-retry"));
            button("next-question").fire();
            shell.applyCss(); shell.layout();
            assertEquals("第 3 / 3 题", ((Label)shell.lookup("#question-position")).getText());
            ((CheckBox)shell.lookup("#option-0")).fire(); ((CheckBox)shell.lookup("#option-1")).fire();
            button("submit-answer").fire(); button("next-question").fire();
            shell.applyCss(); shell.layout();
            assertEquals("共 2 题", ((Label)shell.lookup("#summary-total-count")).getText());
            assertEquals("2", ((Label)shell.lookup("#summary-correct-count")).getText());
            button("summary-previous").fire();
            shell.applyCss(); shell.layout();
            assertNotNull(shell.lookup("#practice-retry"));
            button("previous-question").fire(); button("previous-question").fire();
            shell.applyCss(); shell.layout();
            assertNotNull(shell.lookup("#authoring-question-card")); assertNull(shell.lookup("#submit-answer"));
            button("authoring-question-2").fire(); button("practice-retry").fire();
            ((RadioButton)shell.lookup("#option-1")).fire();
            open("题库/Mixed.qbank"); button("authoring-question-2").fire();
            shell.applyCss(); shell.layout();
            assertTrue(((RadioButton)shell.lookup("#option-1")).isSelected());
            assertFalse(button("submit-answer").isDisabled());
        });
    }
    @Test void essayGenericWindowSavesImageAndFormalSaveReopens() throws Exception {
        fixture.write("题库/Essay.qbank",new QuestionBankV2Codec().write(io.quizforge.infrastructure.testing.EssayTestBanks.bank()));
        Path image=localImage("png");
        fx(()->{
            shell.refresh();open("题库/Essay.qbank");button("file-mode-toggle").fire();shell.applyCss();shell.layout();
            editPrompt(window->{
                var resource=window.session().stage(image);
                window.bridge().loadContent(new RichContent(new RichDocument(List.of(
                        new ParagraphNode(List.of(new InlineTextNode("Before image"))),
                        new BlockImageNode(resource.id(),null,null)))));
            },true);
            ((TextField)shell.lookup("#essay-max-score")).setText("22.5");button("qbank-save").fire();shell.applyCss();shell.layout();
            assertEquals(FileMode.BROWSE,shell.filePane().mode());var bank=readEssay();var blocks=((RichContent)bank.questions().getFirst().prompt()).document().blocks();
            assertTrue(blocks.stream().anyMatch(BlockImageNode.class::isInstance));
            assertTrue(QuestionContentData.plainText(bank.questions().getFirst().prompt()).contains("Before image"));
            assertEquals(new java.math.BigDecimal("22.5"),bank.questions().getFirst().scoreSpec().defaultMaxScore());
            assertEquals(1,nodes(shell.lookup("#authoring-question-card"),javafx.scene.image.ImageView.class).size());
            shell.tabs().closeAll();shell.tabs().openPreview(fixture.alpha.id(),"题库/Essay.qbank");shell.applyCss();shell.layout();
            button("file-mode-toggle").fire();shell.applyCss();shell.layout();
            editPrompt(window->assertEquals(bank.questions().getFirst().prompt(),window.bridge().getContent()),false);
        });
    }
    @Test void essayEditorReplacementAndDeletionRemoveUnusedPackagedResources() throws Exception {
        prepareEssayImage();Path replacement=localImage("jpg");
        fx(()->{
            shell.refresh();open("题库/Essay.qbank");button("file-mode-toggle").fire();shell.applyCss();shell.layout();
            editPrompt(window->{
                var resource=window.session().stage(replacement);
                window.bridge().loadContent(new RichContent(new RichDocument(List.of(
                        new ParagraphNode(List.of(new InlineTextNode("Replaced"))),
                        new BlockImageNode(resource.id(),null,null)))));
            },true);
            button("qbank-save").fire();shell.applyCss();shell.layout();
            var bank=readEssay();assertEquals(1,bank.resources().size());assertEquals("image/jpeg",bank.resources().getFirst().mediaType());
            button("file-mode-toggle").fire();shell.applyCss();shell.layout();
            editPrompt(window->window.bridge().loadContent(new TextContent("Image removed")),true);
            button("qbank-save").fire();shell.applyCss();shell.layout();
            bank=readEssay();assertTrue(bank.resources().isEmpty());assertInstanceOf(TextContent.class,bank.questions().getFirst().prompt());
            assertEquals(java.util.Set.of("manifest.json","bank.json"),QBankTestPackageBuilder.entries(fixture.alphaRoot.resolve("题库/Essay.qbank")).keySet());
        });
    }
    @Test void essayGenericWindowHasFixedCanvasAndToolbarCommands() throws Exception {
        fixture.write("题库/Essay.qbank",new QuestionBankV2Codec().write(io.quizforge.infrastructure.testing.EssayTestBanks.bank()));
        fx(()->{
            shell.refresh();open("题库/Essay.qbank");button("file-mode-toggle").fire();shell.applyCss();shell.layout();
            editPrompt(window->{
                Stage modal=window.stage();
                assertEquals(QuestionContentLayout.QUESTION_CONTENT_WIDTH,
                        ((javafx.scene.layout.Region)((javafx.scene.layout.VBox)shell.lookup("#essay-prompt-preview")).getChildren().getFirst()).getMaxWidth());
                assertNotNull(modal.getScene().lookup("#canvas-editor-webview"));
                assertNotNull(modal.getScene().lookup("#canvas-editor-save"));
                var web=window.bridge().view().getEngine();
                assertEquals(816,((Number)web.executeScript("document.querySelector('#paper').clientWidth")).intValue());
                assertTrue(((Number)web.executeScript("document.querySelectorAll('[data-icon]').length")).intValue()>=20);
                window.bridge().loadContent(new RichContent(new RichDocument(List.of(
                        new HeadingNode(1,List.of(new InlineTextNode("Heading sample")),null)))));
                assertInstanceOf(HeadingNode.class,((RichContent)window.bridge().getContent()).document().blocks().getFirst());
            },true);
            assertNotNull(shell.lookup("#essay-prompt-preview"));
            assertNull(shell.lookup("#rich-content-editor"));
        });
    }
    @Test void essayGenericWindowCancelDiscardsPromptAndStagedImage() throws Exception {
        fixture.write("题库/Essay.qbank",new QuestionBankV2Codec().write(io.quizforge.infrastructure.testing.EssayTestBanks.bank()));
        Path picture=localImage("png");fx(()->{
            shell.refresh();open("题库/Essay.qbank");button("file-mode-toggle").fire();shell.applyCss();shell.layout();
            var before=readEssay();
            editPrompt(window->{window.session().stage(picture);window.bridge().loadContent(new TextContent("Cancelled"));},false);
            assertEquals(before.questions().getFirst().prompt(),readEssay().questions().getFirst().prompt());
            assertEquals(0,readEssay().resources().size());
            assertFalse(text(shell.lookup("#essay-prompt-preview")).contains("Cancelled"));
        });
    }
    @Test void richEditorInsertsResourceNodeAtParagraphEnd() throws Exception {
        Path image=localImage("png");fx(()->{
            var editor=standaloneEditor(new TextContent("Before"),List.of()).editor();editor.insertImage(image);
            var blocks=((RichContent)editor.getContent()).document().blocks();
            assertTrue(blocks.stream().anyMatch(BlockImageNode.class::isInstance));
            assertTrue(QuestionContentData.plainText(editor.getContent()).contains("Before"));
        });
    }
    @Test void richEditorTextLoadPlainSaveAndChangeCallback() throws Exception {
        fx(()->{
            var sample=standaloneEditor(new TextContent("First paragraph.\n\n第二段\nNext line."),List.of());var editor=sample.editor();
            assertEquals(new TextContent("First paragraph.\n\n第二段\nNext line."),editor.getContent());
            editor.setContent(new TextContent("Changed 中文 text"));editor.command("paragraph",null);
            assertEquals(new TextContent("Changed 中文 text"),sample.model().bank().questions().getFirst().prompt());
        });
    }
    @Test void essayAuthoringScoreWithoutWordLimitsOrPracticePersistence() throws Exception {
        prepareEssayImage();fx(()->{
            shell.refresh();open("题库/Essay.qbank");assertNull(shell.lookup("#question-practice"));assertNull(shell.lookup("#submit-answer"));assertNull(shell.lookup("#qbank-history-entry"));
            assertNull(new io.quizforge.infrastructure.persistence.SqlitePracticeSessionRepository(practiceDb()).findActiveByQuestionBankAssetId("qb_essay").orElse(null));
            button("file-mode-toggle").fire();shell.applyCss();shell.layout();
            assertNull(shell.lookup("#rich-content-editor"));assertNotNull(shell.lookup("#essay-prompt-preview"));
            ((TextField)shell.lookup("#essay-max-score")).setText("19.75");
            assertNull(shell.lookup("#essay-min-words"));assertNull(shell.lookup("#essay-max-words"));
            button("qbank-save").fire();shell.applyCss();shell.layout();
            var question=readEssay().questions().getFirst();assertEquals(new java.math.BigDecimal("19.75"),question.scoreSpec().defaultMaxScore());
            assertTrue(text(shell.lookup("#authoring-question-card")).contains("19.75"));
            assertFalse(((Label)shell.lookup("#authoring-essay-metadata")).getText().contains("字数"));
        });
    }
    @Test void existingEssayWordLimitsAreIgnoredAndRemovedByEditorSave() throws Exception {
        var expected=io.quizforge.infrastructure.testing.EssayTestBanks.bank();
        fixture.write("题库/Essay.qbank",new QuestionBankV2Codec().write(expected));
        Path file=fixture.alphaRoot.resolve("题库/Essay.qbank");
        var entries=QBankTestPackageBuilder.entries(file);var json=new com.fasterxml.jackson.databind.ObjectMapper();
        var root=json.readTree(entries.get("bank.json"));
        ((com.fasterxml.jackson.databind.node.ObjectNode)root.path("questions").get(0).path("payload")).put("minWords",160).put("maxWords",200);
        entries.put("bank.json",json.writeValueAsBytes(root));QBankTestPackageBuilder.zip(file,entries);
        fx(()->{
            shell.refresh();open("题库/Essay.qbank");shell.applyCss();shell.layout();
            String metadata=((Label)shell.lookup("#authoring-essay-metadata")).getText();
            assertTrue(metadata.contains("分值："));assertFalse(metadata.contains("字数"));
            button("file-mode-toggle").fire();shell.applyCss();shell.layout();
            assertNull(shell.lookup("#essay-min-words"));assertNull(shell.lookup("#essay-max-words"));
            button("qbank-save").fire();shell.applyCss();shell.layout();
            assertEquals(expected,readEssay());
            var saved=json.readTree(QBankTestPackageBuilder.entries(file).get("bank.json"));
            assertFalse(saved.path("questions").get(0).path("payload").has("minWords"));
            assertFalse(saved.path("questions").get(0).path("payload").has("maxWords"));
            open("题库/Essay.qbank");shell.applyCss();shell.layout();
            assertFalse(((Label)shell.lookup("#authoring-essay-metadata")).getText().contains("字数"));
        });
    }
    @Test void essayCreateFromEmptyDraftUsesGenericEditorAndDefaultScore() throws Exception {
        fx(()->{
            open("空草稿/新题库.qbank");button("file-mode-toggle").fire();shell.applyCss();shell.layout();((MenuButton)shell.lookup("#qbank-add-question")).getItems().get(2).fire();
            assertEquals("1",((TextField)shell.lookup("#essay-max-score")).getText());
            editPrompt(window->window.bridge().loadContent(new TextContent("New author prompt")),true);
            button("qbank-save").fire();shell.applyCss();shell.layout();var bank=new io.quizforge.infrastructure.filesystem.qbank.QBankPackageReader().read(fixture.alphaRoot.resolve("空草稿/新题库.qbank"));
            assertEquals("ESSAY",bank.questions().getFirst().type());assertEquals(new TextContent("New author prompt"),bank.questions().getFirst().prompt());
            assertTrue(text(shell.lookup("#authoring-question-card")).contains("New author prompt"));
        });
    }
    @Test void richEditorLoadsExistingBlockAndInlineImageModelsWithoutHtml() throws Exception {
        var bank=prepareEssayImage();fx(()->{
            var original=bank.questions().getFirst().prompt();var sample=standaloneEditor(original,bank.resources());
            assertEquals(original,sample.editor().getContent());
            var inline=new RichContent(new RichDocument(List.of(new ParagraphNode(List.of(new InlineTextNode("Before"),new InlineImageNode(bank.resources().getFirst().id(),null),new LineBreakNode(),new InlineTextNode("After"))))));
            sample.editor().setContent(inline);assertEquals(inline,sample.editor().getContent());
            String encoded=new QuestionBankV2Codec().write(sample.model().bank());assertFalse(encoded.contains("<img"));assertFalse(encoded.contains("data:image"));
        });
    }
    @Test void essayRendererMissingResourceDoesNotBreakContent() throws Exception {
        var bank=prepareEssayImage();fx(()->{
            var node=QuestionContentRenderer.render(bank.questions().getFirst().prompt(),bank.resources(),io.quizforge.core.port.QuestionResourceInput.NONE,"missing-");
            assertTrue(text(node).contains("Directions."));assertTrue(text(node).contains("图片资源缺失或无法读取"));assertTrue(text(node).contains("Write 160–200 words."));
            assertTrue(nodes(node,javafx.scene.image.ImageView.class).isEmpty());
            assertEquals("plain text",text(QuestionContentRenderer.render(new TextContent("plain text"),List.of(),io.quizforge.core.port.QuestionResourceInput.NONE,"plain-")).trim());
        });
    }
    @Test void richEditorTreatsMarkupAsTextAndRejectsUnknownEditorJson() throws Exception {
        fx(()->{
            String markup="<img src='https://example.invalid/a' onerror='window.attacked=1'> & 中文";
            var editor=standaloneEditor(new TextContent(markup),List.of()).editor();assertEquals(new TextContent(markup),editor.getContent());
            assertEquals(0,((Number)web(editor).executeScript("document.querySelectorAll('img').length")).intValue());
            assertEquals("undefined",web(editor).executeScript("typeof window.attacked"));
            assertThrows(IllegalArgumentException.class,()->RichContentEditorAdapter.fromEditorJson(
                    "{\"type\":\"doc\",\"content\":[{\"type\":\"table\"}]}",Set.of()));
        });
    }
    @Test void richEditorDoesNotSerializeHtml() throws Exception {
        fx(()->{
            var editor=standaloneEditor(new TextContent("<script>window.attacked=1</script>"),List.of()).editor();
            assertEquals(new TextContent("<script>window.attacked=1</script>"),editor.getContent());
            assertEquals("undefined",web(editor).executeScript("typeof window.attacked"));
        });
    }
    @Test void richEditorDeleteKeepsOtherQuestionSharedResource() throws Exception {
        var bank=prepareEssayImage();fx(()->{
            var sample=standaloneEditor(bank.questions().getFirst().prompt(),bank.resources());sample.model().duplicateQuestion(0);
            selectImage(sample.editor());sample.editor().deleteSelectedImage();assertEquals(1,sample.model().bank().resources().size());
            assertFalse(QuestionContentData.imageIds(sample.model().bank().questions().get(1).prompt()).isEmpty());
        });
    }
    @Test void richEditorReplacingWithoutSelectionDoesNotLeakResource() throws Exception {
        Path image=localImage("png");fx(()->{
            var sample=standaloneEditor(new TextContent("No image selected"),List.of());
            assertThrows(IllegalStateException.class,()->sample.editor().replaceSelectedImage(image));assertTrue(sample.model().bank().resources().isEmpty());
        });
    }
    @Test void richEditorUnsupportedDomainIsRejectedWithoutChangingSource() throws Exception {
        fx(()->{
            var editor=standaloneEditor(new TextContent("Known"),List.of()).editor();
            assertThrows(RuntimeException.class,()->editor.setContent(new RichContent(new RichDocument(List.of(new BlockMathNode("x"))))));
            assertEquals(new TextContent("Known"),editor.getContent());
        });
    }
    private record RichEditorFixture(RichContentEditor editor,QuestionBankEditorModel model) { }
    private RichEditorFixture standaloneEditor(QuestionContent content,List<QBankResource> resources) throws Exception {
        var q=io.quizforge.infrastructure.testing.EssayTestBanks.essay("q_generic",content,new EssayPayload(null),null);
        var model=new QuestionBankEditorModel(new QuestionBank("qb_generic","Generic",List.of(),List.of(q),resources));
        byte[] initialImage=io.quizforge.infrastructure.testing.EssayTestBanks.image("png");
        var pending=new java.util.HashMap<String,io.quizforge.infrastructure.filesystem.qbank.QBankImageImporter.ImportedImage>();
        var editor=new RichContentEditor(content,value->model.setPrompt(0,value),path->{var image=new io.quizforge.infrastructure.filesystem.qbank.QBankImageImporter().read(path);model.addResource(image.resource());pending.put(image.resource().id(),image);return image.resource();},()->model.bank().resources(),resource->{
            var imported=pending.get(resource.id());return imported==null?new java.io.ByteArrayInputStream(initialImage):imported.open();
        },message->{throw new IllegalStateException(message);});
        stage.setScene(new Scene(editor,800,500));stage.show();awaitEditor(editor);return new RichEditorFixture(editor,model);
    }
    private void editPrompt(Consumer<CanvasEditorWindow> action,boolean save) {
        AtomicReference<Throwable> failure=new AtomicReference<>();
        Platform.runLater(()->{
            Stage dialog=null;
            try {
                dialog=Window.getWindows().stream().filter(w->w instanceof Stage stage && stage.isShowing()
                        && "编辑题干".equals(stage.getTitle())).map(w->(Stage)w).findFirst().orElseThrow();
                assertEquals(javafx.stage.Modality.WINDOW_MODAL,dialog.getModality());
                var window=(CanvasEditorWindow)dialog.getProperties().get("quizforge.canvas.window");
                assertNotNull(window);for(int i=0;i<160 && !window.bridge().ready();i++)pulse(50);
                assertTrue(window.bridge().ready(),"Canvas Editor WebView must load");action.accept(window);
                var button=(Button)dialog.getScene().lookup(save?"#canvas-editor-save":"#canvas-editor-back");
                assertNotNull(button);button.fire();
            }catch(Throwable error){failure.set(error);if(dialog!=null)dialog.close();}
        });
        button("essay-edit-prompt").fire();
        if(failure.get()!=null)throw new AssertionError("Generic editor window interaction failed",failure.get());
    }
    private static void awaitEditor(RichContentEditor editor){for(int i=0;i<160 && !editor.ready();i++)pulse(50);assertTrue(editor.ready(),"Bundled WebView editor must load");}
    private static javafx.scene.web.WebEngine web(RichContentEditor editor){return ((WebViewRichContentEngine)editor.engine()).webView().getEngine();}
    private static void selectImage(RichContentEditor editor){web(editor).executeScript("document.querySelector('[data-resource-id]').dispatchEvent(new MouseEvent('click',{bubbles:true}))");}
    private Path localImage(String format) throws Exception {Path path=temp.resolve("local-image."+format);Files.write(path,io.quizforge.infrastructure.testing.EssayTestBanks.image(format));return path;}
    private QuestionBank readEssay(){return new io.quizforge.infrastructure.filesystem.qbank.QBankPackageReader().read(fixture.alphaRoot.resolve("题库/Essay.qbank"));}
    private QuestionBank prepareEssayImage() throws Exception {
        var original=io.quizforge.infrastructure.testing.EssayTestBanks.bank();Path local=localImage("png");
        var image=new io.quizforge.infrastructure.filesystem.qbank.QBankImageImporter().read(local);var model=new QuestionBankEditorModel(original);
        model.addResource(image.resource());model.setPrompt(0,io.quizforge.infrastructure.testing.EssayTestBanks.prompt(image.resource().id()));
        Path file=fixture.alphaRoot.resolve("题库/Essay.qbank");Files.createDirectories(file.getParent());
        return new io.quizforge.infrastructure.filesystem.qbank.QBankPackageWriter().write(file,model.bank(),r->image.open());
    }
    private static <T extends Node> List<T> nodes(Node root,Class<T> type) {
        var found=new ArrayList<T>();if(type.isInstance(root))found.add(type.cast(root));
        if(root instanceof ScrollPane pane)found.addAll(nodes(pane.getContent(),type));
        else if(root instanceof Parent parent)parent.getChildrenUnmodifiable().forEach(child->found.addAll(nodes(child,type)));
        return found;
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
