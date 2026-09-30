package io.quizforge.core.question;

public sealed interface QuestionPayload permits ChoicePayload, EssayPayload { }
