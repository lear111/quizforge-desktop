package io.quizforge.desktop.ui.shell;

import io.quizforge.core.workspace.model.WorkspaceFileKind;
import io.quizforge.desktop.ui.file.FilePane;
import io.quizforge.desktop.ui.shared.UiTheme;
import io.quizforge.desktop.ui.workspace.WorkspaceTab;
import io.quizforge.infrastructure.filesystem.markdown.RegisteredMarkdownCodec;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class MarkdownFileUiTest extends WorkspaceUiTestSupport {

    @Test void topRowSeparatorsFollowTheSidebarAndMarkdownOutline() throws Exception {
        fx(() -> {
            open("Java/Java集合.md");
            pulse(100);
            var outer = (SplitPane) shell.getCenter();
            var markdown = (SplitPane) shell.filePane().getCenter();
            var outline = (javafx.scene.layout.Region) markdown.getItems().get(1);
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
            assertEquals(3, shell.lookup("#file-header").lookupAll(".button").size());
            assertTrue(button("practice-draft-toggle").isVisible());
            assertNull(shell.lookup("#asset-info-button"));
            assertFalse(text(shell).contains("doc_java"));
            assertFalse(text(shell).contains("qfd:v1:"));
        });
    }

    @Test void markdownOutlineFillsTheHeaderHeightAndItsDividerCanMove() throws Exception {
        fx(() -> {
            open("Java/Java集合.md");
            shell.applyCss(); shell.layout();
            FilePane pane = shell.filePane();
            var layout = (SplitPane) pane.getCenter();
            var reader = (BorderPane) layout.getItems().getFirst();
            var outline = (javafx.scene.layout.Region) layout.getItems().get(1);
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

    @Test void emptyLegacyDocumentHasNoGenerationEntry() throws Exception {
        fx(() -> {
            open("空草稿/新文档.md");
            assertTrue(shell.filePane().currentFile().draft());
            assertTrue(text(shell).contains("此文档暂无内容"));
            assertEquals(WorkspaceFileKind.INVALID_REGISTERED_MARKDOWN, fixture.files.open(fixture.alpha.id(), "空草稿/新文档.md").entry().kind());
            assertNull(shell.lookup("#empty-asset-ai"));
            assertNull(shell.lookup("#asset-info-button"));
        });
    }

    @Test void populatedStandardDocumentHasNoAiAction() throws Exception {
        fx(() -> { open("Java/Java集合.md"); assertNull(shell.lookup("#empty-asset-ai")); });
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
}
