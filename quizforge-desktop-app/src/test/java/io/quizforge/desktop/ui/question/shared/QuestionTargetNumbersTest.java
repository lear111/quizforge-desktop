package io.quizforge.desktop.ui.question.shared;

import io.quizforge.core.question.type.QuestionTarget;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuestionTargetNumbersTest {
    private QuestionTarget target(String id,int local,boolean gradable,boolean locked){
        return new QuestionTarget(id,local,gradable,locked,"");
    }

    @Test void globalNumbersSkipFixedHintsAndNonGradableTargets(){
        var questions=List.of(
            List.of(target("first",1,true,false)),
            List.of(target("hint",1,true,true),target("a",2,true,false),target("info",3,false,false),target("b",4,true,false)),
            List.of(target("last",1,true,false)));
        var middle=QuestionTargetNumbers.current(questions,1);
        assertEquals(List.of("a","b"),middle.stream().map(t->t.get("id")).toList());
        assertEquals(List.of(2,3),middle.stream().map(t->t.get("number")).toList());
        assertEquals(List.of(2,4),middle.stream().map(t->t.get("localNumber")).toList());
        assertEquals(4,QuestionTargetNumbers.current(questions,2).get(0).get("number"));
        var reduced=List.of(questions.get(0),List.of(target("a",2,true,false),target("b",4,true,true)),questions.get(2));
        assertEquals(3,QuestionTargetNumbers.current(reduced,2).get(0).get("number"));
    }
}
