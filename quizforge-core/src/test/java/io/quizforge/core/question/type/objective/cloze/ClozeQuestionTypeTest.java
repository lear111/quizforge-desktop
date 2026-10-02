package io.quizforge.core.question.type.objective.cloze;

import io.quizforge.core.practice.QuestionBankPracticeSession;
import io.quizforge.core.question.model.QuestionBank;
import io.quizforge.core.question.service.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClozeQuestionTypeTest {
    private QuestionBankEditorModel model(){
        var model=new QuestionBankEditorModel(new QuestionBank("qb_cloze","Cloze",List.of(),List.of(),List.of()));model.addQuestion("CLOZE");return model;
    }
    @Test void repeatedMarkersAreOneBlankAndEscapedAndNonNumericMarkersStayText(){
        assertEquals(List.of(1,2),ClozeQuestionType.numbers("{{1}} {{1}} \\{{3}} {{abc}} {{01}} {{2}}"));
        assertThrows(IllegalArgumentException.class,()->ClozeQuestionType.validateNumbers(List.of(1,3)));
        var model=model();model.setStem(0,"{{1}} and {{2}} and {{1}}");
        assertEquals(2,((ClozePayload)model.bank().questions().getFirst().payload()).blanks().size());
        new QuestionBankValidator().validate(model.bank());
    }
    @Test void existingOptionsAndAnswersSurvivePromptEditsAndCopiesHaveIndependentIds(){
        var model=model();var blank=((ClozePayload)model.bank().questions().getFirst().payload()).blanks().getFirst();
        model.setClozeOption(0,blank.id(),0,"went");model.setClozeCorrect(0,blank.id(),blank.options().get(1).id());
        model.setStem(0,"New {{1}} followed by {{2}} and repeated {{1}}");
        assertEquals(blank.id(),((ClozePayload)model.bank().questions().getFirst().payload()).blanks().getFirst().id());
        assertEquals(blank.options().get(1).id(),((ClozeAnswerSpec)model.bank().questions().getFirst().answerSpec()).answers().getFirst().correctOptionId());
        model.duplicateQuestion(0);new QuestionBankValidator().validate(model.bank());
        var a=(ClozePayload)model.bank().questions().get(0).payload();var b=(ClozePayload)model.bank().questions().get(1).payload();
        assertNotEquals(a.blanks().getFirst().id(),b.blanks().getFirst().id());
        assertTrue(Collections.disjoint(a.options().stream().map(o->o.id()).toList(),b.options().stream().map(o->o.id()).toList()));
    }
    @Test void subquestionsCanBeAddedBeforeMarkersAndSurvivePromptChanges(){
        var model=model();model.addClozeBlank(0);
        assertThrows(IllegalArgumentException.class,()->model.setStem(0,"{{2147483647}}"));
        var second=((ClozePayload)model.bank().questions().getFirst().payload()).blanks().get(1);
        assertEquals(4,second.options().size());
        assertTrue(second.options().stream().allMatch(o->io.quizforge.core.question.content.QuestionContentData.plainText(o.content()).equals("test")));
        model.setClozeCorrect(0,second.id(),second.options().get(2).id());model.setStem(0,"Only {{2}} repeated {{2}}.");
        assertEquals(second,((ClozePayload)model.bank().questions().getFirst().payload()).blanks().get(1));
        model.setStem(0,"正文稍后添加空位");new QuestionBankValidator().validate(model.bank());
        assertEquals(2,((ClozePayload)model.bank().questions().getFirst().payload()).blanks().size());
        assertEquals(second.options().get(2).id(),((ClozeAnswerSpec)model.bank().questions().getFirst().answerSpec()).answers().get(1).correctOptionId());
    }
    @Test void choosingAnOptionReplacesOnlyItsOwnBlankAndRestoreRejectsTwoChoicesForOneBlank(){
        var model=model();model.setStem(0,"{{1}} then {{2}} then {{1}}");var q=model.bank().questions().getFirst();var p=(ClozePayload)q.payload();
        var session=new QuestionBankPracticeSession(model.bank());
        session.select(p.blanks().get(0).options().get(0).id());session.select(p.blanks().get(1).options().get(0).id());
        session.select(p.blanks().get(0).options().get(1).id());
        assertEquals(Set.of(p.blanks().get(0).options().get(1).id(),p.blanks().get(1).options().get(0).id()),session.selected());
        assertThrows(IllegalArgumentException.class,()->session.restoreState(0,Map.of(0,Set.of(p.blanks().get(0).options().get(0).id(),p.blanks().get(0).options().get(1).id())),Map.of(),false));
        assertFalse(session.submit());assertThrows(IllegalStateException.class,()->session.select(p.blanks().get(0).options().get(0).id()));
    }
}
