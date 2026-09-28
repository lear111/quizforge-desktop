package io.quizforge.core.question;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class QuestionBankPracticeSessionTest {
    private static final String REVISION = "qfd:v1:" + "a".repeat(64);

    static QuestionBankFile bank() {
        var source = new QuestionBankFile.SourceDocument("doc_source", REVISION, "Source");
        var ref = new QuestionBankFile.SourceRef("doc_source", REVISION, "section_one", "Source", "One");
        var single = new QuestionBankFile.Entry("q_single", "SINGLE_CHOICE", "Single?", "Because",
                List.of(ref), new QuestionBankFile.Data(List.of(
                        new QuestionBankFile.Option("opt_one", "One"),
                        new QuestionBankFile.Option("opt_two", "Two")), List.of("opt_one")));
        var multiple = new QuestionBankFile.Entry("q_multiple", "MULTIPLE_CHOICE", "Multiple?", "Because",
                List.of(ref), new QuestionBankFile.Data(List.of(
                        new QuestionBankFile.Option("opt_three", "Three"),
                        new QuestionBankFile.Option("opt_four", "Four"),
                        new QuestionBankFile.Option("opt_five", "Five")),
                        List.of("opt_three", "opt_four")));
        return new QuestionBankFile("quizforge-question-bank", "1.0", "qb_practice", "Practice",
                List.of(source), List.of(single, multiple));
    }

    @Test void stateTransitionsAndSingleChoiceAreLocal() {
        var session = new QuestionBankPracticeSession(bank());
        assertEquals(QuestionBankPracticeSession.State.UNANSWERED, session.state());
        assertThrows(IllegalStateException.class, session::submit);
        assertThrows(IllegalArgumentException.class, () -> session.select("opt_unknown"));
        session.select("opt_two");
        assertEquals(QuestionBankPracticeSession.State.SELECTED, session.state());
        session.select("opt_one");
        assertEquals(java.util.Set.of("opt_one"), session.selected());
        assertTrue(session.submit());
        assertEquals(QuestionBankPracticeSession.State.SUBMITTED, session.state());
        assertThrows(IllegalStateException.class, () -> session.select("opt_two"));
        assertThrows(IllegalStateException.class, session::result);
    }

    @Test void multipleChoiceRequiresExactSetAndResultCanRestart() {
        var session = new QuestionBankPracticeSession(bank());
        session.select("opt_two");
        assertFalse(session.submit());
        session.next();
        session.select("opt_three");
        session.select("opt_four");
        session.select("opt_four");
        assertEquals(QuestionBankPracticeSession.State.SELECTED, session.state());
        session.select("opt_four");
        assertTrue(session.submit());
        assertTrue(session.canFinish());
        session.next();
        assertEquals(new QuestionBankPracticeSession.Result(2, 1, 1, 50), session.result());
        session.restart();
        assertEquals(0, session.index());
        assertEquals(QuestionBankPracticeSession.State.UNANSWERED, session.state());
        assertFalse(session.finished());
    }

    @Test void finishRequiresEveryQuestionSubmitted() {
        var session = new QuestionBankPracticeSession(bank());
        session.next();
        assertThrows(IllegalStateException.class, session::next);
        session.previous();
        session.select("opt_one");
        session.submit();
        session.next();
        assertFalse(session.canFinish());
        assertThrows(IllegalStateException.class, session::next);
    }

    @Test void inspectingOtherQuestionsDoesNotNavigateOrChangeSelectionsAndResults() {
        var session = new QuestionBankPracticeSession(bank());
        session.select("opt_two");
        session.submit();
        session.next();
        session.select("opt_three");
        assertEquals(QuestionBankPracticeSession.State.SUBMITTED, session.state(0));
        assertFalse(session.correct(0));
        assertEquals(QuestionBankPracticeSession.State.SELECTED, session.state(1));
        assertThrows(IllegalStateException.class, () -> session.correct(1));
        assertEquals(1, session.index());
        assertEquals(java.util.Set.of("opt_three"), session.selected());
        session.select("opt_four");
        session.submit();
        session.previous();
        assertTrue(session.correct(1));
        assertFalse(session.correct());
        assertEquals(0, session.index());
    }

    @Test void readOnlyInspectionChecksBoundsAndDoesNotSubmitAnUnansweredQuestion() {
        var session = new QuestionBankPracticeSession(bank());
        assertEquals(QuestionBankPracticeSession.State.UNANSWERED, session.state(1));
        assertThrows(IndexOutOfBoundsException.class, () -> session.state(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> session.correct(2));
        assertThrows(IllegalStateException.class, () -> session.correct(0));
        assertFalse(session.canFinish());
        assertEquals(0, session.index());
    }
}
