package io.quizforge.core.question.compat.translation;

/** A marked sentence, identified independently from its current display number. */
public record TranslationItem(String id, int number, String text) { }
