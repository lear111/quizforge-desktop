package io.quizforge.infrastructure.filesystem;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quizforge.core.ErrorCode;
import io.quizforge.core.QuizForgeException;
import io.quizforge.core.port.QuestionBankFileCodec;
import io.quizforge.core.question.QuestionBankFile;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Self-contained portable .qbank v1 schema; source availability is resolved separately. */
public final class QuestionBankV1Codec implements QuestionBankFileCodec {
    private final ObjectMapper json = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

    @Override
    public String write(QuestionBankFile bank) {
        validate(bank);
        try { return json.writerWithDefaultPrettyPrinter().writeValueAsString(bank) + "\n"; }
        catch (Exception error) { throw invalid("Could not serialize QuestionBank", error); }
    }

    @Override
    public QuestionBankFile parse(String source) {
        try {
            QuestionBankFile bank = json.readValue(source, QuestionBankFile.class);
            validate(bank);
            return bank;
        } catch (QuizForgeException error) {
            throw error;
        } catch (Exception error) {
            throw invalid("Invalid QuestionBank JSON", error);
        }
    }

    @Override
    public void validate(QuestionBankFile bank) {
        if (bank == null || !"quizforge-question-bank".equals(bank.format())
                || !"1.0".equals(bank.schemaVersion()) || !id(bank.id(), "qb_")
                || blank(bank.title()) || bank.sourceDocuments().isEmpty()
                || bank.questions().isEmpty()) throw invalid("Invalid QuestionBank metadata", null);
        Map<String, String> sources = new HashMap<>();
        for (QuestionBankFile.SourceDocument source : bank.sourceDocuments()) {
            if (source == null || !id(source.assetId(), "doc_")
                    || !contentId(source.contentId()) || blank(source.title())
                    || sources.putIfAbsent(source.assetId(), source.contentId()) != null) {
                throw invalid("Invalid sourceDocuments", null);
            }
        }
        Set<String> questions = new HashSet<>();
        Set<String> allOptions = new HashSet<>();
        for (QuestionBankFile.Entry entry : bank.questions()) {
            if (entry == null || !id(entry.id(), "q_") || !questions.add(entry.id())
                    || blank(entry.stem()) || blank(entry.analysis()) || entry.data() == null
                    || entry.sourceRefs().isEmpty()) throw invalid("Invalid question", null);
            boolean single = "SINGLE_CHOICE".equals(entry.type());
            boolean multiple = "MULTIPLE_CHOICE".equals(entry.type());
            if (!single && !multiple) throw invalid("Invalid question type", null);
            Set<String> refs = new HashSet<>();
            for (QuestionBankFile.SourceRef ref : entry.sourceRefs()) {
                if (ref == null || !id(ref.documentAssetId(), "doc_")
                        || !contentId(ref.documentContentId()) || !id(ref.sectionId(), "section_")
                        || blank(ref.documentTitle()) || blank(ref.sectionTitle())
                        || !ref.documentContentId().equals(sources.get(ref.documentAssetId()))
                        || !refs.add(ref.documentAssetId() + "\0" + ref.sectionId())) {
                    throw invalid("Invalid sourceRefs", null);
                }
            }
            Set<String> options = new HashSet<>();
            for (QuestionBankFile.Option option : entry.data().options()) {
                if (option == null || !id(option.id(), "opt_") || blank(option.content())
                        || !options.add(option.id()) || !allOptions.add(option.id())) {
                    throw invalid("Invalid option", null);
                }
            }
            Set<String> correct = new HashSet<>(entry.data().correctOptionIds());
            if (options.size() < 2 || correct.size() != entry.data().correctOptionIds().size()
                    || !options.containsAll(correct)
                    || single && correct.size() != 1
                    || multiple && (correct.size() < 2 || correct.size() >= options.size())) {
                throw invalid("Invalid correctOptionIds", null);
            }
        }
    }

    private boolean id(String value, String prefix) {
        return value != null && value.matches(prefix + "[A-Za-z0-9_-]+");
    }

    private boolean contentId(String value) {
        return value != null && value.matches("qfd:v1:[0-9a-f]{64}");
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }

    private QuizForgeException invalid(String message, Throwable error) {
        return new QuizForgeException(ErrorCode.QUESTION_BANK_FILE_INVALID, message, error);
    }
}
