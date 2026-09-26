package io.quizforge.core.question;

import io.quizforge.core.ErrorCode;
import io.quizforge.core.QuizForgeException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Portable v1 schema validation; source availability is a separate runtime concern. */
public final class QuestionBankValidator {
    public void validate(QuestionBankFile bank) {
        if (bank == null || !"quizforge-question-bank".equals(bank.format())
                || !"1.0".equals(bank.schemaVersion()) || !id(bank.id(), "qb_")
                || blank(bank.title()) || bank.sourceDocuments().isEmpty()
                || bank.questions().isEmpty()) fail("Invalid QuestionBank metadata");
        Map<String, String> sources = new HashMap<>();
        for (QuestionBankFile.SourceDocument source : bank.sourceDocuments()) {
            if (source == null || !id(source.assetId(), "doc_")
                    || !contentId(source.contentId()) || blank(source.title())
                    || sources.putIfAbsent(source.assetId(), source.contentId()) != null) {
                fail("Invalid sourceDocuments");
            }
        }
        Set<String> questions = new HashSet<>();
        Set<String> allOptions = new HashSet<>();
        for (QuestionBankFile.Entry entry : bank.questions()) {
            if (entry == null || !id(entry.id(), "q_") || !questions.add(entry.id())
                    || blank(entry.stem()) || blank(entry.analysis()) || entry.data() == null
                    || entry.sourceRefs().isEmpty()) fail("Invalid question");
            boolean single = "SINGLE_CHOICE".equals(entry.type());
            boolean multiple = "MULTIPLE_CHOICE".equals(entry.type());
            if (!single && !multiple) fail("Invalid question type");
            Set<String> refs = new HashSet<>();
            for (QuestionBankFile.SourceRef ref : entry.sourceRefs()) {
                if (ref == null || !id(ref.documentAssetId(), "doc_")
                        || !contentId(ref.documentContentId()) || !id(ref.sectionId(), "section_")
                        || blank(ref.documentTitle()) || blank(ref.sectionTitle())
                        || !ref.documentContentId().equals(sources.get(ref.documentAssetId()))
                        || !refs.add(ref.documentAssetId() + "\0" + ref.sectionId())) {
                    fail("Invalid sourceRefs");
                }
            }
            Set<String> options = new HashSet<>();
            for (QuestionBankFile.Option option : entry.data().options()) {
                if (option == null || !id(option.id(), "opt_") || blank(option.content())
                        || !options.add(option.id()) || !allOptions.add(option.id())) fail("Invalid option");
            }
            Set<String> correct = new HashSet<>(entry.data().correctOptionIds());
            if (options.size() < 2 || correct.size() != entry.data().correctOptionIds().size()
                    || !options.containsAll(correct)
                    || single && correct.size() != 1
                    || multiple && (correct.size() < 2 || correct.size() >= options.size())) {
                fail("Invalid correctOptionIds");
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
    private void fail(String message) {
        throw new QuizForgeException(ErrorCode.QUESTION_BANK_FILE_INVALID, message);
    }
}
