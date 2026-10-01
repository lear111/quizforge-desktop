package io.quizforge.core.question.type;

import io.quizforge.core.question.model.QuestionBank;
import io.quizforge.core.question.service.QuestionBankEditorModel;
import io.quizforge.core.question.service.QuestionBankValidator;
import io.quizforge.core.question.type.objective.choice.ChoicePayload;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuestionTypeRegistryTest {
    @Test void registeredDraftsAndCopiesValidateAndOwnFreshIdentities() {
        var model=new QuestionBankEditorModel(new QuestionBank("qb_types","Types",List.of(),List.of(),List.of()));
        var questionIds=new HashSet<String>();
        var optionIds=new HashSet<String>();
        for(var type:QuestionTypes.definitions()) {
            int index=model.addQuestion(type.id());
            var question=model.bank().questions().get(index);
            assertInstanceOf(type.payloadClass(),question.payload());
            assertInstanceOf(type.answerClass(),question.answerSpec());
            int copiedIndex=model.duplicateQuestion(index);
            var copy=model.bank().questions().get(copiedIndex);
            assertEquals(question.type(),copy.type());
            assertNotEquals(question.id(),copy.id());
        }
        new QuestionBankValidator().validate(model.bank());
        model.bank().questions().forEach(question->{
            assertTrue(questionIds.add(question.id()));
            if(question.payload() instanceof ChoicePayload choice)
                choice.options().forEach(option->assertTrue(optionIds.add(option.id())));
        });
        assertThrows(RuntimeException.class,()->model.addQuestion("UNKNOWN"));
    }
}
