package io.quizforge.core.port;

import io.quizforge.core.practice.QuestionAttempt;
import java.util.List;
import java.util.Optional;

/** No update or individual delete operation for submitted facts. */
public interface QuestionAttemptRepository {
    void append(QuestionAttempt attempt);
    List<QuestionAttempt> listBySessionQuestion(String sessionQuestionId);
    Optional<QuestionAttempt> findLatest(String sessionQuestionId);
    long countBySessionQuestion(String sessionQuestionId);
    /** Advisory only: concurrent append still relies on the database unique constraint. */
    int nextAttemptNo(String sessionQuestionId);
}
