package io.quizforge.infrastructure.extension;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Local immutable package store. Different versions coexist; reinstalling different bytes is refused. */
public final class ExtensionPackageStore {
    public static final long MAX_PACKAGE_BYTES = 32L * 1024 * 1024;
    public static final long MAX_UNPACKED_BYTES = 64L * 1024 * 1024;
    public static final long MAX_ENTRY_BYTES = 8L * 1024 * 1024;
    private static final int MAX_ENTRIES = 1024;
    private final Path root;
    private final Set<String> reservedTypeIds;
    private final ObjectMapper json = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .disable(com.fasterxml.jackson.databind.MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public record InstalledExtension(ExtensionManifest manifest, String sha256, Path directory) { }
    public record PackageInspection(ExtensionManifest manifest,String sha256) { }
    public record ScanResult(List<InstalledExtension> installed, List<String> failures) {
        public ScanResult { installed = List.copyOf(installed); failures = List.copyOf(failures); }
    }
    public record TypeAssets(ExtensionManifest.Type type, String rulesSource, String editorSource,
            String rendererSource, String stylesSource, String questionSchemaSource, String answerSchemaSource,
            String editorHtml, String rendererHtml, String defaultQuestionSource) { }
    public ExtensionPackageStore(Path root, Set<String> reservedTypeIds) {
        this.root = root.toAbsolutePath().normalize(); this.reservedTypeIds = Set.copyOf(reservedTypeIds);
    }
    public Path root() { return root; }
    /** Validate without installing or executing a package, for the host's permission review. */
    public PackageInspection inspect(Path source) throws IOException {
        if(!Files.isRegularFile(source)||Files.size(source)>MAX_PACKAGE_BYTES)throw new IOException("Extension package is missing or too large");
        Files.createDirectories(root);
        Path stage=Files.createTempDirectory(root,".install-review-");
        try {
            byte[] bytes=Files.readAllBytes(source);if(bytes.length>MAX_PACKAGE_BYTES)throw new IOException("Extension package exceeds size limit");
            unpack(bytes,stage);var manifest=manifest(stage);validateAssets(stage,manifest);
            return new PackageInspection(manifest,sha256(bytes));
        } finally {deleteStage(stage);}
    }
    public InstalledExtension install(Path source) throws IOException {
        return install(source,null);
    }
    public InstalledExtension install(Path source,String reviewedHash) throws IOException {
        if (!Files.isRegularFile(source) || Files.size(source) > MAX_PACKAGE_BYTES)
            throw new IOException("Extension package is missing or exceeds 32 MiB");
        Files.createDirectories(root);
        Path stage = Files.createTempDirectory(root, ".install-");
        try {
            byte[] bytes = Files.readAllBytes(source);
            if (bytes.length > MAX_PACKAGE_BYTES) throw new IOException("Extension package exceeds 32 MiB");
            String hash = sha256(bytes);
            if(reviewedHash!=null&&!reviewedHash.equals(hash))throw new IOException("权限确认后扩展包内容已变化，请重新选择并确认");
            unpack(bytes, stage);
            ExtensionManifest manifest = manifest(stage);
            validateAssets(stage, manifest);
            // No extension may claim another publisher's already installed type IDs.
            for (ExtensionManifest existing : installedClaims()) if (!existing.id().equals(manifest.id()))
                for (var candidate : manifest.types()) for (var claimed : existing.types())
                    if (candidate.id().equals(claimed.id())) throw new IOException("Type ID already belongs to " + existing.id() + ": " + candidate.id());
            Path publisher = root.resolve(manifest.id());
            Files.createDirectories(publisher);
            if (Files.isSymbolicLink(publisher)) throw new IOException("Extension directory cannot be a symbolic link");
            Path destination = publisher.resolve(manifest.version());
            Path quarantine = null;
            if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
                try {
                    InstalledExtension existing = readInstalled(destination);
                    if (!existing.sha256().equals(hash)) throw new IOException("This extension version is already installed with different content");
                    return existing;
                } catch (IOException damaged) {
                    // Repair only from the exact previously installed bytes; retain the damaged directory for recovery.
                    Path original = destination.resolve(".package.qfext");
                    if (Files.isSymbolicLink(destination) || !Files.isRegularFile(original, LinkOption.NOFOLLOW_LINKS)
                            || Files.size(original) > MAX_PACKAGE_BYTES || !hash.equals(sha256(Files.readAllBytes(original)))) throw damaged;
                    quarantine = root.resolve(".quarantine-" + java.util.UUID.randomUUID());
                }
            }
            Files.write(stage.resolve(".package.qfext"), bytes);
            Files.writeString(stage.resolve(".package.sha256"), hash, StandardCharsets.US_ASCII);
            if (quarantine != null) Files.move(destination, quarantine, StandardCopyOption.ATOMIC_MOVE);
            try { Files.move(stage, destination, StandardCopyOption.ATOMIC_MOVE); }
            catch (IOException failure) {
                if (quarantine != null && !Files.exists(destination)) Files.move(quarantine, destination, StandardCopyOption.ATOMIC_MOVE);
                throw failure;
            }
            return new InstalledExtension(manifest, hash, destination);
        } finally { deleteStage(stage); }
    }
    public List<InstalledExtension> listInstalled() throws IOException {
        var scan = scanInstalled();
        if (!scan.failures().isEmpty()) throw new IOException(String.join("; ", scan.failures()));
        return scan.installed();
    }
    /** A corrupt source file must not block unrelated installs or relinquish its publisher's type IDs. */
    private List<ExtensionManifest> installedClaims() throws IOException {
        List<ExtensionManifest> claims = new ArrayList<>();
        try (var publishers = Files.list(root)) {
            for (var publisher : publishers.filter(p -> Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)
                    && !p.getFileName().toString().startsWith(".")).toList()) {
                try (var versions = Files.list(publisher)) {
                    for (var directory : versions.filter(p -> Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)).toList()) {
                        try { claims.add(manifest(directory)); }
                        catch (IOException damagedManifest) {
                            // Recover ownership from the archived manifest when only the extracted copy is corrupt.
                            Path archive = directory.resolve(".package.qfext");
                            if (!Files.isRegularFile(archive, LinkOption.NOFOLLOW_LINKS) || Files.size(archive) > MAX_PACKAGE_BYTES) continue;
                            try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive), StandardCharsets.UTF_8)) {
                                ZipEntry entry;
                                while ((entry = zip.getNextEntry()) != null) if ("manifest.json".equals(entry.getName())) {
                                    byte[] bytes = zip.readNBytes(256 * 1024 + 1);
                                    if (bytes.length <= 256 * 1024) claims.add(json.readValue(bytes, ExtensionManifest.class));
                                    break;
                                }
                            } catch (IOException ignored) { /* Startup scan reports the damaged package. */ }
                        }
                    }
                }
            }
        }
        return claims;
    }
    /** Isolate a damaged package at startup so other installed extensions remain available. */
    public ScanResult scanInstalled() throws IOException {
        if (!Files.exists(root)) return new ScanResult(List.of(), List.of());
        List<InstalledExtension> installed = new ArrayList<>(); List<String> failures = new ArrayList<>();
        try (var publishers = Files.list(root)) {
            for (Path publisher : publishers.filter(p -> Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)
                    && !p.getFileName().toString().startsWith(".")).toList()) {
                try (var versions = Files.list(publisher)) {
                    for (Path version : versions.filter(p -> Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)).toList()) {
                        try { installed.add(readInstalled(version)); }
                        catch (IOException failure) { failures.add(root.relativize(version) + ": " + failure.getMessage()); }
                    }
                }
            }
        }
        installed.sort(Comparator.comparing((InstalledExtension e) -> e.manifest().id()).thenComparing(e -> e.manifest().version()));
        return new ScanResult(installed, failures);
    }
    private InstalledExtension readInstalled(Path directory) throws IOException {
        if (Files.isSymbolicLink(directory)) throw new IOException("Extension directory cannot be a symbolic link");
        ExtensionManifest manifest = manifest(directory);
        String hash = Files.readString(directory.resolve(".package.sha256"), StandardCharsets.US_ASCII).strip();
        Path archive = directory.resolve(".package.qfext");
        if (Files.size(archive) > MAX_PACKAGE_BYTES || !hash.equals(sha256(Files.readAllBytes(archive))))
            throw new IOException("Installed extension package hash mismatch");
        verifyExtractedFiles(Files.readAllBytes(archive), directory);
        validateAssets(directory, manifest);
        return new InstalledExtension(manifest, hash, directory);
    }
    public List<TypeAssets> loadAssets(InstalledExtension extension) throws IOException {
        return loadAssets(extension.directory(),extension.manifest());
    }
    /** Source development shares package validation, but has no installation or immutable-archive requirement. */
    public ExtensionManifest readSourceManifest(Path directory) throws IOException {
        var normalized=directory.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalized,LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(normalized))
            throw new IOException("Extension source must be a regular directory");
        var manifest=manifest(normalized);
        validateAssets(normalized,manifest);
        Set<String> declared = new HashSet<>(); declared.add("manifest.json");
        for (var type : manifest.types()) {
            declared.add(type.questionSchema()); declared.add(type.answerSchema()); declared.add(type.rules());
            declared.add(type.editor()); declared.add(type.renderer()); declared.addAll(type.styles());
            declared.add(type.defaultQuestion()); declared.add(type.editorScript()); declared.add(type.rendererScript());
            for(var example:type.examples())declared.add(example.path());
        }
        if (declared.size() > MAX_ENTRIES) throw new IOException("Extension source has too many declared assets");
        long total = 0;
        for (String name : declared) {
            Path asset = safePath(normalized,name);
            total += Files.size(asset);
            if (total > MAX_UNPACKED_BYTES) throw new IOException("Extension source exceeds unpacked size limit");
        }
        return manifest;
    }
    public List<TypeAssets> loadAssets(Path directory, ExtensionManifest manifest) throws IOException {
        directory=directory.toAbsolutePath().normalize();
        List<TypeAssets> result = new ArrayList<>();
        Path assetDirectory=directory;
        for (var type : manifest.types()) result.add(new TypeAssets(type,
                text(directory, type.rules()), text(directory, type.editorScript()),
                text(directory, type.rendererScript()), type.styles().stream().map(path -> {
                    try { return text(assetDirectory, path); } catch (IOException ex) { throw new java.io.UncheckedIOException(ex); }
                }).collect(java.util.stream.Collectors.joining("\n")), text(directory, type.questionSchema()), text(directory, type.answerSchema()),
                text(directory,type.editor()), text(directory,type.renderer()), text(directory,type.defaultQuestion())));
        return List.copyOf(result);
    }
    private ExtensionManifest manifest(Path directory) throws IOException {
        Path file = safePath(directory, "manifest.json");
        if (!Files.isRegularFile(file) || Files.size(file) > 256 * 1024) throw new IOException("Extension manifest is missing or too large");
        ExtensionManifest manifest;
        try { manifest = json.readValue(Files.readString(file), ExtensionManifest.class); }
        catch (Exception ex) { throw new IOException("Invalid extension manifest", ex); }
        ExtensionCompatibility.requireSupported(manifest);
        if (manifest.id() == null || !manifest.id().matches("[a-z][a-z0-9]*(?:[.-][a-z0-9]+)*")
                || manifest.id().length() > 128 || manifest.name() == null || manifest.name().isBlank()
                || manifest.version() == null || !manifest.version().matches("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[A-Za-z0-9.-]+)?"))
            throw new IOException("Invalid extension ID, name or version");
        if (manifest.types().isEmpty() || manifest.types().size() > 64) throw new IOException("An extension must provide 1 to 64 types");
        Set<String> ids = new HashSet<>();
        for (var type : manifest.types()) {
            if (type.id() == null || !type.id().matches("[A-Za-z][A-Za-z0-9_.-]{0,127}") || !ids.add(type.id())
                    || reservedTypeIds.contains(type.id())) throw new IOException("Duplicate, reserved or invalid type ID: " + type.id());
            if (type.label() == null || type.label().isBlank() || type.dataVersion() < 1
                    || !("OBJECTIVE".equals(type.family()) || "SUBJECTIVE".equals(type.family()))) throw new IOException("Invalid type metadata: " + type.id());
            if(type.pageApi()!=null&&!"simple".equals(type.pageApi()))throw new IOException("Unknown page API");
            if("simple".equals(type.pageApi())&&manifest.minSdkApiMinor()<3)throw new IOException("Simple page API requires SDK 2.3");
            if(!java.util.Set.of("useDraft","card","initialLayout").containsAll(type.pageOptions().keySet()))throw new IOException("Unknown page options");
            for(var key:List.of("useDraft","card"))if(type.pageOptions().containsKey(key)&&!(type.pageOptions().get(key) instanceof Boolean))throw new IOException("Page option must be boolean: "+key);
            if(type.pageOptions().containsKey("initialLayout")&&!(type.pageOptions().get("initialLayout") instanceof Map))throw new IOException("initialLayout must be an object");
            if(type.examples().size()>16||"simple".equals(type.pageApi())&&type.examples().isEmpty())throw new IOException("Simple page types require 1 to 16 examples");
            var exampleIds=new HashSet<String>();
            for(var example:type.examples()){
                if(example.id()==null||!example.id().matches("[A-Za-z0-9_-]{1,80}")||!exampleIds.add(example.id())||example.title()==null||example.title().isBlank())throw new IOException("Invalid example metadata");
                if(example.path()==null||!example.path().endsWith(".qbank"))throw new IOException("Example must be a .qbank file");
            }
        }
        return manifest;
    }
    private void validateAssets(Path directory, ExtensionManifest manifest) throws IOException {
        for (var type : manifest.types()) {
            for (String asset : new String[]{type.questionSchema(), type.answerSchema(), type.rules(), type.editor(), type.renderer(),
                    type.defaultQuestion(),type.editorScript(),type.rendererScript()}) text(directory, asset);
            for (String asset : type.styles()) text(directory, asset);
            validateHtml(text(directory,type.editor()), type.editor());
            validateHtml(text(directory,type.renderer()), type.renderer());
            try {
                if (!json.readTree(text(directory, type.questionSchema())).isObject()
                        || !json.readTree(text(directory, type.answerSchema())).isObject()) throw new IOException("Schemas must be JSON objects");
                var template=json.readTree(text(directory,type.defaultQuestion()));
                if (template == null || !template.isObject() || !type.id().equals(template.path("type").asText())
                        || template.path("id").asText().isBlank() || !template.path("prompt").isObject()
                        || !template.path("payload").isObject() || !template.path("answerSpec").isObject()
                        || !template.path("analysis").isObject() || !template.path("scoreSpec").isObject())
                    throw new IOException("defaultQuestion must be a complete persisted question matching its type");
                var validator = new ExtensionSchemaValidator(type.id(), text(directory,type.questionSchema()), text(directory,type.answerSchema()));
                validator.question(json.convertValue(template, new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String,Object>>() { }));
                for(var example:type.examples()){
                    Path file=safePath(directory,example.path());
                    if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)>MAX_ENTRY_BYTES||!file.toRealPath().startsWith(directory.toRealPath()))throw new IOException("Missing or oversized example");
                    var bank=io.quizforge.infrastructure.filesystem.qbank.QBankPackageReader.forExtensionExamples().read(file);
                    if(bank.questions().isEmpty()||bank.questions().stream().anyMatch(q->!type.id().equals(q.type())))throw new IOException("Example must contain only its declared question type");
                    for(var question:bank.questions())validator.question(io.quizforge.core.question.codec.QuestionDataCodec.encodePersisted(question));
                }
            } catch (Exception ex) { throw new IOException("Invalid extension JSON schema or default question: " + ex.getMessage(), ex); }
        }
    }
    /** Pages are local fragments; scripts/styles are declared assets and executed by the scoped SDK. */
    private static void validateHtml(String source, String path) throws IOException {
        String normalized = source.toLowerCase(java.util.Locale.ROOT);
        if (!path.endsWith(".html") || java.util.regex.Pattern.compile("<\\s*(script|iframe|frame|object|embed|base|link|meta)\\b").matcher(normalized).find()
                || java.util.regex.Pattern.compile("\\s(?:on[a-z]+|src|srcset|action|formaction|href)\\s*=").matcher(normalized).find())
            throw new IOException("HTML pages must use local markup without scripts, remote resources, navigation or inline handlers: " + path);
    }
    private static String text(Path directory, String relative) throws IOException {
        Path file = safePath(directory, relative);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > MAX_ENTRY_BYTES) throw new IOException("Missing or oversized extension asset: " + relative);
        // Do not follow a symlink introduced after installation, including parent components.
        if (!file.toRealPath().startsWith(directory.toRealPath())) throw new IOException("Extension asset escapes package");
        return Files.readString(file, StandardCharsets.UTF_8);
    }
    private static Path safePath(Path directory, String name) throws IOException {
        if (name == null || name.isBlank() || name.startsWith("/") || name.contains("\\") || name.contains(":")) throw new IOException("Unsafe extension asset path");
        for (String segment : name.split("/", -1)) if (segment.equals("..") || segment.equals(".") || segment.isEmpty() || segment.startsWith(".package")
                || segment.endsWith(".") || segment.endsWith(" ") || segment.matches(".*[<>|\"*?\\x00-\\x1f].*")) throw new IOException("Unsafe extension asset path");
        Path target;
        try { target = directory.resolve(name).normalize(); }
        catch (java.nio.file.InvalidPathException failure) { throw new IOException("Unsafe extension asset path", failure); }
        if (!target.startsWith(directory)) throw new IOException("Extension asset escapes package");
        return target;
    }
    private static void unpack(byte[] archive, Path directory) throws IOException {
        Set<String> names = new HashSet<>(); long total = 0; int count = 0;
        try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(archive), StandardCharsets.UTF_8)) {
            ZipEntry entry; byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                if (++count > MAX_ENTRIES) throw new IOException("Too many extension package entries");
                String name = entry.getName();
                if (entry.isDirectory()) name = name.substring(0, name.length() - 1);
                Path target = safePath(directory, name);
                if (!names.add(name.toLowerCase(java.util.Locale.ROOT))) throw new IOException("Duplicate extension package entry");
                if (entry.isDirectory()) { Files.createDirectories(target); continue; }
                Files.createDirectories(target.getParent()); long size = 0;
                try (var output = Files.newOutputStream(target)) {
                    int read;
                    while ((read = zip.read(buffer)) != -1) {
                        size += read; total += read;
                        if (size > MAX_ENTRY_BYTES || total > MAX_UNPACKED_BYTES) throw new IOException("Extension package exceeds unpacked size limit");
                        output.write(buffer, 0, read);
                    }
                }
            }
        }
    }
    private static void verifyExtractedFiles(byte[] archive, Path directory) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(archive), StandardCharsets.UTF_8)) {
            ZipEntry entry; int count = 0; long total = 0; byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                if (++count > MAX_ENTRIES) throw new IOException("Too many installed extension entries");
                if (entry.isDirectory()) continue;
                Path target = safePath(directory, entry.getName());
                if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS) || !target.toRealPath().startsWith(directory.toRealPath())) throw new IOException("Missing installed extension asset");
                var original = new java.io.ByteArrayOutputStream(); long size = 0; int read;
                while ((read = zip.read(buffer)) != -1) {
                    size += read; total += read;
                    if (size > MAX_ENTRY_BYTES || total > MAX_UNPACKED_BYTES) throw new IOException("Installed extension exceeds size limits");
                    original.write(buffer, 0, read);
                }
                if (Files.size(target) != size || !java.util.Arrays.equals(Files.readAllBytes(target), original.toByteArray())) throw new IOException("Installed extension asset hash mismatch: " + entry.getName());
            }
        }
    }
    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    private void deleteStage(Path stage) throws IOException {
        if (!Files.exists(stage)) return;
        if (!stage.toAbsolutePath().normalize().startsWith(root) || !stage.getFileName().toString().startsWith(".install-")) throw new IOException("Unsafe staging cleanup");
        try (var paths = Files.walk(stage)) { for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path); }
    }
}
