package io.quizforge.core.question;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
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
        String added = edit.bank().questions().getFirst().data().options().getLast().id();
        edit.setCorrect(0, added, true);
        assertEquals(List.of(added), edit.bank().questions().getFirst().data().correctOptionIds());
        edit.deleteOption(0, 2);
        assertTrue(edit.bank().questions().getFirst().data().correctOptionIds().isEmpty());
        edit.setCorrect(0, "opt_two", true);
        new QuestionBankValidator().validate(edit.bank());
        assertTrue(edit.dirty());
        assertEquals(original.id(), edit.bank().id());
        assertEquals(original.questions().getFirst().id(), edit.bank().questions().getFirst().id());
        assertEquals("Edited option", edit.bank().questions().getFirst().data().options().getFirst().content());
        assertEquals("Single?", original.questions().getFirst().stem());
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
        assertEquals(first.stem(), copy.stem());
        assertNotEquals(first.data().options().getFirst().id(), copy.data().options().getFirst().id());
        assertEquals(copy.data().options().getFirst().id(), copy.data().correctOptionIds().getFirst());
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
        edit.setCorrect(0, edit.bank().questions().getFirst().data().options().getLast().id(), true);
        new QuestionBankValidator().validate(edit.bank());
        var originalRef = edit.bank().questions().getFirst().sourceRefs().getFirst();
        var additional = new QuestionBankFile.SourceRef(originalRef.documentAssetId(),
                originalRef.documentContentId(), "section_two", originalRef.documentTitle(), "Two");
        edit.addSourceRef(0, additional);
        assertEquals(2, edit.bank().questions().getFirst().sourceRefs().size());
        edit.deleteSourceRef(0, 1);
        new QuestionBankValidator().validate(edit.bank());
        edit.deleteSourceRef(0, 0);
        assertThrows(RuntimeException.class, () -> new QuestionBankValidator().validate(edit.bank()));
    }
}
