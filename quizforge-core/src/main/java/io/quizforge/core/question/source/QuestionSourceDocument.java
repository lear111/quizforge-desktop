package io.quizforge.core.question.source;

/** Derived runtime source metadata, not a QBank file DTO. */
public record QuestionSourceDocument(String assetId, String contentId, String title) { }
