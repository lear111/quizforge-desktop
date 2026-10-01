package io.quizforge.core.question;

import io.quizforge.core.practice.QuestionBankPracticeSession;
import io.quizforge.core.question.content.TextContent;
import io.quizforge.core.question.model.Question;
import io.quizforge.core.question.model.QuestionBank;
import io.quizforge.core.question.source.QuestionSourceDocument;
import io.quizforge.core.question.source.SourceRef;
import io.quizforge.core.question.type.objective.choice.ChoiceAnswerSpec;
import io.quizforge.core.question.type.objective.choice.ChoiceOption;
import io.quizforge.core.question.type.objective.choice.ChoicePayload;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuestionBankPracticeSessionTest {
    private static final String REVISION = "qfd:v1:" + "a".repeat(64);

    static QuestionBank bank() {
        var source = new QuestionSourceDocument("doc_source", REVISION, "Source");
        var ref = SourceRef.anchor("doc_source", REVISION, "section_one", 1, "Source", "One");
        var single = Question.choice("q_single", "SINGLE_CHOICE", new TextContent("Single?"), new TextContent("Because"), List.of(ref), new ChoicePayload(List.of(
                        new ChoiceOption("opt_one", new TextContent("One")),
                        new ChoiceOption("opt_two", new TextContent("Two")))), new ChoiceAnswerSpec(List.of("opt_one")));
        var multiple = Question.choice("q_multiple", "MULTIPLE_CHOICE", new TextContent("Multiple?"), new TextContent("Because"), List.of(ref), new ChoicePayload(List.of(
                        new ChoiceOption("opt_three", new TextContent("Three")),
                        new ChoiceOption("opt_four", new TextContent("Four")),
                        new ChoiceOption("opt_five", new TextContent("Five")))), new ChoiceAnswerSpec(List.of("opt_three", "opt_four")));
        return new QuestionBank("qb_practice", "Practice", "2.0", List.of(), List.of(single, multiple), List.of());
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

    @Test void multipleChoiceRequiresExactSetAndReportsSummary() {
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
        session.next();
        assertEquals(new QuestionBankPracticeSession.Result(2, 1, 1, 50), session.result());
    }

    @Test void summaryAllowsUnsubmittedQuestions() {
        var session = new QuestionBankPracticeSession(bank());
        session.next();
        session.next();
        assertTrue(session.finished());
        assertEquals(new QuestionBankPracticeSession.Result(2, 0, 0, 0), session.result());
        session = new QuestionBankPracticeSession(bank());
        session.select("opt_one");
        session.submit();
        session.next();
        session.next();
        assertTrue(session.finished());
        assertEquals(new QuestionBankPracticeSession.Result(2, 1, 0, 100), session.result());
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
        assertEquals(0, session.index());
    }
}
