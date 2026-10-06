package io.quizforge.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quizforge.core.practice.*;
import io.quizforge.core.question.model.*;
import io.quizforge.core.question.service.QuestionBankEditorModel;
import io.quizforge.core.question.type.*;
import io.quizforge.core.question.type.extension.*;
import io.quizforge.core.question.model.extension.ExtensionPayload;
import io.quizforge.infrastructure.extension.ExtensionSchemaValidator;
import io.quizforge.infrastructure.filesystem.qbank.QuestionBankV2Codec;
import io.quizforge.infrastructure.persistence.SqliteDatabase;
import io.quizforge.infrastructure.persistence.practice.SqlitePracticeTransaction;
import java.nio.file.Path;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ExtensionDataValidationTest {
    private static final String TYPE = "test.schema";
    private static final String QUESTION = """
        {"type":"object","required":["id","type","prompt","payload","answerSpec","scoreSpec"],
         "properties":{"type":{"const":"test.schema"},"payload":{"type":"object","required":["data"],
           "properties":{"data":{"type":"object","required":["statement"],"additionalProperties":false,
             "properties":{"statement":{"type":"string","minLength":1}}}}}}}
        """;
    private static final String ANSWER = """
        {"type":"object","required":["value"],"additionalProperties":false,"properties":{"value":{"type":"boolean"}}}
        """;
    private final AtomicReference<Map<String,Object>> badGrade = new AtomicReference<>();
    @TempDir Path temp;
    @AfterEach void cleanup() { QuestionTypes.unregister(TYPE); }
    private Map<String,Object> template() {
        return Map.of("id","q_schema","type",TYPE,"prompt",Map.of("kind","TEXT","text","A statement"),
                "payload",Map.of("kind","EXTENSION","data",Map.of("statement","A statement")),
                "answerSpec",Map.of("kind","EXTENSION","data",Map.of("correct",true)),
                "analysis",Map.of("kind","TEXT","text","Explanation"),"scoreSpec",Map.of("defaultMaxScore",2));
    }
    private ExternalQuestionTypeDefinition type(String version, String answerSchema) {
        var validator = new ExtensionSchemaValidator(TYPE, QUESTION, answerSchema);
        return new ExternalQuestionTypeDefinition(TYPE,"Schema test",QuestionTypeDefinition.Family.OBJECTIVE,version,"test.schemas",1,
                (operation,input) -> switch(operation) {
                    case "validate" -> Map.of("errors",List.of());
                    case "validateAnswer" -> Map.of("errors",List.of(),"empty",false); // Deliberately permissive extension.
                    case "snapshot", "targets" -> Map.of("targets",List.of(Map.of("id","decision","number",1)));
                    case "grade" -> badGrade.get() == null ? Map.of("status","CORRECT","score",2,"maxScore",2) : badGrade.get();
                    default -> throw new IllegalArgumentException(operation);
                }, template(), validator);
    }
    private QuestionBank bank(ExternalQuestionTypeDefinition type) {
        return new QuestionBank("qb_schema","Schema bank","2.0",List.of(),List.of(type.decodeQuestion(template(),"q_schema",List.of())),List.of());
    }
    @Test void permissiveRulesCannotSaveMalformedEditorDataOrPolluteTheBank() {
        var type = type("1.0.0",ANSWER); QuestionTypes.register(type);
        var model = new QuestionBankEditorModel(bank(type)); var original = model.bank();
        var error = assertThrows(ExtensionDataValidationException.class, () -> model.setEditorQuestion(0,Map.of("payload",Map.of("kind","EXTENSION","data",Map.of("statement",42)))));
        assertTrue(error.getMessage().contains("/question/payload/data/statement")); assertSame(original,model.bank()); assertFalse(model.dirty());
        model.setEditorQuestion(0,Map.of("payload",Map.of("kind","EXTENSION","data",Map.of("statement","Edited"))));
        assertEquals("Edited",((ExtensionPayload)model.bank().questions().getFirst().payload()).data().get("statement"));
        var codec = new QuestionBankV2Codec(); var valid = codec.write(model.bank());
        assertThrows(RuntimeException.class, () -> codec.parse(valid.replace("\"Edited\"","42")));
        assertEquals(model.bank(),codec.parse(valid));
    }
    @Test void malformedAnswerAndGradeLeaveSqliteWorkingStateAndAttemptsUnchanged() {
        var type = type("1.0.0",ANSWER); QuestionTypes.register(type); var bank = bank(type);
        var service = new PracticeSessionService(new SqlitePracticeTransaction(new SqliteDatabase(temp.resolve("practice.db"))),Clock.systemUTC());
        var runtime = new PersistentPracticeRuntime(service,bank,new QuestionBankV2Codec().contentId(bank));
        var original = runtime.snapshot();
        var error = assertThrows(ExtensionDataValidationException.class, () -> runtime.saveExtensionDraft(new PracticePayload(Map.of("value","yes"))));
        assertTrue(error.getMessage().contains("/answer/value")); assertEquals(original,runtime.snapshot());
        runtime.saveExtensionDraft(new PracticePayload(Map.of()));assertTrue(runtime.snapshot().questions().getFirst().attempts().isEmpty());
        assertThrows(IllegalStateException.class,runtime::submit); // The malicious empty:false rule cannot grade {}.
        runtime.saveExtensionDraft(new PracticePayload(Map.of("value",true))); var validDraft = runtime.snapshot();
        badGrade.set(Map.of("status","CORRECT","score",999,"maxScore",2));
        assertThrows(ExtensionDataValidationException.class,runtime::submit);assertEquals(validDraft,runtime.snapshot());
        badGrade.set(null);runtime.submit();assertEquals(1,runtime.snapshot().questions().getFirst().attempts().size());
        assertEquals(2.0,runtime.snapshot().questions().getFirst().attempts().getFirst().score());
    }
    @Test void answersStayBoundToFrozenVersionSchemasAcrossAnUpgrade() {
        var old = type("1.0.0",ANSWER); QuestionTypes.registerVersion(old); var bank = bank(old);
        var runtime = new PersistentPracticeRuntime(new PracticeSessionService(new SqlitePracticeTransaction(new SqliteDatabase(temp.resolve("versions.db"))),Clock.systemUTC()),bank,new QuestionBankV2Codec().contentId(bank));
        var newer = type("1.1.0",ANSWER.replace("boolean","string")); QuestionTypes.registerVersion(newer);
        runtime.saveExtensionDraft(new PracticePayload(Map.of("value",true))); runtime.submit();
        assertEquals(QuestionAttempt.Result.CORRECT,runtime.snapshot().questions().getFirst().attempts().getFirst().result());
        runtime.restart();assertThrows(ExtensionDataValidationException.class,()->runtime.saveExtensionDraft(new PracticePayload(Map.of("value",true))));
        runtime.saveExtensionDraft(new PracticePayload(Map.of("value","yes")));runtime.submit();
        assertEquals(1,runtime.snapshot().questions().getFirst().attempts().size());
    }
    @Test void malformedRuleResultsAreRejectedWithAFieldPath() {
        var normal = type("1.0.0",ANSWER); var q = bank(normal).questions().getFirst(); var snapshot = normal.snapshot(q);
        for (var result : List.<Map<String,Object>>of(Map.of("status","CORRECT","score",1),Map.of("status","INCORRECT","score",2),Map.of("status","BOGUS","score",0),Map.of("status","UNSCORED","score",0),Map.of("status","CORRECT","score",2,"maxScore","2"))) {
            badGrade.set(result);var error = assertThrows(ExtensionDataValidationException.class,()->normal.grade(snapshot,new PracticePayload(Map.of("value",true))));
            assertTrue(error.issues().getFirst().path().startsWith("/rules/grade/"));
        }
        var malformed = new ExternalQuestionTypeDefinition(TYPE,"Test",QuestionTypeDefinition.Family.OBJECTIVE,"1.0.0",(operation,input)->Map.of("errors",List.of(123),"empty",false));
        assertThrows(ExtensionDataValidationException.class,()->malformed.validateAnswer(snapshot,new PracticePayload(Map.of("value",true))));
    }
    @Test void offlineLocalReferencesWorkAndInvalidSchemasFailAtCompilation() {
        String local = """
            {"type":"object","required":["value"],"definitions":{"boolean":{"type":"boolean"}},"properties":{"value":{"$ref":"#/definitions/boolean"}}}
            """;
        var validator = new ExtensionSchemaValidator(TYPE,QUESTION,local);validator.answer(Map.of("value",true));
        assertThrows(ExtensionDataValidationException.class,()->validator.answer(Map.of("value","yes")));
        for (String bad : List.of("{\"type\":\"not-a-type\"}","{\"$ref\":\"https://example.invalid/schema\"}","{\"$ref\":\"file:///etc/passwd\"}","{\"$ref\":\"#/missing\"}","{\"definitions\":{\"self\":{\"$ref\":\"#/definitions/self\"}}}","{\"$schema\":\"https://json-schema.org/draft/2020-12/schema\"}","{\"typo\":true}"))
            assertThrows(ExtensionDataValidationException.class,()->new ExtensionSchemaValidator(TYPE,QUESTION,bad),bad);
        for (String dialect : List.of("http","https"))new ExtensionSchemaValidator(TYPE,QUESTION,local.replace("{\"type\"","{\"$schema\":\""+dialect+"://json-schema.org/draft-07/schema#\",\"type\""));
    }
}
