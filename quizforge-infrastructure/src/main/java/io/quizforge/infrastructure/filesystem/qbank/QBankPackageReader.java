package io.quizforge.infrastructure.filesystem.qbank;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quizforge.core.ErrorCode;
import io.quizforge.core.QuizForgeException;
import io.quizforge.core.question.model.QuestionBank;
import io.quizforge.core.question.resource.QBankResource;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.security.*;
import java.util.*;
import java.util.zip.*;
import static io.quizforge.infrastructure.filesystem.qbank.PackageJson.*;

/** ZIP-only reader. Inspection never opens resource streams; opening verifies every resource. */
public final class QBankPackageReader {
    private final PackageLimits limits;
    private final QuestionBankV2Codec codec;
    public QBankPackageReader() { this(PackageLimits.DEFAULT); }
    public QBankPackageReader(PackageLimits limits) {this(limits,CODEC);}
    private QBankPackageReader(PackageLimits limits,QuestionBankV2Codec codec){this.limits=Objects.requireNonNull(limits);this.codec=codec;}
    /** Keep ZIP/resource/reference validation, deferring type validation to the candidate package. */
    public static QBankPackageReader forExtensionExamples(){return new QBankPackageReader(PackageLimits.DEFAULT,QuestionBankV2Codec.forExtensionExamples());}

    public QuestionBank read(Path path) {
        try (LoadedPackage loaded = open(path)) { return loaded.bank(); }
    }
    public QuestionBank inspect(Path path) {
        try (LoadedPackage loaded = load(path, false)) { return loaded.bank(); }
    }
    public LoadedPackage open(Path path) { return load(path, true); }

