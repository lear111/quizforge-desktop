package io.quizforge.core.question;

import io.quizforge.extension.document.StandardDocumentStructure;
import io.quizforge.extension.question.GeneratedOption;
import io.quizforge.extension.question.GeneratedQuestion;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;

public final class QuestionValidator {
    private static final Map<QuestionType, BiPredicate<Integer, Integer>> ANSWER_RULES = Map.of(
            QuestionType.SINGLE_CHOICE, (correct, total) -> correct == 1,
            QuestionType.MULTIPLE_CHOICE, (correct, total) -> correct >= 2 && correct < total);

    public boolean valid(GeneratedQuestion candidate, Set<QuestionType> requestedTypes,
            StandardDocumentStructure structure, GenerationScopeType scope,
            String chapterId, String sectionId) {
        if (candidate == null || blank(candidate.stem()) || blank(candidate.analysis())
                || candidate.options().size() < 2 || candidate.correctAnswers().isEmpty()) return false;
        QuestionType type;
        try { type = QuestionType.valueOf(candidate.type()); }
        catch (IllegalArgumentException | NullPointerException error) { return false; }
        if (!requestedTypes.contains(type)) return false;
        Set<String> optionKeys = new HashSet<>();
        for (GeneratedOption option : candidate.options()) {
            if (option == null || blank(option.key()) || blank(option.content())
                    || !optionKeys.add(option.key().trim())) return false;
        }
        Set<String> answers = new HashSet<>();
        for (String answer : candidate.correctAnswers()) {
            if (blank(answer) || !optionKeys.contains(answer.trim())
                    || !answers.add(answer.trim())) return false;
        }
        if (!ANSWER_RULES.get(type).test(answers.size(), candidate.options().size())) return false;
        for (StandardDocumentStructure.Chapter chapter : structure.chapters()) {
            if (!chapter.title().equals(candidate.sourceChapter())
                    || scope != GenerationScopeType.DOCUMENT && !chapter.id().equals(chapterId)) continue;
            for (StandardDocumentStructure.Section section : chapter.sections()) {
                if (section.title().equals(candidate.sourceSection())
                        && (scope != GenerationScopeType.SECTION || section.id().equals(sectionId))) return true;
            }
        }
        return false;
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
}
