package io.quizforge.desktop.learning;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quizforge.core.practice.*;
import io.quizforge.core.question.type.QuestionTypes;
import java.util.*;

/** Host projection for installed HTML question packages; no embedded type renderers. */
public record SharedPracticeViewModel(String schemaVersion, Session session, Question question) {
    private static final ObjectMapper JSON = io.quizforge.infrastructure.json.DocumentJson.mapper();
    public static boolean supportsType(String type) { return QuestionTypes.isExtension(type); }
    public static String selectionModeFor(String type) {
        if (!supportsType(type)) throw new IllegalArgumentException("缺少对应题型扩展：" + type);
        return "EXTENSION";
    }
    public record Session(String sessionId, String bankAssetId, String bankContentId) { }
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public record Text(String kind, String text, Object document, Map<String,String> images) {
        public Text(String kind, String text) { this(kind, text, null, null); }
        static Text of(String text) { return new Text("TEXT", text); }
    }
    public enum Feedback { NONE, CORRECT, INCORRECT }
    public enum State { UNANSWERED, DRAFT, SUBMITTED, RETRYING, REVISING }
    public record Option(String id, Text content, Feedback feedback) { }
    public record Result(String status, Double score, Double maxScore, String attemptId,
            int attemptNo, String attemptMode, List<String> correctOptionIds, Text analysis) {
        public Result { correctOptionIds = List.copyOf(correctOptionIds); }
    }
    public sealed interface Presentation permits ExtensionPresentation { }
    public record ExtensionPresentation(String extensionId, String extensionVersion, int dataVersion,
            Map<String,Object> question, Object answer, Object reference, Object data) implements Presentation { }
    public record Question(String sessionQuestionId, String questionId, String type,
            int index, int total, Text prompt, List<Option> options, List<String> selectedOptionIds,
            State state, Double maxScore, Result result, String selectionMode,
            @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) Presentation presentation) {
        public Question(String sessionQuestionId, String questionId, String type, int index, int total, Text prompt,
                List<Option> options, List<String> selectedOptionIds, State state, double maxScore, Result result, String selectionMode) {
            this(sessionQuestionId,questionId,type,index,total,prompt,options,selectedOptionIds,state,maxScore,result,selectionMode,null);
        }
        public Question(String sessionQuestionId, String questionId, String type, int index, int total, Text prompt,
                List<Option> options, List<String> selectedOptionIds, State state, double maxScore, Result result) {
            this(sessionQuestionId,questionId,type,index,total,prompt,options,selectedOptionIds,state,maxScore,result,selectionModeFor(type),null);
        }
        public Question { options=List.copyOf(options); selectedOptionIds=List.copyOf(selectedOptionIds); }
    }
    public String toJson() {
        try { return JSON.writeValueAsString(this); }
        catch (JsonProcessingException error) { throw new IllegalStateException("Could not serialize shared practice state",error); }
    }
    public static SharedPracticeViewModel from(ActivePracticeSnapshot snapshot) {
        return from(snapshot, null);
    }
    /** Read-only projection of one saved attempt; never changes the active runtime. */
    public static SharedPracticeViewModel from(ActivePracticeSnapshot snapshot, String attemptId) {
        Objects.requireNonNull(snapshot);
        if (snapshot.session().currentView()!=PracticeSession.View.QUESTION) throw new IllegalArgumentException("Question view required");
        var entry=snapshot.questions().stream().filter(row->row.sessionQuestion().questionId().equals(snapshot.session().currentQuestionId())).findFirst().orElseThrow();
        var row=entry.sessionQuestion(); var content=row.snapshot();
        if (!supportsType(content.questionType())) throw new IllegalArgumentException("缺少对应题型扩展："+content.questionType());
        var metadata=map(content.correctAnswer().value());
        boolean submitted=attemptId!=null || row.practiceState()==PracticeSessionQuestion.State.SUBMITTED;
        var latest=attemptId!=null?entry.attempts().stream().filter(a->a.id().equals(attemptId)).findFirst()
                .orElseThrow(()->new IllegalArgumentException("Attempt does not belong to current question"))
                :submitted?entry.attempts().getLast():null;
        var answer=submitted?latest.answer():row.draftAnswer();
        var selected=answerIds(answer);
        var correct=submitted?stringList(metadata.get("correctOptionIds")):List.<String>of();
        var result=submitted?new Result(latest.result().name(),nullableNumber(latest.score()),nullableNumber(latest.maxScore()),
                latest.id(),latest.attemptNo(),latest.attemptMode().name(),correct,analysis(content.questionType(),metadata,content.analysis())):null;
        var projected=project(content.questionType(),Text.of(content.stem()),content.options(),metadata,selected,correct,submitted,answer);
        var session=snapshot.session();
        return new SharedPracticeViewModel("1.0",new Session(session.id(),session.questionBankAssetId(),session.questionBankContentId()),
                new Question(row.id(),row.questionId(),content.questionType(),row.questionOrder(),snapshot.questions().size(),projected.prompt(),
                    projected.options(),selected,submitted?State.SUBMITTED:State.valueOf(row.practiceState().name()),nullableNumber(metadata.get("maxScore")),result,
                    selectionModeFor(content.questionType()),projected.presentation()));
    }
    public static String targetId(String type,PracticePayload metadata,int number) {
        if (metadata==null || !(map(metadata.value()).get("extensionPresentation") instanceof Map<?,?> presentation)
                || !(presentation.get("targets") instanceof List<?> targets)) return null;
        return targets.stream().map(SharedPracticeViewModel::map).filter(target->target.get("number") instanceof Number n && n.intValue()==number)
                .map(target->(String)target.get("id")).findFirst().orElse(null);
    }
    public record Projection(Text prompt,List<Option> options,Presentation presentation) { }
    public static Projection project(String type,Text fallbackPrompt,PracticePayload flatOptions,Map<?,?> metadata,
            List<String> selected,List<String> correct,boolean submitted,PracticePayload answer) {
        if (!(metadata.get("extensionPresentation") instanceof Map<?,?> extension))
            throw new IllegalArgumentException("缺少新版题型快照或对应扩展："+type);
        var standard=map(metadata.get("extension"));
        var displayed=new LinkedHashMap<String,Object>();
        map(extension.get("question")).forEach((key,value)->displayed.put((String)key,value));
        displayed.remove("answerSpec"); displayed.remove("evaluationSpec"); displayed.remove("analysis");
        var frozen=map(standard.get("question"));
        if (submitted && frozen.get("analysis")!=null) displayed.put("analysis",frozen.get("analysis"));
        var scope=SharedContent.scope(type,metadata);
        Text prompt=SharedContent.read(displayed.get("prompt"),scope);
        displayed.replaceAll((key,value)->SharedContent.resolveNested(value,scope));
        var reference=new LinkedHashMap<String,Object>();
        if (submitted) {
            reference.put("answerSpec",SharedContent.resolveNested(frozen.containsKey("answerSpec")?frozen.get("answerSpec"):standard.get("answerSpec"),scope));
            if (frozen.get("analysis")!=null) reference.put("analysis",SharedContent.resolveNested(frozen.get("analysis"),scope));
        }
        return new Projection(prompt,List.of(),new ExtensionPresentation(
            extension.get("extensionId") instanceof String id?id:type,
            standard.get("version") instanceof String version?version:"2.0.0",
            extension.get("dataVersion") instanceof Number n?n.intValue():1,
            displayed,answer==null?Map.of():answer.value(),submitted?reference:null,SharedContent.resolveNested(extension.get("data"),scope)));
    }
    public static Text analysis(String type,Map<?,?> metadata,String fallback) {
        if (metadata.get("extension") instanceof Map<?,?> extension && extension.get("question") instanceof Map<?,?> question && question.get("analysis")!=null)
            return SharedContent.read(question.get("analysis"),SharedContent.scope(type,metadata));
        return Text.of(fallback==null?"":fallback);
    }
    public static List<String> answerIds(PracticePayload answer) {
        if (answer==null) return List.of();
        if (answer.value() instanceof Map<?,?> fields) return stringList(fields.get("selectedOptionIds"));
        return stringList(answer.value());
    }
    private static List<String> stringList(Object value) {
        return value instanceof List<?> list?list.stream().map(String.class::cast).toList():List.of();
    }
    private static Map<?,?> map(Object value) {
        if (!(value instanceof Map<?,?> fields)) throw new IllegalArgumentException("Missing extension snapshot");
        return fields;
    }
    private static Double nullableNumber(Object value) {
        if(value==null)return null;
        if(!(value instanceof Number number)||!Double.isFinite(number.doubleValue()))throw new IllegalArgumentException("Invalid score");
        return number.doubleValue();
    }
}