    private LoadedPackage load(Path path, boolean verifyBytes) {
        ZipFile zip = null;
        try {
            preflight(path);
            zip = new ZipFile(path.toFile());
            Map<String,ZipEntry> entries = new HashMap<>();
            Set<String> logicalPaths = new HashSet<>();
            long declared = 0;
            var enumeration = zip.entries();
            while (enumeration.hasMoreElements()) {
                ZipEntry entry = enumeration.nextElement();
                safePath(entry.getName(), entry.isDirectory());
                String logical = entry.isDirectory() ? entry.getName().substring(0, entry.getName().length()-1) : entry.getName();
                if (!logicalPaths.add(logical)) throw error(ErrorCode.UNSAFE_ZIP_ENTRY, "Duplicate entry: " + logical);
                entries.put(entry.getName(), entry);
                if (entries.size() > limits.maxEntries()) limit("Too many ZIP entries");
                long maximum = maximum(entry.getName());
                if (entry.getSize() < 0 || entry.getSize() > maximum) limit("Entry size: " + entry.getName());
                if (entry.isDirectory() && entry.getSize() != 0) throw error(ErrorCode.INVALID_PACKAGE, "Nonempty directory entry");
                if (entry.getSize() > limits.totalBytes() - declared) limit("Total uncompressed size");
                declared += entry.getSize();
            }
            ZipEntry manifestEntry = required(entries, "manifest.json", ErrorCode.MISSING_MANIFEST);
            ZipEntry bankEntry = required(entries, "bank.json", ErrorCode.MISSING_BANK);
            Budget budget = new Budget();
            JsonNode manifest = json(zip, manifestEntry, limits.manifestBytes(), budget, ErrorCode.INVALID_MANIFEST);
            fields(manifest, Set.of("format", "schemaVersion", "assetId", "title", "resources"), ErrorCode.INVALID_MANIFEST);
            if (!FORMAT.equals(string(manifest, "format", ErrorCode.INVALID_MANIFEST))) throw error(ErrorCode.UNSUPPORTED_FORMAT, "Unknown format");
            if (!"2.0".equals(string(manifest, "schemaVersion", ErrorCode.INVALID_MANIFEST))) throw error(ErrorCode.UNSUPPORTED_SCHEMA_VERSION, "Only 2.0 is supported");
            if (!string(manifest, "assetId", ErrorCode.INVALID_MANIFEST).matches("qb_[A-Za-z0-9_-]+")) throw error(ErrorCode.INVALID_MANIFEST, "Invalid assetId");
            string(manifest, "title", ErrorCode.INVALID_MANIFEST);
            if (!manifest.path("resources").isArray()) throw error(ErrorCode.INVALID_MANIFEST, "resources must be an array");
            Set<String> ids = new HashSet<>(), paths = new HashSet<>();
            ObjectNode logical = JSON.createObjectNode();
            for (String name : new String[]{"schemaVersion", "assetId", "title"}) logical.set(name, manifest.get(name));
            var resources = logical.putArray("resources");
            for (JsonNode resource : manifest.get("resources")) {
                fields(resource, Set.of("id", "kind", "mediaType", "path", "sha256"), ErrorCode.INVALID_MANIFEST);
                String id = string(resource, "id", ErrorCode.INVALID_MANIFEST);
                String kind = string(resource, "kind", ErrorCode.INVALID_MANIFEST);
                String media = string(resource, "mediaType", ErrorCode.INVALID_MANIFEST);
                String location = string(resource, "path", ErrorCode.INVALID_MANIFEST);
                resourcePath(location);
                if (!id.matches("res_[A-Za-z0-9_-]+") || !ids.add(id) || !paths.add(location)
                        || !Set.of("IMAGE", "AUDIO", "DOCUMENT").contains(kind)
                        || !media.matches("[A-Za-z0-9.+-]+/[A-Za-z0-9.+-]+")
                        || !media.startsWith(kind.equals("IMAGE") ? "image/" : kind.equals("AUDIO") ? "audio/" : "application/")
                        || !string(resource, "sha256", ErrorCode.INVALID_MANIFEST).matches("[0-9a-f]{64}"))
                    throw error(ErrorCode.INVALID_MANIFEST, "Invalid or duplicate resource metadata");
                required(entries, location, ErrorCode.MISSING_RESOURCE);
                ObjectNode mapped = ((ObjectNode) resource).deepCopy();
                mapped.set("locator", mapped.remove("path"));
                resources.add(mapped);
            }
            for (ZipEntry entry : entries.values()) {
                String name = entry.getName();
                if (entry.isDirectory()) {
                    if (!name.startsWith("resources/")) throw error(ErrorCode.INVALID_PACKAGE, "Unexpected directory: " + name);
                } else if (!name.equals("manifest.json") && !name.equals("bank.json") && !paths.contains(name))
                    throw error(ErrorCode.INVALID_PACKAGE, "Unlisted entry: " + name);
            }
            JsonNode body = json(zip, bankEntry, limits.bankBytes(), budget, ErrorCode.INVALID_BANK);
            fields(body, Set.of("stimuli", "questions"), ErrorCode.INVALID_BANK);
            logical.set("stimuli", body.get("stimuli"));
            logical.set("questions", body.get("questions"));
            QuestionBank bank;
            try {
                String source = JSON.writeValueAsString(logical);
                bank = body.path("questions").isArray() && body.path("questions").isEmpty()
                        ? codec.parseEmptyDraft(source) : codec.parse(source);
            } catch (QuizForgeException e) { throw new QuizForgeException(ErrorCode.INVALID_BANK, "INVALID_BANK: " + e.getMessage(), e); }
            if (verifyBytes) for (QBankResource resource : bank.resources()) {
                MessageDigest hash = sha256();
                try (InputStream input = zip.getInputStream(entries.get(resource.locator()))) {
                    transfer(input, OutputStream.nullOutputStream(), limits.resourceBytes(), budget, hash);
                }
                if (!HexFormat.of().formatHex(hash.digest()).equals(resource.sha256()))
                    throw error(ErrorCode.RESOURCE_HASH_MISMATCH, resource.id());
            }
            return new LoadedPackage(zip, bank, limits.resourceBytes());
        } catch (IOException e) { closeOnFailure(zip, e); throw new QuizForgeException(ErrorCode.INVALID_PACKAGE, "INVALID_PACKAGE: Could not read ZIP package", e); }
        catch (RuntimeException e) { closeOnFailure(zip, e); throw e; }
    }

    private long maximum(String name) {
        return name.equals("manifest.json") ? limits.manifestBytes() : name.equals("bank.json") ? limits.bankBytes() : limits.resourceBytes();
    }

