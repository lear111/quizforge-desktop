package io.quizforge.core.question.content;

import java.util.Objects;
import java.util.stream.Collectors;

/** Plain paragraphs use the lightweight TEXT representation; structured nodes remain RICH. */
public final class QuestionContentNormalizer {
    private QuestionContentNormalizer() { }
    public static QuestionContent normalize(QuestionContent content) {
        Objects.requireNonNull(content);
        if (content instanceof TextContent || content instanceof DocumentContent) return content;
        var document=((RichContent)content).document();
        if (document.blocks().stream().allMatch(block->block instanceof ParagraphNode paragraph
                && paragraph.alignment()==null && paragraph.children().stream().allMatch(node->node instanceof InlineTextNode text && text.marks().isEmpty() || node instanceof LineBreakNode))) {
            return new TextContent(document.blocks().stream().map(block->((ParagraphNode)block).children().stream()
                    .map(node->node instanceof InlineTextNode text?text.text():"\n").collect(Collectors.joining()))
                    .collect(Collectors.joining("\n\n")));
        }
        return content;
    }
}
