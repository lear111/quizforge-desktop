package io.quizforge.desktop.ui.content.document.canvas;

import io.quizforge.core.question.content.BlockImageNode;
import io.quizforge.core.question.content.BlockMathNode;
import io.quizforge.core.question.content.HeadingNode;
import io.quizforge.core.question.content.InlineImageNode;
import io.quizforge.core.question.content.InlineMathNode;
import io.quizforge.core.question.content.InlineTextNode;
import io.quizforge.core.question.content.LineBreakNode;
import io.quizforge.core.question.content.LinkNode;
import io.quizforge.core.question.content.ListItemNode;
import io.quizforge.core.question.content.OrderedListNode;
import io.quizforge.core.question.content.ParagraphNode;
import io.quizforge.core.question.content.QuestionContent;
import io.quizforge.core.question.content.QuestionContentData;
import io.quizforge.core.question.content.RichContent;
import io.quizforge.core.question.content.RichDocument;
import io.quizforge.core.question.content.TextAlignment;
import io.quizforge.core.question.content.TextContent;
import io.quizforge.core.question.content.TextMark;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CanvasEditorAdapterTest {
    private static final Map<String,CanvasEditorAdapter.ImageData> IMAGES=Map.of("res_image",
            new CanvasEditorAdapter.ImageData("data:image/png;base64,AAAA",400,200));
    private static QuestionContent roundTrip(QuestionContent content) {
        return CanvasEditorAdapter.fromCanvasJson(CanvasEditorAdapter.toCanvasJson(content,IMAGES),Set.of("res_image"));
    }
    @Test void plainTextAndBasicMarksRoundTrip() {
        assertEquals(new TextContent("Hello"),roundTrip(new TextContent("Hello")));
        assertEquals(new TextContent("First\nSecond\n\nThird"),roundTrip(new TextContent("First\nSecond\n\nThird")));
        var rich=new RichContent(new RichDocument(List.of(new ParagraphNode(List.of(
                new InlineTextNode("Bold",List.of(TextMark.BOLD)),new InlineTextNode(" italic",List.of(TextMark.ITALIC)))))));
        assertEquals(rich,roundTrip(rich));
    }
    @Test void inlineImageMathAndLinkRemainPortable() {
        var rich=new RichContent(new RichDocument(List.of(new ParagraphNode(List.of(
                new InlineTextNode("Before"),new InlineImageNode("res_image","inline"),
                new InlineMathNode("x^2"),new LineBreakNode(),
                new LinkNode("https://example.org",List.of(new InlineTextNode("source"))))))));
        assertEquals(rich,roundTrip(rich));
    }
    @Test void headingAlignmentListAndLatexRoundTrip() {
        var item=new ListItemNode(List.of(new ParagraphNode(List.of(new InlineTextNode("One")))));
        var rich=new RichContent(new RichDocument(List.of(
                new HeadingNode(1,List.of(new InlineTextNode("Heading")),null),
                new ParagraphNode(List.of(new InlineTextNode("Centered")),TextAlignment.CENTER),
                new OrderedListNode(1,List.of(item)),new BlockMathNode("\\frac{-b\\pm\\sqrt{b^2-4ac}}{2a}"))));
        assertEquals(rich,roundTrip(rich));
    }
    @Test void imageKeepsResourceIdWithoutPersistingDataUrl() {
        var rich=new RichContent(new RichDocument(List.of(new BlockImageNode("res_image","Alt",null,50,TextAlignment.CENTER))));
        String runtime=CanvasEditorAdapter.toCanvasJson(rich,IMAGES);
        assertTrue(runtime.contains("data:image/png;base64"));
        assertEquals(rich,CanvasEditorAdapter.fromCanvasJson(runtime,Set.of("res_image")));
        assertFalse(QuestionContentData.encode(roundTrip(rich)).toString().contains("base64"));
        assertThrows(IllegalArgumentException.class,()->CanvasEditorAdapter.fromCanvasJson(runtime,Set.of()));
        assertThrows(IllegalArgumentException.class,()->CanvasEditorAdapter.fromCanvasJson(
                "{\"main\":[{\"value\":\"Red\",\"color\":\"#ff0000\"}]}",Set.of()));
    }
    @Test void inlineImageResizeCannotBeSilentlyDiscarded() {
        var rich=new RichContent(new RichDocument(List.of(new ParagraphNode(List.of(
                new InlineTextNode("A"),new InlineImageNode("res_image",null))))));
        String runtime=CanvasEditorAdapter.toCanvasJson(rich,IMAGES);
        assertTrue(runtime.contains("\"width\":400"));
        assertThrows(IllegalArgumentException.class,()->CanvasEditorAdapter.fromCanvasJson(
                runtime.replace("\"width\":400","\"width\":200"),Set.of("res_image")));
    }
    @Test void unsupportedSuperscriptCannotBeSilentlyDiscarded() {
        assertThrows(IllegalArgumentException.class,()->CanvasEditorAdapter.fromCanvasJson(
                "{\"main\":[{\"value\":\"2\",\"superscript\":true}]}",Set.of()));
        assertThrows(IllegalArgumentException.class,()->CanvasEditorAdapter.fromCanvasJson(
                "{\"main\":[{\"value\":\"2\",\"subscript\":true}]}",Set.of()));
    }
}
