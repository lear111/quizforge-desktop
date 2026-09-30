package io.quizforge.core.question;

public sealed interface BlockNode permits ParagraphNode, HeadingNode, BulletListNode, OrderedListNode, BlockQuoteNode, BlockImageNode, BlockMathNode { }
