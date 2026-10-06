package io.quizforge.desktop.extension;

import io.quizforge.core.question.type.extension.ExtensionExecutionException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import io.quizforge.core.practice.*;
import io.quizforge.core.practice.draft.DraftCanvasDocument;
import io.quizforge.core.question.model.QuestionBank;
import io.quizforge.core.question.type.QuestionTypes;
import io.quizforge.core.question.type.QuestionTypeDefinition;
import io.quizforge.core.question.type.extension.ExternalQuestionTypeDefinition;
import io.quizforge.infrastructure.persistence.SqliteDatabase;
import io.quizforge.infrastructure.persistence.practice.SqlitePracticeTransaction;
import org.junit.jupiter.api.io.TempDir;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExtensionRuleRuntimeTest {
    @TempDir Path directory;
    private static final String SOURCE = """
        QF.defineQuestionType({type:'test.isolated',
          validate(){return [];},
          grade(ctx){
            if(ctx.answer.loop)while(true){}
            if(ctx.answer.fail)throw new Error('failed rule');
            return ctx.reportResult({score:ctx.maxScore});
          }
        });
        """;
    private ExtensionRuleRuntime runtime(String source) throws Exception {
        var runtime = new ExtensionRuleRuntime(source, Map.of(), ExtensionRuleRuntime.STARTUP_TIMEOUT, Duration.ofMillis(600));
        try { runtime.ready().toCompletableFuture().get(ExtensionRuleRuntime.STARTUP_TIMEOUT.plusSeconds(5).toMillis(),TimeUnit.MILLISECONDS); return runtime; }
        catch (Throwable failure) { runtime.close(); throw failure; }
    }
    private static Map<String,Object> grade(boolean loop, boolean fail) {
        return Map.of("question",Map.of(),"answer",Map.of("loop",loop,"fail",fail),"maxScore",2);
    }
    @Test void normalRulesAreIsolatedAndPageSyntaxIsNeverExecuted() throws Exception {
        try(var runtime=runtime(SOURCE)) {
            assertTrue(runtime.hasRules("test.isolated")); assertNotEquals(ProcessHandle.current().pid(),runtime.workerPid());
            runtime.validatePageScripts(List.of("while(true){}", "const value=await QF.editor.getData();"));
            assertEquals(2,runtime.invoke("test.isolated","grade",grade(false,false)).get("score"));
            assertFalse(runtime.hasRules("not.registered"));
        }
    }
    @Test void infiniteRuleIsKilledAndRetryStartsAPinnedFreshWorker() throws Exception {
        try(var runtime=runtime(SOURCE)) {
            long pid=runtime.workerPid(),started=System.nanoTime();
            var failure=assertThrows(ExtensionExecutionException.class,()->runtime.invoke("test.isolated","grade",grade(true,false)));
            assertEquals("EXTENSION_TIMEOUT",failure.code());
            assertTrue(Duration.ofNanos(System.nanoTime()-started).compareTo(Duration.ofSeconds(3))<0);
            ProcessHandle.of(pid).ifPresent(process->assertTimeoutPreemptively(Duration.ofSeconds(3),()->process.onExit().get()));
            assertEquals(2,runtime.invoke("test.isolated","grade",grade(false,false)).get("score"));
            assertNotEquals(pid,runtime.workerPid());
        }
    }
    @Test void ruleExceptionAndUnexpectedExitRecoverWithoutFabricatedScores() throws Exception {
        try(var runtime=runtime(SOURCE)) {
            var error=assertThrows(ExtensionExecutionException.class,()->runtime.invoke("test.isolated","grade",grade(false,true)));
            assertEquals("EXTENSION_FAILED",error.code());assertEquals(-1,runtime.workerPid());
            assertEquals(2,runtime.invoke("test.isolated","grade",grade(false,false)).get("score"));
            ProcessHandle.of(runtime.workerPid()).orElseThrow().destroyForcibly();
            assertEquals("EXTENSION_FAILED",assertThrows(ExtensionExecutionException.class,
                    ()->runtime.invoke("test.isolated","grade",grade(false,false))).code());
            assertEquals(2,runtime.invoke("test.isolated","grade",grade(false,false)).get("score"));
        }
    }
    @Test void startupLoopIsBoundedAndCloseKillsWorker() throws Exception {
        var looping=new ExtensionRuleRuntime("while(true){}",Map.of(),Duration.ofSeconds(2),Duration.ofMillis(500));
        try {
            var error=assertThrows(java.util.concurrent.ExecutionException.class,()->looping.ready().toCompletableFuture().get(5,TimeUnit.SECONDS));
            assertTrue(error.getCause().toString().contains("ExtensionExecutionException"));
            assertEquals(-1,looping.workerPid());
        } finally {looping.close();}
        var normal=runtime(SOURCE);long pid=normal.workerPid();normal.close();normal.close();
        ProcessHandle.of(pid).ifPresent(process->assertTimeoutPreemptively(Duration.ofSeconds(3),()->process.onExit().get()));
        assertEquals("EXTENSION_UNAVAILABLE",assertThrows(ExtensionExecutionException.class,()->normal.hasRules("test.isolated")).code());
    }
    @Test void failedSubmissionLeavesPersistedAnswerAndWhiteboardIntactAndCanBeResubmitted() throws Exception {
        String type="SINGLE_CHOICE";
        Path source=Path.of("../extensions/packages/single-choice");
        var json=new com.fasterxml.jackson.databind.ObjectMapper();
        @SuppressWarnings("unchecked") Map<String,Object> template=json.readValue(Files.readString(source.resolve("default.json")),Map.class);
        template.put("type",type);
        String rules=Files.readString(source.resolve("type.js")).replace("SINGLE_CHOICE",type).replace("grade(ctx) {",
                "grade(ctx) { if(ctx.answer.selectedOptionIds[0]===ctx.question.payload.options[1].id)while(true){} ");
        try(var runtime=new ExtensionRuleRuntime(rules,Map.of(type,template),Duration.ofSeconds(15),Duration.ofMillis(600))) {
            runtime.ready().toCompletableFuture().get(20,TimeUnit.SECONDS);
            var definition=new ExternalQuestionTypeDefinition(type,"Test",QuestionTypeDefinition.Family.OBJECTIVE,
                    "99.0.0","quizforge.types.single-choice",1,runtime.rules(type),template);
            QuestionTypes.registerVersion(definition);
            try {
                var q=definition.createDraft(prefix->prefix+java.util.UUID.randomUUID(),List.of());
                var bank=new QuestionBank("qb_failure_isolation","Failure isolation",List.of(),List.of(q),List.of());
                var service=new PracticeSessionService(new SqlitePracticeTransaction(new SqliteDatabase(directory.resolve("practice.db"))),Clock.systemUTC());
                String revision="qfb:v2:"+"a".repeat(64);
                var initial=service.openOrCreateActiveSession(bank,revision);String session=initial.session().id();
                var selected=new PracticePayload(Map.of("selectedOptionIds",List.of(q.choicePayload().options().get(1).id())));
                service.saveExtensionDraft(session,revision,q.id(),selected);
                var empty=DraftCanvasDocument.createEmpty();
                var draft=new DraftCanvasDocument(empty.schemaVersion(),empty.layoutVersion(),empty.viewport(),empty.questionCard(),
                        List.of(new DraftCanvasDocument.Stroke("stroke_saved","PEN","#7660ab",2,List.of(new DraftCanvasDocument.Point(10,10,.5)))));
                service.saveActiveDraftCanvas(session,revision,q.id(),draft);
                var before=service.loadActiveSession(session,revision);
                assertEquals("EXTENSION_TIMEOUT",assertThrows(ExtensionExecutionException.class,()->service.submitAnswer(session,revision,q.id())).code());
                assertEquals(before,service.loadActiveSession(session,revision));
                assertEquals(draft,service.loadActiveDraftCanvas(session,revision,q.id()).orElseThrow().document());
                assertTrue(service.loadActiveSession(session,revision).questions().getFirst().attempts().isEmpty());
                service.saveExtensionDraft(session,revision,q.id(),new PracticePayload(Map.of("selectedOptionIds",List.of(q.choicePayload().options().getFirst().id()))));
                var submitted=service.submitAnswer(session,revision,q.id());
                var attempt=submitted.questions().getFirst().attempts().getFirst();
                assertEquals(QuestionAttempt.Result.CORRECT,attempt.result());
                assertEquals(draft,service.findAttemptDraftSnapshot(attempt.id()).orElseThrow().document());
                assertTrue(service.loadActiveDraftCanvas(session,revision,q.id()).isEmpty());
            } finally {QuestionTypes.unregisterVersion(type,"99.0.0");}
        }
    }
}
