package io.quizforge.core.question.content;

import java.util.List;

public record LinkNode(String href, List<InlineNode> children) implements InlineNode {
    public LinkNode { children = List.copyOf(children); }
}
