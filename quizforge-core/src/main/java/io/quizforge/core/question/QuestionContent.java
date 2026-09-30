package io.quizforge.core.question;

public sealed interface QuestionContent permits TextContent, RichContent, DocumentContent { }
