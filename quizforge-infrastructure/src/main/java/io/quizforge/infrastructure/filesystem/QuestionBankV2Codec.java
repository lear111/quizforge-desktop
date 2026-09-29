package io.quizforge.infrastructure.filesystem;

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
import io.quizforge.core.question.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.TreeMap;

/**
 * Only QBank v2 JSON is accepted. Discriminators never depend on Java class names.
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
            .configure(com.fasterxml.jackson.databind.cfg.JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES, false)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .addMixIn(QuestionContent.class,ContentTypes.class)
            .addMixIn(BlockNode.class,BlockTypes.class).addMixIn(InlineNode.class,InlineTypes.class)
            .addMixIn(QuestionPayload.class,PayloadTypes.class).addMixIn(QuestionAnswerSpec.class,AnswerTypes.class);
    private final QuestionBankValidator validator = new QuestionBankValidator();

    public QuestionBankV2Codec() {
        json.coercionConfigFor(LogicalType.Textual)
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
    }

    @JsonTypeInfo(use=JsonTypeInfo.Id.NAME,property="kind")
    @JsonSubTypes({@JsonSubTypes.Type(value=TextContent.class,name="TEXT"),
            @JsonSubTypes.Type(value=RichContent.class,name="RICH")})
    private interface ContentTypes { }
    @JsonTypeInfo(use=JsonTypeInfo.Id.NAME,property="type")
    @JsonSubTypes({@JsonSubTypes.Type(value=ParagraphNode.class,name="PARAGRAPH"),
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
    @JsonTypeInfo(use=JsonTypeInfo.Id.NAME,property="kind")
    @JsonSubTypes(@JsonSubTypes.Type(value=ChoicePayload.class,name="CHOICE"))
    private interface PayloadTypes { }
    @JsonTypeInfo(use=JsonTypeInfo.Id.NAME,property="kind")
    @JsonSubTypes(@JsonSubTypes.Type(value=ChoiceAnswerSpec.class,name="CHOICE"))
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
