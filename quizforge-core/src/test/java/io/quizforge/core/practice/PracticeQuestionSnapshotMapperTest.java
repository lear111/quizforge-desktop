package io.quizforge.core.practice;

import io.quizforge.core.question.content.TextContent;
import io.quizforge.core.question.model.Question;
import io.quizforge.core.question.source.QuestionSourceAddress;
import io.quizforge.core.question.source.SourceRef;
import io.quizforge.core.question.type.objective.choice.ChoiceAnswerSpec;
import io.quizforge.core.question.type.objective.choice.ChoiceOption;
import io.quizforge.core.question.type.objective.choice.ChoicePayload;
import io.quizforge.core.question.type.objective.choice.QuestionText;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PracticeQuestionSnapshotMapperTest {
    private final PracticeQuestionSnapshotMapper mapper = new PracticeQuestionSnapshotMapper();

    @Test void mapsAllSnapshotFieldsWithNamedAnchorIdentityAndRevision() {
        var ref = SourceRef.anchor("doc_1", "qfd:v2:revision", "定义", 2, "Java", "定义");
        var question = entry(List.of(ref), List.of("opt_b", "opt_a"));
        var snapshot = mapper.map(question);
        assertEquals(question.type(), snapshot.questionType());
        assertEquals(QuestionText.prompt(question), snapshot.stem());
        assertEquals(QuestionText.analysis(question), snapshot.analysis());
        assertEquals(new PracticePayload(List.of(Map.of("id", "opt_a", "content", "数组"),
                Map.of("id", "opt_b", "content", "链表"))), snapshot.options());
        assertEquals(new PracticePayload(Map.of("correctOptionIds", List.of("opt_a", "opt_b"),
                "maxScore", question.scoreSpec().defaultMaxScore())), snapshot.correctAnswer());
        assertEquals(new PracticePayload(List.of(Map.of("documentAssetId", "doc_1", "documentContentId", "qfd:v2:revision",
                "anchorName", "定义", "occurrence", 2, "documentTitle", "Java", "sectionTitle", "定义"))), snapshot.sourceRefs());
    }

    @Test void currentSnapshotsRejectLegacyAddressesWithoutWritingOldFields() {
        var section = new SourceRef("doc_1", "revision", QuestionSourceAddress.section("same"), "Java", "来源");
        var node = new SourceRef("doc_1", "revision", QuestionSourceAddress.node("same"), "Java", "来源");
        assertThrows(IllegalArgumentException.class, () -> mapper.map(entry(List.of(section), List.of("opt_a"))));
        assertThrows(IllegalArgumentException.class, () -> mapper.map(entry(List.of(node), List.of("opt_a"))));
    }

    @Test void sharedStimuliCannotBeSilentlyDroppedFromTextSnapshots() {
        var q = entry(List.of(), List.of("opt_a"));
        var referenced = new Question(q.id(), q.type(), List.of("stim_article"), q.prompt(),
                q.payload(), q.answerSpec(), q.scoreSpec(), q.evaluationSpec(), q.analysis(), q.sourceRefs());
        assertThrows(UnsupportedOperationException.class, () -> mapper.map(referenced));
    }

    @Test void correctAnswerPermutationHasSameMeaningButOptionPermutationDoesNot() {
        var question = entry(List.of(), List.of("opt_b", "opt_a"));
        var equivalent = entry(List.of(), List.of("opt_a", "opt_b"));
        assertEquals(mapper.map(question), mapper.map(equivalent));
        var reordered = Question.choice(question.id(), question.type(), question.prompt(), question.analysis(), question.sourceRefs(), new ChoicePayload(question.choicePayload().options().reversed()), new ChoiceAnswerSpec(question.choiceAnswerSpec().correctOptionIds()));
        assertNotEquals(mapper.map(question).options(), mapper.map(reordered).options());
    }

    private Question entry(List<SourceRef> refs, List<String> correct) {
        return Question.choice("q_1", "MULTIPLE_CHOICE", new TextContent("选出结构"), new TextContent("解析"), refs, new ChoicePayload(List.of(new ChoiceOption("opt_a", new TextContent("数组")),
                        new ChoiceOption("opt_b", new TextContent("链表")))), new ChoiceAnswerSpec(correct));
    }
}
