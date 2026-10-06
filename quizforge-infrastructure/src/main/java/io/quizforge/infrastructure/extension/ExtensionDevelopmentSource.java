package io.quizforge.infrastructure.extension;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Validated source-directory snapshot; never installs a package or mutates the production registry. */
public final class ExtensionDevelopmentSource {
    private final Path directory;
    private final ExtensionPackageStore validator;
    private final ObjectMapper json = new ObjectMapper();
    public record Candidate(ExtensionManifest manifest, String revision, String rulesRevision,
            List<ExtensionPackageStore.TypeAssets> assets, String bundleJson) {
        public Candidate { assets = List.copyOf(assets); }
    }
    public ExtensionDevelopmentSource(Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
        validator = new ExtensionPackageStore(this.directory, Set.of());
    }
    public Path directory() { return directory; }
    public Candidate load() throws IOException {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(directory)) throw new IOException("开发目录不存在或是符号链接");
        var manifest = validator.readSourceManifest(directory);
        List<ExtensionPackageStore.TypeAssets> assets = validator.loadAssets(directory, manifest);
        var bundle = new LinkedHashMap<String,Object>(); bundle.put("manifest", manifest);
        bundle.put("rulesSource", merge(assets.stream().map(ExtensionPackageStore.TypeAssets::rulesSource).distinct().toList()));
        bundle.put("editorSource", merge(assets.stream().map(ExtensionPackageStore.TypeAssets::editorSource).distinct().toList()));
        bundle.put("rendererSource", merge(assets.stream().map(ExtensionPackageStore.TypeAssets::rendererSource).distinct().toList()));
        bundle.put("stylesSource", String.join("\n", assets.stream().map(ExtensionPackageStore.TypeAssets::stylesSource).distinct().toList()));
        bundle.put("schemas", assets.stream().map(a -> Map.of("type",a.type().id(),"question",a.questionSchemaSource(),"answer",a.answerSchemaSource())).toList());
        var htmlAssets=new java.util.ArrayList<Map<String,Object>>();
        for(var asset:assets){
            var item=new LinkedHashMap<String,Object>();item.put("typeId",asset.type().id());
            item.put("editorHtml",asset.editorHtml());item.put("rendererHtml",asset.rendererHtml());
            item.put("editorSource",asset.editorSource());item.put("rendererSource",asset.rendererSource());
            item.put("rulesSource",asset.rulesSource());item.put("stylesSource",asset.stylesSource());
            item.put("defaultQuestion",json.readTree(asset.defaultQuestionSource()));
            item.put("questionSchemaSource",asset.questionSchemaSource());item.put("answerSchemaSource",asset.answerSchemaSource());
            htmlAssets.add(item);
        }
        bundle.put("assets",htmlAssets);
        // Manifest/schema/rules/definition changes reset test answers; view-only changes preserve them.
        Map<String,Object> logic = new LinkedHashMap<>(); logic.put("manifest", manifest);
        logic.put("rulesSource", bundle.get("rulesSource"));
        logic.put("schemas", bundle.get("schemas"));
        logic.put("defaults",assets.stream().map(ExtensionPackageStore.TypeAssets::defaultQuestionSource).toList());
        String rulesRevision = hash(json.writeValueAsBytes(logic));
        String revision = hash(json.writeValueAsBytes(bundle));
        bundle.put("revision", revision);
        return new Candidate(manifest, revision, rulesRevision, assets, json.writeValueAsString(bundle));
    }
    public static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException failure) { throw new IllegalStateException(failure); }
    }
    private static String merge(List<String> sources) { return String.join("\n;\n", sources); }
}
