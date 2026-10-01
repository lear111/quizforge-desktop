package io.quizforge.core.ai;

import io.quizforge.core.port.AiProviderConfigRepository;
import io.quizforge.core.port.CredentialStore;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AiSettingsServiceTest {
    @Test void failedMetadataSavePreservesOriginalKeyAndConfiguration() {
        var keys=new HashMap<String,String>();
        CredentialStore credentials=new CredentialStore() {
            public void save(String reference,String secret){keys.put(reference,secret);}
            public Optional<String> get(String reference){return Optional.ofNullable(keys.get(reference));}
            public boolean exists(String reference){return keys.containsKey(reference);}
            public void delete(String reference){keys.remove(reference);}
        };
        var repository=new AiProviderConfigRepository() {
            AiProviderConfig current;
            boolean fail;
            public Optional<AiProviderConfig> findDefault(){return Optional.ofNullable(current);}
            public void save(AiProviderConfig value){if(fail)throw new IllegalStateException("test SQL failure");current=value;}
        };
        var service=new AiSettingsService(repository,credentials,Clock.systemUTC());
        var original=service.save("deepseek","https://example.invalid","review-model","original-test-key");
        repository.fail=true;
        assertThrows(IllegalStateException.class,()->service.save("deepseek","https://example.invalid","other-model","replacement-test-key"));
        assertEquals(original,service.configuration().orElseThrow());
        assertEquals(Map.of(original.credentialRef(),"original-test-key"),keys);
        repository.fail=false;
        assertEquals(original.credentialRef(),service.save("deepseek","https://example.invalid","updated-model","").credentialRef());
        var replacement=service.save("deepseek","https://example.invalid","updated-model","replacement-test-key");
        assertEquals(Map.of(replacement.credentialRef(),"replacement-test-key"),keys);
    }
}
