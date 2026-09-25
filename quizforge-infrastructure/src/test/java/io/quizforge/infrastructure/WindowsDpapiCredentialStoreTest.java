package io.quizforge.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quizforge.core.ai.AiSettingsService;
import io.quizforge.infrastructure.filesystem.QuizForgeDataDirectory;
import io.quizforge.infrastructure.persistence.SqliteAiProviderConfigRepository;
import io.quizforge.infrastructure.persistence.SqliteDatabase;
import io.quizforge.infrastructure.security.WindowsDpapiCredentialStore;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

@EnabledOnOs(OS.WINDOWS)
class WindowsDpapiCredentialStoreTest {
    @TempDir Path temporaryDirectory;

    @Test
    void roundTripReplaceDeleteAndNeverPersistPlaintextInSqlite() throws Exception {
        QuizForgeDataDirectory directory = new QuizForgeDataDirectory(temporaryDirectory.resolve("data"));
        var store = new WindowsDpapiCredentialStore(directory);
        String reference = "ai-provider-deepseek";
        String first = UUID.randomUUID().toString();
        String second = UUID.randomUUID().toString();
        assertFalse(store.exists(reference));
        store.save(reference, first);
        assertTrue(store.exists(reference));
        assertEquals(first, store.get(reference).orElseThrow());
        store.save(reference, second);
        assertEquals(second, store.get(reference).orElseThrow());

        var settings = new AiSettingsService(new SqliteAiProviderConfigRepository(
                new SqliteDatabase(directory)), store, Clock.systemUTC());
        settings.save("deepseek", "https://api.deepseek.com", "deepseek-v4-flash", null);
        assertTrue(settings.hasCredential());
        String db = new String(Files.readAllBytes(directory.databaseFile()), StandardCharsets.ISO_8859_1);
        String encrypted = new String(Files.readAllBytes(directory.root().resolve("secrets")
                .resolve(reference + ".dpapi")), StandardCharsets.ISO_8859_1);
        assertFalse(db.contains(first));
        assertFalse(db.contains(second));
        assertFalse(encrypted.contains(second));
        store.delete(reference);
        assertFalse(store.exists(reference));
        assertTrue(store.get(reference).isEmpty());
    }
}
