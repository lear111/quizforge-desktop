package io.quizforge.extension.document;

public interface DocumentProcessor {
    String formatId();

    String formatVersion();

    DocumentProcessResult process(DocumentProcessRequest request);
}
