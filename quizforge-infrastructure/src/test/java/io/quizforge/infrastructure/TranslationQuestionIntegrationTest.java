package io.quizforge.infrastructure;

import io.quizforge.core.port.QuestionResourceInput;
import io.quizforge.core.practice.*;
import io.quizforge.core.question.content.*;
import io.quizforge.core.question.model.*;
import io.quizforge.core.question.resource.*;
import io.quizforge.core.question.service.QuestionBankEditorModel;
import io.quizforge.core.question.type.subjective.translation.*;
import io.quizforge.infrastructure.filesystem.qbank.*;
import io.quizforge.infrastructure.filesystem.workspace.WorkspacePathResolver;
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

class TranslationQuestionIntegrationTest {
    @TempDir Path temp;

    @Test void richSentenceDraftsReopenSubmitRetryAndFreezeReferences() throws Exception {
        var paths = new WorkspacePathResolver(new io.quizforge.infrastructure.filesystem.QuizForgeDataDirectory(temp.resolve("workspace-data")));
        var now = java.time.Instant.now();
        var workspace = new io.quizforge.core.workspace.model.Workspace(io.quizforge.core.workspace.model.WorkspaceId.newId(), "Translation", now, now);
        paths.create(workspace);
        var model = new QuestionBankEditorModel(new QuestionBank("qb_translation", "Translation", List.of(), List.of(), List.of()));
        model.addQuestion("TRANSLATION");
        var original = model.bank().questions().getFirst();
        var payload = (TranslationPayload) original.payload();
        var resources = new ArrayList<QBankResource>();
        var bytes = new LinkedHashMap<String,byte[]>();
        for (String label : List.of("article", "reference", "analysis")) {
            var content = ("native " + label + " document").getBytes(StandardCharsets.UTF_8);
            var hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
            var resource = new QBankResource("res_canvas_" + hash, ResourceKind.DOCUMENT, "application/vnd.quizforge.canvas+json", "resources/" + hash + ".canvas.json", hash);
            resources.add(resource); bytes.put(resource.id(), content);
        }
        var references = new ArrayList<TranslationAnswerSpec.Answer>();
        for (var item : payload.items()) references.add(new TranslationAnswerSpec.Answer(item.id(),
                item.number() == 1 ? new DocumentContent(resources.get(1).id(), "Reference") : new TextContent("参考译文 " + item.number())));
        var question = new Question(original.id(), "TRANSLATION", List.of(),
                new DocumentContent(resources.get(0).id(), QuestionContentData.plainText(original.prompt())),
                payload, new TranslationAnswerSpec(references), original.scoreSpec(), null,
                new DocumentContent(resources.get(2).id(), "Analysis"), List.of());
        var bank = new QuestionBank(model.bank().assetId(), model.bank().title(), List.of(), List.of(question), resources);
        var file = paths.workspaceRoot(workspace.id()).resolve("question-banks/translation.qbank");
        new QBankPackageWriter().write(file, bank, resource -> new ByteArrayInputStream(bytes.get(resource.id())));
        assertEquals(bank, new QBankPackageReader().read(file));
        var provider = new SqliteWorkspacePracticeRuntimeProvider(paths, new QuestionBankV2Codec(), Clock.systemUTC());
        QuestionResourceInput source = resource -> new ByteArrayInputStream(bytes.get(resource.id()));
        var runtime = provider.open(workspace.id(), bank, source);
        var firstId = payload.items().getFirst().id();
        var secondId = payload.items().get(1).id();
        assertEquals(5, payload.items().size());
        assertEquals(QuestionBankPracticeSession.State.UNANSWERED, runtime.session().state());
        assertThrows(IllegalStateException.class, runtime::submit);
        assertThrows(IllegalArgumentException.class, () -> runtime.assignTranslation("item_missing", new EssayPracticeAnswer("错误", null)));
        var rich = new EssayPracticeAnswer("第一句译文", "{\"data\":{\"main\":[{\"value\":\"第一句译文\"}]}}");
        var plain = new EssayPracticeAnswer("第二句译文", null);
        runtime.assignTranslation(firstId, rich);
        runtime.assignTranslation(secondId, plain);
        var draft = Map.of(firstId, rich, secondId, plain);
        var reopened = provider.open(workspace.id(), bank, source);
        assertEquals(draft, reopened.translationAnswers(question.id()));
        assertEquals(draft, reopened.session().translationAnswers());
        assertEquals(QuestionBankPracticeSession.State.SELECTED, reopened.session().state());
        assertTrue(reopened.questionState(question.id()).attempts().isEmpty());
        reopened.assignTranslation(secondId, new EssayPracticeAnswer("", null));
        assertEquals(Map.of(firstId, rich), reopened.session().translationAnswers());
        reopened.assignTranslation(firstId, null);
        assertEquals(QuestionBankPracticeSession.State.UNANSWERED, reopened.session().state());
        reopened.assignTranslation(firstId, rich);
        reopened.assignTranslation(secondId, plain);
        reopened.submit();
        var initial = reopened.questionState(question.id()).attempts().getFirst();
        assertEquals(QuestionAttempt.Result.UNSCORED, initial.result());
        assertNull(initial.score()); assertEquals(10.0, initial.maxScore());
        assertEquals(draft, TranslationPracticeAnswer.from(initial.answer()).answers());
        assertThrows(IllegalStateException.class, () -> reopened.assignTranslation(firstId, plain));
        var submitted = provider.open(workspace.id(), bank, source);
        assertEquals(QuestionBankPracticeSession.State.SUBMITTED, submitted.session().state());
        assertEquals(draft, submitted.session().translationAnswers());
        assertEquals(1, submitted.summary().unscoredCount());
        submitted.retry();
        assertTrue(submitted.session().translationAnswers().isEmpty());
        submitted.assignTranslation(firstId, plain);
        submitted.assignTranslation(firstId, null);
        assertEquals(PracticeSessionQuestion.State.RETRYING, submitted.questionState(question.id()).sessionQuestion().practiceState());
        var retried = provider.open(workspace.id(), bank, source);
        assertTrue(retried.session().translationAnswers().isEmpty());
        assertEquals(1, retried.questionState(question.id()).attempts().size());
        retried.assignTranslation(secondId, rich);
        retried.submit();
        var archived = retried.sessionId(); retried.restart();
        Files.delete(file); bytes.clear();
        var history = provider.history(workspace.id()).loadArchivedSessionDetail(bank.assetId(), archived);
        var row = history.questions().getFirst();
        assertEquals(2, row.attempts().size());
        assertEquals(draft, TranslationPracticeAnswer.from(row.attempts().getFirst().answer()).answers());
        assertEquals(Map.of(secondId, rich), TranslationPracticeAnswer.from(row.attempts().getLast().answer()).answers());
        assertEquals(QuestionAttempt.Mode.RETRY, row.attempts().getLast().mode());
        var fields = QuestionContentData.map(row.contentSnapshot().value());
        var frozen = TranslationQuestionSnapshot.from(new PracticePayload(fields.get("translationPresentation")));
        assertEquals(question, frozen.question()); assertEquals(resources, frozen.resources());
        for (int i = 0; i < resources.size(); i++) assertArrayEquals(("native " + List.of("article", "reference", "analysis").get(i) + " document").getBytes(StandardCharsets.UTF_8), frozen.open(resources.get(i)).readAllBytes());
        assertEquals(question, TranslationQuestionSnapshot.from(new PracticePayload(fields.get("translation"))).question());
        assertEquals(10.0, ((Number) QuestionContentData.map(fields.get("translation")).get("maxScore")).doubleValue());
        assertThrows(IllegalStateException.class, () -> TranslationQuestionSnapshot.capture(question, resources,
                resource -> new ByteArrayInputStream("changed".getBytes(StandardCharsets.UTF_8))));
    }
}
