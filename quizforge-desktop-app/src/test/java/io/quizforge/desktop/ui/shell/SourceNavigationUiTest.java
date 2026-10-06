package io.quizforge.desktop.ui.shell;

import io.quizforge.core.document.navigation.QuizForgeNavigationLink;
import io.quizforge.core.document.navigation.QuizForgeNavigationLinkCodec;
import io.quizforge.core.document.registered.QuizForgeReference;
import io.quizforge.core.document.registered.QuizForgeReferenceCodec;
import io.quizforge.core.port.WorkspaceAssetScanner;
import io.quizforge.core.question.source.QuestionBankReferenceResolver;
import io.quizforge.core.question.source.QuestionSourceLinkService;
import io.quizforge.core.question.content.QuestionText;
import io.quizforge.core.workspace.model.WorkspaceFileKind;
import io.quizforge.desktop.ui.file.FileMode;
import io.quizforge.desktop.ui.markdown.MarkdownOutline;
import io.quizforge.desktop.ui.markdown.SafeMarkdownPreview;
import io.quizforge.desktop.ui.question.source.QuestionSourceNavigationAdapter;
import io.quizforge.desktop.ui.shared.UiTheme;
import io.quizforge.desktop.ui.workspace.WorkspaceNavigationService;
import io.quizforge.infrastructure.filesystem.markdown.LegacyMarkdownCodec;
import io.quizforge.infrastructure.filesystem.markdown.RegisteredMarkdownCodec;
import io.quizforge.infrastructure.filesystem.qbank.QuestionBankV2Codec;
import io.quizforge.infrastructure.testing.QBankTestPackageBuilder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class SourceNavigationUiTest extends WorkspaceUiTestSupport {

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
            assertEquals(new LegacyMarkdownCodec().parseLegacy(markdown)
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
            assertEquals(WorkspaceFileKind.REGISTERED_MARKDOWN, registered.kind());
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
            assertEquals(WorkspaceFileKind.REGISTERED_MARKDOWN, shell.filePane().currentFile().kind());
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
            assertEquals(WorkspaceFileKind.REGISTERED_MARKDOWN, shell.filePane().currentFile().kind());
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
            submitAnswer();
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

    @Test void recreatedShellRestoresSubmittedFeedbackSourcesAndOutlineFromAttempt() throws Exception {
        fx(() -> {
            open("题库/Java集合.qbank");
            ((RadioButton) shell.lookup("#option-1")).fire();
            submitAnswer();
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
}
