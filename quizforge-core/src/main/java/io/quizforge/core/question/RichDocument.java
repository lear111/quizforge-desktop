package io.quizforge.core.question;

import java.util.List;
public record RichDocument(List<BlockNode> blocks) {
    public RichDocument { blocks = List.copyOf(blocks); }
}
