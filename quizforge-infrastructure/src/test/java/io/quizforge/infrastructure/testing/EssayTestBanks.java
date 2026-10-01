package io.quizforge.infrastructure.testing;

import io.quizforge.core.question.content.BlockImageNode;
import io.quizforge.core.question.content.InlineTextNode;
import io.quizforge.core.question.content.ParagraphNode;
import io.quizforge.core.question.content.QuestionContent;
import io.quizforge.core.question.content.RichContent;
import io.quizforge.core.question.content.RichDocument;
import io.quizforge.core.question.content.TextContent;
import io.quizforge.core.question.model.Question;
import io.quizforge.core.question.model.QuestionBank;
import io.quizforge.core.question.model.ScoreSpec;
import io.quizforge.core.question.type.subjective.essay.EssayAnswerSpec;
import io.quizforge.core.question.type.subjective.essay.EssayPayload;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;
import javax.imageio.ImageIO;

/** Small synthetic essay fixtures, used only in isolated test directories. */
public final class EssayTestBanks {
    private EssayTestBanks() { }
    public static Question essay(String id, QuestionContent prompt, EssayPayload payload, QuestionContent reference) {
        return new Question(id,"ESSAY",List.of(),prompt,payload,new EssayAnswerSpec(reference),
                new ScoreSpec(new BigDecimal("20.25")),null,null,List.of());
    }
    public static QuestionBank bank() {
        return new QuestionBank("qb_essay","Essay",List.of(),List.of(
                essay("q_essay",new TextContent("Write an essay."),new EssayPayload("Write here"),new TextContent("Sample essay.")),
                essay("q_optional",new TextContent("Write freely."),new EssayPayload(null),null)),List.of());
    }
    public static RichContent prompt(String id) {
        return new RichContent(new RichDocument(List.of(
                new ParagraphNode(List.of(new InlineTextNode("Directions."))),
                new BlockImageNode(id,"Essay illustration",null),
                new ParagraphNode(List.of(new InlineTextNode("Write 160–200 words."))))));
    }
    public static byte[] image(String format) throws Exception {
        var image=new BufferedImage(24,16,BufferedImage.TYPE_INT_RGB);
        var graphics=image.createGraphics();
        graphics.setColor(java.awt.Color.BLUE);graphics.fillRect(0,0,24,16);graphics.dispose();
        var out=new ByteArrayOutputStream();
        if(!ImageIO.write(image,format,out)) throw new IllegalArgumentException(format);
        return out.toByteArray();
    }
}
