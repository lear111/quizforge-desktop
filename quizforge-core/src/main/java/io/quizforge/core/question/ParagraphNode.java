package io.quizforge.core.question;

import java.util.List;
public record ParagraphNode(List<InlineNode> children) implements BlockNode {
    public ParagraphNode { children = List.copyOf(children); }
}
