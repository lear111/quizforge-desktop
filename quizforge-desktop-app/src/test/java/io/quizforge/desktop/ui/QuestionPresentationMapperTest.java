package io.quizforge.desktop.ui;

import static org.junit.jupiter.api.Assertions.*;

import io.quizforge.core.practice.PracticeHistoryDetail;
import io.quizforge.core.practice.PracticePayload;
import io.quizforge.core.practice.PracticeSessionQuestion;
import io.quizforge.core.practice.QuestionAttempt;
import io.quizforge.core.question.QuestionBankFile;
import io.quizforge.core.question.QuestionBankPracticeSession;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class QuestionPresentationMapperTest {
    static QuestionPresentation content(String type) {
        return new QuestionPresentation(type, "题干", List.of(
                new QuestionPresentation.Option("opt_a", "选项 A"),
                new QuestionPresentation.Option("opt_b", "选项 B"),
                new QuestionPresentation.Option("opt_c", "选项 C"),
                new QuestionPresentation.Option("opt_d", "选项 D")),
                "SINGLE_CHOICE".equals(type) ? Set.of("opt_a") : Set.of("opt_a", "opt_b"), "题目解析");
    }

    private QuestionBankPracticeSession practice(String type) {
        var question = content(type);
        String revision = "qfd:v1:" + "a".repeat(64);
        var source = new QuestionBankFile.SourceDocument("doc_source", revision, "来源");
        var ref = new QuestionBankFile.SourceRef("doc_source", revision, "section_one", "来源", "小节");
        var entry = new QuestionBankFile.Entry("q_one", type, question.stem(), question.analysis(), List.of(ref),
                new QuestionBankFile.Data(question.options().stream()
                        .map(option -> new QuestionBankFile.Option(option.id(), option.content())).toList(),
                        question.correctAnswer().stream().sorted().toList()));
        return new QuestionBankPracticeSession(new QuestionBankFile("quizforge-question-bank", "1.0",
                "qb_test", "题库", List.of(source), List.of(entry)));
    }

    static PracticeHistoryDetail.Question archived(String type, PracticeSessionQuestion.State state,
            PracticePayload draft, List<PracticeHistoryDetail.Attempt> attempts) {
        var question = content(type);
        return new PracticeHistoryDetail.Question("sq_one", "q_one", 0, type, question.stem(),
                question.options().stream().map(option -> new PracticeHistoryDetail.Option(
                        option.id(), option.content())).toList(), question.correctAnswer().stream().sorted().toList(),
                question.analysis(), new PracticePayload(List.of()), state, draft, attempts);
    }

    static PracticeHistoryDetail.Attempt attempt(int number, QuestionAttempt.Result result, String... ids) {
        return new PracticeHistoryDetail.Attempt(number,
                number == 1 ? QuestionAttempt.Mode.INITIAL : QuestionAttempt.Mode.RETRY,
                new PracticePayload(List.of(ids)), result, null, null, Instant.parse("2026-09-29T00:00:00Z"));
    }

    @Test void presentationDefensivelyCopiesContentAndAnswerSelections() {
        var options = new ArrayList<>(content("SINGLE_CHOICE").options());
        var correct = new HashSet<>(Set.of("opt_a"));
        var selected = new HashSet<>(Set.of("opt_b"));
        var question = new QuestionPresentation("SINGLE_CHOICE", "题干", options, correct, "解析");
        var result = new QuestionResultPresentation(question, selected,
                QuestionResultPresentation.Result.INCORRECT, true, true, true);
        options.clear(); correct.clear(); selected.clear();
        assertEquals(4, question.options().size());
        assertEquals(Set.of("opt_a"), question.correctAnswer());
        assertEquals(Set.of("opt_b"), result.userAnswer());
        assertThrows(UnsupportedOperationException.class, () -> result.userAnswer().add("opt_c"));
    }

    @Test void practiceAndArchiveMapToTheSameIndependentContent() {
        for (String type : List.of("SINGLE_CHOICE", "MULTIPLE_CHOICE")) {
            var session = practice(type);
            var archived = archived(type, PracticeSessionQuestion.State.UNANSWERED, null, List.of());
            assertEquals(QuestionPresentationMapper.practice(session), QuestionPresentationMapper.history(archived));
            assertEquals(QuestionBankPracticeSession.State.UNANSWERED, session.state());
            assertEquals(0, session.index());
        }
    }

    @Test void practiceResultUsesHydratedCommittedAnswerRatherThanCorrectAnswerAsSelection() {
        var session = practice("SINGLE_CHOICE");
        session.restoreState(0, Map.of(0, Set.of("opt_b")), Map.of(0, false), false);
        var result = QuestionPresentationMapper.practiceResult(session);
        assertEquals(Set.of("opt_b"), result.userAnswer());
        assertEquals(Set.of("opt_a"), result.question().correctAnswer());
        assertEquals(QuestionResultPresentation.Result.INCORRECT, result.result());
        assertTrue(result.showCorrectAnswer() && result.showAnalysis() && result.showSources());
    }

    @Test void selectedOrRetryingDraftCannotBePresentedAsASubmittedResult() {
        var session = practice("MULTIPLE_CHOICE");
        session.restoreState(0, Map.of(0, Set.of("opt_a")), Map.of(), false);
        assertThrows(IllegalStateException.class, () -> QuestionPresentationMapper.practiceResult(session));
        assertEquals(Set.of("opt_a"), session.selected());
    }

    @Test void historyUsesSelectedAttemptAndKeepsFinalRetryingStateSeparate() {
        var first = attempt(1, QuestionAttempt.Result.INCORRECT, "opt_c");
        var second = attempt(2, QuestionAttempt.Result.CORRECT, "opt_a", "opt_b");
        var draft = new PracticePayload(List.of("opt_d"));
        var archived = archived("MULTIPLE_CHOICE", PracticeSessionQuestion.State.RETRYING,
                draft, List.of(first, second));
        var earlier = QuestionPresentationMapper.historyResult(archived, first);
        var latest = QuestionPresentationMapper.historyResult(archived, second);
        assertEquals(Set.of("opt_c"), earlier.userAnswer());
        assertEquals(QuestionResultPresentation.Result.INCORRECT, earlier.result());
        assertEquals(Set.of("opt_a", "opt_b"), latest.userAnswer());
        assertEquals(QuestionResultPresentation.Result.CORRECT, latest.result());
        assertEquals(PracticeSessionQuestion.State.RETRYING, archived.finalState());
        assertEquals(draft, archived.draftAnswer());
        assertEquals(List.of(first, second), archived.attempts());
    }

    @Test void historyPreservesUnscoredResultWithoutInventingCorrectness() {
        var attempt = attempt(1, QuestionAttempt.Result.UNSCORED, "opt_b");
        var archived = archived("SINGLE_CHOICE", PracticeSessionQuestion.State.SUBMITTED, null, List.of(attempt));
        assertEquals(QuestionResultPresentation.Result.UNSCORED,
                QuestionPresentationMapper.historyResult(archived, attempt).result());
        assertThrows(NullPointerException.class, () -> new QuestionResultPresentation(content("SINGLE_CHOICE"),
                Set.of(), null, true, true, true));
    }

    @Test void answerLabelsFollowOptionOrderForMultipleAnswersAndEmptyDraft() {
        var content = content("MULTIPLE_CHOICE");
        assertEquals("A、C", content.answerLabels(Set.of("opt_c", "opt_a")));
        assertEquals("—", content.answerLabels(Set.of()));
    }

    @Test void answerPayloadRejectsMalformedOrDuplicateIdsInsteadOfProducingFakeAnswer() {
        assertThrows(IllegalStateException.class, () -> QuestionPresentationMapper.answerIds(new PracticePayload("opt_a")));
        assertThrows(IllegalStateException.class, () -> QuestionPresentationMapper.answerIds(new PracticePayload(List.of(1))));
        assertThrows(IllegalStateException.class, () -> QuestionPresentationMapper.answerIds(
                new PracticePayload(List.of("opt_a", "opt_a"))));
        assertEquals(Set.of(), QuestionPresentationMapper.answerIds(new PracticePayload(List.of())));
    }
}
