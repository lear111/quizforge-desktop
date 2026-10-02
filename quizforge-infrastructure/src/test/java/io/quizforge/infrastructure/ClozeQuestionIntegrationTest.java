package io.quizforge.infrastructure;

import io.quizforge.core.practice.*;
import io.quizforge.core.question.content.*;
import io.quizforge.core.question.model.*;
import io.quizforge.core.question.resource.*;
import io.quizforge.core.question.service.QuestionBankEditorModel;
import io.quizforge.core.question.type.objective.cloze.*;
import io.quizforge.infrastructure.filesystem.qbank.*;
import io.quizforge.infrastructure.persistence.SqliteDatabase;
import io.quizforge.infrastructure.persistence.practice.SqlitePracticeTransaction;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ClozeQuestionIntegrationTest {
    @TempDir Path temp;
    private final QuestionBankV2Codec codec=new QuestionBankV2Codec();
    private QuestionBank bank(){var m=new QuestionBankEditorModel(new QuestionBank("qb_cloze","Cloze",List.of(),List.of(),List.of()));m.addQuestion("CLOZE");m.setStem(0,"First {{1}}, next {{2}}, repeat {{1}}.");return m.bank();}
    @Test void workspacePracticeIncludesClozeAfterEssayAndPersistsItsSelections()throws Exception{
        var paths=new io.quizforge.infrastructure.filesystem.workspace.WorkspacePathResolver(
                new io.quizforge.infrastructure.filesystem.QuizForgeDataDirectory(temp.resolve("workspace-data")));
        var now=java.time.Instant.now();
        var workspace=new io.quizforge.core.workspace.model.Workspace(io.quizforge.core.workspace.model.WorkspaceId.newId(),"Mixed",now,now);
        paths.create(workspace);
        var model=new QuestionBankEditorModel(new QuestionBank("qb_mixed_cloze","Mixed",List.of(),List.of(),List.of()));
        model.addQuestion("ESSAY");model.addQuestion("CLOZE");model.setStem(1,"First {{1}}, next {{2}}, repeat {{1}}.");
        var bank=model.bank();var essay=bank.questions().getFirst();var cloze=bank.questions().get(1);var blanks=((ClozePayload)cloze.payload()).blanks();
        new QBankPackageWriter().write(paths.workspaceRoot(workspace.id()).resolve("question-banks/mixed.qbank"),bank);
        var provider=new io.quizforge.infrastructure.persistence.practice.SqliteWorkspacePracticeRuntimeProvider(paths,codec,Clock.systemUTC());
        var runtime=provider.open(workspace.id(),bank);
        assertEquals(bank.questions(),runtime.session().bank().questions());
        runtime.saveEssayDraft(essay.id(),new EssayPracticeAnswer("Essay draft",null));runtime.goTo(1);
        assertEquals(cloze.id(),runtime.session().current().id());
        runtime.select(blanks.get(0).options().get(1).id());runtime.select(blanks.get(1).options().get(0).id());
        runtime.select(blanks.get(0).options().get(0).id());
        var selected=Set.of(blanks.get(0).options().get(0).id(),blanks.get(1).options().get(0).id());
        assertEquals(selected,runtime.session().selected());
        var reopened=provider.open(workspace.id(),bank);
        assertEquals(1,reopened.session().index());assertEquals(selected,reopened.session().selected());
        assertEquals("Essay draft",reopened.essayAnswer(essay.id()).text());
        reopened.submit();assertTrue(reopened.session().correct());
        assertEquals(2.0,reopened.questionState(cloze.id()).attempts().getLast().score());
        assertThrows(IllegalStateException.class,()->reopened.select(blanks.get(0).options().get(1).id()));
        reopened.retry();assertTrue(reopened.session().selected().isEmpty());
        reopened.select(blanks.get(0).options().get(1).id());
        assertEquals(Set.of(blanks.get(0).options().get(1).id()),reopened.session().selected());
    }
    @Test void packageRoundTripPreservesBlankIdsAnswersAndContentId(){
        var bank=bank();var file=temp.resolve("cloze.qbank");new QBankPackageWriter().write(file,bank);
        var restored=new QBankPackageReader().read(file);assertEquals(bank,restored);assertEquals(codec.contentId(bank),codec.contentId(restored));
    }
    @Test void draftsReopenPartialScoringRetryAndArchivedAttemptsUseTheSameFrozenQuestion(){
        var bank=bank();var revision=codec.contentId(bank);var payload=(ClozePayload)bank.questions().getFirst().payload();
        var db=new SqliteDatabase(temp.resolve("practice.db"));var tx=new SqlitePracticeTransaction(db);var service=new PracticeSessionService(tx,Clock.systemUTC());
        var runtime=new PersistentPracticeRuntime(service,bank,revision);var first=payload.blanks().get(0).options().get(0).id();var second=payload.blanks().get(1).options().get(0).id();
        runtime.select(first);assertTrue(runtime.questionState(runtime.session().current().id()).attempts().isEmpty());
        var reopened=new PersistentPracticeRuntime(service,bank,revision);assertEquals(Set.of(first),reopened.session().selected());
        var wrong=payload.blanks().get(1).options().get(1).id();reopened.select(wrong);reopened.submit();
        var attempt=reopened.questionState(reopened.session().current().id()).attempts().getFirst();
        assertEquals(QuestionAttempt.Result.INCORRECT,attempt.result());assertEquals(1.0,attempt.score());assertEquals(2.0,attempt.maxScore());
        assertThrows(IllegalStateException.class,()->reopened.select(second));reopened.retry();assertTrue(reopened.session().selected().isEmpty());
        reopened.select(first);reopened.select(second);reopened.submit();assertTrue(reopened.session().correct());
        assertEquals(2.0,reopened.questionState(reopened.session().current().id()).attempts().getLast().score());
        var archivedId=reopened.sessionId();reopened.restart();var history=new PracticeHistoryService(tx).loadArchivedSessionDetail(bank.assetId(),archivedId);
        assertEquals(2,history.questions().getFirst().attempts().size());
        var fields=QuestionContentData.map(history.questions().getFirst().contentSnapshot().value());
        var frozen=ClozeQuestionSnapshot.from(new PracticePayload(fields.get("clozePresentation")));
        assertEquals(bank.questions().getFirst().prompt(),frozen.question().prompt());assertEquals(payload,frozen.question().payload());
    }
    @Test void historyOwnsNativeDocumentBytesAfterTheSourceIsGone()throws Exception{
        var bank=bank();byte[] bytes="native document bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        var resource=new QBankResource("res_canvas_"+hash,ResourceKind.DOCUMENT,"application/vnd.quizforge.canvas+json","resources/"+hash+".canvas.json",hash);
        var original=bank.questions().getFirst();var q=new Question(original.id(),"CLOZE",List.of(),new DocumentContent(resource.id(),"{{1}} {{2}} {{1}}"),original.payload(),original.answerSpec(),original.scoreSpec(),null,null,List.of());
        var snapshot=ClozeQuestionSnapshot.capture(q,List.of(resource),r->new java.io.ByteArrayInputStream(bytes));
        var restored=ClozeQuestionSnapshot.from(snapshot.payload());assertArrayEquals(bytes,restored.open(resource).readAllBytes());assertEquals(q,restored.question());
    }
}
