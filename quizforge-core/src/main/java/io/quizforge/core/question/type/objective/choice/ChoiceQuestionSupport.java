package io.quizforge.core.question.type.objective.choice;

import io.quizforge.core.question.content.TextContent;
import io.quizforge.core.question.model.Question;
import io.quizforge.core.question.source.SourceRef;
import io.quizforge.core.question.type.QuestionValidationContext;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Function;
import static io.quizforge.core.question.type.QuestionValidationContext.reject;

/** Shared option construction and identity checks; type-specific policies stay in each type. */
final class ChoiceQuestionSupport {
    private ChoiceQuestionSupport() { }

    static Question createDraft(String typeId, int optionCount, int correctCount,
            Function<String, String> newId, List<SourceRef> sources) {
        var options = new ArrayList<ChoiceOption>();
        for (int i = 0; i < optionCount; i++) {
            options.add(new ChoiceOption(newId.apply("opt_"), new TextContent("Option " + (i + 1))));
        }
        var answers = options.stream().limit(correctCount).map(ChoiceOption::id).toList();
        return Question.choice(newId.apply("q_"), typeId, new TextContent("New question"),
                new TextContent("New analysis"), sources, new ChoicePayload(options), new ChoiceAnswerSpec(answers));
    }

    static Question duplicate(Question question, Function<String, String> newId) {
        var optionIds = new LinkedHashMap<String, String>();
        var options = question.choicePayload().options().stream().map(option -> {
            var freshId = newId.apply("opt_");
            optionIds.put(option.id(), freshId);
            return new ChoiceOption(freshId, option.content());
        }).toList();
        var answers = question.choiceAnswerSpec().correctOptionIds().stream().map(optionIds::get).toList();
        return new Question(newId.apply("q_"), question.type(), question.stimulusRefs(), question.prompt(),
                new ChoicePayload(options), new ChoiceAnswerSpec(answers), question.scoreSpec(),
                question.evaluationSpec(), question.analysis(), question.sourceRefs());
    }

    static void validateOptionsAndAnswers(Question question, QuestionValidationContext context) {
        if (!(question.payload() instanceof ChoicePayload) || !(question.answerSpec() instanceof ChoiceAnswerSpec)) {
            reject("Choice requires ChoicePayload and ChoiceAnswerSpec");
        }
        var optionIds = new HashSet<String>();
        for (var option : question.choicePayload().options()) {
            if (option.id() == null || !option.id().matches("opt_[A-Za-z0-9_-]+")
                    || !optionIds.add(option.id()) || !context.optionIds().add(option.id())) {
                reject("Invalid option id");
            }
            context.content().accept(option.content(), true);
        }
        var answers = question.choiceAnswerSpec().correctOptionIds();
        var correctIds = new HashSet<>(answers);
        if (optionIds.size() < 2 || correctIds.size() != answers.size() || !optionIds.containsAll(correctIds)) {
            reject("Invalid correctOptionIds");
        }
    }
}
