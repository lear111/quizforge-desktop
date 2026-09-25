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
}
