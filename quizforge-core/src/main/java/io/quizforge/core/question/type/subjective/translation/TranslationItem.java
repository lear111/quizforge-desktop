package io.quizforge.core.question.type.subjective.translation;

/** A marked sentence, identified independently from its current display number. */
public record TranslationItem(String id, int number, String text) { }
