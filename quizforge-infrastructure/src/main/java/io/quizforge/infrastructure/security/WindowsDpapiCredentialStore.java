package io.quizforge.infrastructure.security;

import com.sun.jna.platform.win32.Crypt32Util;
import io.quizforge.core.ErrorCode;
import io.quizforge.core.QuizForgeException;
import io.quizforge.core.port.CredentialStore;
import io.quizforge.infrastructure.filesystem.QuizForgeDataDirectory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Optional;

public final class WindowsDpapiCredentialStore implements CredentialStore {
    private final Path secrets;

    public WindowsDpapiCredentialStore(QuizForgeDataDirectory dataDirectory) {
        if (!System.getProperty("os.name").toLowerCase().contains("windows")) {
            throw new IllegalStateException("Windows DPAPI is available only on Windows.");
        }
        try {
            secrets = dataDirectory.root().resolve("secrets");
            Files.createDirectories(secrets);
            if (!secrets.toRealPath().startsWith(dataDirectory.root())) {
                throw new IllegalStateException("Secrets directory is outside the data directory.");
            }
        } catch (IOException e) {
            throw failure("create secrets directory", e);
        }
    }

    @Override
    public void save(String reference, String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("Secret must not be empty.");
        }
        byte[] plain = secret.getBytes(StandardCharsets.UTF_8);
        Path temporary = null;
        try {
            byte[] encrypted = Crypt32Util.cryptProtectData(plain);
            temporary = Files.createTempFile(secrets, "credential-", ".tmp");
            Files.write(temporary, encrypted);
            Files.move(temporary, path(reference), StandardCopyOption.REPLACE_EXISTING);
        } catch (RuntimeException | IOException e) {
            throw failure("save credential", e);
        } finally {
            Arrays.fill(plain, (byte) 0);
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // A failed temporary cleanup does not expose plaintext.
                }
            }
        }
    }

    @Override
    public Optional<String> get(String reference) {
        Path file = path(reference);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            byte[] plain = Crypt32Util.cryptUnprotectData(Files.readAllBytes(file));
            try {
                return Optional.of(new String(plain, StandardCharsets.UTF_8));
            } finally {
                Arrays.fill(plain, (byte) 0);
            }
        } catch (RuntimeException | IOException e) {
            throw failure("read credential", e);
        }
    }

    @Override
    public boolean exists(String reference) {
        return Files.isRegularFile(path(reference));
    }

    @Override
    public void delete(String reference) {
        try {
            Files.deleteIfExists(path(reference));
        } catch (IOException e) {
            throw failure("delete credential", e);
        }
    }

    private Path path(String reference) {
        if (reference == null || !reference.matches("[a-z0-9-]{1,80}")) {
            throw new IllegalArgumentException("Invalid credential reference.");
        }
        Path file = secrets.resolve(reference + ".dpapi");
        if (Files.isSymbolicLink(file)) {
            throw failure("access credential", null);
        }
        return file;
    }

    private QuizForgeException failure(String action, Throwable cause) {
        return new QuizForgeException(ErrorCode.AI_PROVIDER_CREDENTIAL_MISSING,
                "Could not " + action + ".", cause);
    }
}
