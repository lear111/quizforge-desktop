package io.quizforge.core.document.navigation;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MarkdownNavigationLinkCodecTest {
    private final MarkdownNavigationLinkCodec markdown = new MarkdownNavigationLinkCodec();
    private final QuizForgeNavigationLinkCodec uri = new QuizForgeNavigationLinkCodec();

    @Test void documentHeadingAndAnchorUseReadableNamesWithoutOccurrence() {
        assertEquals("[Java集合](quizforge://asset/doc_a)",
                markdown.format("Java/Java集合.md", QuizForgeNavigationLink.asset("doc_a")));
        var heading = QuizForgeNavigationLink.heading("doc_a", "示例", 2);
        assertEquals("[Java集合 · 示例](" + uri.encode(heading) + ")",
                markdown.format("Java/Java集合.md", heading));
        var anchor = QuizForgeNavigationLink.anchor("doc_a", "扩容机制", 2);
        assertEquals("[Java集合 · 扩容机制](" + uri.encode(anchor) + ")",
                markdown.format("Java/Java集合.md", anchor));
        assertEquals(anchor, markdown.parse(markdown.format("Java/Java集合.md", anchor)).link());
    }

    @Test void escapesMarkdownLabelAndLeavesUriCodecUnchanged() {
        var link = QuizForgeNavigationLink.heading("doc_a", "List \\ Map", 1);
        String presented = markdown.format("Java [基础].md", link);
        assertEquals("[Java \\[基础\\] · List \\\\ Map](" + uri.encode(link) + ")", presented);
        assertEquals("Java [基础] · List \\ Map", markdown.parse(presented).label());
        assertEquals(uri.encode(link), "quizforge://asset/doc_a/heading/List%20%5C%20Map?occurrence=1");
    }

    @Test void aliasIsNotIdentityAndBareUriRemainsReadable() {
        var link = QuizForgeNavigationLink.anchor("doc_a", "定义", 2);
        assertEquals(link, markdown.parse("[这里讲定义](" + uri.encode(link) + ")").link());
        assertEquals(link, markdown.parse(uri.encode(link)).link());
    }

    @Test void malformedAndExternalLinksFailClosed() {
        for (String text : new String[] {"[broken](quizforge://asset/doc_a", "[x](https://example.com)",
                "[x](quizforge://asset/doc_a) trailing", "[bad\\q](quizforge://asset/doc_a)",
                "quizforge://asset/doc_a/anchor/x?occurrence=0"}) {
            assertThrows(IllegalArgumentException.class, () -> markdown.parse(text), text);
        }
    }
}