    private void preflight(Path path) throws IOException {
        // The EOCD (including its optional comment) is at most 65,557 bytes.
        try (RandomAccessFile input = new RandomAccessFile(path.toFile(), "r")) {
            long length = input.length();
            int tailLength = (int)Math.min(length, 65_557L);
            byte[] tail = new byte[tailLength]; input.seek(length-tailLength); input.readFully(tail);
            ByteBuffer data = ByteBuffer.wrap(tail).order(ByteOrder.LITTLE_ENDIAN);
            int end = -1;
            for (int i=tailLength-22; i>=0; i--) {
                if (data.getInt(i)==0x06054b50 && i+22+Short.toUnsignedInt(data.getShort(i+20))==tailLength) {end=i;break;}
            }
            if (end < 0) throw error(ErrorCode.INVALID_PACKAGE, "ZIP end record missing");
            if (data.getShort(end+4)!=0 || data.getShort(end+6)!=0)
                throw error(ErrorCode.INVALID_PACKAGE, "Split ZIP packages are unsupported");
            long count = Short.toUnsignedInt(data.getShort(end+10));
            long directorySize = Integer.toUnsignedLong(data.getInt(end+12));
            if (count==65_535 || directorySize==0xffff_ffffL) {
                long locator = length-tailLength+end-20;
                if (locator < 0) throw error(ErrorCode.INVALID_PACKAGE, "ZIP64 locator missing");
                byte[] record = new byte[56];
                input.seek(locator); input.readFully(record,0,20);
                ByteBuffer header = ByteBuffer.wrap(record).order(ByteOrder.LITTLE_ENDIAN);
                if (header.getInt(0)!=0x07064b50 || header.getInt(4)!=0 || header.getInt(16)!=1)
                    throw error(ErrorCode.INVALID_PACKAGE, "Invalid ZIP64 locator");
                long offset = header.getLong(8);
                if (offset<0 || offset>length-56) throw error(ErrorCode.INVALID_PACKAGE, "Invalid ZIP64 record offset");
                input.seek(offset); input.readFully(record);
                if (header.getInt(0)!=0x06064b50 || header.getInt(16)!=0 || header.getInt(20)!=0)
                    throw error(ErrorCode.INVALID_PACKAGE, "Invalid ZIP64 end record");
                count=header.getLong(32); directorySize=header.getLong(40);
            }
            if (count<0 || directorySize<0 || directorySize>length) throw error(ErrorCode.INVALID_PACKAGE, "Invalid ZIP directory size");
            if (count>limits.maxEntries() || directorySize>limits.centralDirectoryBytes()) limit("ZIP directory or entry count");
        }
    }
    private ZipEntry required(Map<String,ZipEntry> entries, String name, ErrorCode code) {
        ZipEntry entry = entries.get(name);
        if (entry == null || entry.isDirectory()) throw error(code, name);
        return entry;
    }
    private JsonNode json(ZipFile zip, ZipEntry entry, long max, Budget budget, ErrorCode code) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (InputStream input = zip.getInputStream(entry)) { transfer(input, bytes, max, budget, null); }
        try {
            JsonNode node = JSON.readTree(bytes.toByteArray());
            if (node == null) throw error(code, "Empty JSON");
            return node;
        } catch (com.fasterxml.jackson.core.JacksonException e) { throw new QuizForgeException(code, code + ": Invalid JSON", e); }
    }
    private void transfer(InputStream in, OutputStream out, long max, Budget budget, MessageDigest hash) throws IOException {
        byte[] buffer = new byte[8192];
        long size = 0;
        for (int count; (count = in.read(buffer)) != -1;) {
            if (count > max - size || count > limits.totalBytes() - budget.bytes) limit("Actual uncompressed size");
            size += count; budget.bytes += count;
            if (hash != null) hash.update(buffer, 0, count);
            out.write(buffer, 0, count);
        }
    }
    static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static void limit(String detail) { throw error(ErrorCode.PACKAGE_LIMIT_EXCEEDED, detail); }
    private static void closeOnFailure(ZipFile zip, Throwable error) {
        if (zip != null) try { zip.close(); } catch (IOException closing) { error.addSuppressed(closing); }
    }
    private static final class Budget { long bytes; }

    /** Owns the open ZIP. Close it before replacing a file on Windows. */
    public static final class LoadedPackage implements AutoCloseable, ResourceContentProvider {
        private final ZipFile zip;
        private final QuestionBank bank;
        private final long maximum;
        private LoadedPackage(ZipFile zip, QuestionBank bank, long maximum) { this.zip = zip; this.bank = bank; this.maximum = maximum; }
        public QuestionBank bank() { return bank; }
        @Override public InputStream open(QBankResource resource) throws IOException {
            QBankResource existing = bank.resources().stream().filter(value -> value.id().equals(resource.id())
                    && value.locator().equals(resource.locator())).findFirst()
                    .orElseThrow(() -> error(ErrorCode.MISSING_RESOURCE, resource.id()));
            return new FilterInputStream(zip.getInputStream(zip.getEntry(existing.locator()))) {
                private long bytes;
                @Override public int read() throws IOException {
                    int value = super.read(); if (value != -1 && ++bytes > maximum) limit("Resource size"); return value;
                }
                @Override public int read(byte[] buffer, int offset, int length) throws IOException {
                    int count = in.read(buffer, offset, length);
                    if (count > 0 && (bytes += count) > maximum) limit("Resource size");
                    return count;
                }
            };
        }
        @Override public void close() {
            try { zip.close(); } catch (IOException e) { throw new QuizForgeException(ErrorCode.INVALID_PACKAGE, "Could not close package", e); }
        }
    }
}
