package io.quizforge.core.question.model;

import io.quizforge.core.question.content.QuestionContent;
import io.quizforge.core.question.source.SourceRef;
import io.quizforge.core.question.model.choice.ChoiceAnswerSpec;
import io.quizforge.core.question.model.choice.ChoicePayload;
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
    public ChoiceAnswerSpec choiceAnswerSpec() {
        if (answerSpec instanceof ChoiceAnswerSpec choice) return choice;
        throw new UnsupportedOperationException("Unsupported question answer specification");
    }
}
