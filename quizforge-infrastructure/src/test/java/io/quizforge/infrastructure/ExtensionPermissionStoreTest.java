package io.quizforge.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quizforge.infrastructure.extension.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ExtensionPermissionStoreTest {
    @TempDir Path root;
    private ExtensionPackageStore.InstalledExtension extension(String version,String hash) throws Exception {
        String manifest="""
            {"packageFormatVersion":2,"sdkApiMajor":2,"id":"test.example","name":"Example","version":"%s",
            "types":[{"id":"EXAMPLE","label":"Example","dataVersion":1,"family":"OBJECTIVE",
            "permissions":["answer.write","practice.submit"]}]}
            """.formatted(version);
        return new ExtensionPackageStore.InstalledExtension(new ObjectMapper().readValue(manifest,ExtensionManifest.class),hash,root.resolve(version));
    }
    @Test void declarationAloneNeverGrantsAndSubsetSurvivesReload() throws Exception {
        var store=new ExtensionPermissionStore(root);var extension=extension("1.0.0","a".repeat(64));
        assertFalse(store.hasApproval(extension));assertEquals(Set.of(),store.grants(extension).get("EXAMPLE"));
        store.approve(extension,Map.of("EXAMPLE",Set.of("answer.write")));
        var reloaded=new ExtensionPermissionStore(root);assertTrue(reloaded.hasApproval(extension));
        assertEquals(Set.of("answer.write"),reloaded.grants(extension).get("EXAMPLE"));
        store.approve(extension,Map.of());assertTrue(store.hasApproval(extension));assertEquals(Set.of(),store.grants(extension).get("EXAMPLE"));
    }
    @Test void approvalDoesNotCarryAcrossVersionsOrPackageHashes() throws Exception {
        var store=new ExtensionPermissionStore(root);var first=extension("1.0.0","a".repeat(64));
        store.approve(first,Map.of("EXAMPLE",Set.of("answer.write")));
        for(var other:List.of(extension("1.0.1","a".repeat(64)),extension("1.0.0","b".repeat(64)))) {
            assertFalse(store.hasApproval(other));assertEquals(Set.of(),store.grants(other).get("EXAMPLE"));
        }
    }
    @Test void rejectsUnknownUndeclaredPermissionsAndForeignTypesWithoutWriting() throws Exception {
        var store=new ExtensionPermissionStore(root);var extension=extension("1.0.0","a".repeat(64));
        for(var approval:List.of(Map.of("EXAMPLE",Set.of("bank.delete")),Map.of("EXAMPLE",Set.of("files.read")),Map.of("OTHER",Set.of("answer.write"))))
            assertThrows(IllegalArgumentException.class,()->store.approve(extension,approval));
        assertFalse(Files.exists(root.resolve(".permissions.json")));
        assertThrows(IllegalArgumentException.class,()->ExtensionPermissions.validate(Arrays.asList((String)null)));
    }
    @Test void malformedOrAmbiguousHostRecordsFailClosed() throws Exception {
        var store=new ExtensionPermissionStore(root);var extension=extension("1.0.0","a".repeat(64));
        for(String content:List.of("null","{}","{\"schemaVersion\":1,\"packages\":{\"bad\":null}}","{\"schemaVersion\":1,\"packages\":{}} {}","{\"schemaVersion\":1,\"schemaVersion\":1,\"packages\":{}}")) {
            Files.writeString(root.resolve(".permissions.json"),content);
            assertThrows(java.io.IOException.class,()->store.grants(extension));
        }
    }
}
