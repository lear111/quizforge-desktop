package io.quizforge.core.port;

import io.quizforge.core.question.*;

public interface QuestionBankFileCodec {
    String write(QuestionBank bank);
    QuestionBank parse(String json);
    QuestionBank parseEmptyDraft(String json);
    void validate(QuestionBank bank);
    String contentId(QuestionBank bank);
}
