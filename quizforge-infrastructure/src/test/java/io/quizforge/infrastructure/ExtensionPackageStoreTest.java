package io.quizforge.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quizforge.infrastructure.extension.ExtensionPackageStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ExtensionPackageStoreTest {
    @TempDir Path temp;
    private ExtensionPackageStore store() { return new ExtensionPackageStore(temp.resolve("installed"), Set.of("SINGLE_CHOICE")); }
    private Map<String,String> entries(String extension, String version, String type) throws Exception {
        var result = new LinkedHashMap<String,String>();
        result.put("manifest.json", new ObjectMapper().writeValueAsString(Map.of("packageFormatVersion",2,"id",extension,
                "name","Example","version",version,"sdkApiMajor",2,"types",List.of(Map.ofEntries(
                Map.entry("id",type), Map.entry("label","Example"), Map.entry("dataVersion",1), Map.entry("family","OBJECTIVE"),
                Map.entry("capabilities",List.of("preview","practice")), Map.entry("questionSchema","schema/question.json"),
                Map.entry("answerSchema","schema/answer.json"), Map.entry("rules","rules.js"), Map.entry("editor","editor.html"),
                Map.entry("renderer","practice.html"), Map.entry("defaultQuestion","default.json"),
                Map.entry("editorScript","editor.js"),Map.entry("rendererScript","renderer.js"),Map.entry("styles",List.of("style.css")))))));
        result.put("schema/question.json","{\"type\":\"object\"}"); result.put("schema/answer.json","{\"type\":\"boolean\"}");
        result.put("rules.js","/* rules */"); result.put("editor.js","/* editor */"); result.put("renderer.js","/* renderer */"); result.put("style.css",".example{};");
        result.put("editor.html","<section><textarea id=\"prompt\"></textarea></section>");
        result.put("practice.html","<section><div id=\"prompt\"></div></section>");
        result.put("default.json",new ObjectMapper().writeValueAsString(Map.of("id","template","type",type,
                "prompt",Map.of("kind","TEXT","text","Question"),"payload",Map.of("kind","CHOICE","options",List.of()),
                "answerSpec",Map.of("kind","CHOICE","correctOptionIds",List.of()),"analysis",Map.of("kind","TEXT","text",""),
                "scoreSpec",Map.of("defaultMaxScore",1))));
        return result;
    }
    private Path zip(Map<String,String> entries) throws Exception {
        Path file = Files.createTempFile(temp,"extension-",".qfext");
        try (var output = new ZipOutputStream(Files.newOutputStream(file))) {
            for (var entry : entries.entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey())); output.write(entry.getValue().getBytes(StandardCharsets.UTF_8)); output.closeEntry();
            }
        }
        return file;
    }
    @Test void installsIdempotentlyAndPreservesMultipleVersionsAndAssets() throws Exception {
        var store = store(); Path first = zip(entries("test.example","1.0.0","EXAMPLE"));
        var installed = store.install(first); assertEquals(installed, store.install(first));
        assertEquals(64,installed.sha256().length()); assertEquals("/* renderer */", store.loadAssets(installed).getFirst().rendererSource());
        store.install(zip(entries("test.example","1.1.0","EXAMPLE")));
        assertEquals(2,store.listInstalled().size()); assertTrue(Files.exists(installed.directory().resolve("renderer.js")));
    }
    @Test void permissionReviewDoesNotInstallAndChangedBytesCannotUseReviewedHash() throws Exception {
        var store=store();var entries=entries("test.example","1.0.0","EXAMPLE");var file=zip(entries);
        var inspection=store.inspect(file);assertTrue(store.listInstalled().isEmpty());
        assertEquals(inspection.sha256(),store.install(file,inspection.sha256()).sha256());
        var other=store();entries.put("manifest.json",entries.get("manifest.json").replace("1.0.0","1.1.0"));
        var changed=zip(entries);
        assertThrows(IOException.class,()->other.install(changed,inspection.sha256()));assertEquals(1,other.listInstalled().size());
    }
    @Test void unknownPermissionsAreRejectedBeforeReviewAndInstall() throws Exception {
        var entries=entries("test.example","1.0.0","EXAMPLE");
        entries.put("manifest.json",entries.get("manifest.json").replace("\"family\":\"OBJECTIVE\"","\"family\":\"OBJECTIVE\",\"permissions\":[\"files.read\"]"));
        var file=zip(entries);assertThrows(IOException.class,()->store().inspect(file));assertThrows(IOException.class,()->store().install(file));
    }
    @Test void schemaAndDefaultMismatchAreRejectedBeforeReviewOrInstallation() throws Exception {
        for (String schema : List.of("{\"type\":\"invalid\"}","{\"$ref\":\"https://example.invalid/schema\"}",
                "{\"type\":\"object\",\"required\":[\"missing\"]}")) {
            var entries=entries("test.schema","1.0.0","EXAMPLE");entries.put("schema/question.json",schema);
            var store=store();var file=zip(entries);
            var failure=assertThrows(IOException.class,()->store.inspect(file));assertTrue(failure.getMessage().contains("DATA_VALIDATION_FAILED"));
            assertThrows(IOException.class,()->store.install(file));assertTrue(store.listInstalled().isEmpty());
        }
    }
    @Test void sdkCompatibilityReportsUpgradeDirectionAndRejectsMalformedMinorVersions() throws Exception {
        var store=store();
        for(int minor:List.of(0,1)) {
            var entries=entries("test.example","1.0.0","EXAMPLE");
            entries.put("manifest.json",entries.get("manifest.json").replace("\"sdkApiMajor\":2","\"sdkApiMajor\":2,\"minSdkApiMinor\":"+minor));
            assertNotNull(store.inspect(zip(entries)));
        }
        for(var change:Map.of("\"sdkApiMajor\":3","请更新应用","\"sdkApiMajor\":1","请更新扩展","\"sdkApiMajor\":2,\"minSdkApiMinor\":2","SDK 2.2").entrySet()) {
            var entries=entries("test.example","1.0.0","EXAMPLE");
            entries.put("manifest.json",entries.get("manifest.json").replace("\"sdkApiMajor\":2",change.getKey()));
            var failure=assertThrows(IOException.class,()->store.inspect(zip(entries)));assertTrue(failure.getMessage().contains(change.getValue()),failure.getMessage());
        }
        for(String minor:List.of("-1","0.5","\"1\"")) {
            var entries=entries("test.example","1.0.0","EXAMPLE");
            entries.put("manifest.json",entries.get("manifest.json").replace("\"sdkApiMajor\":2","\"sdkApiMajor\":2,\"minSdkApiMinor\":"+minor));
            assertThrows(IOException.class,()->store.inspect(zip(entries)));
        }
    }
    @Test void refusesChangingBytesForAnInstalledVersion() throws Exception {
        var entries = entries("test.example","1.0.0","EXAMPLE"); var store = store(); store.install(zip(entries));
        entries.put("renderer.js","changed"); assertThrows(IOException.class,()->store.install(zip(entries)));
        assertEquals("/* renderer */",store.loadAssets(store.listInstalled().getFirst()).getFirst().rendererSource());
    }
    @Test void exposesSeparateMarkupScriptsAndPersistedDefaultQuestion() throws Exception {
        var entries=entries("test.html","2.0.0","HTML");var store=store();
        var asset=store.loadAssets(store.install(zip(entries))).getFirst();
        assertEquals(entries.get("editor.html"),asset.editorHtml());
        assertEquals(entries.get("practice.html"),asset.rendererHtml());
        assertEquals(entries.get("default.json"),asset.defaultQuestionSource());
        assertEquals(entries.get("rules.js"),asset.rulesSource());
    }
    @Test void rejectsOldPackagesMalformedTemplatesAndHtmlNetworkOrInlineExecution() throws Exception {
        var old=entries("test.old","1.0.0","OLD");
        old.put("manifest.json",old.get("manifest.json").replace("\"packageFormatVersion\":2","\"packageFormatVersion\":1"));
        assertThrows(IOException.class,()->store().install(zip(old)));
        var invalid=entries("test.invalid","2.0.0","INVALID");invalid.put("default.json","{}");
        assertThrows(IOException.class,()->store().install(zip(invalid)));
        for(String html:List.of("<script>alert(1)</script>","<img src=\"https://example.com/x\">","<button onclick=\"fetch()\">x</button>","<iframe></iframe>","<a href=\"https://example.com\">x</a>")){
            var unsafe=entries("test.unsafe","2.0.0","UNSAFE");unsafe.put("practice.html",html);
            assertThrows(IOException.class,()->store().install(zip(unsafe)));
        }
    }
    @Test void rejectsPathTraversalMissingAssetsAndReservedOrConflictingTypes() throws Exception {
        var store = store(); var malicious = entries("test.example","1.0.0","EXAMPLE"); malicious.put("../escaped.txt","bad");
        assertThrows(IOException.class,()->store.install(zip(malicious))); assertFalse(Files.exists(temp.resolve("escaped.txt")));
        var missing = entries("test.example","1.0.0","EXAMPLE"); missing.remove("rules.js"); assertThrows(IOException.class,()->store.install(zip(missing)));
        assertThrows(IOException.class,()->store.install(zip(entries("test.example","1.0.0","SINGLE_CHOICE"))));
        store.install(zip(entries("test.example","1.0.0","EXAMPLE")));
        assertThrows(IOException.class,()->store.install(zip(entries("test.other","1.0.0","EXAMPLE"))));
    }
    @Test void detectsChangedExtractedSourceAndPackageBytes() throws Exception {
        var store = store(); var installed = store.install(zip(entries("test.example","1.0.0","EXAMPLE")));
        Files.writeString(installed.directory().resolve("rules.js"),"changed"); assertThrows(IOException.class,store::listInstalled);
    }
    @Test void rejectsUnpackedZipBombBeforeInstalling() throws Exception {
        var entries = entries("test.example","1.0.0","EXAMPLE"); entries.put("bomb.js","x".repeat((int)ExtensionPackageStore.MAX_ENTRY_BYTES+1));
        assertThrows(IOException.class,()->store().install(zip(entries)));
        assertTrue(store().listInstalled().isEmpty());
    }
    @Test void damagedPackageDoesNotBlockUnrelatedInstallOrReleaseOwnedTypeAndCanBeRepaired() throws Exception {
        var store=store();var original=zip(entries("test.example","1.0.0","EXAMPLE"));
        var installed=store.install(original);Files.writeString(installed.directory().resolve("rules.js"),"damaged");
        store.install(zip(entries("test.other","1.0.0","OTHER")));
        assertEquals(1,store.scanInstalled().failures().size());
        assertThrows(IOException.class,()->store.install(zip(entries("test.takeover","1.0.0","EXAMPLE"))));
        assertEquals(installed,store.install(original));
        assertTrue(store.scanInstalled().failures().isEmpty());
        assertEquals(2,store.listInstalled().size());
    }
}
