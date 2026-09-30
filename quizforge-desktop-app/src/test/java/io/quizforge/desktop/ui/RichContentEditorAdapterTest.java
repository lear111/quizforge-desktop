package io.quizforge.desktop.ui;

import io.quizforge.core.question.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RichContentEditorAdapterTest {
    private static final Set<String> IMAGE=Set.of("res_image");

    private static QuestionContent roundTrip(QuestionContent content) {
        return RichContentEditorAdapter.fromEditorJson(RichContentEditorAdapter.toEditorJson(content),IMAGE);
    }
    private static RichContent rich(BlockNode... blocks) {
        return new RichContent(new RichDocument(List.of(blocks)));
    }

    @Test void plainTextRoundTripsAndNormalizes() {
        var text=new TextContent("First paragraph.\nSecond line.\n\nAnother paragraph.");
        assertEquals(text,roundTrip(text));
        assertEquals(new TextContent("Plain"),roundTrip(rich(new ParagraphNode(List.of(new InlineTextNode("Plain"))))));
    }
    @Test void marksRemainRichAndRoundTrip() {
        for(TextMark mark:TextMark.values()) {
            var content=rich(new ParagraphNode(List.of(new InlineTextNode("Marked",List.of(mark)))));
            assertEquals(content,roundTrip(content));
            assertInstanceOf(RichContent.class,roundTrip(content));
        }
    }
    @Test void headingsAndAlignmentRoundTrip() {
        for(int level=1;level<=3;level++)for(TextAlignment alignment:TextAlignment.values()) {
            var content=rich(new HeadingNode(level,List.of(new InlineTextNode("Heading")),alignment));
            assertEquals(content,roundTrip(content));
        }
        var centered=rich(new ParagraphNode(List.of(new InlineTextNode("Center")),TextAlignment.CENTER));
        assertEquals(centered,roundTrip(centered));
    }
    @Test void listsAndQuoteRoundTrip() {
        var item=new ListItemNode(List.of(new ParagraphNode(List.of(new InlineTextNode("Item")))));
        var content=rich(new BulletListNode(List.of(item)),new OrderedListNode(3,List.of(item)),
                new BlockQuoteNode(List.of(new ParagraphNode(List.of(new InlineTextNode("Quote"))))));
        assertEquals(content,roundTrip(content));
    }
    @Test void linkRoundTrips() {
        var content=rich(new ParagraphNode(List.of(new LinkNode("https://example.org/source",
                List.of(new InlineTextNode("source",List.of(TextMark.BOLD)))))));
        assertEquals(content,roundTrip(content));
    }
    @Test void imageWidthAlignmentAndInlineImageRoundTrip() {
        var content=rich(new ParagraphNode(List.of(new InlineTextNode("Before"),
                new InlineImageNode("res_image","Inline"))),
                new BlockImageNode("res_image","Alt","Caption",50,TextAlignment.CENTER));
        assertEquals(content,roundTrip(content));
        assertFalse(RichContentEditorAdapter.toEditorJson(content).contains("file:"));
    }
    @Test void unknownNodesMarksAndResourcesAreRejected() {
        assertThrows(IllegalArgumentException.class,()->RichContentEditorAdapter.fromEditorJson(
                "{\"type\":\"doc\",\"content\":[{\"type\":\"table\"}]}",Set.of()));
        assertThrows(IllegalArgumentException.class,()->RichContentEditorAdapter.fromEditorJson(
                "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"a\",\"marks\":[{\"type\":\"code\"}]}]}]}",Set.of()));
        assertThrows(IllegalArgumentException.class,()->RichContentEditorAdapter.fromEditorJson(
                "{\"type\":\"doc\",\"content\":[{\"type\":\"image\",\"attrs\":{\"resourceId\":\"res_unknown\"}}]}",Set.of()));
    }
    @Test void htmlLookingTextStaysText() {
        String value="<img src='https://example.org/x' onerror='bad()'> & 中文";
        assertEquals(new TextContent(value),roundTrip(new TextContent(value)));
    }
}
