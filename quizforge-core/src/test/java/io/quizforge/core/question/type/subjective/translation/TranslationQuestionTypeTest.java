package io.quizforge.core.question.type.subjective.translation;

import io.quizforge.core.question.content.DocumentContent;
import io.quizforge.core.question.content.TextContent;
import io.quizforge.core.question.model.*;
import io.quizforge.core.question.resource.*;
import io.quizforge.core.question.service.QuestionBankEditorModel;
import io.quizforge.core.question.service.QuestionBankValidator;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TranslationQuestionTypeTest {
    private QuestionBankEditorModel model() {
        var model = new QuestionBankEditorModel(new QuestionBank("qb_translation", "Translation", List.of(), List.of(), List.of()));
        model.addQuestion("TRANSLATION");
        return model;
    }
    @Test void markersAreSequentialAndRepeatedSentencesAreIndependent() {
        assertEquals(List.of("First.", "Same.", "Same."), TranslationQuestionType.sentences("Intro {{First.}} \\{{literal}} {{Same.}} {{Same.}}"));
        assertEquals(List.of(), TranslationQuestionType.sentences("An ordinary passage."));
        for (var invalid : List.of("{{}}", "{{ \n }}", "{{missing", "missing}}", "{{outer {{inner}} end}}", "\\{{unfinished"))
            assertThrows(IllegalArgumentException.class, () -> TranslationQuestionType.sentences(invalid), invalid);
    }
    @Test void defaultAndDuplicateHaveFiveItemsAndDistinctIdentitiesWithoutAutomaticGrading() {
        var model = model();
        var q = model.bank().questions().getFirst();
        var items = ((TranslationPayload) q.payload()).items();
        assertEquals(5, items.size());
        assertEquals(new BigDecimal("2"), q.scoreSpec().defaultMaxScore());
        model.setTranslationReference(0, items.getFirst().id(), new TextContent("阅读为我们打开一扇通往世界的窗户。"));
        model.duplicateQuestion(0);
        var copy = model.bank().questions().get(1);
        assertNotEquals(q.id(), copy.id());
        assertTrue(Collections.disjoint(items.stream().map(TranslationItem::id).toList(),
                ((TranslationPayload) copy.payload()).items().stream().map(TranslationItem::id).toList()));
        assertEquals(List.of("阅读为我们打开一扇通往世界的窗户。"), ((TranslationAnswerSpec) copy.answerSpec()).referenceAnswers().values().stream()
                .map(content -> ((TextContent) content).text()).toList());
        new QuestionBankValidator().validate(model.bank());
        assertThrows(UnsupportedOperationException.class, () -> new TranslationQuestionType().evaluate(copy, Set.of()));
    }
    @Test void passageEditsMatchUnchangedOccurrencesAndNeverReassignChangedSentenceAnswers() {
        var model = model();
        model.setStem(0, "Intro {{Same.}} {{Different.}} {{Same.}}");
        var previous = ((TranslationPayload) model.bank().questions().getFirst().payload()).items();
        model.setTranslationReference(0, previous.get(0).id(), new TextContent("first"));
        model.setTranslationReference(0, previous.get(1).id(), new TextContent("different"));
        model.setTranslationReference(0, previous.get(2).id(), new TextContent("second"));
        model.setStem(0, "Reordered {{Different.}} {{Same.}} {{New.}} {{Same.}}");
        var q = model.bank().questions().getFirst();
        var edited = ((TranslationPayload) q.payload()).items();
        assertEquals(previous.get(1).id(), edited.get(0).id());
        assertEquals(previous.get(0).id(), edited.get(1).id());
        assertEquals(previous.get(2).id(), edited.get(3).id());
        assertEquals(List.of(1,2,3,4), edited.stream().map(TranslationItem::number).toList());
        assertFalse(((TranslationAnswerSpec) q.answerSpec()).referenceAnswers().containsKey(edited.get(2).id()));
        model.setStem(0, "{{Changed.}}");
        assertTrue(((TranslationAnswerSpec) model.bank().questions().getFirst().answerSpec()).referenceAnswers().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> model.setTranslationReference(0, previous.get(0).id(), new TextContent("stale")));
        var valid = model.bank();
        assertThrows(IllegalArgumentException.class, () -> model.setStem(0, "{{broken"));
        assertSame(valid, model.bank());
        new QuestionBankValidator().validate(model.bank());
    }
    @Test void validatorRejectsMissingMarkersMismatchedTextAndUnrelatedAnswerIdentity() {
        var model = model();
        var q = model.bank().questions().getFirst();
        var items = new ArrayList<>(((TranslationPayload) q.payload()).items());
        items.set(0, new TranslationItem(items.getFirst().id(), 1, "Wrong sentence."));
        assertInvalid(new Question(q.id(), q.type(), q.stimulusRefs(), q.prompt(), new TranslationPayload(items), q.answerSpec(),
                q.scoreSpec(), null, null, List.of()));
        var answers = new ArrayList<>(((TranslationAnswerSpec) q.answerSpec()).answers());
        answers.set(0, new TranslationAnswerSpec.Answer("item_missing", null));
        assertInvalid(new Question(q.id(), q.type(), q.stimulusRefs(), q.prompt(), q.payload(), new TranslationAnswerSpec(answers),
                q.scoreSpec(), null, null, List.of()));
        model.setStem(0, "No marked sentences yet.");
        assertTrue(((TranslationPayload) model.bank().questions().getFirst().payload()).items().isEmpty());
        assertThrows(RuntimeException.class, () -> new QuestionBankValidator().validate(model.bank()));
    }
    @Test void referenceResourcesSurviveSharedUsesAndAreRemovedAfterTheirLastUse() {
        var model = model();
        var resource = new QBankResource("res_ref", ResourceKind.DOCUMENT, "application/vnd.quizforge.canvas+json", "resources/ref.canvas.json", "a".repeat(64));
        model.addResource(resource);
        var items = ((TranslationPayload) model.bank().questions().getFirst().payload()).items();
        var reference = new DocumentContent(resource.id(), "Reference translation");
        model.setTranslationReference(0, items.get(0).id(), reference);
        model.setTranslationReference(0, items.get(1).id(), reference);
        model.setTranslationReference(0, items.get(0).id(), null);
        assertEquals(List.of(resource), model.bank().resources());
        model.setStem(0, "{{" + items.get(2).text() + "}}");
        assertTrue(model.bank().resources().isEmpty());
        model.addResource(resource);
        var surviving = ((TranslationPayload) model.bank().questions().getFirst().payload()).items().getFirst();
        model.setTranslationReference(0, surviving.id(), reference);
        model.duplicateQuestion(0);
        model.setType(0, "ESSAY");
        assertEquals(List.of(resource), model.bank().resources());
        model.deleteQuestion(1);
        assertTrue(model.bank().resources().isEmpty());
        model.setType(0, "TRANSLATION");
        assertEquals(surviving.text(), ((TranslationPayload) model.bank().questions().getFirst().payload()).items().getFirst().text());
        new QuestionBankValidator().validate(model.bank());
    }
    private void assertInvalid(Question question) {
        assertThrows(RuntimeException.class, () -> new QuestionBankValidator().validate(
                new QuestionBank("qb_invalid", "Invalid", List.of(), List.of(question), List.of())));
    }
}
