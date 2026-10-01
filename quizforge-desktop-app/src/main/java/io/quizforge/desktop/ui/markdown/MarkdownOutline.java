package io.quizforge.desktop.ui.markdown;

import io.quizforge.core.document.registered.MarkdownSourceRange;
import io.quizforge.core.document.registered.NamedMarkdownAnchor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.commonmark.node.Code;
import org.commonmark.node.Heading;
import org.commonmark.node.Node;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.SourceSpan;
import org.commonmark.node.Text;

/** Navigation positions belong to one parsed preview and are never written to Markdown. */
public final class MarkdownOutline {
    public enum Kind { HEADING, ANCHOR }

    public record Entry(String runtimeId, Kind kind, String label, int level, int depth,
            int sourceLine, MarkdownSourceRange target, boolean orphan, int occurrence) { }

    private record Candidate(Kind kind, String label, int level, int sourceLine,
            MarkdownSourceRange target, boolean orphan, int order) { }

    public static List<Entry> extract(Node parsed, List<NamedMarkdownAnchor> anchors) {
        List<Candidate> candidates = new ArrayList<>();
        headings(parsed, candidates);
        for (NamedMarkdownAnchor anchor : anchors) {
            candidates.add(new Candidate(Kind.ANCHOR, anchor.name(), 0, anchor.sourceLine(),
                    anchor.blockRange(), anchor.orphan(), candidates.size()));
        }
        candidates.sort(Comparator.comparingInt(Candidate::sourceLine)
                .thenComparingInt(Candidate::order));
        List<Entry> result = new ArrayList<>();
        List<Integer> headingLevels = new ArrayList<>();
        Map<String, Integer> headingCounts = new HashMap<>();
        Map<String, Integer> anchorCounts = new HashMap<>();
        for (Candidate candidate : candidates) {
            int depth;
            if (candidate.kind() == Kind.HEADING) {
                while (!headingLevels.isEmpty()
                        && headingLevels.getLast() >= candidate.level()) headingLevels.removeLast();
                depth = headingLevels.size();
                headingLevels.add(candidate.level());
            } else depth = headingLevels.size();
            int occurrence = (candidate.kind() == Kind.HEADING ? headingCounts : anchorCounts)
                    .merge(candidate.label(), 1, Integer::sum);
            result.add(new Entry("qf-nav-" + result.size(), candidate.kind(), candidate.label(),
                    candidate.level(), depth, candidate.sourceLine(), candidate.target(),
                    candidate.orphan(), occurrence));
        }
        return List.copyOf(result);
    }

    private static void headings(Node parent, List<Candidate> result) {
        for (Node node = parent.getFirstChild(); node != null; node = node.getNext()) {
            if (node instanceof Heading heading && !node.getSourceSpans().isEmpty()) {
                SourceSpan first = node.getSourceSpans().getFirst();
                SourceSpan last = node.getSourceSpans().getLast();
                MarkdownSourceRange range = new MarkdownSourceRange(first.getLineIndex() + 1,
                        first.getColumnIndex() + 1, last.getLineIndex() + 1,
                        last.getColumnIndex() + last.getLength() + 1);
                result.add(new Candidate(Kind.HEADING, label(node).trim(), heading.getLevel(),
                        range.startLine(), range, false, result.size()));
            }
            headings(node, result);
        }
    }

    private static String label(Node node) {
        StringBuilder text = new StringBuilder();
        append(node, text);
        return text.toString().replaceAll("\\s+", " ");
    }

    private static void append(Node node, StringBuilder text) {
        if (node instanceof Text value) text.append(value.getLiteral());
        else if (node instanceof Code value) text.append(value.getLiteral());
        else if (node instanceof SoftLineBreak || node instanceof org.commonmark.node.HardLineBreak)
            text.append(' ');
        for (Node child = node.getFirstChild(); child != null; child = child.getNext())
            append(child, text);
    }
}
