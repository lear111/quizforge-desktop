package io.quizforge.core.question.type;

import io.quizforge.core.ErrorCode;
import io.quizforge.core.QuizForgeException;
import io.quizforge.core.question.content.QuestionContent;
import java.util.Set;
import java.util.function.BiConsumer;

public record QuestionValidationContext(BiConsumer<QuestionContent,Boolean> content,Set<String> optionIds) {
    public static void reject(String message) { throw new QuizForgeException(ErrorCode.QUESTION_BANK_FILE_INVALID,message); }
}
