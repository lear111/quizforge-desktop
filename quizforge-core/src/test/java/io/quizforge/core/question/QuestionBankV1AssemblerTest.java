package io.quizforge.core.question;

import static org.junit.jupiter.api.Assertions.*;

import io.quizforge.extension.question.SourceAwareQuestionGenerator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class QuestionBankV1AssemblerTest {
    private final QuestionBankV1Assembler assembler = new QuestionBankV1Assembler();
    private final SourceDocumentSnapshot source = new SourceDocumentSnapshot("doc_a",
            "qfd:v1:" + "a".repeat(64), "Document", List.of(new SourceDocumentSnapshot.Chapter(
                    "chapter_a", "Chapter", List.of(new SourceDocumentSnapshot.Section(
                            "section_a", "Section", "Body")))));

    @Test void rejectsInvalidSingleChoiceRules() {
        assertRejected(candidate("SINGLE_CHOICE", List.of("A", "B"), 2, refs()));
        assertRejected(candidate("SINGLE_CHOICE", List.of("A"), 1, refs()));
        assertRejected(candidate("SINGLE_CHOICE", List.of("Z"), 2, refs()));
        assertRejected(new SourceAwareQuestionGenerator.Candidate("SINGLE_CHOICE", "Stem", "Analysis",
                List.of(new SourceAwareQuestionGenerator.Option("A", "One"),
                        new SourceAwareQuestionGenerator.Option("A", "Two")), List.of("A"), refs()));
    }

    @Test void rejectsInvalidMultipleChoiceRules() {
        assertRejected(candidate("MULTIPLE_CHOICE", List.of("A"), 3, refs()));
        assertRejected(candidate("MULTIPLE_CHOICE", List.of("A", "B"), 2, refs()));
        assertEquals(1, assemble(candidate("MULTIPLE_CHOICE", List.of("A", "B"), 3, refs()))
                .bank().questions().size());
    }

    @Test void rejectsMissingDuplicateAndUnselectedReferences() {
        assertRejected(candidate("SINGLE_CHOICE", List.of("A"), 2, List.of()));
        assertRejected(candidate("SINGLE_CHOICE", List.of("A"), 2,
                List.of(new SourceAwareQuestionGenerator.SourceRef("doc_missing", "section_a"))));
        assertRejected(candidate("SINGLE_CHOICE", List.of("A"), 2,
                List.of(new SourceAwareQuestionGenerator.SourceRef("doc_a", "section_missing"))));
        assertRejected(candidate("SINGLE_CHOICE", List.of("A"), 2,
                List.of(refs().getFirst(), refs().getFirst())));
        var result = assembler.assemble("Bank", null, List.of(source), Map.of("doc_a", Set.of()),
                List.of(candidate("SINGLE_CHOICE", List.of("A"), 2, refs())),
                EnumSet.of(QuestionType.SINGLE_CHOICE), 1);
        assertEquals(1, result.rejected());
    }

    @Test void assignsDistinctLocalQuestionAndOptionIds() {
        var result = assembler.assemble("Bank", null, List.of(source), Map.of("doc_a", Set.of("section_a")),
                List.of(candidate("SINGLE_CHOICE", List.of("A"), 2, refs()),
                        candidate("SINGLE_CHOICE", List.of("B"), 2, refs())),
                EnumSet.of(QuestionType.SINGLE_CHOICE), 2);
        var first = result.bank().questions().getFirst();
        var second = result.bank().questions().getLast();
        assertNotEquals(first.id(), second.id());
        assertNotEquals(first.data().options().getFirst().id(), second.data().options().getFirst().id());
        assertTrue(first.data().options().stream().map(QuestionBankFile.Option::id).toList()
                .containsAll(first.data().correctOptionIds()));
        assertEquals(source.contentId(), first.sourceRefs().getFirst().documentContentId());
    }

    private void assertRejected(SourceAwareQuestionGenerator.Candidate candidate) {
        assertEquals(1, assemble(candidate).rejected());
    }

    private QuestionBankV1Assembler.Result assemble(SourceAwareQuestionGenerator.Candidate candidate) {
        return assembler.assemble("Bank", null, List.of(source), Map.of("doc_a", Set.of("section_a")),
                List.of(candidate), EnumSet.allOf(QuestionType.class), 1);
    }

    private List<SourceAwareQuestionGenerator.SourceRef> refs() {
        return List.of(new SourceAwareQuestionGenerator.SourceRef("doc_a", "section_a"));
    }

    private SourceAwareQuestionGenerator.Candidate candidate(String type, List<String> correct,
            int optionCount, List<SourceAwareQuestionGenerator.SourceRef> refs) {
        var options = new java.util.ArrayList<SourceAwareQuestionGenerator.Option>();
        for (int i = 0; i < optionCount; i++) options.add(new SourceAwareQuestionGenerator.Option(
                Character.toString('A' + i), "Option " + i));
        return new SourceAwareQuestionGenerator.Candidate(type, "Stem", "Analysis", options, correct, refs);
    }
}
