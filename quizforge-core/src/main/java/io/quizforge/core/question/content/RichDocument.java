package io.quizforge.core.question.content;

import java.util.List;

public record RichDocument(List<BlockNode> blocks) {
    public RichDocument { blocks = List.copyOf(blocks); }
}
