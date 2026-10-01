package io.quizforge.core.question.content;

public sealed interface QuestionContent permits TextContent, RichContent, DocumentContent { }
