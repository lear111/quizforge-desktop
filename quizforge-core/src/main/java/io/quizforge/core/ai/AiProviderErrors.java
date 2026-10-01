package io.quizforge.core.ai;

import io.quizforge.core.ErrorCode;
import io.quizforge.core.QuizForgeException;

public final class AiProviderErrors {
    private AiProviderErrors() {
    }

    public static QuizForgeException map(AiProviderException error) {
        ErrorCode code = switch (error.kind()) {
            case AUTHENTICATION_FAILED -> ErrorCode.AI_PROVIDER_AUTHENTICATION_FAILED;
            case RATE_LIMITED -> ErrorCode.AI_PROVIDER_RATE_LIMITED;
            case TIMEOUT -> ErrorCode.AI_PROVIDER_TIMEOUT;
            case UNAVAILABLE -> ErrorCode.AI_PROVIDER_UNAVAILABLE;
            case INVALID_RESPONSE -> ErrorCode.AI_PROVIDER_INVALID_RESPONSE;
        };
        return new QuizForgeException(code, error.getMessage());
    }
}
