package io.quizforge.extensions.document.standardmd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StandardDocumentStructureTest {
    private final StandardMarkdownV1Validator parser = new StandardMarkdownV1Validator();
    private static final String DOCUMENT = """
            ---
            quizforge_version: "1.0"
            title: "Java"
            language: "zh-CN"
            ---

            # Java

            ## 1. List

            ### 1.1 ArrayList

            Body A.

            ```markdown
            ## fake chapter
            ### fake section
            ```

            ### 1.2 LinkedList

            Body B.

            ## 2. Map

            ### 2.1 HashMap

            Body C.
            """;

    @Test void parsesChaptersSectionsAndRawSlices() {
        var structure = parser.parse(DOCUMENT);
        assertEquals("Java", structure.title());
        assertEquals(2, structure.chapters().size());
        var first = structure.chapters().getFirst();
        assertEquals("chapter-1", first.id());
        assertEquals("1. List", first.title());
        assertEquals(2, first.sections().size());
        assertTrue(first.markdown().startsWith("## 1. List"));
        assertFalse(first.markdown().contains("## 2. Map"));
        assertTrue(first.sections().getFirst().markdown().contains("## fake chapter"));
        assertFalse(first.sections().getFirst().markdown().contains("### 1.2 LinkedList"));
        assertEquals("chapter-2-section-1", structure.chapters().get(1).sections().getFirst().id());
    }

    @Test void invalidStructureCannotBeParsed() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("# Invalid"));
    }
}
