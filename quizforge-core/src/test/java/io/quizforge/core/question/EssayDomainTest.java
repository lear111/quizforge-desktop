package io.quizforge.core.question;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class EssayDomainTest {
    @ParameterizedTest @CsvSource({
        "'Hello, world!',2", "'First paragraph.\n\nSecond paragraph.',4",
        "'don''t mother-in-law state-of-the-art',3", "'2026 a1b2',2",
        "'English 中文 words',2", "'-- ... ！',0", "'a—b a/b',4",
        "' leading   spaces ',2", "'it’s everyone’s',2"
    }) void stableEnglishWordCount(String text,int expected) {assertEquals(expected,EnglishWordCount.count(text));}
    @Test void nullAndBlankHaveNoWords() {assertEquals(0,EnglishWordCount.count(null));assertEquals(0,EnglishWordCount.count("\n\t "));}
    @Test void newEssayUsesCommonSkeletonAndDefaultScore() {
        var model=new QuestionBankEditorModel(new QuestionBank("qb_draft","Draft",List.of(),List.of(),List.of()));
        model.addQuestion("ESSAY");var question=model.bank().questions().getFirst();
        assertEquals("ESSAY",question.type());assertEquals(ScoreSpec.defaultScore(),question.scoreSpec());
        assertEquals(new EssayPayload(null),question.essayPayload());assertNull(question.essayAnswerSpec().referenceAnswer());
        assertTrue(question.stimulusRefs().isEmpty());assertNull(question.evaluationSpec());
    }
    @Test void richContentSnapshotAdapterPreservesEveryNodeAndOptionalField() {
        var content=new RichContent(new RichDocument(List.of(new ParagraphNode(List.of(new InlineTextNode("A"),
                new InlineImageNode("res_inline",null),new LineBreakNode(),new LinkNode("https://example.org",List.of(new InlineTextNode("link"))),
                new InlineMathNode("x"))),new BlockImageNode("res_block",null,null),new BlockMathNode("y"))));
        assertEquals(content,QuestionContentData.decode(QuestionContentData.encode(content)));
        assertEquals(java.util.Set.of("res_inline","res_block"),QuestionContentData.imageIds(content));
    }
    @Test void editingGuidancePreservesExistingCriteriaAndEmptyGuidanceIsOptional() {
        var model=new QuestionBankEditorModel(new QuestionBank("qb_draft","Draft",List.of(),List.of(),List.of()));
        model.addQuestion("ESSAY");model.setEvaluatorGuidance(0,"Describe the scoring rules.");
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
