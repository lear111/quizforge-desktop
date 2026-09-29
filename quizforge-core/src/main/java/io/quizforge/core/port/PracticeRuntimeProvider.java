package io.quizforge.core.port;

import io.quizforge.core.question.*;

import io.quizforge.core.practice.PersistentPracticeRuntime;
import io.quizforge.core.practice.PracticeHistoryService;
import io.quizforge.core.workspace.WorkspaceId;

/** Select the Workspace persistence boundary without exposing database details to the UI. */
public interface PracticeRuntimeProvider {
    PersistentPracticeRuntime open(WorkspaceId workspace, QuestionBank bank);
    PracticeHistoryService history(WorkspaceId workspace);
}
