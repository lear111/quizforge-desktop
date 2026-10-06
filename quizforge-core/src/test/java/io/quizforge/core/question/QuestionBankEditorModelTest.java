package io.quizforge.core.question;

import io.quizforge.core.question.content.TextContent;
import io.quizforge.core.question.model.QuestionBank;
import io.quizforge.core.question.service.QuestionBankEditorModel;
import io.quizforge.core.question.service.QuestionBankValidator;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuestionBankEditorModelTest {
    @Test void basicChoicePromptsArePlainButAnalysisKeepsRichContent() {
        var rich = new io.quizforge.core.question.content.RichContent(new io.quizforge.core.question.content.RichDocument(List.of(
                new io.quizforge.core.question.content.ParagraphNode(List.of(new io.quizforge.core.question.content.InlineTextNode("说明"))))));
        var edit = new QuestionBankEditorModel(new QuestionBank("qb_plain_choice", "Choices", List.of(), List.of(), List.of()));
        for (var type : List.of("SINGLE_CHOICE", "MULTIPLE_CHOICE")) {
            int index = edit.addQuestion(type);
            var before = edit.bank();
            assertThrows(IllegalArgumentException.class, () -> edit.setPrompt(index, rich));
            assertSame(before, edit.bank());
            edit.setStem(index, "题干\n第二行");
            edit.setOptionContent(index, 0, "普通选项");
            edit.setAnalysis(index, rich);
            assertEquals(new TextContent("题干\n第二行"), edit.bank().questions().get(index).prompt());
            assertSame(rich, edit.bank().questions().get(index).analysis());
        }
        int reading = edit.addQuestion("READING");
        edit.setPrompt(reading, rich);
        assertSame(rich, edit.bank().questions().get(reading).prompt());
        new QuestionBankValidator().validate(edit.bank());
    }
    @Test void movesWholeCardsToFinalIndexesWithoutChangingNestedContentOrResources() {
        var edit = new QuestionBankEditorModel(new QuestionBank("qb_reorder", "Reorder", List.of(), List.of(), List.of()));
        for (var type : List.of("ESSAY", "READING", "CLOZE", "TRANSLATION", "MATCHING")) edit.addQuestion(type);
        edit.setStem(2,"Passage {{1}} and {{2}}.");
        var original = edit.bank();
        var moved = new QuestionBankEditorModel(original);
        moved.moveQuestion(1, 4);
        assertTrue(moved.dirty());
        assertEquals(List.of(original.questions().get(0),original.questions().get(2),original.questions().get(3),
                original.questions().get(4),original.questions().get(1)),moved.bank().questions());
        assertSame(original.questions().get(1),moved.bank().questions().getLast());
        moved.moveQuestion(4, 0);
        assertSame(original.questions().get(1),moved.bank().questions().getFirst());
        assertEquals(original.assetId(),moved.bank().assetId());
        assertEquals(original.resources(),moved.bank().resources());
        assertEquals(original.stimuli(),moved.bank().stimuli());
        new QuestionBankValidator().validate(moved.bank());
        var unchanged = new QuestionBankEditorModel(original);
        unchanged.moveQuestion(2,2);
        assertFalse(unchanged.dirty());assertSame(original,unchanged.bank());
        assertThrows(IndexOutOfBoundsException.class,()->unchanged.moveQuestion(-1,0));
        assertThrows(IndexOutOfBoundsException.class,()->unchanged.moveQuestion(0,5));
        assertSame(original,unchanged.bank());
    }
}
