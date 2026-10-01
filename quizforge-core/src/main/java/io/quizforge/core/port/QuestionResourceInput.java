package io.quizforge.core.port;

import io.quizforge.core.question.resource.QBankResource;
import java.io.IOException;
import java.io.InputStream;

/** Transient caller-owned streams. Null means this source does not contain the resource. */
@FunctionalInterface
public interface QuestionResourceInput {
    InputStream open(QBankResource resource) throws IOException;
    QuestionResourceInput NONE = resource -> null;
}
