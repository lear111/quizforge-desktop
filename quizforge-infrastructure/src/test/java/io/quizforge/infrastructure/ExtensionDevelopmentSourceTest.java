package io.quizforge.infrastructure;

import io.quizforge.infrastructure.extension.ExtensionDevelopmentSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ExtensionDevelopmentSourceTest {
    @TempDir Path temp;
    private Path create() throws Exception {
        Files.writeString(temp.resolve("manifest.json"),"""
                {"packageFormatVersion":2,"sdkApiMajor":2,"id":"test.development","name":"Development","version":"2.0.0",
                "types":[{"id":"test.development.choice","label":"Choice","dataVersion":1,"family":"OBJECTIVE","capabilities":["editor","preview"],
                "questionSchema":"question.json","answerSchema":"answer.json","rules":"rules.js","editor":"editor.html","renderer":"practice.html",
                "defaultQuestion":"default.json","editorScript":"editor.js","rendererScript":"renderer.js","styles":"style.css"}]}
                """);
        for (String file : List.of("question.json","answer.json")) Files.writeString(temp.resolve(file),"{\"type\":\"object\"}");
        for (String file : List.of("rules.js","editor.js","renderer.js")) Files.writeString(temp.resolve(file),"/* "+file+" */");
        Files.writeString(temp.resolve("editor.html"),"<section><textarea></textarea></section>");
        Files.writeString(temp.resolve("practice.html"),"<section></section>");
        Files.writeString(temp.resolve("default.json"),"""
                {"id":"template","type":"test.development.choice","prompt":{"kind":"TEXT","text":"Question"},
                 "payload":{"kind":"CHOICE","options":[]},"answerSpec":{"kind":"CHOICE","correctOptionIds":[]},
                 "analysis":{"kind":"TEXT","text":""},"scoreSpec":{"defaultMaxScore":1}}
                """);
        Files.writeString(temp.resolve("style.css"),".test{color:red}"); return temp;
    }
    @Test void sourceSnapshotRequiresNoInstalledHashAndBundlesHtmlAndDefaults() throws Exception {
        var source = new ExtensionDevelopmentSource(create()); var candidate = source.load();
        assertEquals(candidate.revision(),source.load().revision());
        assertEquals(64,candidate.revision().length()); assertFalse(Files.exists(temp.resolve(".package.sha256")));
        for (var asset : candidate.assets()) {
            assertEquals("/* rules.js */",asset.rulesSource());
            assertTrue(asset.editorHtml().contains("textarea"));
            assertTrue(candidate.bundleJson().contains("defaultQuestion"));
        }
    }
    @Test void cssChangesRevisionAndRulesChangesAlsoInvalidatesTestAnswers() throws Exception {
        var source = new ExtensionDevelopmentSource(create()); var before = source.load();
        Files.writeString(temp.resolve("style.css"),".test{color:blue}"); var css = source.load();
        assertNotEquals(before.revision(),css.revision()); assertEquals(before.rulesRevision(),css.rulesRevision());
        Files.writeString(temp.resolve("rules.js"),"/* revised rules */"); var rules = source.load();
        assertNotEquals(css.revision(),rules.revision()); assertNotEquals(css.rulesRevision(),rules.rulesRevision());
        Files.writeString(temp.resolve("question.json"),"{\"type\":\"object\",\"required\":[\"prompt\"]}"); var schema = source.load();
        assertNotEquals(rules.revision(),schema.revision()); assertNotEquals(rules.rulesRevision(),schema.rulesRevision());
    }
    @Test void invalidAssetOrTraversalFailsThenRecoversWithRepairedSource() throws Exception {
        var source = new ExtensionDevelopmentSource(create()); var good = source.load();
        String manifest = Files.readString(temp.resolve("manifest.json"));
        Files.writeString(temp.resolve("manifest.json"),manifest.replace("renderer.js","../renderer.js"));
        assertThrows(java.io.IOException.class,source::load);
        Files.writeString(temp.resolve("manifest.json"),manifest); assertEquals(good.revision(),source.load().revision());
        Files.writeString(temp.resolve("question.json"),"broken schema"); assertThrows(java.io.IOException.class,source::load);
    }
}
