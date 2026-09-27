package io.quizforge.infrastructure.filesystem;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quizforge.core.question.QuestionSourceAddress;
import io.quizforge.core.ErrorCode;
import io.quizforge.core.QuizForgeException;
import io.quizforge.core.port.QuestionBankFileCodec;
import io.quizforge.core.question.QuestionBankFile;
import io.quizforge.core.question.QuestionBankValidator;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Portable .qbank v1 JSON plus a deterministic hash of the validated model. */
public final class QuestionBankV1Codec implements QuestionBankFileCodec {
    private final ObjectMapper json = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private final QuestionBankValidator validator = new QuestionBankValidator();

    @Override public String write(QuestionBankFile bank) {
        validate(bank);
        try {
            ObjectNode root = json.valueToTree(bank);
            writeRefs(root, bank.schemaVersion());
            return json.writerWithDefaultPrettyPrinter().writeValueAsString(root) + "\n";
        }
        catch (Exception error) { throw invalid("Could not serialize QuestionBank", error); }
    }

    @Override public QuestionBankFile parse(String source) {
        try {
            JsonNode root = json.readTree(source);
            if (root == null || !root.isObject()) throw invalid("Invalid QuestionBank JSON", null);
            String version = root.path("schemaVersion").asText();
            readRefs((ObjectNode) root, version);
            QuestionBankFile bank = json.treeToValue(root, QuestionBankFile.class);
            validate(bank);
            return bank;
        } catch (QuizForgeException error) { throw error; }
        catch (Exception error) { throw invalid("Invalid QuestionBank JSON", error); }
    }

    @Override public void validate(QuestionBankFile bank) { validator.validate(bank); }

    @Override public QuestionBankFile parseEmptyDraft(String source) {
        try {
            QuestionBankFile bank = json.readValue(source, QuestionBankFile.class);
            if (bank == null || !"quizforge-question-bank".equals(bank.format())
                    || !("1.0".equals(bank.schemaVersion()) || "1.1".equals(bank.schemaVersion())
                            || "1.2".equals(bank.schemaVersion()))
                    || bank.id() == null
                    || !bank.id().matches("qb_[A-Za-z0-9_-]+") || bank.title() == null
                    || bank.title().isBlank() || !bank.questions().isEmpty()) {
                throw invalid("Invalid empty QuestionBank draft", null);
            }
            java.util.Set<String> ids = new java.util.HashSet<>();
            for (var ref : bank.sourceDocuments()) {
                if (ref == null || ref.assetId() == null
                        || !ref.assetId().matches("doc_[A-Za-z0-9_-]+")
                        || ref.contentId() == null || !ref.contentId().matches("qfd:v[12]:[0-9a-f]{64}")
                        || ref.title() == null || ref.title().isBlank() || !ids.add(ref.assetId())) {
                    throw invalid("Invalid empty QuestionBank draft sources", null);
                }
            }
            return bank;
        } catch (QuizForgeException error) { throw error; }
        catch (Exception error) { throw invalid("Invalid empty QuestionBank draft", error); }
    }

    @Override public String contentId(QuestionBankFile bank) {
        validate(bank);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            value(out, "qbank-canonical-v1");
            value(out, bank.format());
            value(out, bank.schemaVersion());
            value(out, bank.title());
            out.writeInt(bank.sourceDocuments().size());
            for (var source : bank.sourceDocuments()) {
                value(out, source.assetId()); value(out, source.contentId()); value(out, source.title());
            }
            out.writeInt(bank.questions().size());
            for (var question : bank.questions()) {
                value(out, question.id()); value(out, question.type()); value(out, question.stem());
                value(out, question.analysis());
                out.writeInt(question.sourceRefs().size());
                for (var ref : question.sourceRefs()) {
                    value(out, ref.documentAssetId()); value(out, ref.documentContentId());
                    if ("1.2".equals(bank.schemaVersion())) {
                        value(out, ref.anchorName());
                        out.writeInt(ref.occurrence());
                    } else {
                        value(out, ref.nodeId());
                        value(out, ref.documentTitle()); value(out, ref.sectionTitle());
                    }
                }
                out.writeInt(question.data().options().size());
                for (var option : question.data().options()) {
                    value(out, option.id()); value(out, option.content());
                }
                out.writeInt(question.data().correctOptionIds().size());
                for (String correct : question.data().correctOptionIds()) value(out, correct);
            }
            out.flush();
            return "qfb:v1:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(bytes.toByteArray()));
        } catch (IOException | NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private void value(DataOutputStream out, String value) throws IOException {
        if (value == null) { out.writeInt(-1); return; }
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(encoded.length);
        out.write(encoded);
    }

    private void writeRefs(ObjectNode root, String version) {
        for (JsonNode question : root.path("questions")) for (JsonNode ref : question.path("sourceRefs")) {
            ObjectNode object = (ObjectNode) ref;
            JsonNode address = object.remove("address");
            if ("1.2".equals(version)) {
                if (!"ANCHOR".equals(address.path("kind").asText()))
                    throw invalid("1.2 requires named anchor references", null);
                object.set("anchorName", address.path("value"));
                object.set("occurrence", address.path("occurrence"));
                object.remove("documentTitle");
                object.remove("sectionTitle");
            } else {
                if ("ANCHOR".equals(address.path("kind").asText()))
                    throw invalid("Legacy banks require legacy references", null);
                object.set("1.0".equals(version) ? "sectionId" : "nodeId", address.path("value"));
            }
        }
    }

    private void readRefs(ObjectNode root, String version) {
        String field = switch (version) {
            case "1.0" -> "sectionId";
            case "1.1" -> "nodeId";
            case "1.2" -> "anchorName";
            default -> throw invalid("Unsupported QuestionBank schema version", null);
        };
        for (JsonNode question : root.path("questions")) for (JsonNode ref : question.path("sourceRefs")) {
            if (!(ref instanceof ObjectNode object) || !object.has(field) || object.has("address")
                    || object.has("1.0".equals(version) ? "nodeId" : "sectionId")
                    || "1.2".equals(version) && (object.has("nodeId")
                            || !object.path("occurrence").isIntegralNumber()
                            || !object.path("anchorName").isTextual())
                    || !"1.2".equals(version) && object.has("anchorName"))
                throw invalid("Invalid QuestionBank sourceRef version", null);
            ObjectNode address = json.createObjectNode();
            address.put("kind", switch (version) {
                case "1.0" -> QuestionSourceAddress.Kind.LEGACY_SECTION.name();
                case "1.1" -> QuestionSourceAddress.Kind.LEGACY_NODE.name();
                default -> QuestionSourceAddress.Kind.ANCHOR.name();
            });
            address.set("value", object.remove(field));
            if ("1.2".equals(version)) {
                address.set("occurrence", object.remove("occurrence"));
                String assetId = object.path("documentAssetId").asText();
                String title = "";
                for (JsonNode source : root.path("sourceDocuments")) {
                    if (assetId.equals(source.path("assetId").asText())) title = source.path("title").asText();
                }
                object.put("documentTitle", title);
                object.put("sectionTitle", address.path("value").asText());
            }
            object.set("address", address);
        }
    }

    private QuizForgeException invalid(String message, Throwable error) {
        return new QuizForgeException(ErrorCode.QUESTION_BANK_FILE_INVALID, message, error);
    }
}
