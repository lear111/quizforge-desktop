package io.quizforge.core.question;

import java.util.Set;

public record QuestionGenerationCommand(String name, GenerationScopeType scope,
        String chapterId, String sectionId, Set<QuestionType> types, int count) { }
