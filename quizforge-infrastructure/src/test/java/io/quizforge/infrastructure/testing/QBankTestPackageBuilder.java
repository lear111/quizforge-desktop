package io.quizforge.infrastructure.testing;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quizforge.core.question.model.QuestionBank;
import io.quizforge.infrastructure.filesystem.qbank.QBankPackageReader;
import io.quizforge.infrastructure.filesystem.qbank.QBankPackageWriter;
import io.quizforge.infrastructure.filesystem.qbank.QuestionBankV2Codec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Shared isolated-file fixtures. Logical JSON is only a convenient input to this test builder. */
public final class QBankTestPackageBuilder {
    private static final ObjectMapper JSON = new ObjectMapper();
    private QBankTestPackageBuilder() { }
    public static Path write(Path path, String logicalJson, java.nio.charset.Charset... charset) throws IOException {
        if (!path.toString().toLowerCase(Locale.ROOT).endsWith(".qbank")) {
            return Files.writeString(path, logicalJson, charset.length == 0 ? StandardCharsets.UTF_8 : charset[0]);
        }
        QuestionBank bank;
        try {
            var codec = new QuestionBankV2Codec();
            JsonNode tree = JSON.readTree(logicalJson);
            bank = tree.path("questions").isArray() && tree.path("questions").isEmpty()
                    ? codec.parseEmptyDraft(logicalJson) : codec.parse(logicalJson);
        } catch (io.quizforge.core.QuizForgeException | com.fasterxml.jackson.core.JacksonException invalid) {
            // Deliberately invalid domain fixtures still have a ZIP container, never a JSON fallback.
            malformedLogical(path, logicalJson);
            return path;
        }
        new QBankPackageWriter().write(path, bank, resource -> new ByteArrayInputStream(new byte[]{0, 1, 2, 3}));
        return path;
    }
    public static String read(Path path) throws IOException {
        if (!path.toString().toLowerCase(Locale.ROOT).endsWith(".qbank")) return Files.readString(path);
        return new QuestionBankV2Codec().write(new QBankPackageReader().read(path));
    }
    public static Map<String,byte[]> entries(Path path) throws IOException {
        Map<String,byte[]> entries = new LinkedHashMap<>();
        try (ZipFile zip = new ZipFile(path.toFile())) {
            var values = zip.entries();
            while (values.hasMoreElements()) {
                ZipEntry entry = values.nextElement();
                try (InputStream input = zip.getInputStream(entry)) { entries.put(entry.getName(), input.readAllBytes()); }
            }
        }
        return entries;
    }
    public static void zip(Path path, Map<String,byte[]> entries) throws IOException { zip(path, entries, 0, 6); }
    public static void zip(Path path, Map<String,byte[]> entries, long timestamp, int compression) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
            zip.setLevel(compression);
            for (var entry : entries.entrySet()) {
                ZipEntry value = new ZipEntry(entry.getKey()); value.setTime(timestamp);
                zip.putNextEntry(value); zip.write(entry.getValue()); zip.closeEntry();
            }
        }
    }
    /** Corrupt central size declarations to prove readers also count actual inflated bytes. */
    public static void declaredSizes(Path path, Map<String,Integer> sizes) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        var buffer = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        for (int i=0; i<=bytes.length-46; i++) if (buffer.getInt(i)==0x02014b50) {
            int length = Short.toUnsignedInt(buffer.getShort(i+28));
            if (i+46+length > bytes.length) continue;
            String name = new String(bytes,i+46,length,StandardCharsets.UTF_8);
            Integer size = sizes.get(name); if (size != null) buffer.putInt(i+24,size);
        }
        Files.write(path,bytes);
    }
    private static void malformedLogical(Path path, String source) throws IOException {
        ObjectNode manifest = JSON.createObjectNode();
        manifest.put("format", "quizforge-question-bank"); manifest.put("schemaVersion", "2.0");
        manifest.put("assetId", "qb_invalid"); manifest.put("title", "Invalid fixture"); manifest.putArray("resources");
        byte[] body = source.getBytes(StandardCharsets.UTF_8);
        try {
            JsonNode logical = JSON.readTree(source);
            if (logical != null && logical.isObject()) {
                for (String name : new String[]{"schemaVersion", "assetId", "title", "resources"})
                    if (logical.has(name)) manifest.set(name, logical.get(name));
                if (logical.has("format")) manifest.set("format", logical.get("format"));
                for (JsonNode resource : manifest.path("resources")) {
                    if (resource instanceof ObjectNode object && object.has("locator")) object.set("path", object.remove("locator"));
                }
                ObjectNode bank = JSON.createObjectNode();
                bank.set("stimuli", logical.path("stimuli")); bank.set("questions", logical.path("questions"));
                body = JSON.writeValueAsBytes(bank);
            }
        } catch (com.fasterxml.jackson.core.JacksonException ignored) { /* Intentionally malformed bank.json. */ }
        Map<String,byte[]> entries = new LinkedHashMap<>();
        entries.put("manifest.json", JSON.writeValueAsBytes(manifest)); entries.put("bank.json", body);
        zip(path, entries);
    }
}
