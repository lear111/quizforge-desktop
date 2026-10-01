package io.quizforge.desktop.ui.question.objective.choice;

import io.quizforge.core.practice.PracticeHistoryDetail;
import io.quizforge.core.practice.PracticePayload;
import io.quizforge.core.practice.PracticeSessionQuestion;
import io.quizforge.core.practice.QuestionAttempt;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

/** Shared immutable values for choice UI contract tests. */
public final class ChoiceTestFixtures {
    private ChoiceTestFixtures(){ }
    public static ChoicePresentation content(String type) {
        return new ChoicePresentation(type, "题干", List.of(
                new ChoicePresentation.Option("opt_a", "选项 A"),
                new ChoicePresentation.Option("opt_b", "选项 B"),
                new ChoicePresentation.Option("opt_c", "选项 C"),
                new ChoicePresentation.Option("opt_d", "选项 D")),
                "SINGLE_CHOICE".equals(type) ? Set.of("opt_a") : Set.of("opt_a", "opt_b"), "题目解析");
    }

    public static PracticeHistoryDetail.Question archived(String type, PracticeSessionQuestion.State state,
            PracticePayload draft, List<PracticeHistoryDetail.Attempt> attempts) {
        var question = content(type);
        return new PracticeHistoryDetail.Question("sq_one", "q_one", 0, type, question.stem(),
                question.options().stream().map(option -> new PracticeHistoryDetail.Option(
                        option.id(), option.content())).toList(), question.correctAnswer().stream().sorted().toList(),
                question.analysis(), new PracticePayload(List.of()), state, draft, attempts);
    }

    public static PracticeHistoryDetail.Attempt attempt(int number, QuestionAttempt.Result result, String... ids) {
        return new PracticeHistoryDetail.Attempt(number,
                number == 1 ? QuestionAttempt.Mode.INITIAL : QuestionAttempt.Mode.RETRY,
                new PracticePayload(List.of(ids)), result, null, null, Instant.parse("2026-09-29T00:00:00Z"));
    }
}
