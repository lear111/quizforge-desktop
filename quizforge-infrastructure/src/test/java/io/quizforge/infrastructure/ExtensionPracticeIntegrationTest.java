package io.quizforge.infrastructure;

import io.quizforge.core.practice.*;
import io.quizforge.core.question.model.*;
import io.quizforge.core.question.type.*;
import io.quizforge.core.question.type.extension.*;
import io.quizforge.core.workspace.model.*;
import io.quizforge.infrastructure.filesystem.QuizForgeDataDirectory;
import io.quizforge.infrastructure.filesystem.qbank.*;
import io.quizforge.infrastructure.filesystem.workspace.WorkspacePathResolver;
import io.quizforge.infrastructure.persistence.SqliteDatabase;
import io.quizforge.infrastructure.persistence.practice.*;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ExtensionPracticeIntegrationTest {
    private static final String TYPE="test.practice.extension";
    @TempDir Path temp;
    private final AtomicInteger ids=new AtomicInteger();
    @AfterEach void cleanup(){QuestionTypes.unregister(TYPE);QuestionTypes.unregister("SINGLE_CHOICE");}
    private ExternalQuestionTypeDefinition type(String version) {
        return new ExternalQuestionTypeDefinition(TYPE,"Example",QuestionTypeDefinition.Family.OBJECTIVE,version,(operation,input)->switch(operation){
            case "createDraft" -> Map.of("prompt",Map.of("kind","TEXT","text","Prompt"),"payload",Map.of("statement","Example"),"answerSpec",Map.of("correct",true),"maxScore",2);
            case "validate" -> Map.of("errors",List.of());
            case "snapshot","targets" -> Map.of("targets",List.of(Map.of("id","decision","number",1)));
            case "validateAnswer" -> Map.of("errors",List.of(),"empty",!ExternalQuestionTypeDefinition.object(input.get("answer")).containsKey("value"));
            case "grade" -> Map.of("status",version.equals("1.0.0")?"CORRECT":"INCORRECT","score",version.equals("1.0.0")?2:0,"maxScore",2);
            case "duplicate" -> ExternalQuestionTypeDefinition.object(input.get("question"));
            default -> throw new IllegalArgumentException(operation);
        });
    }
    private QuestionBank bank(){return new QuestionBank("qb_extension_review","Extension","2.0",List.of(),List.of(
            QuestionTypes.require(TYPE).createDraft(prefix->prefix+ids.incrementAndGet(),List.of())),List.of());}
    @Test void actualWorkspaceEntryKeepsExtensionOnlyAndMixedBanksComplete() throws Exception {
        QuestionTypes.register(type("1.0.0"));
        // No built-in registry: this workspace fixture explicitly provides its second external type.
        var template = Map.<String,Object>of("id","q_choice_template","type","SINGLE_CHOICE",
                "prompt",Map.of("kind","TEXT","text","Choice"),
                "payload",Map.of("kind","CHOICE","options",List.of(
                        Map.of("id","opt_a","content",Map.of("kind","TEXT","text","A")),
                        Map.of("id","opt_b","content",Map.of("kind","TEXT","text","B")))),
                "answerSpec",Map.of("kind","CHOICE","correctOptionIds",List.of("opt_a")),
                "scoreSpec",Map.of("defaultMaxScore",1));
        QuestionTypes.register(new ExternalQuestionTypeDefinition("SINGLE_CHOICE","Choice",
                QuestionTypeDefinition.Family.OBJECTIVE,"1.0.0","test.practice.choice",1,
                (operation,input)->operation.equals("validate")?Map.of("errors",List.of()):
                        Map.of("targets",List.of(Map.of("id",ExternalQuestionTypeDefinition.object(input.get("question")).get("id"),"number",1))),template));
        var paths=new WorkspacePathResolver(new QuizForgeDataDirectory(temp.resolve("data")));
        var now=Instant.now();var workspace=new Workspace(WorkspaceId.newId(),"Test",now,now);paths.create(workspace);
        var codec=new QuestionBankV2Codec();var provider=new SqliteWorkspacePracticeRuntimeProvider(paths,codec,Clock.systemUTC());
        var original=bank();var file=paths.workspaceRoot(workspace.id()).resolve("question-banks/test.qbank");
        new QBankPackageWriter().write(file,original);
        assertEquals(1,provider.open(workspace.id(),original).session().bank().questions().size());
        var basic=QuestionTypes.require("SINGLE_CHOICE").createDraft(prefix->prefix+ids.incrementAndGet(),List.of());
        var mixed=new QuestionBank(original.assetId(),original.title(),"2.0",List.of(),List.of(original.questions().getFirst(),basic),List.of());
        new QBankPackageWriter().write(file,mixed);
        assertEquals(mixed.questions(),provider.open(workspace.id(),mixed).session().bank().questions());
    }
    @Test void upgradeRestoresFrozenAnswersAndUsesOldRulesUntilNewRound() {
        QuestionTypes.register(type("1.0.0"));var bank=bank();var contentId="qfb:v2:"+"a".repeat(64);
        var service=new PracticeSessionService(new SqlitePracticeTransaction(new SqliteDatabase(temp.resolve("practice.db"))),Clock.systemUTC());
        var original=new PersistentPracticeRuntime(service,bank,contentId);
        original.saveExtensionDraft(new PracticePayload(Map.of("value",true)));
        QuestionTypes.registerVersion(type("1.1.0"));
        var renamed=new QuestionBank(bank.assetId(),"Renamed",bank.schemaVersion(),bank.stimuli(),bank.questions(),bank.resources());
        var reopened=new PersistentPracticeRuntime(service,renamed,"qfb:v2:"+"b".repeat(64));
        assertEquals(original.sessionId(),reopened.sessionId());
        assertEquals(Map.of("value",true),reopened.extensionAnswer(bank.questions().getFirst().id()).value());
        reopened.submit();assertEquals(QuestionAttempt.Result.CORRECT,reopened.snapshot().questions().getFirst().attempts().getLast().result());
        reopened.restart();reopened.saveExtensionDraft(new PracticePayload(Map.of("value",true)));reopened.submit();
        assertEquals(QuestionAttempt.Result.INCORRECT,reopened.snapshot().questions().getFirst().attempts().getLast().result());
    }
}
