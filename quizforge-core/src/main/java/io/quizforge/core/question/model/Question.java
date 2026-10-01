package io.quizforge.core.question.model;

import io.quizforge.core.question.content.QuestionContent;
import io.quizforge.core.question.source.SourceRef;
import io.quizforge.core.question.type.objective.choice.ChoiceAnswerSpec;
import io.quizforge.core.question.type.objective.choice.ChoicePayload;
import io.quizforge.core.question.type.subjective.essay.EssayAnswerSpec;
import io.quizforge.core.question.type.subjective.essay.EssayPayload;
import java.util.List;

public record Question(String id, String type, List<String> stimulusRefs, QuestionContent prompt,
        QuestionPayload payload, QuestionAnswerSpec answerSpec, ScoreSpec scoreSpec,
        EvaluationSpec evaluationSpec, QuestionContent analysis, List<SourceRef> sourceRefs) {
    public Question {
        stimulusRefs = List.copyOf(stimulusRefs);
        sourceRefs = List.copyOf(sourceRefs);
    }
    public static Question choice(String id, String type, QuestionContent prompt, QuestionContent analysis,
            List<SourceRef> sourceRefs, ChoicePayload payload, ChoiceAnswerSpec answerSpec) {
        return new Question(id, type, List.of(), prompt, payload, answerSpec, ScoreSpec.defaultScore(),
                null, analysis, sourceRefs);
    }
    public ChoicePayload choicePayload() {
        if (payload instanceof ChoicePayload choice) return choice;
        throw new UnsupportedOperationException("Unsupported question payload");
    }
    public EssayPayload essayPayload() {
        if (payload instanceof EssayPayload essay) return essay;
        throw new UnsupportedOperationException("Not an essay payload");
    }
    public EssayAnswerSpec essayAnswerSpec() {
        if (answerSpec instanceof EssayAnswerSpec essay) return essay;
        throw new UnsupportedOperationException("Not an essay answer specification");
    }
    public ChoiceAnswerSpec choiceAnswerSpec() {
        if (answerSpec instanceof ChoiceAnswerSpec choice) return choice;
        throw new UnsupportedOperationException("Unsupported question answer specification");
    }
}
