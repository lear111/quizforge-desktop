package io.quizforge.core.workspace;

import io.quizforge.core.question.QuestionBankFile;

/** File content captured when a viewer is opened, rather than read from legacy tables. */
public record OpenedWorkspaceFile(WorkspaceFileEntry entry, String sourceText,
        QuestionBankFile questionBank) { }
