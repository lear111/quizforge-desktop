package io.quizforge.core.ai;

public final class AiProviderException extends RuntimeException {
    private final AiFailureKind kind;

    public AiProviderException(AiFailureKind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public AiProviderException(AiFailureKind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public AiFailureKind kind() {
        return kind;
    }
}
