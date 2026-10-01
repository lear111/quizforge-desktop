package io.quizforge.desktop.ui.shell;

import io.quizforge.core.question.content.BlockImageNode;
import io.quizforge.core.question.content.DocumentContent;
import io.quizforge.core.question.content.HeadingNode;
import io.quizforge.core.question.content.InlineTextNode;
import io.quizforge.core.question.content.ParagraphNode;
import io.quizforge.core.question.content.QuestionContentData;
import io.quizforge.core.question.content.RichContent;
import io.quizforge.core.question.content.RichDocument;
import io.quizforge.core.question.content.TextContent;
import io.quizforge.core.question.model.QuestionBank;
import io.quizforge.desktop.ui.content.QuestionContentLayout;
import io.quizforge.desktop.ui.content.QuestionContentRenderer;
import io.quizforge.desktop.ui.content.document.canvas.CanvasEditorTestDriver;
import io.quizforge.desktop.ui.file.FileMode;
import io.quizforge.desktop.ui.question.objective.choice.ChoiceCardView;
import io.quizforge.infrastructure.filesystem.qbank.QuestionBankV2Codec;
import io.quizforge.infrastructure.testing.QBankTestPackageBuilder;
import java.nio.file.Path;
import java.util.List;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class EssayEditorUiTest extends WorkspaceUiTestSupport {

    @Test void richChoiceOptionsInMixedBankRenderWithoutTextCast() throws Exception {
        var codec = new QuestionBankV2Codec();
        var original = codec.parse(QBankTestPackageBuilder.read(fixture.alphaRoot.resolve("题库/Java集合.qbank")))
                .questions().getFirst();
        var rich = new RichContent(new RichDocument(List.of(new ParagraphNode(List.of(
                new InlineTextNode("Rich option", List.of(io.quizforge.core.question.content.TextMark.BOLD)))))));
        var options = new java.util.ArrayList<>(original.choicePayload().options());
        options.set(0, new io.quizforge.core.question.type.objective.choice.ChoiceOption(options.getFirst().id(), rich));
        var choice = io.quizforge.core.question.model.Question.choice(original.id(), original.type(), original.prompt(),
                original.analysis(), original.sourceRefs(),
                new io.quizforge.core.question.type.objective.choice.ChoicePayload(options), original.choiceAnswerSpec());
        var essay = io.quizforge.infrastructure.testing.EssayTestBanks.bank().questions().getFirst();
        fixture.write("题库/MixedRich.qbank", codec.write(new QuestionBank("qb_mixed_rich", "Mixed rich",
                List.of(), List.of(choice, essay), List.of())));
        byte[] before = java.nio.file.Files.readAllBytes(fixture.alphaRoot.resolve("题库/MixedRich.qbank"));
        fx(() -> {
            shell.refresh(); open("题库/MixedRich.qbank");
            // The durable runtime starts at its supported essay. Navigate to the
            // RICH choice preview, which intentionally has no text-only answer controls.
            assertNotNull(shell.lookup("#authoring-essay-metadata"));
            button("authoring-previous-question").fire();
            shell.applyCss(); shell.layout();
            assertNotNull(shell.lookup("#authoring-question-card"));
            var rendered = shell.lookup("#authoring-option-0-rich-content");
            assertNotNull(rendered, "A RICH option must use the shared content renderer");
            assertEquals("Rich option", nodes(rendered, javafx.scene.text.Text.class).stream()
                    .map(javafx.scene.text.Text::getText).collect(java.util.stream.Collectors.joining()));
            button("authoring-next-question").fire();
            shell.applyCss(); shell.layout();
            assertNotNull(shell.lookup("#authoring-essay-metadata"));
            button("authoring-previous-question").fire();
            shell.applyCss(); shell.layout();
            assertNotNull(shell.lookup("#authoring-option-0-rich-content"));
        });
        assertArrayEquals(before, java.nio.file.Files.readAllBytes(fixture.alphaRoot.resolve("题库/MixedRich.qbank")));
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
            assertTrue(shell.lookup("#practice-question-card") instanceof ChoiceCardView, text(shell));
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
            assertEquals("共 3 题", ((Label)shell.lookup("#summary-total-count")).getText());
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
            assertEquals(FileMode.BROWSE,shell.filePane().mode());var bank=readEssay();assertInstanceOf(DocumentContent.class,bank.questions().getFirst().prompt());
            assertTrue(nativePrompt(bank,"题库/Essay.qbank").contains("data:image/png;base64,"));
            assertTrue(QuestionContentData.plainText(bank.questions().getFirst().prompt()).contains("Before image"));
            assertEquals(new java.math.BigDecimal("22.5"),bank.questions().getFirst().scoreSpec().defaultMaxScore());
            assertNotNull(shell.lookup("#authoring-prompt-native-document"));
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
            var bank=readEssay();assertEquals(1,bank.resources().size());assertEquals(CanvasEditorTestDriver.mediaType(),bank.resources().getFirst().mediaType());assertTrue(nativePrompt(bank,"题库/Essay.qbank").contains("data:image/jpeg;base64,"));
            button("file-mode-toggle").fire();shell.applyCss();shell.layout();
            editPrompt(window->window.bridge().loadContent(new TextContent("Image removed")),true);
            button("qbank-save").fire();shell.applyCss();shell.layout();
            bank=readEssay();assertEquals(1,bank.resources().size());assertInstanceOf(DocumentContent.class,bank.questions().getFirst().prompt());assertFalse(nativePrompt(bank,"题库/Essay.qbank").contains("data:image/"));
            assertEquals(3,QBankTestPackageBuilder.entries(fixture.alphaRoot.resolve("题库/Essay.qbank")).size());
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
                assertTrue(window.session().document((DocumentContent)window.bridge().getContent()).contains("Heading sample"));
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

    @Test void essayAuthoringScoreKeepsPracticePersistenceWithoutWordLimits() throws Exception {
        prepareEssayImage();fx(()->{
            shell.refresh();open("题库/Essay.qbank");assertNull(shell.lookup("#question-practice"));assertNull(shell.lookup("#submit-answer"));assertNotNull(shell.lookup("#qbank-history-entry"));
            assertNotNull(new io.quizforge.infrastructure.persistence.practice.SqlitePracticeSessionRepository(practiceDb()).findActiveByQuestionBankAssetId("qb_essay").orElse(null));
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
            assertEquals("ESSAY",bank.questions().getFirst().type());assertInstanceOf(DocumentContent.class,bank.questions().getFirst().prompt());assertEquals("New author prompt",QuestionContentData.plainText(bank.questions().getFirst().prompt()));
            assertNotNull(shell.lookup("#authoring-prompt-native-document"));
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
}
