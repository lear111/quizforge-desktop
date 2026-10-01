package io.quizforge.core.question.type.objective.choice;

import io.quizforge.core.question.model.Question;
import io.quizforge.core.question.source.SourceRef;
import io.quizforge.core.question.type.QuestionTypeDefinition;
import io.quizforge.core.question.type.QuestionValidationContext;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import static io.quizforge.core.question.type.QuestionValidationContext.reject;

/** Single-choice rules: exactly one correct option and one selected answer. */
public final class SingleChoiceQuestionType implements QuestionTypeDefinition {
    @Override
    public String id() { return "SINGLE_CHOICE"; }

    @Override
    public Family family() { return Family.OBJECTIVE; }

    @Override
    public boolean multipleSelection() { return false; }

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
        // Two options, with the first option as the sole correct answer.
        return ChoiceQuestionSupport.createDraft(id(), 2, 1, newId, sources);
    }

    @Override
    public Question duplicate(Question question, Function<String, String> newId) {
        return ChoiceQuestionSupport.duplicate(question, newId);
    }

    @Override
    public void validate(Question question, QuestionValidationContext context) {
        ChoiceQuestionSupport.validateOptionsAndAnswers(question, context);
        if (question.choiceAnswerSpec().correctOptionIds().size() != 1) {
            reject("Invalid correctOptionIds");
        }
    }

    @Override
    public boolean evaluate(Question question, Set<String> selected) {
        return selected.equals(Set.copyOf(question.choiceAnswerSpec().correctOptionIds()));
    }
}
