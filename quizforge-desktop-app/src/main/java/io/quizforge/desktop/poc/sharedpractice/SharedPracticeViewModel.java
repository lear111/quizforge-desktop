package io.quizforge.desktop.poc.sharedpractice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quizforge.core.practice.ActivePracticeSnapshot;
import io.quizforge.core.practice.PracticePayload;
import io.quizforge.core.practice.PracticeSession;
import io.quizforge.core.practice.PracticeSessionQuestion;
import io.quizforge.core.question.type.QuestionTypes;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Explicit platform-neutral v1 contract, deliberately separate from Core persistence records. */
public record SharedPracticeViewModel(String schemaVersion, Session session, Question question) {
    private static final ObjectMapper JSON = new ObjectMapper();

    public record Session(String sessionId, String bankAssetId, String bankContentId) { }
    public record Text(String kind, String text) {
        static Text of(String text) { return new Text("TEXT", text); }
    }
    public enum Feedback { NONE, CORRECT, INCORRECT }
    public enum State { UNANSWERED, DRAFT, SUBMITTED, RETRYING }
    public record Option(String id, Text content, Feedback feedback) { }
    public record Result(String status, double score, double maxScore, String attemptId,
            int attemptNo, String attemptMode, List<String> correctOptionIds, Text analysis) {
        public Result { correctOptionIds = List.copyOf(correctOptionIds); }
    }
    public record Question(String sessionQuestionId, String questionId, String type,
            int index, int total, Text prompt, List<Option> options, List<String> selectedOptionIds,
            State state, double maxScore, Result result) {
        public Question {
            options = List.copyOf(options);
            selectedOptionIds = List.copyOf(selectedOptionIds);
        }
    }

    public String toJson() {
        try { return JSON.writeValueAsString(this); }
        catch (JsonProcessingException error) { throw new IllegalStateException("Could not serialize shared practice state", error); }
    }

    /** Read the current persisted question; do not calculate grading or transition practice state. */
    public static SharedPracticeViewModel from(ActivePracticeSnapshot snapshot) {
        Objects.requireNonNull(snapshot);
        if (snapshot.session().currentView() != PracticeSession.View.QUESTION)
            throw new IllegalArgumentException("Shared Practice v1 requires the question view");
        var entry = snapshot.questions().stream()
                .filter(row -> row.sessionQuestion().questionId().equals(snapshot.session().currentQuestionId()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Current question is missing"));
        var row = entry.sessionQuestion();
        var content = row.snapshot();
        if (!QuestionTypes.isSingleChoice(content.questionType()))
            throw new IllegalArgumentException("Shared Practice v1 supports SINGLE_CHOICE only");
        var state = State.valueOf(row.practiceState().name());
        var metadata = fields(content.correctAnswer());
        double maximum = number(metadata.get("maxScore"));
        boolean submitted = row.practiceState() == PracticeSessionQuestion.State.SUBMITTED;
        var latest = submitted ? entry.attempts().getLast() : null;
        List<String> selected = ids(submitted ? latest.answer() : row.draftAnswer());
        // Correct IDs and analysis cross the bridge only for a successfully submitted result.
        List<String> correct = submitted ? stringList(metadata.get("correctOptionIds")) : List.of();
        Result result = submitted ? new Result(latest.result().name(), number(latest.score()),
                number(latest.maxScore()), latest.id(), latest.attemptNo(), latest.attemptMode().name(),
                correct, Text.of(content.analysis())) : null;
        List<Option> options = ((List<?>) content.options().value()).stream().map(value -> {
            var option = (Map<?, ?>) value;
            String id = (String) option.get("id");
            Feedback feedback = !submitted ? Feedback.NONE : correct.contains(id) ? Feedback.CORRECT
                    : selected.contains(id) ? Feedback.INCORRECT : Feedback.NONE;
            return new Option(id, Text.of((String) option.get("content")), feedback);
        }).toList();
        var session = snapshot.session();
        return new SharedPracticeViewModel("1.0", new Session(session.id(), session.questionBankAssetId(),
                session.questionBankContentId()), new Question(row.id(), row.questionId(), "SINGLE_CHOICE",
                row.questionOrder(), snapshot.questions().size(), Text.of(content.stem()), options,
                selected, state, maximum, result));
    }

    private static Map<?, ?> fields(PracticePayload payload) { return (Map<?, ?>) payload.value(); }
    private static List<String> ids(PracticePayload payload) {
        return payload == null ? List.of() : stringList(payload.value());
    }
    private static List<String> stringList(Object value) {
        return ((List<?>) value).stream().map(item -> (String) item).toList();
    }
    private static double number(Object value) {
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue()))
            throw new IllegalStateException("Choice score metadata is missing or invalid");
        return number.doubleValue();
    }
}
