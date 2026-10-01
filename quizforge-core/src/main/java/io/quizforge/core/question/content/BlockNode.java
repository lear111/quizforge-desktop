package io.quizforge.core.question.content;

public sealed interface BlockNode permits ParagraphNode, HeadingNode, BulletListNode, OrderedListNode, BlockQuoteNode, BlockImageNode, BlockMathNode { }
