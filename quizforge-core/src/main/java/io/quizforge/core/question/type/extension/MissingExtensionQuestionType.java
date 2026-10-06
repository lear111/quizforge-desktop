package io.quizforge.core.question.type.extension;

import io.quizforge.core.question.codec.QuestionDataCodec;
import io.quizforge.core.question.model.extension.ExtensionAnswerSpec;
import io.quizforge.core.question.model.extension.ExtensionPayload;
import io.quizforge.core.question.model.*;
import io.quizforge.core.question.source.SourceRef;
import io.quizforge.core.question.type.*;
import java.util.List;
import java.util.function.Function;

/** Opaque storage compatibility when the required package is absent; offers no execution. */
public final class MissingExtensionQuestionType implements QuestionTypeDefinition {
    private final String id;
    private final Class<? extends QuestionPayload> payloadClass;
    private final Class<? extends QuestionAnswerSpec> answerClass;
    public MissingExtensionQuestionType(String id) { this.id = id; this.payloadClass=ExtensionPayload.class; this.answerClass=ExtensionAnswerSpec.class; }
    public MissingExtensionQuestionType(Question question) {
        this.id=question.type(); this.payloadClass=question.payload().getClass(); this.answerClass=question.answerSpec().getClass();
    }
    public String id() { return id; }
    public String label() { return id + "（需要题型扩展）"; }
    public Family family() { return Family.OBJECTIVE; }
    public String payloadKind() { return kind(); }
    public Class<? extends QuestionPayload> payloadClass() { return payloadClass; }
    public String answerKind() { return kind(); }
    public Class<? extends QuestionAnswerSpec> answerClass() { return answerClass; }
    public boolean supportsPractice(Question question) { return false; }
    public void validate(Question question, QuestionValidationContext context) {
        if (id == null || !id.matches("[A-Za-z][A-Za-z0-9_.-]{0,127}") || !id.equals(question.type())
                || !payloadClass.isInstance(question.payload()) || !answerClass.isInstance(question.answerSpec()))
            QuestionValidationContext.reject("Missing extension data is invalid");
        var data = QuestionDataCodec.encodePersisted(question);
        if (!ExternalQuestionTypeDefinition.object(data.get("payload")).get("kind").equals(ExternalQuestionTypeDefinition.object(data.get("answerSpec")).get("kind")))
            QuestionValidationContext.reject("Stored payload and answer kinds differ");
        inspect(data.get("payload"),context);
        inspect(data.get("answerSpec"),context);
    }
    public Question createDraft(Function<String, String> ids, List<SourceRef> sources) { throw unavailable(); }
    public Question duplicate(Question question, Function<String, String> ids) { throw unavailable(); }
    private String kind() {
        return payloadClass == ExtensionPayload.class ? "EXTENSION" : switch(id) {
            case "SINGLE_CHOICE", "MULTIPLE_CHOICE" -> "CHOICE";
            default -> id;
        };
    }
    private static void inspect(Object value,QuestionValidationContext context) {
        if (value instanceof java.util.Map<?,?> map) {
            if (map.get("kind") instanceof String kind && java.util.Set.of("TEXT","RICH","DOCUMENT").contains(kind)) {
                context.content().accept(io.quizforge.core.question.content.QuestionContentData.decode(map),false);
                return;
            }
            if (map.containsKey("content") && map.get("id") instanceof String optionId && !context.optionIds().add(optionId))
                QuestionValidationContext.reject("Duplicate option ID in stored question");
            map.values().forEach(item -> inspect(item,context));
        } else if(value instanceof java.util.List<?> list) list.forEach(item -> inspect(item,context));
    }
    private IllegalStateException unavailable() { return new IllegalStateException("缺少对应题型扩展：" + id + "，请安装后再使用。"); }
}
