package io.quizforge.core.question.type.extension;

/** A failed extension execution is never a grading result. */
public final class ExtensionExecutionException extends IllegalStateException {
    private final String code;
    public ExtensionExecutionException(String code, String message) { super(message); this.code = code; }
    public ExtensionExecutionException(String code, String message, Throwable cause) { super(message, cause); this.code = code; }
    public String code() { return code; }
}
