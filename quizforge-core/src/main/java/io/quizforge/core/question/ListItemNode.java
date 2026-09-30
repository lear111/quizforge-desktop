package io.quizforge.core.question;
import java.util.List;
public record ListItemNode(List<BlockNode> blocks) {
    public ListItemNode { blocks=List.copyOf(blocks); }
}
