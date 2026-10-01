package io.quizforge.core.question.content;

import java.util.List;

public record OrderedListNode(int start,List<ListItemNode> items) implements BlockNode {
    public OrderedListNode {
        if(start<1)throw new IllegalArgumentException("List start must be positive");
        items=List.copyOf(items);
    }
}
