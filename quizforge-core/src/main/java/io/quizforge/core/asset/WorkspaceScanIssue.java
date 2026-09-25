package io.quizforge.core.asset;

/** A path is always relative to the scanned workspace. */
public record WorkspaceScanIssue(String code, String currentPath, String detail) { }
