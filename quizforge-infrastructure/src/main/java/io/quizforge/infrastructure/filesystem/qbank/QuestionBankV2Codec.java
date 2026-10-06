package io.quizforge.infrastructure.filesystem.qbank;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.node.*;
import com.fasterxml.jackson.databind.type.LogicalType;
import io.quizforge.core.ErrorCode;
import io.quizforge.core.QuizForgeException;
import io.quizforge.core.port.QuestionBankFileCodec;
import io.quizforge.core.question.content.BlockImageNode;
import io.quizforge.core.question.content.BlockMathNode;
import io.quizforge.core.question.content.BlockNode;
import io.quizforge.core.question.content.BlockQuoteNode;
import io.quizforge.core.question.content.BulletListNode;
import io.quizforge.core.question.content.DocumentContent;
import io.quizforge.core.question.content.HeadingNode;
import io.quizforge.core.question.content.InlineImageNode;
import io.quizforge.core.question.content.InlineMathNode;
import io.quizforge.core.question.content.InlineNode;
import io.quizforge.core.question.content.InlineTextNode;
import io.quizforge.core.question.content.LineBreakNode;
import io.quizforge.core.question.content.LinkNode;
import io.quizforge.core.question.content.OrderedListNode;
import io.quizforge.core.question.content.ParagraphNode;
import io.quizforge.core.question.content.QuestionContent;
import io.quizforge.core.question.content.RichContent;
import io.quizforge.core.question.content.TextContent;
import io.quizforge.core.question.content.TextMark;
import io.quizforge.core.question.model.QuestionAnswerSpec;
import io.quizforge.core.question.model.QuestionBank;
import io.quizforge.core.question.model.QuestionPayload;
import io.quizforge.core.question.service.QuestionBankValidator;
import io.quizforge.core.question.type.QuestionTypes;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.TreeMap;

/**
 * Internal logical QBank v2 JSON serialization, used to assemble bank.json and manifest.json.
 * Physical .qbank files are read exclusively by QBankPackageReader.
 * Discriminators never depend on Java class names.
 * Missing and explicit-null optional properties both become null in the logical model.
 * NON_NULL omits them from both writer output and the logical tree used by contentId.
 * Required fields retain their normal validation; null never replaces a required value.
 */
public final class QuestionBankV2Codec implements QuestionBankFileCodec {
    private final ObjectMapper json = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .configure(com.fasterxml.jackson.databind.cfg.JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES, false)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .addMixIn(QuestionContent.class,ContentTypes.class)
            .addMixIn(BlockNode.class,BlockTypes.class).addMixIn(InlineNode.class,InlineTypes.class)
            .addMixIn(InlineTextNode.class,TextNodeProperties.class)
            .addMixIn(QuestionPayload.class,PayloadTypes.class).addMixIn(QuestionAnswerSpec.class,AnswerTypes.class);
    private final QuestionBankValidator validator = new QuestionBankValidator();

    public QuestionBankV2Codec() {
        // Generic data remains readable even when its owning extension is not installed.
        json.registerSubtypes(new com.fasterxml.jackson.databind.jsontype.NamedType(io.quizforge.core.question.model.extension.ExtensionPayload.class,"EXTENSION"),
                new com.fasterxml.jackson.databind.jsontype.NamedType(io.quizforge.core.question.model.extension.ExtensionAnswerSpec.class,"EXTENSION"));
        // Stored data codecs are independent from executable extensions. Missing types stay readable.
        storedType("CHOICE",io.quizforge.core.question.model.choice.ChoicePayload.class,io.quizforge.core.question.model.choice.ChoiceAnswerSpec.class);
        storedType("CLOZE",io.quizforge.core.question.compat.cloze.ClozePayload.class,io.quizforge.core.question.compat.cloze.ClozeAnswerSpec.class);
        storedType("READING",io.quizforge.core.question.compat.reading.ReadingPayload.class,io.quizforge.core.question.compat.reading.ReadingAnswerSpec.class);
        storedType("MATCHING",io.quizforge.core.question.compat.matching.MatchingPayload.class,io.quizforge.core.question.compat.matching.MatchingAnswerSpec.class);
        storedType("TRANSLATION",io.quizforge.core.question.compat.translation.TranslationPayload.class,io.quizforge.core.question.compat.translation.TranslationAnswerSpec.class);
        storedType("ESSAY",io.quizforge.core.question.compat.essay.EssayPayload.class,io.quizforge.core.question.compat.essay.EssayAnswerSpec.class);
        json.coercionConfigFor(LogicalType.Textual)
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
    }
    private void storedType(String kind,Class<?> payload,Class<?> answer) {
        json.registerSubtypes(new com.fasterxml.jackson.databind.jsontype.NamedType(payload,kind),
                new com.fasterxml.jackson.databind.jsontype.NamedType(answer,kind));
    }

