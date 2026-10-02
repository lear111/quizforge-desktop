package io.quizforge.core.question.type.objective.reading;

import io.quizforge.core.question.content.DocumentContent;
import io.quizforge.core.question.content.TextContent;
import io.quizforge.core.question.model.Question;
import io.quizforge.core.question.model.QuestionBank;
import io.quizforge.core.question.resource.QBankResource;
import io.quizforge.core.question.resource.ResourceKind;
import io.quizforge.core.question.service.QuestionBankEditorModel;
import io.quizforge.core.question.service.QuestionBankValidator;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReadingQuestionTypeTest {
    private QuestionBankEditorModel model() {
        var model = new QuestionBankEditorModel(new QuestionBank("qb_reading", "Reading", List.of(), List.of(), List.of()));
        model.addQuestion("READING");
        return model;
    }
    @Test void editingAndCopyingPreserveAnswersAndRenumberWithoutReusingIdentity() {
        var model = model();
        var initial = (ReadingPayload) model.bank().questions().getFirst().payload();
        assertEquals(5, initial.items().size());
        assertEquals(new BigDecimal("2"), model.bank().questions().getFirst().scoreSpec().defaultMaxScore());
        var second = initial.items().get(1);
        model.setReadingPrompt(0, second.id(), new TextContent("What is the author's view?"));
        model.setReadingOption(0, second.id(), 2, "Positive");
        model.setReadingCorrect(0, second.id(), second.options().get(2).id());
        model.setStem(0, "A changed passage retains its independent questions.");
        model.deleteReadingItem(0, initial.items().getFirst().id());
        model.addReadingItem(0);
        var edited = (ReadingPayload) model.bank().questions().getFirst().payload();
        assertEquals(second.id(), edited.items().getFirst().id());
        assertEquals(1, edited.items().getFirst().number());
        assertEquals("Positive", ((TextContent) edited.items().getFirst().options().get(2).content()).text());
        assertEquals(second.options().get(2).id(), ((ReadingAnswerSpec) model.bank().questions().getFirst().answerSpec()).answers().getFirst().correctOptionId());
        model.duplicateQuestion(0);
        var copied = (ReadingPayload) model.bank().questions().get(1).payload();
        assertTrue(Collections.disjoint(edited.options().stream().map(option -> option.id()).toList(), copied.options().stream().map(option -> option.id()).toList()));
        assertNotEquals(edited.items().getFirst().id(), copied.items().getFirst().id());
        new QuestionBankValidator().validate(model.bank());
        while (((ReadingPayload) model.bank().questions().getFirst().payload()).items().size() > 1)
            model.deleteReadingItem(0, ((ReadingPayload) model.bank().questions().getFirst().payload()).items().getLast().id());
        assertThrows(IllegalArgumentException.class, () -> model.deleteReadingItem(0, second.id()));
    }
    @Test void validationRejectsTwoChoicesPerItemAndCrossItemCorrectAnswers() {
        var model = model();
        var q = model.bank().questions().getFirst();
        var payload = (ReadingPayload) q.payload();
        ReadingQuestionType.validateSelection(payload, Set.of(payload.items().getFirst().options().getFirst().id()));
        assertThrows(IllegalArgumentException.class, () -> ReadingQuestionType.validateSelection(payload,
                Set.of(payload.items().getFirst().options().get(0).id(), payload.items().getFirst().options().get(1).id())));
        assertThrows(IllegalArgumentException.class, () -> ReadingQuestionType.validateSelection(payload, Set.of("opt_missing")));
        assertThrows(IllegalArgumentException.class, () -> model.setReadingCorrect(0, payload.items().getFirst().id(), payload.items().get(1).options().getFirst().id()));
        var correct = Set.copyOf(((ReadingAnswerSpec) q.answerSpec()).correctOptionIds());
        assertTrue(new ReadingQuestionType().evaluate(q, correct));
        assertFalse(new ReadingQuestionType().evaluate(q, Set.of()));
        var answers = new ArrayList<>(((ReadingAnswerSpec) q.answerSpec()).answers());
        answers.set(0, new ReadingAnswerSpec.Answer(payload.items().getFirst().id(), payload.items().get(1).options().getFirst().id()));
        var invalid = new Question(q.id(), q.type(), q.stimulusRefs(), q.prompt(), q.payload(), new ReadingAnswerSpec(answers),
                q.scoreSpec(), q.evaluationSpec(), q.analysis(), q.sourceRefs());
        assertThrows(RuntimeException.class, () -> new QuestionBankValidator().validate(new QuestionBank("qb_bad", "Bad", List.of(), List.of(invalid), List.of())));
    }
    @Test void subquestionDocumentResourcesRemainUntilTheirLastReferenceIsRemoved() {
        var model = model();
        var resource = new QBankResource("res_shared", ResourceKind.DOCUMENT, "application/vnd.quizforge.canvas+json", "resources/shared.canvas.json", "a".repeat(64));
        model.addResource(resource);
        var content = new DocumentContent(resource.id(), "Question");
        model.setPrompt(0, content);
        var item = ((ReadingPayload) model.bank().questions().getFirst().payload()).items().getFirst();
        model.setReadingPrompt(0, item.id(), content);
        model.setStem(0, "The article is text again.");
        assertEquals(List.of(resource), model.bank().resources());
        new QuestionBankValidator().validate(model.bank());
        model.setReadingPrompt(0, item.id(), new TextContent("The item is text again."));
        assertTrue(model.bank().resources().isEmpty());
    }
}
