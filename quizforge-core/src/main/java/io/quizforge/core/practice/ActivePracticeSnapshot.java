package io.quizforge.core.practice;

import java.util.List;

/** Persisted active state, ordered by global question order; independent of the UI session. */
public record ActivePracticeSnapshot(PracticeSession session, List<Question> questions) {
    public ActivePracticeSnapshot { questions = List.copyOf(questions); }

    public record Question(PracticeSessionQuestion sessionQuestion, List<QuestionAttempt> attempts) {
        public Question { attempts = List.copyOf(attempts); }
    }
}
