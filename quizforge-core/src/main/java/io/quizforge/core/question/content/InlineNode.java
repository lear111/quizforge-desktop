package io.quizforge.core.question.content;

public sealed interface InlineNode permits InlineTextNode, InlineImageNode, InlineMathNode, LineBreakNode, LinkNode { }
