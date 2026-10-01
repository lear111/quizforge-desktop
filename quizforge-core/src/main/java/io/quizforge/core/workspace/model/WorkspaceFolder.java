package io.quizforge.core.workspace.model;

import java.nio.file.Path;

/** Identity and location read from an existing workspace folder. */
public record WorkspaceFolder(WorkspaceId id, String name, Path rootPath) { }
