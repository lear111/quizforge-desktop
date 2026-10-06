package io.quizforge.core.question.type.extension;

import io.quizforge.core.question.model.extension.ExtensionPayload;
import io.quizforge.core.practice.*;
import io.quizforge.core.question.model.*;
import io.quizforge.core.question.service.QuestionBankValidator;
import io.quizforge.core.question.type.QuestionTypes;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExternalQuestionTypeDefinitionTest {
    private static final String TYPE = "sample.TRUE_FALSE";
    private final AtomicInteger sequence = new AtomicInteger();
    private final ExternalQuestionTypeDefinition type = new ExternalQuestionTypeDefinition(TYPE, "判断题",
            io.quizforge.core.question.type.QuestionTypeDefinition.Family.OBJECTIVE, "1.0.0", this::invoke);

    private Map<String, Object> invoke(String operation, Map<String, Object> input) {
        return switch (operation) {
            case "createDraft" -> Map.of("prompt", Map.of("kind", "TEXT", "text", "The Earth is round."),
                    "payload", Map.of("statement", "The Earth is round."), "answerSpec", Map.of("correct", true),
                    "analysis", Map.of("kind", "TEXT", "text", "Reference explanation"), "maxScore", 2);
            case "duplicate" -> ExternalQuestionTypeDefinition.object(input.get("question"));
            case "validate" -> Map.of("errors", List.of());
            case "snapshot", "targets" -> Map.of("targets", List.of(Map.of("id", "decision", "number", 1, "gradable", true, "label", "判断")));
            case "validateAnswer" -> {
                var answer = ExternalQuestionTypeDefinition.object(input.get("answer"));
                yield Map.of("errors", !answer.containsKey("value") || answer.get("value") instanceof Boolean ? List.of() : List.of("Boolean answer required"), "empty", !answer.containsKey("value"));
            }
            case "grade" -> {
                var question = ExternalQuestionTypeDefinition.object(input.get("question"));
                var spec = ExternalQuestionTypeDefinition.object(ExternalQuestionTypeDefinition.object(question.get("answerSpec")).get("data"));
                boolean correct = Objects.equals(spec.get("correct"), ExternalQuestionTypeDefinition.object(input.get("answer")).get("value"));
                yield Map.of("status", correct ? "CORRECT" : "INCORRECT", "score", correct ? input.get("maxScore") : 0, "maxScore", input.get("maxScore"));
            }
            default -> throw new IllegalArgumentException(operation);
        };
    }
    private Question draft() { return type.createDraft(prefix -> prefix + sequence.incrementAndGet(), List.of()); }
    private QuestionBank bank(Question question) { return new QuestionBank("qb_extension", "Extension bank", "2.0", List.of(), List.of(question), List.of()); }
    @AfterEach void remove() { QuestionTypes.unregister(TYPE); }

    @Test void independentlyRegisteredTypeCreatesDuplicatesValidatesSnapshotsAndGrades() {
        QuestionTypes.register(type);
        assertEquals(type, QuestionTypes.require(TYPE));
        var question = draft();
        new QuestionBankValidator().validate(bank(question));
        var copy = type.duplicate(question, prefix -> prefix + sequence.incrementAndGet());
        assertNotEquals(question.id(), copy.id());
        assertEquals(question.payload(), copy.payload());
        assertEquals("decision", type.outlineTargets(question).getFirst().id());
        var snapshot = new PracticeQuestionSnapshotMapper().map(question);
        var presentation = ExternalQuestionTypeDefinition.object(ExternalQuestionTypeDefinition.object(snapshot.correctAnswer().value()).get("extensionPresentation"));
        var publicQuestion = ExternalQuestionTypeDefinition.object(presentation.get("question"));
        assertFalse(publicQuestion.containsKey("answerSpec"));
        assertFalse(publicQuestion.containsKey("analysis"));
        assertTrue(type.validateAnswer(snapshot, new PracticePayload(Map.of())));
        assertFalse(type.validateAnswer(snapshot, new PracticePayload(Map.of("value", false))));
        assertEquals(QuestionAttempt.Result.CORRECT, type.grade(snapshot, new PracticePayload(Map.of("value", true))).result());
        assertEquals(0.0, type.grade(snapshot, new PracticePayload(Map.of("value", false))).score());
        assertThrows(IllegalArgumentException.class, () -> type.validateAnswer(snapshot, new PracticePayload(Map.of("value", "invalid"))));
        assertThrows(IllegalStateException.class, () -> type.grade(snapshot, null));
    }

    @Test void opaqueDataRemainsReadableWithoutInstalledTypeButCannotExecute() {
        var question = draft();
        new QuestionBankValidator().validate(bank(question));
        assertTrue(QuestionTypes.find(TYPE).isEmpty());
        assertInstanceOf(MissingExtensionQuestionType.class, QuestionTypes.forData(question));
        assertThrows(IllegalStateException.class, () -> QuestionTypes.forData(question).createDraft(prefix -> prefix + "1", List.of()));
        var mutable = new LinkedHashMap<String, Object>(); mutable.put("nested", new ArrayList<>(List.of("before")));
        var frozen = new ExtensionPayload(mutable);
        ((List<String>) mutable.get("nested")).add("after");
        assertEquals(List.of("before"), frozen.data().get("nested"));
        assertThrows(UnsupportedOperationException.class, () -> frozen.data().put("added", 1));
    }

    @Test void maliciousRuleCannotIncreaseFrozenScoreOrGiveCorrectPartialCredit() {
        QuestionTypes.register(type);
        var snapshot = type.snapshot(draft());
        for (var badGrade : List.of(Map.of("status", "CORRECT", "score", 3, "maxScore", 2),
                Map.of("status", "CORRECT", "score", 1, "maxScore", 2), Map.of("status", "INCORRECT", "score", -1, "maxScore", 2),
                Map.of("status", "CORRECT", "score", 3, "maxScore", 3))) {
            var bad = new ExternalQuestionTypeDefinition(TYPE, "判断题", type.family(), "1.0.0", (operation, input) ->
                    operation.equals("grade") ? new LinkedHashMap<>(badGrade) : invoke(operation, input));
            assertThrows(IllegalArgumentException.class, () -> bad.grade(snapshot, new PracticePayload(Map.of("value", true))));
        }
    }

    @Test void genericAnswerAndScoreHydrateWithoutChoicePayloadAndFreezeAttemptResult() {
        QuestionTypes.register(type);
        var question = draft(); var bank = bank(question); var now = Instant.parse("2026-10-04T00:00:00Z");
        var snapshot = new PracticeQuestionSnapshotMapper().map(question, List.of(), io.quizforge.core.port.QuestionResourceInput.NONE);
        var answer = new PracticePayload(Map.of("value", true));
        var row = new PracticeSessionQuestion("psq_1", "ps_1", question.id(), 0, snapshot, PracticeSessionQuestion.State.SUBMITTED, null, now, now);
        var attempt = new QuestionAttempt("pa_1", row.id(), 1, QuestionAttempt.Mode.INITIAL, answer, QuestionAttempt.Result.CORRECT, 2.0, 2.0, now);
        var persisted = new ActivePracticeSnapshot(new PracticeSession("ps_1", bank.assetId(), "qfb:v2:" + "0".repeat(64), bank.title(),
                PracticeSession.Status.ACTIVE, PracticeSession.View.QUESTION, question.id(), now, now, null), List.of(new ActivePracticeSnapshot.Question(row, List.of(attempt))));
        var runtime = new QuestionBankPracticeSession(bank);
        new PracticeRuntimeMapper().hydrate(runtime, persisted);
        assertEquals(QuestionBankPracticeSession.State.SUBMITTED, runtime.state());
        assertTrue(runtime.correct());
        assertEquals(BigDecimal.valueOf(2), PracticeSummary.from(persisted).score().orElseThrow());
        var presentation = new LinkedHashMap<>(ExternalQuestionTypeDefinition.object(snapshot.correctAnswer().value()));
        presentation.remove("extensionPresentation");
        var logical = new PracticeSessionQuestion.Snapshot(snapshot.questionType(), snapshot.stem(), snapshot.options(), new PracticePayload(presentation), snapshot.analysis(), snapshot.sourceRefs());
        assertEquals(PracticeQuestionSnapshotMapper.logical(snapshot), PracticeQuestionSnapshotMapper.logical(logical));
    }

    @Test void missingTargetNumbersAndFlagsBecomeFrozenDefaultsAndInvalidValuesAreRejected() {
        var values = ExternalQuestionTypeDefinition.targetsFrom(Map.of("targets", List.of(Map.of("id", "first"), Map.of("id", "second", "locked", true))));
        assertEquals(BigDecimal.ONE, values.getFirst().get("number"));
        assertEquals(BigDecimal.valueOf(2), values.getLast().get("number"));
        assertEquals(false, values.getFirst().get("locked"));
        assertEquals(true, values.getFirst().get("gradable"));
        assertEquals(false, values.getLast().get("gradable"));
        for (var malformed : List.of(Map.of("id", "item", "number", 0), Map.of("id", "item", "number", 1.5),
                Map.of("id", "item", "number", "1"), Map.of("id", "item", "locked", "false"), Map.of("id", "item", "gradable", "true")))
            assertThrows(IllegalArgumentException.class, () -> ExternalQuestionTypeDefinition.targetsFrom(Map.of("targets", List.of(malformed))));
        assertThrows(IllegalArgumentException.class, () -> ExternalQuestionTypeDefinition.targetsFrom(Map.of("targets", List.of(
                Map.of("id", "first", "number", 1), Map.of("id", "second", "number", 1)))));
    }

    @Test void opaqueExtensionKeepsNestedResourcesWhenAnotherQuestionChanges() {
        var extension = draft();
        var resource = new io.quizforge.core.question.resource.QBankResource("res_nested", io.quizforge.core.question.resource.ResourceKind.IMAGE,
                "image/png", "resources/nested.png", "a".repeat(64));
        var image = new io.quizforge.core.question.content.RichContent(new io.quizforge.core.question.content.RichDocument(List.of(
                new io.quizforge.core.question.content.BlockImageNode(resource.id(), "image", null))));
        extension = new Question(extension.id(), extension.type(), extension.stimulusRefs(), extension.prompt(),
                new ExtensionPayload(Map.of("nested", Map.of("reference", io.quizforge.core.question.content.QuestionContentData.encode(image)))),
                extension.answerSpec(), extension.scoreSpec(), extension.evaluationSpec(), extension.analysis(), extension.sourceRefs());
        QuestionTypes.register(type);
        var other = draft();
        other = new Question(other.id(), other.type(), other.stimulusRefs(), image, other.payload(), other.answerSpec(), other.scoreSpec(), other.evaluationSpec(), other.analysis(), other.sourceRefs());
        var model = new io.quizforge.core.question.service.QuestionBankEditorModel(new QuestionBank("qb_extension", "Resources", "2.0", List.of(), List.of(other, extension), List.of(resource)));
        model.setPrompt(0, new io.quizforge.core.question.content.TextContent("A different question"));
        assertEquals(List.of(resource), model.bank().resources());
        new QuestionBankValidator().validate(model.bank());
    }

    @Test void extensionPartialEditKeepsOmittedFieldsAndInvalidEditPublishesNothing() {
        QuestionTypes.register(type);
        var original = draft();
        var model = new io.quizforge.core.question.service.QuestionBankEditorModel(bank(original));
        var untouched = model.bank();
        assertThrows(IllegalArgumentException.class, () -> model.setExtensionQuestion(0, Map.of("scoreSpec", Map.of("defaultMaxScore", -1))));
        assertSame(untouched, model.bank());
        assertFalse(model.dirty());
        model.setExtensionQuestion(0, Map.of("prompt", Map.of("kind", "TEXT", "text", ""), "id", "q_injected", "type", "ESSAY"));
        var edited = model.bank().questions().getFirst();
        assertEquals(original.id(), edited.id()); assertEquals(TYPE, edited.type());
        assertEquals(original.payload(), edited.payload()); assertEquals(original.answerSpec(), edited.answerSpec());
        assertEquals(original.analysis(), edited.analysis()); assertEquals(original.scoreSpec(), edited.scoreSpec());
        assertEquals("", io.quizforge.core.question.content.QuestionContentData.plainText(edited.prompt()));
    }
}
