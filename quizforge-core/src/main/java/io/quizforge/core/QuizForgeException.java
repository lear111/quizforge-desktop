package io.quizforge.core;

public final class QuizForgeException extends RuntimeException {
    private final ErrorCode code;

    public QuizForgeException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public QuizForgeException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }
}
