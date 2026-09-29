package io.quizforge.core.question;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class QuestionBankEditorModelTest {
    @Test void editsPreserveBankAndExistingQuestionIdentity() {
        var original = QuestionBankPracticeSessionTest.bank();
        var edit = new QuestionBankEditorModel(original);
        assertFalse(edit.dirty());
        edit.setTitle("Edited title");
        edit.setStem(0, "Edited stem");
        edit.setAnalysis(0, "Edited analysis");
        edit.setOptionContent(0, 0, "Edited option");
        edit.addOption(0);
        String added = edit.bank().questions().getFirst().choicePayload().options().getLast().id();
        edit.setCorrect(0, added, true);
        assertEquals(List.of(added), edit.bank().questions().getFirst().choiceAnswerSpec().correctOptionIds());
        edit.deleteOption(0, 2);
        assertTrue(edit.bank().questions().getFirst().choiceAnswerSpec().correctOptionIds().isEmpty());
        edit.setCorrect(0, "opt_two", true);
        new QuestionBankValidator().validate(edit.bank());
        assertTrue(edit.dirty());
        assertEquals(original.assetId(), edit.bank().assetId());
        assertEquals(original.questions().getFirst().id(), edit.bank().questions().getFirst().id());
        assertEquals(new TextContent("Edited option"), edit.bank().questions().getFirst().choicePayload().options().getFirst().content());
        assertEquals("Single?", QuestionText.prompt(original.questions().getFirst()));
    }

    @Test void newDuplicateAndDeleteManageUniqueLocalIds() {
        var edit = new QuestionBankEditorModel(QuestionBankPracticeSessionTest.bank());
        int added = edit.addQuestion("SINGLE_CHOICE");
        assertTrue(edit.bank().questions().get(added).id().startsWith("q_"));
        int duplicate = edit.duplicateQuestion(0);
        assertEquals(1, duplicate);
        var first = edit.bank().questions().getFirst();
        var copy = edit.bank().questions().get(duplicate);
        assertNotEquals(first.id(), copy.id());
        assertEquals(first.prompt(), copy.prompt());
        assertNotEquals(first.choicePayload().options().getFirst().id(), copy.choicePayload().options().getFirst().id());
        assertEquals(copy.choicePayload().options().getFirst().id(), copy.choiceAnswerSpec().correctOptionIds().getFirst());
        edit.deleteQuestion(duplicate);
        new QuestionBankValidator().validate(edit.bank());
        assertEquals(3, edit.bank().questions().size());
    }

    @Test void typeAndReferenceEditsAreValidated() {
        var edit = new QuestionBankEditorModel(QuestionBankPracticeSessionTest.bank());
        assertThrows(IllegalArgumentException.class, () -> edit.setType(0, "OTHER"));
        edit.setType(0, "MULTIPLE_CHOICE");
        assertThrows(RuntimeException.class, () -> new QuestionBankValidator().validate(edit.bank()));
        edit.addOption(0);
        edit.setCorrect(0, edit.bank().questions().getFirst().choicePayload().options().getLast().id(), true);
        new QuestionBankValidator().validate(edit.bank());
        var originalRef = edit.bank().questions().getFirst().sourceRefs().getFirst();
        var additional = SourceRef.anchor(originalRef.documentAssetId(), originalRef.documentContentId(), "section_two", 1, originalRef.documentTitle(), "Two");
        edit.addSourceRef(0, additional);
        assertEquals(2, edit.bank().questions().getFirst().sourceRefs().size());
        edit.deleteSourceRef(0, 1);
        new QuestionBankValidator().validate(edit.bank());
        edit.deleteSourceRef(0, 0);
        assertDoesNotThrow(() -> new QuestionBankValidator().validate(edit.bank()));
    }

    @Test void editsAndDuplicationPreserveV2MetadataAndNewQuestionsDefaultToOnePoint() {
        var base = QuestionBankPracticeSessionTest.bank();
        var old = base.questions().getFirst();
        var evaluation = new EvaluationSpec(List.of(new EvaluationCriterion("criterion_reason",
                "Reasoning", BigDecimal.ONE)), "Explain the choice");
        var question = new Question(old.id(), old.type(), List.of("stim_article"), old.prompt(),
                old.payload(), old.answerSpec(), new ScoreSpec(new BigDecimal("2.5")),
                evaluation, old.analysis(), old.sourceRefs());
        var stimuli = List.of(new Stimulus("stim_article", new TextContent("Shared article")));
        var resources = List.of(new QBankResource("res_image", ResourceKind.IMAGE, "image/png",
                "resources/image.png", "a".repeat(64)));
        var edit = new QuestionBankEditorModel(new QuestionBank(base.assetId(), base.title(),
                stimuli, List.of(question), resources));
        edit.setTitle("Edited");
        edit.setStem(0, "Changed prompt");
        edit.setOptionContent(0, 0, "Changed option");
        edit.setAnalysis(0, "Changed analysis");
        edit.deleteSourceRef(0, 0);
        int duplicate = edit.duplicateQuestion(0);
        for (var item : edit.bank().questions()) {
            assertEquals(List.of("stim_article"), item.stimulusRefs());
            assertEquals(new BigDecimal("2.5"), item.scoreSpec().defaultMaxScore());
            assertEquals(evaluation, item.evaluationSpec());
        }
        assertEquals(stimuli, edit.bank().stimuli());
        assertEquals(resources, edit.bank().resources());
        assertNotEquals(question.id(), edit.bank().questions().get(duplicate).id());
        int added = edit.addQuestion("SINGLE_CHOICE");
        assertEquals(BigDecimal.ONE, edit.bank().questions().get(added).scoreSpec().defaultMaxScore());
        assertNull(edit.bank().questions().get(added).evaluationSpec());
        new QuestionBankValidator().validate(edit.bank());
    }
}
