package io.quizforge.core.question.type.extension;

import java.util.List;

/** A host-owned rejection; paths are JSON pointers, never extension-supplied error codes. */
public final class ExtensionDataValidationException extends IllegalArgumentException {
    public record Issue(String path, String message) { }
    private final List<Issue> issues;
    public ExtensionDataValidationException(List<Issue> issues) {
        super("DATA_VALIDATION_FAILED: " + String.join("; ", issues.stream()
                .map(issue -> issue.path() + ": " + issue.message()).toList()));
        this.issues = List.copyOf(issues);
    }
    public List<Issue> issues() { return issues; }
    public static ExtensionDataValidationException at(String path, String message) {
        return new ExtensionDataValidationException(List.of(new Issue(path, message)));
    }
}
