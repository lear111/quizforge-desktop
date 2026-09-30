package io.quizforge.core.question;

import java.util.List;
public record ParagraphNode(List<InlineNode> children, TextAlignment alignment) implements BlockNode {
    public ParagraphNode { children = List.copyOf(children); }
    public ParagraphNode(List<InlineNode> children) { this(children,null); }
}
