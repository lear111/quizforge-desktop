package io.quizforge.core.port;

import java.util.function.Function;

/** Practice repositories scoped to one atomic operation. No JDBC types cross this boundary. */
public interface PracticeTransaction {
    record Repositories(PracticeSessionRepository sessions, PracticeSessionQuestionRepository questions,
            QuestionAttemptRepository attempts) { }

    /** Repositories must only be used inside the callback; failure rolls back all writes. */
    <T> T execute(Function<Repositories, T> operation);
}
