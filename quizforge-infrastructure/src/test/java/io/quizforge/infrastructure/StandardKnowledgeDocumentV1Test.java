package io.quizforge.infrastructure;

import io.quizforge.infrastructure.filesystem.markdown.LegacyMarkdownCodec;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StandardKnowledgeDocumentV1Test {
    private final LegacyMarkdownCodec format = new LegacyMarkdownCodec();
    private static final String DOCUMENT = """
            ---
            quizforge_format: "study-document"
            schema_version: "1.0"
            quizforge_id: "doc_one"
            title: "Java Collections"
            language: "en-US"
            ---
            # Java Collections
            ## Lists
            <!-- qf:id=chapter_lists -->
            ### ArrayList
            <!-- qf:id=section_arraylist -->
            A list preserves order.

            - first
            - second

            ```java
            List<String> names = new ArrayList<>();
            ```
            """;

    @Test void identifiesAssetAndHasDeterministicContentId() {
        var first = format.parseLegacy(DOCUMENT).orElseThrow();
        assertEquals("doc_one", first.assetId());
        assertTrue(first.contentId().matches("qfd:v1:[0-9a-f]{64}"));
        assertEquals(first.contentId(), format.parseLegacy(DOCUMENT).orElseThrow().contentId());
        assertEquals(first.contentId(), format.parseLegacy(DOCUMENT.replace("\n", "\r\n"))
                .orElseThrow().contentId());
    }

    @Test void bodyTitleAndSectionIdentityChangeRevision() {
        String original = format.parseLegacy(DOCUMENT).orElseThrow().contentId();
        assertNotEquals(original, format.parseLegacy(DOCUMENT.replace("preserves order", "is ordered"))
                .orElseThrow().contentId());
        assertNotEquals(original, format.parseLegacy(DOCUMENT.replace("title: \"Java Collections\"",
                "title: \"Collections in Java\"")).orElseThrow().contentId());
        assertNotEquals(original, format.parseLegacy(DOCUMENT.replace("section_arraylist", "section_list"))
                .orElseThrow().contentId());
        assertNotEquals(original, format.parseLegacy(DOCUMENT.replace("chapter_lists", "chapter_all"))
                .orElseThrow().contentId());
    }

    @Test void assetIdentityAndUnrelatedMetadataDoNotChangeRevision() {
        String original = format.parseLegacy(DOCUMENT).orElseThrow().contentId();
        assertEquals(original, format.parseLegacy(DOCUMENT.replace("doc_one", "doc_two"))
                .orElseThrow().contentId());
        assertEquals(original, format.parseLegacy(DOCUMENT.replace("language: \"en-US\"",
                "language: \"en-US\"\nui_state: \"expanded\""))
                .orElseThrow().contentId());
    }

    @Test void validatesChapterAndSectionIdsAndBody() {
        assertThrows(IllegalArgumentException.class,
                () -> format.parseLegacy(DOCUMENT.replace("<!-- qf:id=chapter_lists -->", "")));
        assertThrows(IllegalArgumentException.class,
                () -> format.parseLegacy(DOCUMENT.replace("<!-- qf:id=section_arraylist -->", "")));
        assertThrows(IllegalArgumentException.class,
                () -> format.parseLegacy(DOCUMENT.replace("section_arraylist", "chapter_lists")));
        assertThrows(IllegalArgumentException.class,
                () -> format.parseLegacy(DOCUMENT.replace("A list preserves order.\n\n- first\n- second\n\n"
                        + "```java\nList<String> names = new ArrayList<>();\n```", "")));
    }

    @Test void validatesHeadingStructure() {
        assertThrows(IllegalArgumentException.class,
                () -> format.parseLegacy(DOCUMENT.replace("# Java Collections", "# Java Collections\n# Extra")));
        assertThrows(IllegalArgumentException.class,
                () -> format.parseLegacy(DOCUMENT.replace("### ArrayList", "#### ArrayList")));
        assertThrows(IllegalArgumentException.class,
                () -> format.parseLegacy(DOCUMENT.replace("### ArrayList\n<!-- qf:id=section_arraylist -->",
                        "")));
    }

    @Test void rejectsDuplicateSectionIdsAndUnclosedDeclaredFrontMatter() {
        String duplicate = DOCUMENT + "\n### LinkedList\n<!-- qf:id=section_arraylist -->\nA linked list.\n";
        assertThrows(IllegalArgumentException.class, () -> format.parseLegacy(duplicate));
        assertThrows(IllegalArgumentException.class,
                () -> format.parseLegacy("---\nquizforge_format: study-document\n"));
    }

    @Test void ordinaryMarkdownIsNotAnAsset() {
        assertFalse(format.parseLegacy("# Plain notes\nA paragraph.").isPresent());
        assertFalse(format.parseLegacy(DOCUMENT.replace("study-document", "other-format")).isPresent());
    }
}
