package io.quizforge.infrastructure.filesystem.qbank;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quizforge.core.ErrorCode;
import io.quizforge.core.QuizForgeException;
import io.quizforge.core.question.model.QuestionBank;
import java.util.Set;

final class PackageJson {
    static final String FORMAT = "quizforge-question-bank";
    static final ObjectMapper JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .configure(com.fasterxml.jackson.databind.cfg.JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES, false);
    static final QuestionBankV2Codec CODEC = new QuestionBankV2Codec();

    static ObjectNode manifest(QuestionBank bank) throws java.io.IOException {
        ObjectNode logical = (ObjectNode) JSON.readTree(CODEC.write(bank));
        ObjectNode manifest = JSON.createObjectNode();
        manifest.put("format", FORMAT);
        for (String name : new String[]{"schemaVersion", "assetId", "title"}) manifest.set(name, logical.get(name));
        var resources = manifest.putArray("resources");
        for (JsonNode resource : logical.get("resources")) {
            ObjectNode entry = ((ObjectNode) resource).deepCopy();
            entry.set("path", entry.remove("locator"));
            resources.add(entry);
        }
        return manifest;
    }

    static ObjectNode body(QuestionBank bank) throws java.io.IOException {
        ObjectNode logical = (ObjectNode) JSON.readTree(CODEC.write(bank));
        ObjectNode body = JSON.createObjectNode();
        body.set("stimuli", logical.get("stimuli"));
        body.set("questions", logical.get("questions"));
        return body;
    }

    static void fields(JsonNode object, Set<String> expected, ErrorCode code) {
        if (!object.isObject() || object.size() != expected.size()) throw error(code, "Unexpected or missing properties");
        object.fieldNames().forEachRemaining(name -> {
            if (!expected.contains(name)) throw error(code, "Unexpected property: " + name);
        });
    }
    static String string(JsonNode node, String name, ErrorCode code) {
        JsonNode value = node.get(name);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) throw error(code, "Required string: " + name);
        return value.textValue();
    }
    static QuizForgeException error(ErrorCode code, String detail) { return new QuizForgeException(code, code + ": " + detail); }
    static void safePath(String path, boolean directory) {
        String normalized = directory && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        if (normalized.isEmpty() || normalized.startsWith("/") || normalized.contains("\\") || normalized.contains(":" )
                || normalized.chars().anyMatch(Character::isISOControl))
            throw error(ErrorCode.UNSAFE_ZIP_ENTRY, "Unsafe ZIP path: " + path);
        for (String segment : normalized.split("/", -1))
            if (segment.isEmpty() || segment.equals(".") || segment.equals(".."))
                throw error(ErrorCode.UNSAFE_ZIP_ENTRY, "Noncanonical ZIP path: " + path);
    }
    static void resourcePath(String path) {
        safePath(path, false);
        if (!path.startsWith("resources/")) throw error(ErrorCode.UNSAFE_ZIP_ENTRY, "Resource must be under resources/: " + path);
    }
}
