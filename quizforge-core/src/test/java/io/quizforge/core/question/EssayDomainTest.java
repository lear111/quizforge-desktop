package io.quizforge.core.question;

import io.quizforge.core.question.content.BlockImageNode;
import io.quizforge.core.question.content.BlockMathNode;
import io.quizforge.core.question.content.InlineImageNode;
import io.quizforge.core.question.content.InlineMathNode;
import io.quizforge.core.question.content.InlineTextNode;
import io.quizforge.core.question.content.LineBreakNode;
import io.quizforge.core.question.content.LinkNode;
import io.quizforge.core.question.content.ParagraphNode;
import io.quizforge.core.question.content.QuestionContentData;
import io.quizforge.core.question.content.RichContent;
import io.quizforge.core.question.content.RichDocument;
import io.quizforge.core.question.model.EvaluationCriterion;
import io.quizforge.core.question.model.EvaluationSpec;
import io.quizforge.core.question.model.Question;
import io.quizforge.core.question.model.QuestionBank;
import io.quizforge.core.question.model.ScoreSpec;
import io.quizforge.core.question.service.QuestionBankEditorModel;
import io.quizforge.core.question.model.extension.ExtensionPayload;
import io.quizforge.core.question.model.extension.ExtensionAnswerSpec;
import io.quizforge.core.question.content.TextContent;
import java.util.Map;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EssayDomainTest {
    @Test void richContentSnapshotAdapterPreservesEveryNodeAndOptionalField() {
        var content=new RichContent(new RichDocument(List.of(new ParagraphNode(List.of(new InlineTextNode("A"),
                new InlineImageNode("res_inline",null),new LineBreakNode(),new LinkNode("https://example.org",List.of(new InlineTextNode("link"))),
                new InlineMathNode("x"))),new BlockImageNode("res_block",null,null),new BlockMathNode("y"))));
        assertEquals(content,QuestionContentData.decode(QuestionContentData.encode(content)));
        assertEquals(java.util.Set.of("res_inline","res_block"),QuestionContentData.imageIds(content));
    }
    @Test void editingGuidancePreservesExistingCriteriaAndEmptyGuidanceIsOptional() {
        var model=new QuestionBankEditorModel(new QuestionBank("qb_draft","Draft",List.of(),List.of(new Question("q_essay","ESSAY",List.of(),new TextContent("Write"),new ExtensionPayload(Map.of()),new ExtensionAnswerSpec(Map.of()),ScoreSpec.defaultScore(),null,null,List.of())),List.of()));
        model.setEvaluatorGuidance(0,"Describe the scoring rules.");
        assertEquals("Describe the scoring rules.",model.bank().questions().getFirst().evaluationSpec().evaluatorGuidance());
        model.setEvaluatorGuidance(0,"");assertNull(model.bank().questions().getFirst().evaluationSpec());
        var question=model.bank().questions().getFirst();
        var criteria=List.of(new EvaluationCriterion("content","Content",java.math.BigDecimal.ONE));
        var withCriteria=new Question(question.id(),question.type(),question.stimulusRefs(),question.prompt(),question.payload(),question.answerSpec(),question.scoreSpec(),
                new EvaluationSpec(criteria,"Before"),question.analysis(),question.sourceRefs());
        model=new QuestionBankEditorModel(new QuestionBank("qb_draft","Draft",List.of(),List.of(withCriteria),List.of()));
        model.setEvaluatorGuidance(0,"");assertEquals(criteria,model.bank().questions().getFirst().evaluationSpec().criteria());
        assertNull(model.bank().questions().getFirst().evaluationSpec().evaluatorGuidance());
    }
}
