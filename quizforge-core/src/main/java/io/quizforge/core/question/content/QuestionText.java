package io.quizforge.core.question.content;

import io.quizforge.core.question.model.choice.ChoiceOption;
import io.quizforge.core.question.model.choice.ChoicePayload;
import io.quizforge.core.question.model.Question;
import io.quizforge.core.question.model.QuestionBank;

/** TEXT-only adapter for the existing editor, practice renderer and persisted text snapshots. */
public final class QuestionText {
    private QuestionText() { }
    public static String read(QuestionContent content) {
        if (content == null) return "";
        if (content instanceof TextContent text) return text.text();
        throw new UnsupportedOperationException("RICH question content is not supported by the current text viewer");
    }
    public static String prompt(Question question) { return read(question.prompt()); }
    public static String analysis(Question question) { return read(question.analysis()); }
    public static String option(ChoiceOption option) { return read(option.content()); }
    public static boolean supports(QuestionBank bank) {
        return bank.questions().stream().allMatch(QuestionText::supports);
    }
    public static boolean supports(Question question) {
        return question.payload() instanceof ChoicePayload && question.prompt() instanceof TextContent
                && question.stimulusRefs().isEmpty()
                && (question.analysis() == null || question.analysis() instanceof TextContent)
                && question.choicePayload().options().stream().allMatch(option -> option.content() instanceof TextContent);
    }
}
