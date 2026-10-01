package io.quizforge.core.question.type.objective.choice;

import io.quizforge.core.question.model.Question;
import io.quizforge.core.question.source.SourceRef;
import io.quizforge.core.question.type.QuestionTypeDefinition;
import io.quizforge.core.question.type.QuestionValidationContext;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import static io.quizforge.core.question.type.QuestionValidationContext.reject;

/** Multiple-choice rules: multiple correct options and exact-set grading. */
public final class MultipleChoiceQuestionType implements QuestionTypeDefinition {
    @Override
    public String id() { return "MULTIPLE_CHOICE"; }

    @Override
    public Family family() { return Family.OBJECTIVE; }

    @Override
    public boolean multipleSelection() { return true; }

    @Override
    public String payloadKind() { return "CHOICE"; }

    @Override
    public Class<ChoicePayload> payloadClass() { return ChoicePayload.class; }

    @Override
    public String answerKind() { return "CHOICE"; }

    @Override
    public Class<ChoiceAnswerSpec> answerClass() { return ChoiceAnswerSpec.class; }

    @Override
    public Question createDraft(Function<String, String> newId, List<SourceRef> sources) {
        // Three options, with the first two options as the correct answers.
        return ChoiceQuestionSupport.createDraft(id(), 3, 2, newId, sources);
    }

    @Override
    public Question duplicate(Question question, Function<String, String> newId) {
        return ChoiceQuestionSupport.duplicate(question, newId);
    }

    @Override
    public void validate(Question question, QuestionValidationContext context) {
        ChoiceQuestionSupport.validateOptionsAndAnswers(question, context);
        int correctCount = question.choiceAnswerSpec().correctOptionIds().size();
        int optionCount = question.choicePayload().options().size();
        // Preserve the existing rule: at least two correct and one incorrect option.
        if (correctCount < 2 || correctCount >= optionCount) {
            reject("Invalid correctOptionIds");
        }
    }

    @Override
    public boolean evaluate(Question question, Set<String> selected) {
        // Missing or extra selections receive no credit under the current policy.
        return selected.equals(Set.copyOf(question.choiceAnswerSpec().correctOptionIds()));
    }
}
