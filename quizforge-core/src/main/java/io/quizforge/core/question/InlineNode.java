package io.quizforge.core.question;

public sealed interface InlineNode permits InlineTextNode, InlineImageNode, InlineMathNode, LineBreakNode, LinkNode { }
