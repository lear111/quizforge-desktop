package io.quizforge.core.question.type.objective.matching;

import io.quizforge.core.question.content.TextContent;
import io.quizforge.core.question.model.QuestionBank;
import io.quizforge.core.question.service.QuestionBankEditorModel;
import io.quizforge.core.question.service.QuestionBankValidator;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MatchingQuestionTypeTest {
    private QuestionBankEditorModel model() {
        var model = new QuestionBankEditorModel(new QuestionBank("qb_matching", "Matching", List.of(), List.of(), List.of()));
        model.addQuestion("MATCHING"); return model;
    }
    @Test void correctLetterSwapsPreservePermutationAndLockedHints() {
        var model = model(); var q = model.bank().questions().getFirst();
        var payload = (MatchingPayload) q.payload();
        assertEquals(8, payload.blanks().size()); assertEquals(8, payload.options().size());
        assertEquals(new BigDecimal("2"), q.scoreSpec().defaultMaxScore());
        assertEquals(List.of("A", "B", "C", "D", "E", "F", "G", "H"), payload.options().stream().map(MatchingOption::label).toList());
        var first = payload.blanks().get(0); var second = payload.blanks().get(1);
        var a = payload.options().get(0).id(); var b = payload.options().get(1).id();
        assertEquals(3, payload.blanks().stream().filter(MatchingBlank::locked).count());
        assertEquals(5, MatchingQuestionType.gradableCount(q));
        new QuestionBankValidator().validate(model.bank());
        model.setMatchingLocked(0, first.id(), false);
        model.setMatchingCorrect(0, first.id(), b);
        var swapped = ((MatchingAnswerSpec) model.bank().questions().getFirst().answerSpec()).assignments();
        assertEquals(b, swapped.get(first.id())); assertEquals(a, swapped.get(second.id()));
        assertEquals(8, new HashSet<>(swapped.values()).size());
        model.setMatchingLocked(0, first.id(), true);
        assertEquals(b, ((MatchingAnswerSpec) model.bank().questions().getFirst().answerSpec()).assignments().get(first.id()));
        assertThrows(IllegalArgumentException.class, () -> model.setMatchingCorrect(0, first.id(), a));
        assertThrows(IllegalArgumentException.class, () -> model.setMatchingCorrect(0, second.id(), b));
        model.setMatchingLocked(0, first.id(), false);
        model.setMatchingCorrect(0, second.id(), b);
        assertEquals(a, ((MatchingAnswerSpec) model.bank().questions().getFirst().answerSpec()).assignments().get(first.id()));
        model.setMatchingLocked(0, second.id(), true);
        assertThrows(IllegalArgumentException.class, () -> model.setMatchingLocked(0, payload.blanks().get(2).id(), true));
        assertEquals(5, MatchingQuestionType.gradableCount(model.bank().questions().getFirst()));
        model.setMatchingLocked(0, second.id(), false);
        assertThrows(io.quizforge.core.QuizForgeException.class, () -> new QuestionBankValidator().validate(model.bank()));
        model.setMatchingLocked(0, first.id(), true);
        new QuestionBankValidator().validate(model.bank());
    }
    @Test void draftsExcludeLockedSlotsAndReservedLettersAndScoringIgnoresHints() {
        var model = model();
        var q = model.bank().questions().getFirst(); var payload = (MatchingPayload) q.payload();
        var correct = ((MatchingAnswerSpec) q.answerSpec()).assignments();
        var gradable = payload.blanks().stream().filter(slot -> !slot.locked()).toList();
        var draft = new LinkedHashMap<String, String>();
        payload.blanks().stream().filter(slot -> !slot.locked()).forEach(slot -> draft.put(slot.id(), correct.get(slot.id())));
        assertEquals(5, MatchingQuestionType.gradableCount(q));
        assertTrue(MatchingQuestionType.evaluateAssignments(q, draft));
        assertEquals(5, MatchingQuestionType.matchingCount(q, draft));
        MatchingQuestionType.validateAssignments(q, Map.of());
        assertFalse(MatchingQuestionType.evaluateAssignments(q, Map.of()));
        assertEquals(0, MatchingQuestionType.matchingCount(q, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> MatchingQuestionType.validateAssignments(q, Map.of(payload.blanks().get(0).id(), payload.options().get(0).id())));
        assertThrows(IllegalArgumentException.class, () -> MatchingQuestionType.validateAssignments(q, Map.of(payload.blanks().get(2).id(), payload.options().get(0).id())));
        var swapped = new LinkedHashMap<>(draft);
        swapped.put(gradable.get(0).id(), correct.get(gradable.get(1).id()));
        swapped.put(gradable.get(1).id(), correct.get(gradable.get(0).id()));
        assertEquals(3, MatchingQuestionType.matchingCount(q, swapped));
        assertFalse(MatchingQuestionType.evaluateAssignments(q, swapped));
        var repeated = Map.of(gradable.get(0).id(), payload.options().get(2).id(), gradable.get(1).id(), payload.options().get(2).id());
        MatchingQuestionType.validateAssignments(q, repeated);
        assertEquals(1, MatchingQuestionType.matchingCount(q, repeated));
        assertFalse(MatchingQuestionType.evaluateAssignments(q, repeated));
        var repeatedStandard = new LinkedHashMap<>(correct);
        repeatedStandard.put(gradable.get(0).id(), correct.get(gradable.get(1).id()));
        assertThrows(IllegalArgumentException.class, () -> MatchingQuestionType.validateCorrectAssignments(payload, repeatedStandard));
        assertThrows(IllegalArgumentException.class, () -> MatchingQuestionType.validateAssignments(q, Map.of("blank_missing", payload.options().get(2).id())));
        assertThrows(IllegalArgumentException.class, () -> MatchingQuestionType.validateCorrectAssignments(payload, draft));
    }
    @Test void ordinaryPromptDoesNotParseMarkersAndCopyPreservesLocksWithFreshIds() {
        var model = model(); var initial = (MatchingPayload) model.bank().questions().getFirst().payload();
        model.setMatchingLocked(0, initial.blanks().get(0).id(), true);
        model.setStem(0, "Entire article and options A to H, including literal {{99}} and {{invalid}}.");
        var changed = model.bank().questions().getFirst();
        assertEquals(8, ((MatchingPayload) changed.payload()).blanks().size());
        assertEquals(initial.blanks().getFirst().id(), ((MatchingPayload) changed.payload()).blanks().getFirst().id());
        assertEquals("Entire article and options A to H, including literal {{99}} and {{invalid}}.", ((TextContent) changed.prompt()).text());
        model.duplicateQuestion(0);
        var copy = (MatchingPayload) model.bank().questions().get(1).payload();
        assertTrue(copy.blanks().getFirst().locked());
        assertEquals(initial.options().stream().map(MatchingOption::label).toList(), copy.options().stream().map(MatchingOption::label).toList());
        assertTrue(Collections.disjoint(initial.options().stream().map(MatchingOption::id).toList(), copy.options().stream().map(MatchingOption::id).toList()));
        assertTrue(Collections.disjoint(initial.blanks().stream().map(MatchingBlank::id).toList(), copy.blanks().stream().map(MatchingBlank::id).toList()));
        new QuestionBankValidator().validate(model.bank());
    }
}
