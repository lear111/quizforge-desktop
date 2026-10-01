package io.quizforge.desktop.ui.question.objective.choice;

import io.quizforge.core.practice.PracticeHistoryDetail;
import io.quizforge.core.practice.PracticePayload;
import io.quizforge.core.practice.QuestionBankPracticeSession;
import io.quizforge.core.question.type.objective.choice.QuestionText;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The only bridge from question/session entities to the shared rendering models. */
public final class ChoicePresentationMapper {
    private ChoicePresentationMapper() { }

    public static ChoicePresentation practice(QuestionBankPracticeSession session) {
        var question = session.current();
        return new ChoicePresentation(question.type(), QuestionText.prompt(question), question.choicePayload().options().stream()
                .map(option -> new ChoicePresentation.Option(option.id(), QuestionText.option(option))).toList(),
                Set.copyOf(question.choiceAnswerSpec().correctOptionIds()), QuestionText.analysis(question));
    }

    public static ChoiceResultPresentation practiceResult(QuestionBankPracticeSession session) {
        if (session.state() != QuestionBankPracticeSession.State.SUBMITTED)
            throw new IllegalStateException("A submitted answer is required");
        // PracticeRuntimeMapper hydrates these fields from the latest committed Attempt.
        return new ChoiceResultPresentation(practice(session), session.selected(), session.correct()
                ? ChoiceResultPresentation.Result.CORRECT : ChoiceResultPresentation.Result.INCORRECT,
                true, true, true);
    }

    public static ChoicePresentation history(PracticeHistoryDetail.Question question) {
        return new ChoicePresentation(question.questionType(), question.stem(), question.options().stream()
                .map(option -> new ChoicePresentation.Option(option.id(), option.content())).toList(),
                Set.copyOf(question.correctOptionIds()), question.analysis());
    }

    public static ChoiceResultPresentation historyResult(PracticeHistoryDetail.Question question,
            PracticeHistoryDetail.Attempt attempt) {
        return new ChoiceResultPresentation(history(question), answerIds(attempt.answer()),
                ChoiceResultPresentation.Result.valueOf(attempt.result().name()), true, true, true);
    }

    public static Set<String> answerIds(PracticePayload payload) {
        if (!(payload.value() instanceof List<?> values))
            throw new IllegalStateException("Choice answer must be an option ID list");
        Set<String> ids = new HashSet<>();
        for (Object value : values) {
            if (!(value instanceof String id) || !ids.add(id))
                throw new IllegalStateException("Invalid choice option IDs");
        }
        return Set.copyOf(ids);
    }
}
