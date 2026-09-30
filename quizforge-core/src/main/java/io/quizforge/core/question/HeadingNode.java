package io.quizforge.core.question;
import java.util.List;
public record HeadingNode(int level,List<InlineNode> children,TextAlignment alignment) implements BlockNode {
    public HeadingNode {
        if(level<1 || level>3)throw new IllegalArgumentException("Heading level must be 1–3");
        children=List.copyOf(children);
    }
}