    @JsonTypeInfo(use=JsonTypeInfo.Id.NAME,property="kind")
    @JsonSubTypes({@JsonSubTypes.Type(value=TextContent.class,name="TEXT"),
            @JsonSubTypes.Type(value=RichContent.class,name="RICH"),
            @JsonSubTypes.Type(value=DocumentContent.class,name="DOCUMENT")})
    private interface ContentTypes { }
    @JsonTypeInfo(use=JsonTypeInfo.Id.NAME,property="type")
    @JsonSubTypes({@JsonSubTypes.Type(value=ParagraphNode.class,name="PARAGRAPH"),
            @JsonSubTypes.Type(value=HeadingNode.class,name="HEADING"),
            @JsonSubTypes.Type(value=BulletListNode.class,name="BULLET_LIST"),
            @JsonSubTypes.Type(value=OrderedListNode.class,name="ORDERED_LIST"),
            @JsonSubTypes.Type(value=BlockQuoteNode.class,name="BLOCK_QUOTE"),
            @JsonSubTypes.Type(value=BlockImageNode.class,name="IMAGE"),
            @JsonSubTypes.Type(value=BlockMathNode.class,name="MATH")})
    private interface BlockTypes { }
    @JsonTypeInfo(use=JsonTypeInfo.Id.NAME,property="type")
    @JsonSubTypes({@JsonSubTypes.Type(value=InlineTextNode.class,name="TEXT"),
            @JsonSubTypes.Type(value=InlineImageNode.class,name="IMAGE"),
            @JsonSubTypes.Type(value=InlineMathNode.class,name="MATH"),
            @JsonSubTypes.Type(value=LineBreakNode.class,name="LINE_BREAK"),
            @JsonSubTypes.Type(value=LinkNode.class,name="LINK")})
    private interface InlineTypes { }
    private interface TextNodeProperties {
        @JsonInclude(JsonInclude.Include.NON_EMPTY) java.util.List<TextMark> marks();
    }
    @JsonTypeInfo(use=JsonTypeInfo.Id.NAME,property="kind")
    private interface PayloadTypes { }
    @JsonTypeInfo(use=JsonTypeInfo.Id.NAME,property="kind")
    private interface AnswerTypes { }

    @Override public String write(QuestionBank bank) {
        if (bank.questions().isEmpty()) validator.validateEmptyDraft(bank); else validate(bank);
        try { return json.writerWithDefaultPrettyPrinter().writeValueAsString(tree(bank)) + "\n"; }
        catch (Exception e) { throw invalid("Could not serialize QBank v2",e); }
    }
    @Override public QuestionBank parse(String source) { return parse(source,false); }
    @Override public QuestionBank parseEmptyDraft(String source) { return parse(source,true); }
    @Override public void validate(QuestionBank bank) { validator.validate(bank); }
    private QuestionBank parse(String source,boolean draft) {
        try {
            JsonNode root = json.readTree(source);
            if (!(root instanceof ObjectNode) || !"2.0".equals(root.path("schemaVersion").asText()))
                throw invalid("Only QBank schemaVersion 2.0 is supported",null);
            // Removed ESSAY settings are inert input metadata, never part of the canonical model.
            // Existing v2 packages remain readable; their next save drops these properties.
            for (JsonNode q : root.path("questions")) {
                if (QuestionTypes.isEssay(q.path("type").asText()) && q.path("payload") instanceof ObjectNode payload
                        && "ESSAY".equals(payload.path("kind").asText()))
                    payload.remove(java.util.List.of("minWords", "maxWords"));
            }
            for (JsonNode q : root.path("questions")) for (JsonNode ref : q.path("sourceRefs")) {
                if (!(ref instanceof ObjectNode object) || object.has("address") || object.has("sectionId")
                        || object.has("nodeId") || !object.path("anchorName").isTextual()
                        || !object.path("occurrence").isIntegralNumber() || !object.path("occurrence").canConvertToInt())
                    throw invalid("QBank v2 sourceRefs require named anchors",null);
                ObjectNode address=json.createObjectNode();
                address.put("kind","ANCHOR");
                address.set("value",object.remove("anchorName"));
                address.set("occurrence",object.remove("occurrence"));
                object.set("address",address);
            }
            // Check raw extension data before deserialization can drop unknown fields or coerce values.
            for (JsonNode q : root.path("questions")) {
                var definition = QuestionTypes.find(q.path("type").asText()).orElse(null);
                if (definition instanceof io.quizforge.core.question.type.extension.ExternalQuestionTypeDefinition extension)
                    extension.validateQuestionData(json.convertValue(q,new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String,Object>>() { }));
            }
            QuestionBank bank=json.treeToValue(root,QuestionBank.class);
            if (draft) validator.validateEmptyDraft(bank); else validate(bank);
            return bank;
        } catch (QuizForgeException e) { throw e; }
        catch (Exception e) { throw invalid("Invalid QBank v2 JSON",e); }
    }
    private ObjectNode tree(QuestionBank bank) {
        ObjectNode root=json.valueToTree(bank);
        for (JsonNode q : root.path("questions")) for (JsonNode ref : q.path("sourceRefs")) {
            ObjectNode object=(ObjectNode)ref;
            JsonNode address=object.remove("address");
            object.set("anchorName",address.path("value"));
            object.set("occurrence",address.path("occurrence"));
        }
        return root;
    }
    @Override public String contentId(QuestionBank bank) {
        if (bank.questions().isEmpty()) validator.validateEmptyDraft(bank); else validate(bank);
        try {
            ObjectNode root=tree(bank); root.remove("assetId");
            byte[] bytes=json.writeValueAsString(canonical(root)).getBytes(StandardCharsets.UTF_8);
            return "qfb:v2:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) { throw invalid("Could not hash QBank v2 logical content",e); }
    }
    private JsonNode canonical(JsonNode node) {
        if (node.isObject()) {
            ObjectNode result=json.createObjectNode();
            TreeMap<String,JsonNode> sorted=new TreeMap<>();
            node.fields().forEachRemaining(field -> sorted.put(field.getKey(),field.getValue()));
            sorted.forEach((key,value) -> result.set(key,canonical(value)));
            return result;
        }
        if (node.isArray()) {
            var result=json.createArrayNode(); node.forEach(value -> result.add(canonical(value))); return result;
        }
        if (node.isNumber()) return DecimalNode.valueOf(node.decimalValue().stripTrailingZeros());
        return node;
    }
    private QuizForgeException invalid(String message,Throwable error) {
        return new QuizForgeException(ErrorCode.QUESTION_BANK_FILE_INVALID,message,error);
    }
}
