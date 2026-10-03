package io.quizforge.desktop.poc.sharedpractice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quizforge.core.practice.ActivePracticeSnapshot;
import io.quizforge.core.practice.PracticePayload;
import io.quizforge.core.practice.PracticeSession;
import io.quizforge.core.practice.PracticeSessionQuestion;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Explicit platform-neutral v1 contract, deliberately separate from Core persistence records. */
public record SharedPracticeViewModel(String schemaVersion, Session session, Question question) {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Map<String, String> SELECTION_MODES = Map.of(
            "SINGLE_CHOICE", "SINGLE", "MULTIPLE_CHOICE", "MULTIPLE");
    public static boolean supportsType(String type) { return SELECTION_MODES.containsKey(type) || "READING".equals(type) || "CLOZE".equals(type) || "MATCHING".equals(type) || "TRANSLATION".equals(type) || "ESSAY".equals(type); }
    public static String selectionModeFor(String type) {
        if ("ESSAY".equals(type)) return "LONG_TEXT";
        if ("TRANSLATION".equals(type)) return "TEXT_FIELDS";
        if ("MATCHING".equals(type)) return "ASSIGNMENT";
        var mode = SELECTION_MODES.get(type);
        if (mode == null && ("READING".equals(type) || "CLOZE".equals(type))) return "COMPOSITE_SINGLE";
        if (mode == null) throw new IllegalArgumentException("Unsupported shared question type: " + type);
        return mode;
    }

