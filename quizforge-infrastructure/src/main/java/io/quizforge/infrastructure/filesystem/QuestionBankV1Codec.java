package io.quizforge.infrastructure.filesystem;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
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
        try { return json.writerWithDefaultPrettyPrinter().writeValueAsString(bank) + "\n"; }
        catch (Exception error) { throw invalid("Could not serialize QuestionBank", error); }
    }

    @Override public QuestionBankFile parse(String source) {
        try {
            QuestionBankFile bank = json.readValue(source, QuestionBankFile.class);
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
                    || !"1.0".equals(bank.schemaVersion()) || bank.id() == null
                    || !bank.id().matches("qb_[A-Za-z0-9_-]+") || bank.title() == null
                    || bank.title().isBlank() || !bank.questions().isEmpty()) {
                throw invalid("Invalid empty QuestionBank draft", null);
            }
            java.util.Set<String> ids = new java.util.HashSet<>();
            for (var ref : bank.sourceDocuments()) {
                if (ref == null || ref.assetId() == null
                        || !ref.assetId().matches("doc_[A-Za-z0-9_-]+")
                        || ref.contentId() == null || !ref.contentId().matches("qfd:v1:[0-9a-f]{64}")
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
                    value(out, ref.sectionId()); value(out, ref.documentTitle()); value(out, ref.sectionTitle());
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

    private QuizForgeException invalid(String message, Throwable error) {
        return new QuizForgeException(ErrorCode.QUESTION_BANK_FILE_INVALID, message, error);
    }
}
