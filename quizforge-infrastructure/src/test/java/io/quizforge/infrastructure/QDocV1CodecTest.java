package io.quizforge.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import io.quizforge.core.document.qdoc.*;
import io.quizforge.infrastructure.filesystem.QDocV1Codec;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class QDocV1CodecTest {
    private final QDocV1Codec codec = new QDocV1Codec();

    private QDocDocument document(String id, String title, String sectionId, String body) {
        var section = new DocumentNode(sectionId, DocumentNodeType.SECTION, "ArrayList",
                List.of(ContentBlock.text(ContentBlockType.PARAGRAPH, body)));
        var chapter = new DocumentNode("chapter_lists", DocumentNodeType.CHAPTER, "Lists", List.of(section));
        return new QDocDocument("quizforge-document", "1.0", id,
                DocumentTemplate.GENERAL_KNOWLEDGE.reference(), title, "zh-CN", List.of(chapter));
    }

    @Test void writesReadableVersionedJsonAndRoundTrips() {
        var model = document("doc_one", "Java 集合", "section_arraylist", "按顺序存储。");
        String json = codec.write(model);
        assertTrue(json.contains("\n  \"format\" : \"quizforge-document\""));
        assertTrue(json.contains("\"template\""));
        assertFalse(json.contains("currentPath"));
        assertEquals(model, codec.parse(new String(json.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8)));
        assertEquals(json, codec.write(codec.parse(json)));
    }

    @Test void hashesCanonicalModelAndExcludesAssetIdAndFormatting() {
        var model = document("doc_one", "Java 集合", "section_arraylist", "按顺序存储。");
        String hash = codec.contentId(model);
        assertTrue(hash.matches("qfd:v1:[0-9a-f]{64}"));
        assertEquals(hash, codec.contentId(model));
        assertEquals(hash, codec.contentId(document("doc_two", "Java 集合", "section_arraylist", "按顺序存储。")));
        assertEquals(hash, codec.contentId(codec.parse(codec.write(model).replace("  ", "    "))));
    }

    @Test void actualContentTitleAndNodeIdChangeRevision() {
        String original = codec.contentId(document("doc_one", "Java 集合", "section_arraylist", "按顺序存储。"));
        assertNotEquals(original, codec.contentId(document("doc_one", "Java 集合", "section_arraylist", "支持索引。")));
        assertNotEquals(original, codec.contentId(document("doc_one", "Java 列表", "section_arraylist", "按顺序存储。")));
        assertNotEquals(original, codec.contentId(document("doc_one", "Java 集合", "section_new", "按顺序存储。")));
        var model = document("doc_one", "Java 集合", "section_arraylist", "按顺序存储。");
        var chapter = model.content().getFirst();
        var renamed = new DocumentNode(chapter.id(), chapter.type(), "Renamed chapter", chapter.children());
        assertNotEquals(original, codec.contentId(new QDocDocument(model.format(), model.schemaVersion(),
                model.id(), model.template(), model.title(), model.language(), List.of(renamed))));
    }

    @Test void subsectionAndAllBlockTypesAreValidAndOrdered() {
        var blocks = List.<DocumentElement>of(
                ContentBlock.text(ContentBlockType.PARAGRAPH, "intro"),
                ContentBlock.list(ContentBlockType.BULLET_LIST, List.of("one", "two")),
                ContentBlock.list(ContentBlockType.ORDERED_LIST, List.of("first", "second")),
                ContentBlock.text(ContentBlockType.QUOTE, "quote"),
                new ContentBlock(ContentBlockType.CODE_BLOCK, "int x = 1;", List.of(), "java"));
        var subsection = new DocumentNode("subsection_one", DocumentNodeType.SUBSECTION, "Details", blocks);
        var section = new DocumentNode("section_one", DocumentNodeType.SECTION, "Section", List.of(subsection));
        var chapter = new DocumentNode("chapter_one", DocumentNodeType.CHAPTER, "Chapter", List.of(section));
        var model = new QDocDocument("quizforge-document", "1.0", "doc_one",
                DocumentTemplate.GENERAL_KNOWLEDGE.reference(), "Knowledge", "en-US", List.of(chapter));
        assertEquals(model, codec.parse(codec.write(model)));
        assertNotEquals(codec.contentId(model), codec.contentId(document("doc_one", "Java 集合", "section_arraylist", "intro")));
    }

    @Test void rejectsDocumentDirectSectionAndChapterDirectSubsection() {
        var section = new DocumentNode("section_one", DocumentNodeType.SECTION, "Section", List.of());
        var subsection = new DocumentNode("subsection_one", DocumentNodeType.SUBSECTION, "Subsection", List.of());
        assertThrows(IllegalArgumentException.class, () -> codec.write(new QDocDocument("quizforge-document", "1.0",
                "doc_one", DocumentTemplate.GENERAL_KNOWLEDGE.reference(), "Title", "en-US", List.of(section))));
        var chapter = new DocumentNode("chapter_one", DocumentNodeType.CHAPTER, "Chapter", List.of(subsection));
        assertThrows(IllegalArgumentException.class, () -> codec.write(new QDocDocument("quizforge-document", "1.0",
                "doc_one", DocumentTemplate.GENERAL_KNOWLEDGE.reference(), "Title", "en-US", List.of(chapter))));
    }

    @Test void rejectsChapterWithoutSectionAndDuplicateIds() {
        var empty = new DocumentNode("chapter_one", DocumentNodeType.CHAPTER, "Chapter", List.of());
        assertThrows(IllegalArgumentException.class, () -> codec.write(new QDocDocument("quizforge-document", "1.0",
                "doc_one", DocumentTemplate.GENERAL_KNOWLEDGE.reference(), "Title", "en-US", List.of(empty))));
        var model = document("doc_one", "Title", "section_one", "content");
        var duplicated = new QDocDocument(model.format(), model.schemaVersion(), model.id(), model.template(),
                model.title(), model.language(), List.of(model.content().getFirst(), model.content().getFirst()));
        assertThrows(IllegalArgumentException.class, () -> codec.write(duplicated));
    }

    @Test void rejectsUnsupportedTemplateUnknownNodeAndUnknownFields() {
        var model = document("doc_one", "Title", "section_one", "content");
        assertThrows(IllegalArgumentException.class, () -> codec.write(new QDocDocument(model.format(),
                model.schemaVersion(), model.id(), new QDocDocument.TemplateRef("unsupported", "1.0"),
                model.title(), model.language(), model.content())));
        String json = codec.write(model);
        assertThrows(IllegalArgumentException.class, () -> codec.parse(json.replace("\"SECTION\"", "\"H5\"")));
        assertThrows(IllegalArgumentException.class, () -> codec.parse(json.replace("\"content\" : [", "\"path\" : \"C:/secret\", \"content\" : [")));
    }
}
