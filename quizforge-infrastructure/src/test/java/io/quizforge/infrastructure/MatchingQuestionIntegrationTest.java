package io.quizforge.infrastructure;

import io.quizforge.core.port.QuestionResourceInput;
import io.quizforge.core.practice.*;
import io.quizforge.core.question.content.*;
import io.quizforge.core.question.model.*;
import io.quizforge.core.question.resource.*;
import io.quizforge.core.question.service.QuestionBankEditorModel;
import io.quizforge.core.question.type.objective.matching.*;
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

class MatchingQuestionIntegrationTest {
    @TempDir Path temp;

    @Test void positionalDraftScoresLocksRetriesAndFreezesRichResources() throws Exception {
        var paths = new WorkspacePathResolver(new io.quizforge.infrastructure.filesystem.QuizForgeDataDirectory(temp.resolve("workspace-data")));
        var now = java.time.Instant.now();
        var workspace = new io.quizforge.core.workspace.model.Workspace(io.quizforge.core.workspace.model.WorkspaceId.newId(), "Matching", now, now);
        paths.create(workspace);
        var model = new QuestionBankEditorModel(new QuestionBank("qb_matching", "Matching", List.of(), List.of(), List.of()));
        model.addQuestion("MATCHING");
        var original = model.bank().questions().getFirst();
        var originalPayload = (MatchingPayload) original.payload();
        var resources = new ArrayList<QBankResource>();
        var bytes = new LinkedHashMap<String,byte[]>();
        for (String label : List.of("article", "analysis")) {
            var content = ("native " + label + " document").getBytes(StandardCharsets.UTF_8);
            var hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
            var resource = new QBankResource("res_canvas_" + hash, ResourceKind.DOCUMENT, "application/vnd.quizforge.canvas+json", "resources/" + hash + ".canvas.json", hash);
            resources.add(resource); bytes.put(resource.id(), content);
        }
        assertEquals(8, originalPayload.blanks().size());
        var options = originalPayload.options();
        var blanks = new ArrayList<>(originalPayload.blanks());
        var payload = new MatchingPayload(blanks, options);
        var gradable = blanks.stream().filter(blank -> !blank.locked()).toList();
        var correctAssignments = ((MatchingAnswerSpec) original.answerSpec()).assignments();
        var correctGradable = new LinkedHashMap<String,String>();
        gradable.forEach(blank -> correctGradable.put(blank.id(), correctAssignments.get(blank.id())));
        var question = new Question(original.id(), "MATCHING", List.of(), new DocumentContent(resources.get(0).id(), "Article"),
                payload, original.answerSpec(), original.scoreSpec(), null, new DocumentContent(resources.get(1).id(), "Analysis"), List.of());
        var bank = new QuestionBank(model.bank().assetId(), model.bank().title(), List.of(), List.of(question), resources);
        var file = paths.workspaceRoot(workspace.id()).resolve("question-banks/matching.qbank");
        new QBankPackageWriter().write(file, bank, resource -> new ByteArrayInputStream(bytes.get(resource.id())));
        var reopenedBank = new QBankPackageReader().read(file);
        assertEquals(bank, reopenedBank);
        var codec = new QuestionBankV2Codec();
        var provider = new SqliteWorkspacePracticeRuntimeProvider(paths, codec, Clock.systemUTC());
        QuestionResourceInput source = resource -> new ByteArrayInputStream(bytes.get(resource.id()));
        var runtime = provider.open(workspace.id(), reopenedBank, source);
        assertEquals(bank.questions(), runtime.session().bank().questions());
        assertEquals(QuestionBankPracticeSession.State.UNANSWERED, runtime.session().state());
        assertTrue(runtime.session().matchingAnswers().isEmpty());
        assertEquals(5, MatchingQuestionType.gradableCount(question));
        assertThrows(IllegalArgumentException.class, () -> runtime.assignMatching(blanks.getFirst().id(), null));
        assertThrows(IllegalArgumentException.class, () -> runtime.assignMatching(gradable.getFirst().id(), correctAssignments.get(blanks.getFirst().id())));
        runtime.assignMatching(gradable.getFirst().id(), correctGradable.get(gradable.getFirst().id()));
        runtime.assignMatching(gradable.get(1).id(), correctGradable.get(gradable.getFirst().id()));
        assertEquals(Map.of(gradable.getFirst().id(), correctGradable.get(gradable.getFirst().id()),
                gradable.get(1).id(), correctGradable.get(gradable.getFirst().id())), runtime.session().matchingAnswers());
        var reopened = provider.open(workspace.id(), bank, source);
        assertEquals(runtime.session().matchingAnswers(), reopened.session().matchingAnswers());
        assertEquals(QuestionBankPracticeSession.State.SELECTED, reopened.session().state());
        assertTrue(reopened.questionState(question.id()).attempts().isEmpty());
        reopened.assignMatching(gradable.getFirst().id(), null);
        assertEquals(QuestionBankPracticeSession.State.SELECTED, reopened.session().state());
        reopened.assignMatching(gradable.get(1).id(), null);
        assertEquals(QuestionBankPracticeSession.State.UNANSWERED, reopened.session().state());
        reopened.assignMatching(gradable.getFirst().id(), correctGradable.get(gradable.getFirst().id()));
        reopened.submit();
        var first = reopened.questionState(question.id()).attempts().getFirst();
        assertEquals(2.0, first.score()); assertEquals(10.0, first.maxScore());
        assertEquals(QuestionAttempt.Result.INCORRECT, first.result());
        assertThrows(IllegalStateException.class, () -> reopened.assignMatching(gradable.getFirst().id(), correctGradable.get(gradable.get(1).id())));
        var locked = provider.open(workspace.id(), bank, source);
        assertEquals(QuestionBankPracticeSession.State.SUBMITTED, locked.session().state());
        assertEquals(reopened.session().matchingAnswers(), locked.session().matchingAnswers());
        locked.retry();
        assertTrue(locked.session().matchingAnswers().isEmpty());
        locked.assignMatching(gradable.getFirst().id(), correctGradable.get(gradable.getFirst().id()));
        locked.assignMatching(gradable.getFirst().id(), null);
        assertEquals(PracticeSessionQuestion.State.RETRYING, locked.questionState(question.id()).sessionQuestion().practiceState());
        var retryReopened = provider.open(workspace.id(), bank, source);
        assertTrue(retryReopened.session().matchingAnswers().isEmpty());
        assertEquals(1, retryReopened.questionState(question.id()).attempts().size());
        for (var blank : gradable) locked.assignMatching(blank.id(), correctGradable.get(gradable.getFirst().id()));
        locked.submit();
        var repeated = locked.questionState(question.id()).attempts().getLast();
        assertEquals(2.0, repeated.score());
        assertEquals(QuestionAttempt.Result.INCORRECT, repeated.result());
        assertEquals(1, new HashSet<>(locked.session().matchingAnswers().values()).size());
        assertEquals(5, locked.session().matchingAnswers().size());
        locked.retry();
        correctGradable.forEach(locked::assignMatching);
        locked.submit();
        assertTrue(locked.session().correct());
        assertEquals(10.0, locked.questionState(question.id()).attempts().getLast().score());
        var archived = locked.sessionId(); locked.restart();
        Files.delete(file); bytes.clear();
        var history = provider.history(workspace.id()).loadArchivedSessionDetail(bank.assetId(), archived);
        var row = history.questions().getFirst();
        assertEquals(3, row.attempts().size());
        assertEquals(MatchingPracticeAnswer.from(repeated.answer()).assignments(),
                MatchingPracticeAnswer.from(row.attempts().get(1).answer()).assignments());
        assertEquals(QuestionAttempt.Mode.RETRY, row.attempts().getLast().mode());
        assertEquals(correctGradable, MatchingPracticeAnswer.from(row.attempts().getLast().answer()).assignments());
        var fields = QuestionContentData.map(row.contentSnapshot().value());
        var frozen = MatchingQuestionSnapshot.from(new PracticePayload(fields.get("matchingPresentation")));
        assertEquals(question, frozen.question()); assertEquals(resources, frozen.resources());
        for (int i = 0; i < resources.size(); i++) assertArrayEquals(("native " + List.of("article", "analysis").get(i) + " document").getBytes(StandardCharsets.UTF_8), frozen.open(resources.get(i)).readAllBytes());
        assertEquals(3, ((MatchingPayload) frozen.question().payload()).blanks().stream().filter(MatchingBlank::locked).count());
        assertEquals(10.0, ((Number) QuestionContentData.map(fields.get("matching")).get("maxScore")).doubleValue());
        assertEquals(question, MatchingQuestionSnapshot.from(new PracticePayload(fields.get("matching"))).question());
        assertThrows(IllegalStateException.class, () -> MatchingQuestionSnapshot.capture(question, resources,
                resource -> new ByteArrayInputStream("changed".getBytes(StandardCharsets.UTF_8))));
    }
}
