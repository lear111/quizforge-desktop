package io.quizforge.core.question;

import io.quizforge.core.question.content.BlockImageNode;
import io.quizforge.core.question.content.BlockMathNode;
import io.quizforge.core.question.content.InlineImageNode;
import io.quizforge.core.question.content.InlineTextNode;
import io.quizforge.core.question.content.LineBreakNode;
import io.quizforge.core.question.content.LinkNode;
import io.quizforge.core.question.content.ParagraphNode;
import io.quizforge.core.question.content.QuestionContentNormalizer;
import io.quizforge.core.question.content.RichContent;
import io.quizforge.core.question.content.RichDocument;
import io.quizforge.core.question.content.TextContent;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuestionContentNormalizerTest {
    @Test void textRemainsTextIncludingWhitespaceAndMarkup(){
        var text=new TextContent("<b>not HTML</b>\n\n中文  ");assertSame(text,QuestionContentNormalizer.normalize(text));
    }
    @Test void plainParagraphsAndBreaksBecomeCanonicalText(){
        var content=new RichContent(new RichDocument(List.of(new ParagraphNode(List.of(new InlineTextNode("One"),new LineBreakNode(),new InlineTextNode("line"))),
                new ParagraphNode(List.of(new InlineTextNode("Two"))))));
        assertEquals(new TextContent("One\nline\n\nTwo"),QuestionContentNormalizer.normalize(content));
    }
    @Test void structuredInlineAndBlockNodesRemainRich(){
        for(var block:List.of(new BlockImageNode("res_image",null,null),new BlockMathNode("x"),new ParagraphNode(List.of(new InlineImageNode("res_image",null))),
                new ParagraphNode(List.of(new LinkNode("https://example.org",List.of(new InlineTextNode("Link"))))))) {
            var content=new RichContent(new RichDocument(List.of(block)));assertSame(content,QuestionContentNormalizer.normalize(content));
        }
    }
    @Test void removingLastImageAllowsTextAgain(){
        var content=new RichContent(new RichDocument(List.of(new ParagraphNode(List.of(new InlineTextNode("Before"))),new ParagraphNode(List.of(new InlineTextNode("After"))))));
        assertEquals(new TextContent("Before\n\nAfter"),QuestionContentNormalizer.normalize(content));
    }
}
