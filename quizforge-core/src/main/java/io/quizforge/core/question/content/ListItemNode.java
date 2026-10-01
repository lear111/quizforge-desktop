package io.quizforge.core.question.content;

import java.util.List;

public record ListItemNode(List<BlockNode> blocks) {
    public ListItemNode { blocks=List.copyOf(blocks); }
}
