package io.quizforge.desktop.ui;

import io.quizforge.core.question.QuestionText;

import io.quizforge.core.practice.PracticeHistoryDetail;
import io.quizforge.core.practice.PracticePayload;
import io.quizforge.core.question.QuestionBankPracticeSession;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The only bridge from question/session entities to the shared rendering models. */
final class QuestionPresentationMapper {
    private QuestionPresentationMapper() { }

    static QuestionPresentation practice(QuestionBankPracticeSession session) {
        var question = session.current();
        return new QuestionPresentation(question.type(), QuestionText.prompt(question), question.choicePayload().options().stream()
                .map(option -> new QuestionPresentation.Option(option.id(), QuestionText.option(option))).toList(),
                Set.copyOf(question.choiceAnswerSpec().correctOptionIds()), QuestionText.analysis(question));
    }

    static QuestionResultPresentation practiceResult(QuestionBankPracticeSession session) {
        if (session.state() != QuestionBankPracticeSession.State.SUBMITTED)
            throw new IllegalStateException("A submitted answer is required");
        // PracticeRuntimeMapper hydrates these fields from the latest committed Attempt.
        return new QuestionResultPresentation(practice(session), session.selected(), session.correct()
                ? QuestionResultPresentation.Result.CORRECT : QuestionResultPresentation.Result.INCORRECT,
                true, true, true);
    }

    static QuestionPresentation history(PracticeHistoryDetail.Question question) {
        return new QuestionPresentation(question.questionType(), question.stem(), question.options().stream()
                .map(option -> new QuestionPresentation.Option(option.id(), option.content())).toList(),
                Set.copyOf(question.correctOptionIds()), question.analysis());
    }

    static QuestionResultPresentation historyResult(PracticeHistoryDetail.Question question,
            PracticeHistoryDetail.Attempt attempt) {
        return new QuestionResultPresentation(history(question), answerIds(attempt.answer()),
                QuestionResultPresentation.Result.valueOf(attempt.result().name()), true, true, true);
    }

    static Set<String> answerIds(PracticePayload payload) {
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
