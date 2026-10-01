package io.quizforge.core.question.type.subjective.essay;

import io.quizforge.core.question.content.TextContent;
import io.quizforge.core.question.model.Question;
import io.quizforge.core.question.model.ScoreSpec;
import io.quizforge.core.question.source.SourceRef;
import io.quizforge.core.question.type.QuestionTypeDefinition;
import io.quizforge.core.question.type.QuestionValidationContext;
import java.util.List;
import java.util.function.Function;
import static io.quizforge.core.question.type.QuestionValidationContext.reject;

public final class EssayQuestionType implements QuestionTypeDefinition {
    public String id(){return "ESSAY";}
    public Family family(){return Family.SUBJECTIVE;}
    public String payloadKind(){return "ESSAY";}
    public Class<EssayPayload> payloadClass(){return EssayPayload.class;}
    public String answerKind(){return "ESSAY";}
    public Class<EssayAnswerSpec> answerClass(){return EssayAnswerSpec.class;}
    public Question createDraft(Function<String,String> newId,List<SourceRef> sources) {
        return new Question(newId.apply("q_"),id(),List.of(),new TextContent("New essay question"),new EssayPayload(null),
                new EssayAnswerSpec(null),ScoreSpec.defaultScore(),null,null,sources);
    }
    public Question duplicate(Question question,Function<String,String> newId) {
        return new Question(newId.apply("q_"),question.type(),question.stimulusRefs(),question.prompt(),question.payload(),
                question.answerSpec(),question.scoreSpec(),question.evaluationSpec(),question.analysis(),question.sourceRefs());
    }
    public void validate(Question question,QuestionValidationContext context) {
        if(!(question.payload() instanceof EssayPayload) || !(question.answerSpec() instanceof EssayAnswerSpec))
            reject("ESSAY requires EssayPayload and EssayAnswerSpec");
        if(question.essayAnswerSpec().referenceAnswer()!=null)context.content().accept(question.essayAnswerSpec().referenceAnswer(),false);
    }
}
