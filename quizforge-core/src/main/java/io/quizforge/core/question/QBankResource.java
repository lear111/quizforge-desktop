package io.quizforge.core.question;

public record QBankResource(String id, ResourceKind kind, String mediaType, String locator, String sha256) { }
