package io.quizforge.desktop.ui;

import io.quizforge.core.question.QBankResource;
import java.io.ByteArrayInputStream;
import java.io.InputStream;

/** Pending bytes for either an imported image or a native document. */
record StagedContentResource(QBankResource resource, byte[] bytes) {
    StagedContentResource { bytes = bytes.clone(); }
    @Override public byte[] bytes() { return bytes.clone(); }
    InputStream open() { return new ByteArrayInputStream(bytes); }
}
