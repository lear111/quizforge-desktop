package io.quizforge.extensions.question.choice;

import io.quizforge.extension.ai.AiGenerationOptions;
import io.quizforge.extension.ai.AiMessage;
import io.quizforge.extension.ai.AiProvider;
import io.quizforge.extension.ai.AiRequest;
import io.quizforge.extension.ai.AiRole;
import io.quizforge.extension.question.QuestionGenerationRequest;
import io.quizforge.extension.question.QuestionGenerationResult;
import io.quizforge.extension.question.QuestionGenerator;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public final class DefaultChoiceQuestionGenerator implements QuestionGenerator {
    private final QuestionGenerationPromptBuilder prompt = new QuestionGenerationPromptBuilder();
    private final GeneratedQuestionParser parser = new GeneratedQuestionParser();
    private final Supplier<AiProvider> providers;

    public DefaultChoiceQuestionGenerator(Supplier<AiProvider> providers) {
        this.providers = Objects.requireNonNull(providers);
    }

    @Override
    public QuestionGenerationResult generate(QuestionGenerationRequest request) {
        String output = providers.get().generate(new AiRequest(List.of(
                new AiMessage(AiRole.SYSTEM, prompt.system()),
                new AiMessage(AiRole.USER, prompt.user(request))),
                new AiGenerationOptions(0.3, true, 8192))).content();
        return parser.parse(output);
    }
}
