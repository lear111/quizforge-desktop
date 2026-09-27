package io.quizforge.core.document.navigation;

/** Presentation of a navigation URI as one Markdown link. Labels are never identity. */
public final class MarkdownNavigationLinkCodec {
    public record Presented(String label, QuizForgeNavigationLink link) { }

    private final QuizForgeNavigationLinkCodec uri = new QuizForgeNavigationLinkCodec();

    public String format(String relativePath, QuizForgeNavigationLink link) {
        String filename = relativePath.replace('\\', '/');
        filename = filename.substring(filename.lastIndexOf('/') + 1);
        if (filename.toLowerCase(java.util.Locale.ROOT).endsWith(".md"))
            filename = filename.substring(0, filename.length() - 3);
        String label = filename;
        if (link.target() instanceof QuizForgeNavigationLink.HeadingTarget heading)
            label += " · " + heading.headingText();
        else if (link.target() instanceof QuizForgeNavigationLink.AnchorTarget anchor)
            label += " · " + anchor.anchorName();
        return formatLabel(label, link);
    }

    public String formatLabel(String label, QuizForgeNavigationLink link) {
        if (label == null || label.isBlank()) throw new IllegalArgumentException("Link label is required");
        String escaped = label.replace("\\", "\\\\").replace("[", "\\[").replace("]", "\\]");
        return "[" + escaped + "](" + uri.encode(link) + ")";
    }

    /** Accepts exactly one Markdown link or a legacy bare quizforge URI. */
    public Presented parse(String input) {
        if (input == null) throw new IllegalArgumentException("Link is required");
        String value = input.trim();
        if (value.startsWith("quizforge://")) return new Presented("", uri.decode(value));
        if (!value.startsWith("[")) throw new IllegalArgumentException("Invalid Markdown navigation link");
        StringBuilder label = new StringBuilder();
        int i = 1;
        for (; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '\\') {
                if (++i == value.length() || "\\[]".indexOf(value.charAt(i)) < 0)
                    throw new IllegalArgumentException("Invalid Markdown label escape");
                label.append(value.charAt(i));
            } else if (ch == ']') break;
            else label.append(ch);
        }
        if (label.isEmpty() || i >= value.length() || !value.startsWith("](", i)
                || !value.endsWith(")")) throw new IllegalArgumentException("Invalid Markdown navigation link");
        String target = value.substring(i + 2, value.length() - 1);
        if (target.indexOf('(') >= 0 || target.indexOf(')') >= 0 || target.isBlank())
            throw new IllegalArgumentException("Invalid Markdown navigation target");
        return new Presented(label.toString(), uri.decode(target));
    }
}
