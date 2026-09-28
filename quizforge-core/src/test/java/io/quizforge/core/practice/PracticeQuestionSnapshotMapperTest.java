package io.quizforge.core.practice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import io.quizforge.core.question.QuestionBankFile;
import io.quizforge.core.question.QuestionSourceAddress;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PracticeQuestionSnapshotMapperTest {
    private final PracticeQuestionSnapshotMapper mapper = new PracticeQuestionSnapshotMapper();

    @Test void mapsAllSnapshotFieldsWithNamedAnchorIdentityAndRevision() {
        var ref = QuestionBankFile.SourceRef.anchor("doc_1", "qfd:v2:revision", "定义", 2, "Java", "定义");
        var question = entry(List.of(ref), List.of("opt_b", "opt_a"));
        var snapshot = mapper.map(question);
        assertEquals(question.type(), snapshot.questionType());
        assertEquals(question.stem(), snapshot.stem());
        assertEquals(question.analysis(), snapshot.analysis());
        assertEquals(new PracticePayload(List.of(Map.of("id", "opt_a", "content", "数组"),
                Map.of("id", "opt_b", "content", "链表"))), snapshot.options());
        assertEquals(new PracticePayload(Map.of("correctOptionIds", List.of("opt_a", "opt_b"))), snapshot.correctAnswer());
        assertEquals(new PracticePayload(List.of(Map.of("documentAssetId", "doc_1", "documentContentId", "qfd:v2:revision",
                "anchorName", "定义", "occurrence", 2, "documentTitle", "Java", "sectionTitle", "定义"))), snapshot.sourceRefs());
    }

    @Test void keepsLegacySectionAndNodeAddressKindsDistinct() {
        var section = new QuestionBankFile.SourceRef("doc_1", "revision", QuestionSourceAddress.section("same"), "Java", "来源");
        var node = new QuestionBankFile.SourceRef("doc_1", "revision", QuestionSourceAddress.node("same"), "Java", "来源");
        var sectionSnapshot = mapper.map(entry(List.of(section), List.of("opt_a")));
        var nodeSnapshot = mapper.map(entry(List.of(node), List.of("opt_a")));
        assertNotEquals(sectionSnapshot.sourceRefs(), nodeSnapshot.sourceRefs());
        var fields = (Map<?, ?>) ((List<?>) sectionSnapshot.sourceRefs().value()).getFirst();
        assertEquals("same", fields.get("sectionId"));
        assertFalse(fields.containsKey("nodeId"));
        assertFalse(fields.containsKey("anchorName"));
    }

    @Test void correctAnswerPermutationHasSameMeaningButOptionPermutationDoesNot() {
        var question = entry(List.of(), List.of("opt_b", "opt_a"));
        var equivalent = entry(List.of(), List.of("opt_a", "opt_b"));
        assertEquals(mapper.map(question), mapper.map(equivalent));
        var reordered = new QuestionBankFile.Entry(question.id(), question.type(), question.stem(), question.analysis(),
                question.sourceRefs(), new QuestionBankFile.Data(question.data().options().reversed(), question.data().correctOptionIds()));
        assertNotEquals(mapper.map(question).options(), mapper.map(reordered).options());
    }

    private QuestionBankFile.Entry entry(List<QuestionBankFile.SourceRef> refs, List<String> correct) {
        return new QuestionBankFile.Entry("q_1", "MULTIPLE_CHOICE", "选出结构", "解析", refs,
                new QuestionBankFile.Data(List.of(new QuestionBankFile.Option("opt_a", "数组"),
                        new QuestionBankFile.Option("opt_b", "链表")), correct));
    }
}
