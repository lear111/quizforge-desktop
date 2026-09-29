package io.quizforge.core.question;

public sealed interface BlockNode permits ParagraphNode, BlockImageNode, BlockMathNode { }
