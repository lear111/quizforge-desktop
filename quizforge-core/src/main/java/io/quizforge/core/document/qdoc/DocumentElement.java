package io.quizforge.core.document.qdoc;

/** A structural node or a typed content block in document order. */
public sealed interface DocumentElement permits DocumentNode, ContentBlock { }
