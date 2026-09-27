package io.quizforge.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import io.quizforge.core.question.QuestionBankFile;
import io.quizforge.infrastructure.filesystem.QuestionBankV1Codec;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuestionBankV1CodecTest {
    private final QuestionBankV1Codec codec = new QuestionBankV1Codec();
    private static final String REVISION = "qfd:v1:" + "a".repeat(64);

    static QuestionBankFile valid(String type) {
        var source = new QuestionBankFile.SourceDocument("doc_one", REVISION, "Document");
        var ref = new QuestionBankFile.SourceRef("doc_one", REVISION, "section_one", "Document", "Section");
        var options = List.of(new QuestionBankFile.Option("opt_a", "A"),
                new QuestionBankFile.Option("opt_b", "B"), new QuestionBankFile.Option("opt_c", "C"));
        var correct = "SINGLE_CHOICE".equals(type) ? List.of("opt_a") : List.of("opt_a", "opt_b");
        var question = new QuestionBankFile.Entry("q_one", type, "Stem", "Analysis",
                List.of(ref), new QuestionBankFile.Data(options, correct));
        return new QuestionBankFile("quizforge-question-bank", "1.0", "qb_one", "Bank",
                List.of(source), List.of(question));
    }

    private QuestionBankFile withQuestions(QuestionBankFile bank, List<QuestionBankFile.Entry> questions) {
        return new QuestionBankFile(bank.format(), bank.schemaVersion(), bank.id(), bank.title(),
                bank.sourceDocuments(), questions);
    }

    private QuestionBankFile.Entry withCorrect(QuestionBankFile.Entry question, List<String> ids) {
        return new QuestionBankFile.Entry(question.id(), question.type(), question.stem(),
                question.analysis(), question.sourceRefs(),
                new QuestionBankFile.Data(question.data().options(), ids));
    }

    @Test void parseAndRoundTripBothChoiceTypes() {
        for (String type : List.of("SINGLE_CHOICE", "MULTIPLE_CHOICE")) {
            var bank = valid(type);
            assertEquals(bank, codec.parse(codec.write(bank)));
        }
    }

    @Test void rejectsInvalidJsonMissingMetadataAndTrailingData() {
        String json = codec.write(valid("SINGLE_CHOICE"));
        assertThrows(RuntimeException.class, () -> codec.parse("{invalid"));
        assertThrows(RuntimeException.class, () -> codec.parse(json.replaceFirst("\"format\"",
                "\"absent\"")));
        assertThrows(RuntimeException.class, () -> codec.parse(json.replaceFirst("\"schemaVersion\"",
                "\"absent\"")));
        assertThrows(RuntimeException.class, () -> codec.parse(json + "{}"));
    }

    @Test void rejectsDuplicateQuestionAndOptionIds() {
        var bank = valid("SINGLE_CHOICE");
        assertThrows(RuntimeException.class, () -> codec.validate(withQuestions(bank,
                List.of(bank.questions().getFirst(), bank.questions().getFirst()))));
        var question = bank.questions().getFirst();
        var duplicate = new QuestionBankFile.Entry("q_second", question.type(), question.stem(),
                question.analysis(), question.sourceRefs(), question.data());
        assertThrows(RuntimeException.class, () -> codec.validate(withQuestions(bank,
                List.of(question, duplicate))));
    }

    @Test void enforcesSingleAndMultipleCorrectAnswerRules() {
        var single = valid("SINGLE_CHOICE");
        var question = single.questions().getFirst();
        assertThrows(RuntimeException.class, () -> codec.validate(withQuestions(single,
                List.of(withCorrect(question, List.of())))));
        assertThrows(RuntimeException.class, () -> codec.validate(withQuestions(single,
                List.of(withCorrect(question, List.of("opt_a", "opt_b"))))));
        assertThrows(RuntimeException.class, () -> codec.validate(withQuestions(single,
                List.of(withCorrect(question, List.of("opt_absent"))))));
        var multiple = valid("MULTIPLE_CHOICE");
        var multiQuestion = multiple.questions().getFirst();
        assertThrows(RuntimeException.class, () -> codec.validate(withQuestions(multiple,
                List.of(withCorrect(multiQuestion, List.of("opt_a"))))));
        assertThrows(RuntimeException.class, () -> codec.validate(withQuestions(multiple,
                List.of(withCorrect(multiQuestion, List.of("opt_a", "opt_b", "opt_c"))))));
    }

    @Test void sourceRefsMustBelongToDeclaredDocumentRevision() {
        var bank = valid("SINGLE_CHOICE");
        var question = bank.questions().getFirst();
        for (var invalid : List.of(
                new QuestionBankFile.SourceRef("doc_missing", REVISION, "section_one", "Other", "Section"),
                new QuestionBankFile.SourceRef("doc_one", "qfd:v1:" + "b".repeat(64),
                        "section_one", "Document", "Section"),
                new QuestionBankFile.SourceRef("doc_one", REVISION, "bad section", "Document", "Section"))) {
            var edited = new QuestionBankFile.Entry(question.id(), question.type(), question.stem(),
                    question.analysis(), List.of(invalid), question.data());
            assertThrows(RuntimeException.class,
                    () -> codec.validate(withQuestions(bank, List.of(edited))));
        }
    }

    @Test void revisionIsDeterministicAndTracksMeaningfulChanges() {
        var bank = valid("SINGLE_CHOICE");
        String initial = codec.contentId(bank);
        assertTrue(initial.matches("qfb:v1:[0-9a-f]{64}"));
        assertEquals(initial, codec.contentId(codec.parse(codec.write(bank))));
        var renamed = new QuestionBankFile(bank.format(), bank.schemaVersion(), "qb_another",
                bank.title(), bank.sourceDocuments(), bank.questions());
        assertEquals(initial, codec.contentId(renamed));
        var question = bank.questions().getFirst();
        var changed = new QuestionBankFile.Entry(question.id(), question.type(), "New stem",
                question.analysis(), question.sourceRefs(), question.data());
        assertNotEquals(initial, codec.contentId(withQuestions(bank, List.of(changed))));
        var second = new QuestionBankFile.Entry("q_second", question.type(), changed.stem(),
                question.analysis(), question.sourceRefs(), new QuestionBankFile.Data(List.of(
                        new QuestionBankFile.Option("opt_second_a", "A"),
                        new QuestionBankFile.Option("opt_second_b", "B")), List.of("opt_second_a")));
        assertNotEquals(initial, codec.contentId(withQuestions(bank,
                List.of(question, second))));
    }
}
