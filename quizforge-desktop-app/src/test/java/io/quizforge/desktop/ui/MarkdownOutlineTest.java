package io.quizforge.desktop.ui;

import static org.junit.jupiter.api.Assertions.*;

import io.quizforge.infrastructure.filesystem.RegisteredMarkdownCodec;
import java.util.List;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;
import org.junit.jupiter.api.Test;

class MarkdownOutlineTest {
    private static final Parser MARKDOWN = Parser.builder()
            .includeSourceSpans(IncludeSourceSpans.BLOCKS).build();
    private final RegisteredMarkdownCodec anchors = new RegisteredMarkdownCodec();

    private List<MarkdownOutline.Entry> extract(String source) {
        return MarkdownOutline.extract(MARKDOWN.parse(source), anchors.inspectAnchors(source));
    }

    @Test void allHeadingLevelsKeepSourceOrderAndReadableLabels() {
        var entries = extract("# One\n## **Two** `code`\n### Three\n#### Four\n##### Five\n###### Six\n");
        assertEquals(List.of("One", "Two code", "Three", "Four", "Five", "Six"),
                entries.stream().map(MarkdownOutline.Entry::label).toList());
        assertEquals(List.of(1, 2, 3, 4, 5, 6),
                entries.stream().map(MarkdownOutline.Entry::level).toList());
        assertEquals(6, entries.stream().map(MarkdownOutline.Entry::runtimeId).distinct().count());
    }

    @Test void skippedHeadingLevelsUseNearestEarlierLowerLevel() {
        var entries = extract("# Java\n### Collection\n#### ArrayList\n## JVM\n#### GC\n");
        assertEquals(List.of(0, 1, 2, 1, 2),
                entries.stream().map(MarkdownOutline.Entry::depth).toList());
    }

    @Test void anchorsKeepSourceOrderAndBindTheFollowingAddressableBlock() {
        var entries = extract("# List\n<!-- qf:anchor=Definition -->\nParagraph.\n## Next\n");
        assertEquals(List.of("List", "Definition", "Next"),
                entries.stream().map(MarkdownOutline.Entry::label).toList());
        assertEquals(3, entries.get(1).target().startLine());
        assertEquals(1, entries.get(1).depth());
    }

    @Test void repeatedNamesRemainPlainLabelsWithDifferentRuntimeTargets() {
        var entries = extract("# List\n<!-- qf:anchor=定义 -->\nFirst.\n"
                + "<!-- qf:anchor=定义 -->\nSecond.\n");
        assertEquals(List.of("定义", "定义"), entries.stream()
                .filter(entry -> entry.kind() == MarkdownOutline.Kind.ANCHOR)
                .map(MarkdownOutline.Entry::label).toList());
        assertNotEquals(entries.get(1).runtimeId(), entries.get(2).runtimeId());
        assertNotEquals(entries.get(1).target(), entries.get(2).target());
    }

    @Test void multipleAnchorsOnOneBlockRemainSeparateEntriesWithOneTarget() {
        var entries = extract("# List\n<!-- qf:anchor=First -->\n"
                + "<!-- qf:anchor=Second -->\nParagraph.\n");
        assertEquals(List.of("List", "First", "Second"),
                entries.stream().map(MarkdownOutline.Entry::label).toList());
        assertEquals(entries.get(1).target(), entries.get(2).target());
        assertNotEquals(entries.get(1).runtimeId(), entries.get(2).runtimeId());
    }

    @Test void anchorBoundToHeadingAppearsBeforeThatHeading() {
        var entries = extract("# List\n<!-- qf:anchor=Section -->\n## ArrayList\n");
        assertEquals(List.of("List", "Section", "ArrayList"),
                entries.stream().map(MarkdownOutline.Entry::label).toList());
        assertEquals(entries.get(1).target(), entries.get(2).target());
    }

    @Test void orphanIsVisibleButHasNoTarget() {
        var entries = extract("# List\n<!-- qf:anchor=Orphan -->\n");
        assertTrue(entries.get(1).orphan());
        assertNull(entries.get(1).target());
    }
}
