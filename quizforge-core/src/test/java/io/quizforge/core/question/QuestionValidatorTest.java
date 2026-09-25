package io.quizforge.core.question;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quizforge.extension.document.StandardDocumentStructure;
import io.quizforge.extension.question.GeneratedOption;
import io.quizforge.extension.question.GeneratedQuestion;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class QuestionValidatorTest {
    private final QuestionValidator validator = new QuestionValidator();
    private final StandardDocumentStructure structure = new StandardDocumentStructure("Java", "all", List.of(
            new StandardDocumentStructure.Chapter("c1", "1. List", "chapter", List.of(
                    new StandardDocumentStructure.Section("s1", "1.1 ArrayList", "section"))),
            new StandardDocumentStructure.Chapter("c2", "2. Map", "chapter", List.of(
                    new StandardDocumentStructure.Section("s2", "2.1 HashMap", "section")))));
    private final List<GeneratedOption> options = List.of(new GeneratedOption("A", "one"),
            new GeneratedOption("B", "two"), new GeneratedOption("C", "three"));

    private GeneratedQuestion candidate(String type, List<GeneratedOption> opts, List<String> answers) {
        return new GeneratedQuestion(type, "stem", opts, answers, "analysis", "1. List", "1.1 ArrayList");
    }

    private boolean valid(GeneratedQuestion candidate) {
        return validator.valid(candidate, Set.of(QuestionType.SINGLE_CHOICE, QuestionType.MULTIPLE_CHOICE),
                structure, GenerationScopeType.SECTION, "c1", "s1");
    }

    @Test void validSingleAndMultiple() {
        assertTrue(valid(candidate("SINGLE_CHOICE", options, List.of("A"))));
        assertTrue(valid(candidate("MULTIPLE_CHOICE", options, List.of("A", "B"))));
    }

    @Test void commonInvalidFields() {
        assertFalse(valid(new GeneratedQuestion("SINGLE_CHOICE", " ", options, List.of("A"), "analysis", "1. List", "1.1 ArrayList")));
        assertFalse(valid(new GeneratedQuestion("SINGLE_CHOICE", "stem", options, List.of("A"), " ", "1. List", "1.1 ArrayList")));
        assertFalse(valid(candidate("UNKNOWN", options, List.of("A"))));
        assertFalse(valid(candidate("SINGLE_CHOICE", List.of(options.getFirst(), options.getFirst()), List.of("A"))));
        assertFalse(valid(candidate("SINGLE_CHOICE", List.of(new GeneratedOption("A", " "), options.get(1)), List.of("A"))));
        assertFalse(valid(candidate("SINGLE_CHOICE", List.of(new GeneratedOption(" ", "one"), options.get(1)), List.of("A"))));
        assertFalse(valid(candidate("SINGLE_CHOICE", options, List.of("Z"))));
        assertFalse(valid(candidate("SINGLE_CHOICE", options, List.of("A", "A"))));
    }

    @Test void typeRules() {
        assertFalse(valid(candidate("SINGLE_CHOICE", options, List.of())));
        assertFalse(valid(candidate("SINGLE_CHOICE", options, List.of("A", "B"))));
        assertFalse(valid(candidate("MULTIPLE_CHOICE", options, List.of())));
        assertFalse(valid(candidate("MULTIPLE_CHOICE", options, List.of("A"))));
        assertFalse(valid(candidate("MULTIPLE_CHOICE", options, List.of("A", "B", "C"))));
        assertFalse(validator.valid(candidate("SINGLE_CHOICE", options, List.of("A")),
                Set.of(QuestionType.MULTIPLE_CHOICE), structure, GenerationScopeType.DOCUMENT, null, null));
    }

    @Test void scopeRules() {
        GeneratedQuestion wrongChapter = new GeneratedQuestion("SINGLE_CHOICE", "stem", options,
                List.of("A"), "analysis", "2. Map", "2.1 HashMap");
        GeneratedQuestion wrongSection = new GeneratedQuestion("SINGLE_CHOICE", "stem", options,
                List.of("A"), "analysis", "1. List", "2.1 HashMap");
        assertFalse(valid(wrongChapter));
        assertFalse(valid(wrongSection));
        assertTrue(validator.valid(wrongChapter, Set.of(QuestionType.SINGLE_CHOICE), structure,
                GenerationScopeType.DOCUMENT, null, null));
        assertFalse(validator.valid(wrongChapter, Set.of(QuestionType.SINGLE_CHOICE), structure,
                GenerationScopeType.CHAPTER, "c1", null));
    }
}
