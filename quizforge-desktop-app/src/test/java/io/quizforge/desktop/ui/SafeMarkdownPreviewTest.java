package io.quizforge.desktop.ui;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SafeMarkdownPreviewTest {
    private final SafeMarkdownPreview preview = new SafeMarkdownPreview();

    @Test void rendersHeadingsParagraphsListsAndCodeAsTextBlocks() {
        var blocks = preview.project("# Title\n\nParagraph **bold**.\n\n- First\n- Second\n\n```java\nint x = 1;\n```\n");
        assertTrue(blocks.stream().anyMatch(block -> block.style().equals("preview-heading-1")
                && block.text().equals("Title")));
        assertTrue(blocks.stream().anyMatch(block -> block.text().contains("Paragraph bold.")));
        assertTrue(blocks.stream().anyMatch(block -> block.text().contains("•  First")));
        assertTrue(blocks.stream().anyMatch(block -> block.style().equals("preview-code")
                && block.text().contains("int x = 1;")));
    }

    @Test void rawHtmlAndJavascriptLinksCannotBecomeExecutablePreviewContent() {
        var blocks = preview.project("""
                # Safe

                <script>alert('pwn')</script>

                [Click me](javascript:alert(1))

                <img src=x onerror=alert(2)>
                """);
        String content = blocks.stream().map(SafeMarkdownPreview.Block::text)
                .reduce("", (left, right) -> left + "\n" + right);
        assertTrue(content.contains("Click me"));
        assertFalse(content.contains("<script"));
        assertFalse(content.contains("javascript:"));
        assertFalse(content.contains("onerror="));
    }

    @Test void formalFrontMatterIsNotShownInPreview() {
        var blocks = preview.project("---\nquizforge_format: study-document\n---\n# Knowledge\nBody");
        assertEquals("Knowledge", blocks.getFirst().text());
        assertTrue(blocks.stream().noneMatch(block -> block.text().contains("quizforge_format")));
    }

    @Test void projectsEveryHeadingLevelWhileKeepingNamedAnchorsInvisible() {
        var blocks = preview.project("""
                # One
                ## Two
                ### Three
                #### Four
                ##### Five
                ###### Six

                <!-- qf:anchor=Source -->
                Paragraph with `inline code` and **strong** text.

                > Quoted text
                """);
        for (int level = 1; level <= 6; level++) {
            String style = "preview-heading-" + level;
            assertTrue(blocks.stream().anyMatch(block -> block.style().equals(style)), style);
        }
        assertTrue(blocks.stream().anyMatch(block -> block.text().contains("inline code")));
        assertTrue(blocks.stream().noneMatch(block -> block.text().contains("qf:anchor")));
    }
}
