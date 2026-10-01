package io.quizforge.desktop.ui.content;

import io.quizforge.core.question.resource.QBankResource;
import java.io.ByteArrayInputStream;
import java.io.InputStream;

/** Pending bytes for either an imported image or a native document. */
public record StagedContentResource(QBankResource resource, byte[] bytes) {
    public StagedContentResource { bytes = bytes.clone(); }
    @Override public byte[] bytes() { return bytes.clone(); }
    public InputStream open() { return new ByteArrayInputStream(bytes); }
}
