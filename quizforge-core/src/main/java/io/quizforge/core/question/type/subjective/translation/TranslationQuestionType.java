package io.quizforge.core.question.type.subjective.translation;

import io.quizforge.core.question.content.QuestionContentData;
import io.quizforge.core.question.content.TextContent;
import io.quizforge.core.question.model.Question;
import io.quizforge.core.question.model.ScoreSpec;
import io.quizforge.core.question.source.SourceRef;
import io.quizforge.core.question.type.QuestionTypeDefinition;
import io.quizforge.core.question.type.QuestionValidationContext;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import static io.quizforge.core.question.type.QuestionValidationContext.reject;

/** Sequential {{sentence}} markers describe the independently answered translation items. */
public final class TranslationQuestionType implements QuestionTypeDefinition {
    public String id() { return "TRANSLATION"; }
    public Family family() { return Family.SUBJECTIVE; }
    public String payloadKind() { return "TRANSLATION"; }
    public Class<TranslationPayload> payloadClass() { return TranslationPayload.class; }
    public String answerKind() { return "TRANSLATION"; }
    public Class<TranslationAnswerSpec> answerClass() { return TranslationAnswerSpec.class; }

    /** An escaped opening marker, \\{{literal}}, is ordinary passage text. */
    public static List<String> sentences(String text) {
        Objects.requireNonNull(text, "Passage text");
        var result = new ArrayList<String>();
        for (int position = 0; position < text.length();) {
            if (text.startsWith("\\{{", position)) {
                int end = text.indexOf("}}", position + 3);
                if (end < 0) throw new IllegalArgumentException("转义标记缺少结束符 }}");
                position = end + 2;
            } else if (text.startsWith("{{", position)) {
                int end = text.indexOf("}}", position + 2);
                if (end < 0) throw new IllegalArgumentException("翻译标记缺少结束符 }}");
                String sentence = text.substring(position + 2, end);
                if (sentence.contains("{{")) throw new IllegalArgumentException("翻译标记不能嵌套");
                if (sentence.isBlank()) throw new IllegalArgumentException("翻译标记内需要填写句子");
                result.add(sentence);
                position = end + 2;
            } else if (text.startsWith("}}", position)) {
                throw new IllegalArgumentException("翻译标记缺少开始符 {{");
            } else position++;
        }
        return List.copyOf(result);
    }

    public Question createDraft(Function<String, String> ids, List<SourceRef> sources) {
        String text = "请将下列画线句子译成中文。\n\n{{Reading opens a window to the world.}}\n"
                + "{{Ideas connect people across time and space.}}\n"
                + "{{Learning requires patience and curiosity.}}\n"
                + "{{Every small step can make a difference.}}\n"
                + "{{Understanding others helps us understand ourselves.}}";
        var items = new ArrayList<TranslationItem>();
        for (var sentence : sentences(text)) items.add(new TranslationItem(ids.apply("item_"), items.size() + 1, sentence));
        var answers = items.stream().map(item -> new TranslationAnswerSpec.Answer(item.id(), null)).toList();
        return new Question(ids.apply("q_"), id(), List.of(), new TextContent(text), new TranslationPayload(items),
                new TranslationAnswerSpec(answers), new ScoreSpec(new BigDecimal("2")), null, null, sources);
    }

    public Question duplicate(Question q, Function<String, String> ids) {
        var references = ((TranslationAnswerSpec) q.answerSpec()).referenceAnswers();
        var items = new ArrayList<TranslationItem>();
        var answers = new ArrayList<TranslationAnswerSpec.Answer>();
        for (var item : ((TranslationPayload) q.payload()).items()) {
            String itemId = ids.apply("item_");
            items.add(new TranslationItem(itemId, item.number(), item.text()));
            answers.add(new TranslationAnswerSpec.Answer(itemId, references.get(item.id())));
        }
        return new Question(ids.apply("q_"), id(), q.stimulusRefs(), q.prompt(), new TranslationPayload(items),
                new TranslationAnswerSpec(answers), q.scoreSpec(), q.evaluationSpec(), q.analysis(), q.sourceRefs());
    }

    public void validate(Question q, QuestionValidationContext context) {
        if (!(q.payload() instanceof TranslationPayload) || !(q.answerSpec() instanceof TranslationAnswerSpec))
            reject("TRANSLATION requires translation payload and reference answers");
        var payload = (TranslationPayload) q.payload();
        var answers = (TranslationAnswerSpec) q.answerSpec();
        List<String> sentences;
        try { sentences = sentences(QuestionContentData.plainText(q.prompt())); }
        catch (IllegalArgumentException invalid) { reject(invalid.getMessage()); return; }
        if (sentences.isEmpty()) reject("翻译题至少需要一个 {{句子}} 标记");
        if (sentences.size() != payload.items().size()) reject("翻译句子与题干标记数量不一致");
        var itemIds = new HashSet<String>();
        for (int index = 0; index < payload.items().size(); index++) {
            var item = payload.items().get(index);
            if (item.id() == null || !item.id().matches("item_[A-Za-z0-9_-]+") || !itemIds.add(item.id()))
                reject("Invalid translation item ID");
            if (item.number() != index + 1) reject("翻译句子编号应从 1 连续递增");
            if (!Objects.equals(item.text(), sentences.get(index))) reject("翻译句子与题干标记内容不一致");
        }
        var answerIds = new HashSet<String>();
        for (var answer : answers.answers()) {
            if (!itemIds.contains(answer.itemId()) || !answerIds.add(answer.itemId())) reject("Invalid translation reference answer");
            if (answer.referenceAnswer() != null) context.content().accept(answer.referenceAnswer(), false);
        }
        if (!answerIds.equals(itemIds)) reject("每个翻译句子需要对应的参考答案记录");
    }
}
