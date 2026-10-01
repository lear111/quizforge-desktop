package io.quizforge.core.question.type;

import io.quizforge.core.QuizForgeException;
import io.quizforge.core.question.content.TextContent;
import io.quizforge.core.question.model.Question;
import io.quizforge.core.question.model.QuestionBank;
import io.quizforge.core.question.service.QuestionBankValidator;
import io.quizforge.core.question.type.objective.choice.ChoiceAnswerSpec;
import io.quizforge.core.question.type.objective.choice.ChoiceOption;
import io.quizforge.core.question.type.objective.choice.ChoicePayload;
import io.quizforge.core.question.type.objective.choice.MultipleChoiceQuestionType;
import io.quizforge.core.question.type.objective.choice.SingleChoiceQuestionType;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChoiceQuestionTypesTest {
    @Test
    void registryDispatchesToIndependentRulesAndSelectionBehaviors() {
        var single = QuestionTypes.require("SINGLE_CHOICE");
        var multiple = QuestionTypes.require("MULTIPLE_CHOICE");
        assertInstanceOf(SingleChoiceQuestionType.class, single);
        assertInstanceOf(MultipleChoiceQuestionType.class, multiple);
        assertTrue(QuestionTypes.isSingleChoice(single.id()));
        assertFalse(QuestionTypes.isSingleChoice(multiple.id()));
        assertFalse(single.multipleSelection());
        assertTrue(multiple.multipleSelection());
        assertSame(single.payloadClass(), multiple.payloadClass());
        assertSame(single.answerClass(), multiple.answerClass());
    }

    @Test
    void singleChoiceRequiresExactlyOneCorrectAnswer() {
        assertDoesNotThrow(() -> validate(question("SINGLE_CHOICE", List.of("opt_c"))));
        assertThrows(QuizForgeException.class, () -> validate(question("SINGLE_CHOICE", List.of())));
        assertThrows(QuizForgeException.class,
                () -> validate(question("SINGLE_CHOICE", List.of("opt_a", "opt_b"))));
    }

    @Test
    void singleChoiceRejectsEmptyWrongAndExtraSelections() {
        var question = question("SINGLE_CHOICE", List.of("opt_c"));
        var type = QuestionTypes.require(question.type());
        assertTrue(type.evaluate(question, Set.of("opt_c")));
        assertFalse(type.evaluate(question, Set.of()));
        assertFalse(type.evaluate(question, Set.of("opt_a")));
        assertFalse(type.evaluate(question, Set.of("opt_a", "opt_c")));
    }

    @Test
    void multipleChoicePreservesExistingCorrectAnswerCountPolicy() {
        assertDoesNotThrow(() -> validate(question("MULTIPLE_CHOICE", List.of("opt_b", "opt_a"))));
        assertThrows(QuizForgeException.class, () -> validate(question("MULTIPLE_CHOICE", List.of())));
        assertThrows(QuizForgeException.class,
                () -> validate(question("MULTIPLE_CHOICE", List.of("opt_a"))));
        assertThrows(QuizForgeException.class,
                () -> validate(question("MULTIPLE_CHOICE", List.of("opt_a", "opt_b", "opt_c"))));
    }

    @Test
    void multipleChoiceGradingIgnoresOrderButRejectsMissingOrExtraSelections() {
        var question = question("MULTIPLE_CHOICE", List.of("opt_b", "opt_a"));
        var type = QuestionTypes.require(question.type());
        assertTrue(type.evaluate(question, Set.of("opt_a", "opt_b")));
        assertFalse(type.evaluate(question, Set.of("opt_a")));
        assertFalse(type.evaluate(question, Set.of("opt_a", "opt_b", "opt_c")));
        assertFalse(type.evaluate(question, Set.of("opt_a", "opt_c")));
        assertFalse(type.evaluate(question, Set.of()));
    }

    @Test
    void bothTypesRejectDuplicateAndDanglingAnswerIdsAndDuplicateOptionIds() {
        for (var typeId : List.of("SINGLE_CHOICE", "MULTIPLE_CHOICE")) {
            assertThrows(QuizForgeException.class,
                    () -> validate(question(typeId, List.of("opt_a", "opt_a"))));
            var missingAnswer = typeId.equals("SINGLE_CHOICE")
                    ? List.of("opt_missing") : List.of("opt_a", "opt_missing");
            assertThrows(QuizForgeException.class, () -> validate(question(typeId, missingAnswer)));
            var validAnswerCount = typeId.equals("SINGLE_CHOICE")
                    ? List.of("opt_a") : List.of("opt_a", "opt_c");
            var duplicateOptions = Question.choice("q_duplicate", typeId, new TextContent("Question"),
                    null, List.of(), new ChoicePayload(List.of(
                            new ChoiceOption("opt_a", new TextContent("A")),
                            new ChoiceOption("opt_a", new TextContent("B")),
                            new ChoiceOption("opt_c", new TextContent("C")))),
                    new ChoiceAnswerSpec(validAnswerCount));
            assertThrows(QuizForgeException.class, () -> validate(duplicateOptions));
        }
    }

    private static Question question(String typeId, List<String> correctIds) {
        return Question.choice("q_rules", typeId, new TextContent("Question"), null, List.of(),
                new ChoicePayload(List.of(
                        new ChoiceOption("opt_a", new TextContent("A")),
                        new ChoiceOption("opt_b", new TextContent("B")),
                        new ChoiceOption("opt_c", new TextContent("C")))), new ChoiceAnswerSpec(correctIds));
    }

    private static void validate(Question question) {
        new QuestionBankValidator().validate(new QuestionBank("qb_rules", "Rules",
                List.of(), List.of(question), List.of()));
    }
}