    public record Session(String sessionId, String bankAssetId, String bankContentId) { }
    public record Text(String kind, String text) {
        static Text of(String text) { return new Text("TEXT", text); }
    }
    public enum Feedback { NONE, CORRECT, INCORRECT }
    public enum State { UNANSWERED, DRAFT, SUBMITTED, RETRYING, REVISING }
    public record Option(String id, Text content, Feedback feedback) { }
    public record Result(String status, Double score, Double maxScore, String attemptId,
            int attemptNo, String attemptMode, List<String> correctOptionIds, Text analysis) {
        public Result { correctOptionIds = List.copyOf(correctOptionIds); }
    }
    public sealed interface Presentation permits ReadingPresentation, ClozePresentation, MatchingPresentation, TranslationPresentation, EssayPresentation { }
    public record ReadingItem(String id, int number, Text prompt, List<Option> options) {
        public ReadingItem { options = List.copyOf(options); }
    }
    public record ReadingPresentation(List<ReadingItem> items) implements Presentation {
        public ReadingPresentation { items = List.copyOf(items); }
    }
    public record ClozePresentation(List<ReadingItem> items) implements Presentation {
        public ClozePresentation { items = List.copyOf(items); }
    }
    public record MatchingSlot(String id, int number, boolean locked, String givenOptionId, String selectedOptionId, Feedback feedback, String correctOptionId) { }
    public record MatchingPresentation(List<MatchingSlot> slots, Map<String,String> assignments) implements Presentation {
        public MatchingPresentation { slots=List.copyOf(slots);assignments=Map.copyOf(assignments); }
    }
    public record TranslationItem(String id,int number,String text,Text answer,Text reference) { }
    public record TranslationPresentation(List<TranslationItem> items) implements Presentation {
        public TranslationPresentation { items=List.copyOf(items); }
    }
    public record EssayPresentation(Text answer, Text reference) implements Presentation { }
    public record Question(String sessionQuestionId, String questionId, String type,
            int index, int total, Text prompt, List<Option> options, List<String> selectedOptionIds,
            State state, Double maxScore, Result result, String selectionMode,
            @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) Presentation presentation) {
        public Question(String sessionQuestionId, String questionId, String type, int index, int total, Text prompt,
                List<Option> options, List<String> selectedOptionIds, State state, double maxScore, Result result, String selectionMode) {
            this(sessionQuestionId, questionId, type, index, total, prompt, options, selectedOptionIds, state, maxScore, result, selectionMode, null);
        }
        public Question(String sessionQuestionId, String questionId, String type, int index, int total, Text prompt,
                List<Option> options, List<String> selectedOptionIds, State state, double maxScore, Result result) {
            this(sessionQuestionId, questionId, type, index, total, prompt, options, selectedOptionIds, state, maxScore, result,
                    selectionModeFor(type), null);
        }
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
        if (!supportsType(content.questionType()))
            throw new IllegalArgumentException("Unsupported Shared Practice type");
        var state = State.valueOf(row.practiceState().name());
        var metadata = fields(content.correctAnswer());
        Double maximum = nullableNumber(metadata.get("maxScore"));
        boolean submitted = row.practiceState() == PracticeSessionQuestion.State.SUBMITTED;
        var latest = submitted ? entry.attempts().getLast() : null;
        var answer = submitted ? latest.answer() : row.draftAnswer();
        List<String> selected = ("MATCHING".equals(content.questionType()) || "TRANSLATION".equals(content.questionType()) || "ESSAY".equals(content.questionType())) ? List.of() : ids(answer);
        // Correct IDs and analysis cross the bridge only for a successfully submitted result.
        List<String> correct = submitted ? stringList(metadata.get("correctOptionIds")) : List.of();
        Result result = submitted ? new Result(latest.result().name(), nullableNumber(latest.score()),
                nullableNumber(latest.maxScore()), latest.id(), latest.attemptNo(), latest.attemptMode().name(),
                correct, Text.of(content.analysis())) : null;
        var projected = project(content.questionType(), Text.of(content.stem()), content.options(), metadata, selected, correct, submitted, answer);
        List<Option> options = projected.options();
        var session = snapshot.session();
        return new SharedPracticeViewModel("1.0", new Session(session.id(), session.questionBankAssetId(),
                session.questionBankContentId()), new Question(row.id(), row.questionId(), content.questionType(),
                row.questionOrder(), snapshot.questions().size(), projected.prompt(), options,
                selected, state, maximum, result, selectionModeFor(content.questionType()), projected.presentation()));
    }

    /** Display number resolves to a stable child target; never becomes a Draft/business key. */
    public static String targetId(String type,PracticePayload metadata,int number) {
        String family=switch(type){case "READING"->"reading";case "CLOZE"->"cloze";case "MATCHING"->"matching";case "TRANSLATION"->"translation";default->null;};
        if(family==null || metadata==null)return null;
        var data=map(map(metadata.value()).get(family));
        String collection=type.equals("READING") || type.equals("TRANSLATION")?"items":"blanks";
        return ((List<?>)data.get(collection)).stream().map(SharedPracticeViewModel::map)
                .filter(item->((Number)item.get("number")).intValue()==number).map(item->(String)item.get("id")).findFirst().orElse(null);
    }
    public record Projection(Text prompt, List<Option> options, Presentation presentation) { }
    /** The same frozen-content projection is used by Active and History; no business transitions. */
    public static Projection project(String type, Text fallbackPrompt, PracticePayload flatOptions, Map<?, ?> metadata,
            List<String> selected, List<String> correct, boolean submitted, PracticePayload answer) {
        if ("ESSAY".equals(type)) {
            var supplied=io.quizforge.core.practice.EssayPracticeAnswer.from(answer);
            if(supplied.document()!=null)throw new IllegalArgumentException("Unsupported content: TEXT answer required");
            var data=metadata.get("essayPresentation") instanceof Map<?,?> value?value:metadata;
            var prompt=text(data.get("prompt"));if(data.containsKey("analysis"))text(data.get("analysis"));
            Object referenceValue=data.containsKey("reference")?data.get("reference"):data.get("referenceAnswer");
            Text reference=null;if(referenceValue instanceof Map<?,?> value && !value.isEmpty())reference=text(value);
            return new Projection(prompt,List.of(),new EssayPresentation(Text.of(supplied.text()),submitted?reference:null));
        }
        if ("TRANSLATION".equals(type)) {
            var data=map(metadata.get("translation"));var prompt=text(data.get("prompt"));
            if(data.containsKey("analysis"))text(data.get("analysis"));
            var references=new java.util.LinkedHashMap<String,Text>();
            ((List<?>)data.get("answers")).forEach(value->{var a=map(value);if(a.containsKey("referenceAnswer"))references.put((String)a.get("itemId"),text(a.get("referenceAnswer")));});
            var answers=io.quizforge.core.practice.TranslationPracticeAnswer.from(answer).answers();
            var items=((List<?>)data.get("items")).stream().map(value->{var item=map(value);String id=(String)item.get("id");
                var supplied=answers.get(id);
                if(supplied!=null && supplied.document()!=null)throw new IllegalArgumentException("Unsupported content: TEXT answer required");
                return new TranslationItem(id,((Number)item.get("number")).intValue(),(String)item.get("text"),Text.of(supplied==null?"":supplied.text()),submitted?references.get(id):null);
            }).toList();
            return new Projection(prompt,List.of(),new TranslationPresentation(items));
        }
        if ("MATCHING".equals(type)) {
            var data=map(metadata.get("matching"));var prompt=text(data.get("prompt"));
            if(data.containsKey("analysis"))text(data.get("analysis"));
            var assignments=io.quizforge.core.practice.MatchingPracticeAnswer.from(answer).assignments();
            var answers=new java.util.LinkedHashMap<String,String>();
            ((List<?>)data.get("answers")).forEach(value->{var a=map(value);answers.put((String)a.get("blankId"),(String)a.get("correctOptionId"));});
            var slots=((List<?>)data.get("blanks")).stream().map(value->{
                var slot=map(value);String id=(String)slot.get("id");boolean locked=Boolean.TRUE.equals(slot.get("locked"));String chosen=assignments.get(id);
                var feedback=!submitted || locked || chosen==null ? Feedback.NONE : Objects.equals(chosen,answers.get(id)) ? Feedback.CORRECT : Feedback.INCORRECT;
                return new MatchingSlot(id,((Number)slot.get("number")).intValue(),locked,locked?answers.get(id):null,chosen,feedback,submitted?answers.get(id):null);
            }).toList();
            var options=((List<?>)data.get("options")).stream().map(value->{var o=map(value);return new Option((String)o.get("id"),Text.of((String)o.get("label")),Feedback.NONE);}).toList();
            return new Projection(prompt,options,new MatchingPresentation(slots,assignments));
        }
        if ("READING".equals(type) || "CLOZE".equals(type)) {
            boolean cloze="CLOZE".equals(type);
            var data = map(metadata.get(cloze ? "cloze" : "reading"));
            Text prompt = text(data.get("prompt"));
            if (data.containsKey("analysis")) text(data.get("analysis"));
            var items = ((List<?>) data.get(cloze ? "blanks" : "items")).stream().map(value -> {
                var item = map(value);
                var options = ((List<?>) item.get("options")).stream().map(optionValue -> {
                    var o = map(optionValue); String id = (String) o.get("id");
                    return new Option(id, text(o.get("content")), feedback(id, selected, correct, submitted));
                }).toList();
                return new ReadingItem((String) item.get("id"), ((Number) item.get("number")).intValue(), cloze ? Text.of("") : text(item.get("prompt")), options);
            }).toList();
            return new Projection(prompt, items.stream().flatMap(i -> i.options().stream()).toList(), cloze ? new ClozePresentation(items) : new ReadingPresentation(items));
        }
        var options = ((List<?>) flatOptions.value()).stream().map(value -> {
            var o = map(value); String id = (String) o.get("id");
            return new Option(id, Text.of((String) o.get("content")), feedback(id, selected, correct, submitted));
        }).toList();
        return new Projection(fallbackPrompt, options, null);
    }
    private static Feedback feedback(String id, List<String> selected, List<String> correct, boolean submitted) {
        return !submitted ? Feedback.NONE : correct.contains(id) ? Feedback.CORRECT : selected.contains(id) ? Feedback.INCORRECT : Feedback.NONE;
    }
    private static Map<?, ?> map(Object value) {
        if (!(value instanceof Map<?, ?> fields)) throw new IllegalArgumentException("Missing structured presentation");
        return fields;
    }
    private static Text text(Object value) {
        var data = map(value);
        if (!"TEXT".equals(data.get("kind")) || !(data.get("text") instanceof String text))
            throw new IllegalArgumentException("Unsupported content: Shared Renderer supports TEXT only");
        return Text.of(text);
    }
    private static Map<?, ?> fields(PracticePayload payload) { return (Map<?, ?>) payload.value(); }
    private static List<String> ids(PracticePayload payload) {
        return payload == null ? List.of() : stringList(payload.value());
    }
    private static List<String> stringList(Object value) {
        return ((List<?>) value).stream().map(item -> (String) item).toList();
    }
    private static Double nullableNumber(Object value) {return value==null?null:number(value);}
    private static double number(Object value) {
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue()))
            throw new IllegalStateException("Choice score metadata is missing or invalid");
        return number.doubleValue();
    }
}
