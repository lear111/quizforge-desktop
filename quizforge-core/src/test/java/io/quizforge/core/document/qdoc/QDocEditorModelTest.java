package io.quizforge.core.document.qdoc;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class QDocEditorModelTest {
    private QDocEditorModel empty() {
        return new QDocEditorModel(new QDocDocument("quizforge-document", "1.0", "doc_edit",
                DocumentTemplate.GENERAL_KNOWLEDGE.reference(), "Knowledge", "en-US", List.of()),
                DocumentTemplate.GENERAL_KNOWLEDGE);
    }

    @Test void createsChapterSectionSubsectionAndParagraphWithLocalIds() {
        QDocEditorModel edit = empty();
        edit.addNode(List.of(), DocumentNodeType.CHAPTER);
        edit.addNode(List.of(0), DocumentNodeType.SECTION);
        edit.addNode(List.of(0, 0), DocumentNodeType.SUBSECTION);
        edit.addBlock(List.of(0, 0, 0), ContentBlockType.PARAGRAPH);
        edit.setNodeTitle(List.of(0), "Java");
        edit.setBlockText(List.of(0, 0, 0, 0), "ArrayList uses an array.");
        assertTrue(edit.document().content().getFirst().id().startsWith("chapter_"));
        assertTrue(((DocumentNode) edit.element(List.of(0, 0))).id().startsWith("section_"));
        assertTrue(((DocumentNode) edit.element(List.of(0, 0, 0))).id().startsWith("subsection_"));
        assertEquals("ArrayList uses an array.", ((ContentBlock) edit.element(List.of(0, 0, 0, 0))).text());
        assertDoesNotThrow(() -> new DocumentSchemaValidator().validate(edit.document()));
    }

    @Test void rejectsIllegalRelationshipsBeforeCreationAndMenusUseSchema() {
        QDocEditorModel edit = empty();
        assertEquals(java.util.Set.of(DocumentNodeType.CHAPTER), edit.allowedNodes(List.of()));
        assertTrue(edit.allowedBlocks(List.of()).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> edit.addNode(List.of(), DocumentNodeType.SECTION));
        edit.addNode(List.of(), DocumentNodeType.CHAPTER);
        assertEquals(java.util.Set.of(ContentBlockType.PARAGRAPH), edit.allowedBlocks(List.of(0)));
        assertThrows(IllegalArgumentException.class,
                () -> edit.addBlock(List.of(0), ContentBlockType.CODE_BLOCK));
    }

    @Test void editsListItemsAndDeletesNodesWithoutChangingDocumentIdentity() {
        QDocEditorModel edit = empty();
        edit.addNode(List.of(), DocumentNodeType.CHAPTER);
        edit.addNode(List.of(0), DocumentNodeType.SECTION);
        edit.addBlock(List.of(0, 0), ContentBlockType.BULLET_LIST);
        edit.addListItem(List.of(0, 0, 0));
        edit.setListItem(List.of(0, 0, 0), 1, "Second");
        assertEquals(List.of("New item", "Second"), ((ContentBlock) edit.element(List.of(0, 0, 0))).items());
        edit.delete(List.of(0, 0, 0));
        assertTrue(((DocumentNode) edit.element(List.of(0, 0))).children().isEmpty());
        edit.delete(List.of(0, 0));
        assertEquals("doc_edit", edit.document().id());
        assertTrue(edit.document().content().getFirst().children().isEmpty());
        assertTrue(edit.dirty());
    }
}
