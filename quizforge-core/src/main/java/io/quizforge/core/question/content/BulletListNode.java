package io.quizforge.core.question.content;

import java.util.List;

public record BulletListNode(List<ListItemNode> items) implements BlockNode {
    public BulletListNode { items=List.copyOf(items); }
}
