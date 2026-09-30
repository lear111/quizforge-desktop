package io.quizforge.core.question;

import java.util.List;
public record InlineTextNode(String text, List<TextMark> marks) implements InlineNode {
    public InlineTextNode { marks=marks==null?List.of():List.copyOf(marks); }
    public InlineTextNode(String text) { this(text,List.of()); }
}
