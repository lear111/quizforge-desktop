package io.quizforge.core.port;

import java.util.Optional;

public interface CredentialStore {
    void save(String reference, String secret);

    Optional<String> get(String reference);

    boolean exists(String reference);

    void delete(String reference);
}
