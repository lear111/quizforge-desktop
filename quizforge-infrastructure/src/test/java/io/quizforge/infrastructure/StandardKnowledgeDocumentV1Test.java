package io.quizforge.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quizforge.infrastructure.filesystem.StandardKnowledgeDocumentV1;
import org.junit.jupiter.api.Test;

class StandardKnowledgeDocumentV1Test {
    private final StandardKnowledgeDocumentV1 format = new StandardKnowledgeDocumentV1();
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
        var first = format.parseIfStandard(DOCUMENT).orElseThrow();
        assertEquals("doc_one", first.assetId());
        assertTrue(first.contentId().matches("qfd:v1:[0-9a-f]{64}"));
        assertEquals(first.contentId(), format.parseIfStandard(DOCUMENT).orElseThrow().contentId());
        assertEquals(first.contentId(), format.parseIfStandard(DOCUMENT.replace("\n", "\r\n"))
                .orElseThrow().contentId());
    }

    @Test void bodyTitleAndSectionIdentityChangeRevision() {
        String original = format.parseIfStandard(DOCUMENT).orElseThrow().contentId();
        assertNotEquals(original, format.parseIfStandard(DOCUMENT.replace("preserves order", "is ordered"))
                .orElseThrow().contentId());
        assertNotEquals(original, format.parseIfStandard(DOCUMENT.replace("title: \"Java Collections\"",
                "title: \"Collections in Java\"")).orElseThrow().contentId());
        assertNotEquals(original, format.parseIfStandard(DOCUMENT.replace("section_arraylist", "section_list"))
                .orElseThrow().contentId());
        assertNotEquals(original, format.parseIfStandard(DOCUMENT.replace("chapter_lists", "chapter_all"))
                .orElseThrow().contentId());
    }

    @Test void assetIdentityAndUnrelatedMetadataDoNotChangeRevision() {
        String original = format.parseIfStandard(DOCUMENT).orElseThrow().contentId();
        assertEquals(original, format.parseIfStandard(DOCUMENT.replace("doc_one", "doc_two"))
                .orElseThrow().contentId());
        assertEquals(original, format.parseIfStandard(DOCUMENT.replace("language: \"en-US\"",
                "language: \"en-US\"\nui_state: \"expanded\""))
                .orElseThrow().contentId());
    }

    @Test void validatesChapterAndSectionIdsAndBody() {
        assertThrows(IllegalArgumentException.class,
                () -> format.parseIfStandard(DOCUMENT.replace("<!-- qf:id=chapter_lists -->", "")));
        assertThrows(IllegalArgumentException.class,
                () -> format.parseIfStandard(DOCUMENT.replace("<!-- qf:id=section_arraylist -->", "")));
        assertThrows(IllegalArgumentException.class,
                () -> format.parseIfStandard(DOCUMENT.replace("section_arraylist", "chapter_lists")));
        assertThrows(IllegalArgumentException.class,
                () -> format.parseIfStandard(DOCUMENT.replace("A list preserves order.\n\n- first\n- second\n\n"
                        + "```java\nList<String> names = new ArrayList<>();\n```", "")));
    }

    @Test void validatesHeadingStructure() {
        assertThrows(IllegalArgumentException.class,
                () -> format.parseIfStandard(DOCUMENT.replace("# Java Collections", "# Java Collections\n# Extra")));
        assertThrows(IllegalArgumentException.class,
                () -> format.parseIfStandard(DOCUMENT.replace("### ArrayList", "#### ArrayList")));
        assertThrows(IllegalArgumentException.class,
                () -> format.parseIfStandard(DOCUMENT.replace("### ArrayList\n<!-- qf:id=section_arraylist -->",
                        "")));
    }

    @Test void rejectsDuplicateSectionIdsAndUnclosedDeclaredFrontMatter() {
        String duplicate = DOCUMENT + "\n### LinkedList\n<!-- qf:id=section_arraylist -->\nA linked list.\n";
        assertThrows(IllegalArgumentException.class, () -> format.parseIfStandard(duplicate));
        assertThrows(IllegalArgumentException.class,
                () -> format.parseIfStandard("---\nquizforge_format: study-document\n"));
    }

    @Test void ordinaryMarkdownIsNotAnAsset() {
        assertFalse(format.parseIfStandard("# Plain notes\nA paragraph.").isPresent());
        assertFalse(format.parseIfStandard(DOCUMENT.replace("study-document", "other-format")).isPresent());
    }
}
