package io.quizforge.extensions.document.standardmd;

import io.quizforge.extension.ai.AiGenerationOptions;
import io.quizforge.extension.ai.AiMessage;
import io.quizforge.extension.ai.AiRequest;
import io.quizforge.extension.ai.AiRole;
import io.quizforge.extension.document.DocumentProcessRequest;
import io.quizforge.extension.document.DocumentProcessResult;
import io.quizforge.extension.document.DocumentProcessor;
import java.util.List;

public final class StandardMarkdownV1Processor implements DocumentProcessor {
    public static final String FORMAT_ID = "quizforge-standard-markdown";
    public static final String FORMAT_VERSION = "1.0";
    private final StandardMarkdownV1PromptBuilder prompts = new StandardMarkdownV1PromptBuilder();

    @Override
    public String formatId() {
        return FORMAT_ID;
    }

    @Override
    public String formatVersion() {
        return FORMAT_VERSION;
    }

    @Override
    public DocumentProcessResult process(DocumentProcessRequest request) {
        var messages = List.of(new AiMessage(AiRole.SYSTEM, prompts.systemPrompt()),
                new AiMessage(AiRole.USER, prompts.userPrompt(request.sources())));
        return new DocumentProcessResult(request.provider()
                .generate(new AiRequest(messages, new AiGenerationOptions(0.2))).content());
    }
}
