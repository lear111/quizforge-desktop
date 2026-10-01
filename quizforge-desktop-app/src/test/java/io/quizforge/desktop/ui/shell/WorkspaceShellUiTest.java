package io.quizforge.desktop.ui.shell;

import io.quizforge.core.question.model.QuestionBank;
import io.quizforge.core.workspace.model.WorkspaceFileEntry;
import io.quizforge.core.workspace.model.WorkspaceFileKind;
import io.quizforge.desktop.ui.file.FileMode;
import io.quizforge.desktop.ui.shared.UiTheme;
import io.quizforge.desktop.ui.workspace.WorkspaceFileTreeView;
import io.quizforge.desktop.ui.workspace.WorkspaceHistory;
import io.quizforge.desktop.ui.workspace.WorkspaceSwitcher;
import io.quizforge.infrastructure.filesystem.qbank.QuestionBankV2Codec;
import io.quizforge.infrastructure.testing.QBankTestPackageBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class WorkspaceShellUiTest extends WorkspaceUiTestSupport {

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
            var recent = new ArrayList<io.quizforge.core.workspace.model.Workspace>();
            for (int index = 0; index < 12; index++) {
                recent.add(new io.quizforge.core.workspace.model.Workspace(
                        io.quizforge.core.workspace.model.WorkspaceId.newId(), "Workspace " + index,
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
            var tree = new WorkspaceFileTreeView((entry, pinned) -> {}, new WorkspaceFileTreeView.FileActions() {
                public void createFolder(String path) { calls.add("folder:" + path); }
                public void createFile(String path, io.quizforge.core.workspace.model.WorkspaceFileType type) {
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
        var codec = new QuestionBankV2Codec();
        var original = codec.parse(QBankTestPackageBuilder.read(fixture.alphaRoot.resolve("题库/Java集合.qbank")));
        fixture.write("Root.qbank", codec.write(new QuestionBank("qb_root", original.title(),
                original.schemaVersion(), original.stimuli(), original.questions(), original.resources())));
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
}
