package io.quizforge.core.port;

import io.quizforge.core.document.qdoc.QDocDocument;

public interface QDocCodec {
    QDocDocument parse(String content);
    String write(QDocDocument document);
    String contentId(QDocDocument document);
}
