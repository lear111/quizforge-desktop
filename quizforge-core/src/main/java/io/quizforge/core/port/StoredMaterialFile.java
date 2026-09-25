package io.quizforge.core.port;

public record StoredMaterialFile(String originalFileName, String storedFileName, long fileSize) {
}
