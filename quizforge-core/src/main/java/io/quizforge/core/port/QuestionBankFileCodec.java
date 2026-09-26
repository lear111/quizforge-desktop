package io.quizforge.core.port;

import io.quizforge.core.question.QuestionBankFile;

public interface QuestionBankFileCodec {
    String write(QuestionBankFile bank);
    QuestionBankFile parse(String json);
    QuestionBankFile parseEmptyDraft(String json);
    void validate(QuestionBankFile bank);
    String contentId(QuestionBankFile bank);
}
