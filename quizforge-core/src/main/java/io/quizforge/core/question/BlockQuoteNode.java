package io.quizforge.core.question;
import java.util.List;
public record BlockQuoteNode(List<BlockNode> blocks) implements BlockNode {
    public BlockQuoteNode { blocks=List.copyOf(blocks); }
}
