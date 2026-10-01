package io.quizforge.infrastructure;

import io.quizforge.core.document.registered.MarkdownBlockType;
import io.quizforge.infrastructure.filesystem.markdown.RegisteredMarkdownCodec;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegisteredMarkdownCodecTest {
    private final RegisteredMarkdownCodec codec = new RegisteredMarkdownCodec();

    @Test void markersInsideNestedCodeRemainLiteralAndProduceNoAnchorErrors() {
        String body = "> ```html\n> <!-- qf:anchor=quoted -->\n> ```\n\n"
                + "- Example\n\n  ```html\n  <!-- qf:anchor=listed -->\n  ```\n\n"
                + "<!-- qf:anchor=real -->\nActual paragraph.\n";
        var document=codec.prepareRegistration(body,"notes.md").document();
        assertEquals(java.util.List.of("real"),document.anchors().stream().map(anchor->anchor.name()).toList());
        assertTrue(document.anchorErrors().isEmpty());
    }

    @Test void ordinaryMarkdownGetsMetadataAndStableBlockIdsWithoutChangingItsExtension() {
        String original = "# Java\n\nA paragraph.\n";
        var prepared = codec.prepare(original, "notes/Java.md");
        assertEquals("notes/Java.md", prepared.document().relativePath());
        assertTrue(prepared.document().documentAssetId().startsWith("doc_"));
        assertEquals(2, prepared.document().addressableBlocks().size());
        assertEquals(0, prepared.document().unaddressedBlockCount());
        assertEquals(prepared.source(), codec.prepare(prepared.source(), "notes/Java.md").source());
        assertEquals(original, stripAddedMetadata(prepared.source()));
        assertEquals(prepared.document(), codec.parseIfRegistered(prepared.source(), "notes/Java.md").orElseThrow());
    }

    @Test void existingFrontMatterFieldsAndFormattingArePreserved() {
        String original = "---\r\ntitle: Java 集合\r\ntags:\r\n  - java\r\naliases: [Collections]\r\n"
                + "date: 2026-09-27\r\ncustom: yes\r\n---\r\n# Java\r\n\r\nText.\r\n";
        var prepared = codec.prepare(original, "Java.md");
        assertTrue(prepared.source().startsWith("---\r\ntitle: Java 集合\r\ntags:\r\n"
                + "  - java\r\naliases: [Collections]\r\ndate: 2026-09-27\r\ncustom: yes\r\n"
                + "quizforge:\r\n  format: document\r\n  version: 1\r\n  assetId: doc_"));
        assertEquals("Java 集合", prepared.document().title());
        assertEquals(original, stripAddedMetadata(prepared.source()));
    }

    @Test void emptyExistingFrontMatterCanBeRegisteredWithoutReplacingTheMarkdown() {
        String original = "---\n---\n# Heading\n";
        var prepared = codec.prepare(original, "empty-front-matter.md");
        assertTrue(prepared.source().startsWith("---\nquizforge:\n  format: document\n"));
        assertEquals("# Heading\n", stripAddedMetadata(prepared.source()));
    }

    @Test void allSafeTopLevelBlocksAreAddressedAsWholeBlocks() {
        String source = "# H\n\nParagraph.\n\n- one\n- two\n\n1. first\n2. second\n\n"
                + "> quoted\n> again\n\n```java\nSystem.out.println(1);\n```\n\n"
                + "    indented code\n";
        var prepared = codec.prepare(source, "blocks.md");
        var types = prepared.document().addressableBlocks().stream().map(block -> block.blockType()).toList();
        assertEquals(7, types.size());
        assertEquals(2, types.stream().filter(type -> type == MarkdownBlockType.LIST).count());
        assertTrue(types.contains(MarkdownBlockType.HEADING));
        assertTrue(types.contains(MarkdownBlockType.PARAGRAPH));
        assertTrue(types.contains(MarkdownBlockType.BLOCK_QUOTE));
        assertTrue(types.contains(MarkdownBlockType.FENCED_CODE));
        assertTrue(types.contains(MarkdownBlockType.INDENTED_CODE));
        assertEquals(source, stripAddedMetadata(prepared.source()));
        assertEquals(types.size(), new HashSet<>(prepared.document().addressableBlocks().stream()
                .map(block -> block.nodeId()).toList()).size());
    }

    @Test void existingIdsStayStableAndOnlyNewBlocksGetNewIds() {
        var first = codec.prepare("# H\n\nOriginal.\n", "one.md");
        var second = codec.prepare(first.source() + "\nNew paragraph.\n", "one.md");
        assertEquals(first.document().documentAssetId(), second.document().documentAssetId());
        assertEquals(first.document().addressableBlocks().get(0).nodeId(),
                second.document().addressableBlocks().get(0).nodeId());
        assertEquals(first.document().addressableBlocks().get(1).nodeId(),
                second.document().addressableBlocks().get(1).nodeId());
        assertEquals(3, second.document().addressableBlocks().size());
        assertFalse(first.document().addressableBlocks().stream().anyMatch(block ->
                block.nodeId().equals(second.document().addressableBlocks().get(2).nodeId())));
    }

    @Test void missingIdsAreReportedUntilAddressingIsEnsured() {
        var first = codec.prepare("# H\n", "one.md");
        var unaddressed = codec.parseIfRegistered(first.source() + "\nAnother paragraph.\n", "one.md")
                .orElseThrow();
        assertEquals(1, unaddressed.unaddressedBlockCount());
        assertEquals(0, codec.prepare(first.source() + "\nAnother paragraph.\n", "one.md")
                .document().unaddressedBlockCount());
    }

    @Test void duplicateAndOrphanNodeIdsAreRejected() {
        var first = codec.prepare("# H\n\nParagraph.\n", "one.md");
        String duplicate = first.source().replaceFirst("node_[A-Za-z0-9_-]+(?= -->\\nParagraph)",
                first.document().addressableBlocks().getFirst().nodeId());
        assertThrows(IllegalArgumentException.class, () -> codec.parseIfRegistered(duplicate, "one.md"));
        assertThrows(IllegalArgumentException.class, () -> codec.parseIfRegistered(
                first.source() + "\n<!-- qf:id=node_orphan -->\n", "one.md"));
    }

    @Test void contentIdTracksContentAndAddressingButNotPathOrAssetId() {
        var first = codec.prepare("# H\n\nParagraph.\n", "one.md");
        var moved = codec.parseIfRegistered(first.source(), "other/two.md").orElseThrow();
        assertEquals(first.document().contentId(), moved.contentId());
        String otherId = first.source().replace(first.document().documentAssetId(), "doc_other");
        assertEquals(first.document().contentId(), codec.parseIfRegistered(otherId, "one.md")
                .orElseThrow().contentId());
        assertNotEquals(first.document().contentId(), codec.parseIfRegistered(
                first.source().replace("Paragraph.", "Changed."), "one.md").orElseThrow().contentId());
        assertNotEquals(first.document().contentId(), codec.parseIfRegistered(
                first.source().replace(first.document().addressableBlocks().getFirst().nodeId(), "node_new"),
                "one.md").orElseThrow().contentId());
        assertTrue(first.document().contentId().matches("qfd:v2:[0-9a-f]{64}"));
    }

    @Test void htmlUnknownSyntaxAndBlankLinesRemainByteForByte() {
        String source = "# H\r\n\r\n<div data-x='1'>\r\ncustom html\r\n</div>\r\n\r\n"
                + "Text with ==custom== syntax.\r\n\r\n";
        var prepared = codec.prepare(source, "one.md");
        assertEquals(source, stripAddedMetadata(prepared.source()));
        assertTrue(prepared.source().contains("<div data-x='1'>\r\ncustom html\r\n</div>"));
        assertTrue(prepared.source().contains("==custom=="));
    }

    @Test void invalidExistingMetadataIsNotReplaced() {
        String source = "---\nquizforge:\n  format: document\n  version: 1\n  assetId: oops\n---\n# H\n";
        assertThrows(IllegalArgumentException.class, () -> codec.prepare(source, "one.md"));
        assertThrows(IllegalArgumentException.class, () -> codec.parseIfRegistered(source, "one.md"));
    }

    private String stripAddedMetadata(String source) {
        String clean = source.replaceAll("(?m)^<!-- qf:id=node_[A-Za-z0-9_-]+ -->\\r?\\n", "");
        clean = clean.replaceFirst("(?s)^---\\r?\\nquizforge:\\r?\\n  format: document\\r?\\n"
                + "  version: 1\\r?\\n  assetId: doc_[A-Za-z0-9_-]+\\r?\\n---\\r?\\n", "");
        clean = clean.replaceFirst("(?m)^quizforge:\\r?\\n  format: document\\r?\\n"
                + "  version: 1\\r?\\n  assetId: doc_[A-Za-z0-9_-]+\\r?\\n", "");
        return clean;
    }
}
