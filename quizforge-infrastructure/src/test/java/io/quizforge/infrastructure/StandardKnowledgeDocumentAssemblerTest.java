package io.quizforge.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quizforge.infrastructure.filesystem.StandardKnowledgeDocumentAssembler;
import io.quizforge.infrastructure.filesystem.StandardKnowledgeDocumentV1;
import org.junit.jupiter.api.Test;

class StandardKnowledgeDocumentAssemblerTest {
    private static final String DRAFT = """
            ---
            quizforge_version: "1.0"
            quizforge_id: "doc_forged"
            title: "Java Collections"
            language: "en-US"
            ---
            # Java Collections
            ## Lists
            <!-- qf:id=chapter_forged -->
            ### ArrayList
            <!-- qf:id=section_forged -->
            Lists preserve order.
            """;
    private final StandardKnowledgeDocumentAssembler assembler = new StandardKnowledgeDocumentAssembler();
    private final StandardKnowledgeDocumentV1 validator = new StandardKnowledgeDocumentV1();

    @Test void draftBecomesValidV1WithOnlyLocalIds() {
        var result = assembler.assemble(DRAFT, "Java Collections", assembler.draftLanguage(DRAFT), null);
        var parsed = validator.parseIfStandard(result.markdown()).orElseThrow();
        assertTrue(result.assetId().matches("doc_[0-9a-f]{32}"));
        assertEquals(result.assetId(), parsed.assetId());
        assertEquals(result.contentId(), parsed.contentId());
        assertTrue(result.markdown().contains("quizforge_format: \"study-document\""));
        assertTrue(result.markdown().matches("(?s).*qf:id=chapter_[0-9a-f]{32}.*"));
        assertTrue(result.markdown().matches("(?s).*qf:id=section_[0-9a-f]{32}.*"));
        assertTrue(!result.markdown().contains("doc_forged"));
        assertTrue(!result.markdown().contains("chapter_forged"));
        assertTrue(!result.markdown().contains("section_forged"));
    }

    @Test void createAllocatesNewIdentityAndRegeneratePreservesRequestedIdentity() {
        var first = assembler.assemble(DRAFT, "Java Collections", "en-US", null);
        var second = assembler.assemble(DRAFT, "Java Collections", "en-US", null);
        assertNotEquals(first.assetId(), second.assetId());
        var regenerated = assembler.assemble(DRAFT.replace("preserve order", "preserve insertion order"),
                "Java Collections", "en-US", first.assetId());
        assertEquals(first.assetId(), regenerated.assetId());
        assertNotEquals(first.contentId(), regenerated.contentId());
    }
}
