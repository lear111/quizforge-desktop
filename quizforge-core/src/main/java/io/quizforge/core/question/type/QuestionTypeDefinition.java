package io.quizforge.core.question.type;

import io.quizforge.core.question.model.Question;
import io.quizforge.core.question.model.QuestionAnswerSpec;
import io.quizforge.core.question.model.QuestionPayload;
import io.quizforge.core.question.source.SourceRef;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/** Data and rules for one registered type; independent of JSON, UI and persistence. */
public interface QuestionTypeDefinition {
    enum Family { OBJECTIVE, SUBJECTIVE }
    String id();
    Family family();
    String payloadKind();
    Class<? extends QuestionPayload> payloadClass();
    String answerKind();
    Class<? extends QuestionAnswerSpec> answerClass();
    /** Selection behavior for types reusing the existing choice response model. */
    default boolean multipleSelection() { return false; }
    void validate(Question question,QuestionValidationContext context);
    Question createDraft(Function<String,String> newId,List<SourceRef> sources);
    Question duplicate(Question question,Function<String,String> newId);
    default boolean evaluate(Question question,Set<String> selected) {
        throw new UnsupportedOperationException("This type requires a separate grading workflow");
    }
}
