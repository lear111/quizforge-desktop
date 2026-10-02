package io.quizforge.infrastructure;

import io.quizforge.core.port.QuestionResourceInput;
import io.quizforge.core.practice.*;
import io.quizforge.core.question.content.*;
import io.quizforge.core.question.model.*;
import io.quizforge.core.question.resource.*;
import io.quizforge.core.question.service.QuestionBankEditorModel;
import io.quizforge.core.question.type.objective.reading.*;
import io.quizforge.infrastructure.filesystem.qbank.*;
import io.quizforge.infrastructure.filesystem.workspace.WorkspacePathResolver;
import io.quizforge.infrastructure.persistence.SqliteDatabase;
import io.quizforge.infrastructure.persistence.practice.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ReadingQuestionIntegrationTest {
    @TempDir Path temp;
    private final QuestionBankV2Codec codec = new QuestionBankV2Codec();

    @Test void workspaceRoundTripDraftPartialScoreRetryAndHistory() throws Exception {
        var paths = new WorkspacePathResolver(new io.quizforge.infrastructure.filesystem.QuizForgeDataDirectory(temp.resolve("workspace-data")));
        var now = java.time.Instant.now();
        var workspace = new io.quizforge.core.workspace.model.Workspace(io.quizforge.core.workspace.model.WorkspaceId.newId(), "Reading", now, now);
        paths.create(workspace);
        var model = new QuestionBankEditorModel(new QuestionBank("qb_reading", "Reading", List.of(), List.of(), List.of()));
        model.addQuestion("ESSAY");
        model.addQuestion("READING");
        var bank = model.bank();
        var file = paths.workspaceRoot(workspace.id()).resolve("question-banks/reading.qbank");
        new QBankPackageWriter().write(file, bank);
        var restoredBank = new QBankPackageReader().read(file);
        assertEquals(bank, restoredBank);
        assertEquals(codec.contentId(bank), codec.contentId(restoredBank));
        var provider = new SqliteWorkspacePracticeRuntimeProvider(paths, codec, Clock.systemUTC());
        var runtime = provider.open(workspace.id(), restoredBank);
        assertEquals(bank.questions(), runtime.session().bank().questions());
        var essay = bank.questions().getFirst();
        var reading = bank.questions().get(1);
        var items = ((ReadingPayload) reading.payload()).items();
        assertEquals(5, items.size());
        runtime.saveEssayDraft(essay.id(), new EssayPracticeAnswer("Retained essay", null));
        runtime.goTo(1);
        runtime.select(items.get(0).options().get(1).id());
        runtime.select(items.get(1).options().get(0).id());
        runtime.select(items.get(0).options().get(0).id());
        runtime.select(items.get(2).options().get(0).id());
        var selection = Set.of(items.get(0).options().get(0).id(), items.get(1).options().get(0).id(), items.get(2).options().get(0).id());
        assertEquals(selection, runtime.session().selected());
        runtime.goTo(0);
        var reopened = provider.open(workspace.id(), bank);
        assertEquals(0, reopened.session().index());
        assertEquals("Retained essay", reopened.essayAnswer(essay.id()).text());
        reopened.goTo(1);
        assertEquals(selection, reopened.session().selected());
        assertTrue(reopened.questionState(reading.id()).attempts().isEmpty());
        reopened.submit();
        var first = reopened.questionState(reading.id()).attempts().getFirst();
        assertEquals(QuestionAttempt.Result.INCORRECT, first.result());
        assertEquals(6.0, first.score());
        assertEquals(10.0, first.maxScore());
        assertThrows(IllegalStateException.class, () -> reopened.select(items.get(0).options().get(1).id()));
        var locked = provider.open(workspace.id(), bank);
        assertEquals(QuestionBankPracticeSession.State.SUBMITTED, locked.session().state());
        locked.retry();
        assertTrue(locked.session().selected().isEmpty());
        items.forEach(item -> locked.select(item.options().getFirst().id()));
        locked.submit();
        assertTrue(locked.session().correct());
        assertEquals(10.0, locked.questionState(reading.id()).attempts().getLast().score());
        var archived = locked.sessionId();
        locked.restart();
        var history = provider.history(workspace.id()).loadArchivedSessionDetail(bank.assetId(), archived);
        assertEquals(2, history.questions().get(1).attempts().size());
        var fields = QuestionContentData.map(history.questions().get(1).contentSnapshot().value());
        var frozen = ReadingQuestionSnapshot.from(new PracticePayload(fields.get("readingPresentation")));
        assertEquals(reading.prompt(), frozen.question().prompt());
        assertEquals(reading.payload(), frozen.question().payload());
        assertEquals(reading.answerSpec(), frozen.question().answerSpec());
        assertEquals(reading, ReadingQuestionSnapshot.from(new PracticePayload(fields.get("reading"))).question());

        locked.goTo(1);
        locked.select(items.getFirst().options().getFirst().id());
        model.setStem(1, "Changed passage");
        var changed = model.bank();
        new QBankPackageWriter().write(file, changed);
        var synchronizedRuntime = provider.open(workspace.id(), changed);
        assertTrue(synchronizedRuntime.session().selected().isEmpty());
        assertTrue(synchronizedRuntime.questionState(reading.id()).attempts().isEmpty());
        assertEquals("Changed passage", QuestionContentData.plainText(synchronizedRuntime.session().current().prompt()));
    }

    @Test void historyFreezesArticleItemAndAnalysisResourcesAfterSourceRemoval() throws Exception {
        var model = new QuestionBankEditorModel(new QuestionBank("qb_reading_resources", "Reading resources", List.of(), List.of(), List.of()));
        model.addQuestion("READING");
        var original = model.bank().questions().getFirst();
        var payload = (ReadingPayload) original.payload();
        var resources = new ArrayList<QBankResource>();
        var bytes = new LinkedHashMap<String,byte[]>();
        for (String label : List.of("article", "item", "analysis")) {
            var content = ("native " + label + " document").getBytes(StandardCharsets.UTF_8);
            var hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
            var resource = new QBankResource("res_canvas_" + hash, ResourceKind.DOCUMENT, "application/vnd.quizforge.canvas+json", "resources/" + hash + ".canvas.json", hash);
            resources.add(resource);
            bytes.put(resource.id(), content);
        }
        var items = new ArrayList<>(payload.items());
        var first = items.getFirst();
        items.set(0, new ReadingItem(first.id(), first.number(), new DocumentContent(resources.get(1).id(), "First item"), first.options()));
        var question = new Question(original.id(), "READING", List.of(), new DocumentContent(resources.get(0).id(), "Article"),
                new ReadingPayload(items), original.answerSpec(), original.scoreSpec(), null, new DocumentContent(resources.get(2).id(), "Analysis"), List.of());
        var bank = new QuestionBank(model.bank().assetId(), model.bank().title(), List.of(), List.of(question), resources);
        var file = temp.resolve("resources.qbank");
        new QBankPackageWriter().write(file, bank, resource -> new ByteArrayInputStream(bytes.get(resource.id())));
        assertEquals(bank, new QBankPackageReader().read(file));
        var transaction = new SqlitePracticeTransaction(new SqliteDatabase(temp.resolve("practice.db")));
        var service = new PracticeSessionService(transaction, Clock.systemUTC());
        QuestionResourceInput source = resource -> new ByteArrayInputStream(bytes.get(resource.id()));
        var runtime = new PersistentPracticeRuntime(service, bank, codec.contentId(bank), source);
        runtime.select(first.options().getFirst().id());
        runtime.submit();
        var archived = runtime.sessionId();
        runtime.restart();
        Files.delete(file);
        bytes.clear();
        var history = new PracticeHistoryService(transaction).loadArchivedSessionDetail(bank.assetId(), archived);
        var fields = QuestionContentData.map(history.questions().getFirst().contentSnapshot().value());
        var snapshot = ReadingQuestionSnapshot.from(new PracticePayload(fields.get("readingPresentation")));
        assertEquals(question, snapshot.question());
        assertEquals(resources, snapshot.resources());
        for (int index = 0; index < resources.size(); index++) {
            var label = List.of("article", "item", "analysis").get(index);
            assertArrayEquals(("native " + label + " document").getBytes(StandardCharsets.UTF_8), snapshot.open(resources.get(index)).readAllBytes());
        }
        assertThrows(IllegalStateException.class, () -> ReadingQuestionSnapshot.capture(question, resources,
                resource -> new ByteArrayInputStream("changed bytes".getBytes(StandardCharsets.UTF_8))));
    }
}
