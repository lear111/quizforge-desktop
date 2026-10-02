package io.quizforge.core.question.type;

import io.quizforge.core.question.type.objective.choice.MultipleChoiceQuestionType;
import io.quizforge.core.question.type.objective.choice.SingleChoiceQuestionType;
import io.quizforge.core.question.type.subjective.essay.EssayQuestionType;
import java.util.List;

/** The single explicit list of supported data/rule types. Add a concrete definition here. */
public final class QuestionTypes {
    private static final List<QuestionTypeDefinition> DEFINITIONS = List.of(
            new SingleChoiceQuestionType(),
            new MultipleChoiceQuestionType(),
            new EssayQuestionType(),
            new io.quizforge.core.question.type.objective.cloze.ClozeQuestionType(),
            new io.quizforge.core.question.type.objective.reading.ReadingQuestionType(),
            new io.quizforge.core.question.type.objective.matching.MatchingQuestionType(),
            new io.quizforge.core.question.type.subjective.translation.TranslationQuestionType());
    private QuestionTypes(){ }
    public static boolean isCloze(String id){return "CLOZE".equals(id);}
    public static boolean isReading(String id){return "READING".equals(id);}
    public static boolean isMatching(String id){return "MATCHING".equals(id);}
    public static boolean isTranslation(String id){return "TRANSLATION".equals(id);}
    public static List<QuestionTypeDefinition> definitions(){return DEFINITIONS;}
    public static boolean isChoice(String id) {
        return find(id).map(type -> type.payloadClass() ==
                io.quizforge.core.question.type.objective.choice.ChoicePayload.class).orElse(false);
    }
    public static boolean isEssay(String id) {
        return find(id).map(type -> type.payloadClass() ==
                io.quizforge.core.question.type.subjective.essay.EssayPayload.class).orElse(false);
    }
    public static boolean isSingleChoice(String id) {
        return isChoice(id) && !require(id).multipleSelection();
    }
    public static java.util.Optional<QuestionTypeDefinition> find(String id) {
        return DEFINITIONS.stream().filter(type->type.id().equals(id)).findFirst();
    }
    public static QuestionTypeDefinition require(String id) {
        return find(id).orElseThrow(()->
                new io.quizforge.core.QuizForgeException(io.quizforge.core.ErrorCode.QUESTION_BANK_FILE_INVALID,"Unsupported question type: "+id));
    }
}
