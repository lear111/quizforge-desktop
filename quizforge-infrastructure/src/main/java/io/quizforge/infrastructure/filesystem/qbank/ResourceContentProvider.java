package io.quizforge.infrastructure.filesystem.qbank;

import io.quizforge.core.question.QBankResource;
import java.io.IOException;
import java.io.InputStream;

/** Each opened stream belongs to the caller. Binary content stays outside the logical domain. */
@FunctionalInterface
public interface ResourceContentProvider {
    InputStream open(QBankResource resource) throws IOException;
    ResourceContentProvider NONE = resource -> { throw new IOException("No bytes supplied for " + resource.id()); };
}
