package io.quizforge.infrastructure.extension;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.*;
import io.quizforge.core.question.type.extension.ExtensionDataValidationException;
import io.quizforge.core.question.type.extension.QuestionExtensionDataValidator;
import java.io.IOException;
import java.util.*;

/** Offline Draft-07 validation, executed by the host and never in the rules WebView. */
public final class ExtensionSchemaValidator implements QuestionExtensionDataValidator {
    private static final ObjectMapper JSON = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final SchemaValidatorsConfig CONFIG = SchemaValidatorsConfig.builder()
            .formatAssertionsEnabled(false).typeLoose(false).nullableKeywordEnabled(false).locale(Locale.ROOT).build();
    private static final JsonSchemaFactory FACTORY = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7,
            builder -> builder.schemaLoaders(loaders -> loaders.values(values -> {
                values.clear();
                values.add(iri -> { throw new IllegalArgumentException("External schema loading is disabled"); });
            })));
    // Fixed bundled meta-schema only; extension URLs never reach this factory.
    private static final JsonSchema META = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7)
            .getSchema(SchemaLocation.of("http://json-schema.org/draft-07/schema#"), CONFIG);
    private static final Set<String> KEYWORDS = Set.of("$schema", "$ref", "$comment", "title", "description", "default", "examples",
            "readOnly", "writeOnly", "type", "enum", "const", "multipleOf", "maximum", "exclusiveMaximum", "minimum",
            "exclusiveMinimum", "maxLength", "minLength", "pattern", "format", "items", "additionalItems", "maxItems",
            "minItems", "uniqueItems", "contains", "maxProperties", "minProperties", "required", "properties",
            "patternProperties", "additionalProperties", "dependencies", "propertyNames", "allOf", "anyOf", "oneOf",
            "not", "if", "then", "else", "definitions", "contentEncoding", "contentMediaType");
    private static final Set<String> MAPS = Set.of("properties", "patternProperties", "definitions", "dependencies");
    private static final Set<String> SINGLES = Set.of("additionalItems", "additionalProperties", "contains", "propertyNames", "not", "if", "then", "else");
    private static final Set<String> ARRAYS = Set.of("allOf", "anyOf", "oneOf");
    private static final Set<String> ANNOTATIONS = Set.of("$schema", "$ref", "$comment", "title", "description", "default", "examples", "readOnly", "writeOnly");
    private static final int MAX_SCHEMA_CHARACTERS = 256 * 1024;
    private static final int MAX_REFERENCE_EXPANSIONS = 16384;
    private final String type;
    private final JsonSchema question;
    private final JsonSchema answer;

    public ExtensionSchemaValidator(String type, String questionSource, String answerSource) {
        this.type = Objects.requireNonNull(type);
        this.question = compile(questionSource, "/schemas/question");
        this.answer = compile(answerSource, "/schemas/answer");
    }
    private static JsonSchema compile(String source, String path) {
        try {
            if (source == null || source.length() > MAX_SCHEMA_CHARACTERS)
                throw ExtensionDataValidationException.at(path, "schema source exceeds 256 KiB characters");
            JsonNode root = JSON.readTree(source);
            if (root == null || !root.isObject()) throw ExtensionDataValidationException.at(path, "schema must be a JSON object");
            var nodes = new LinkedHashMap<String, JsonNode>();
            var edges = new HashMap<String, List<String>>();
            collect(root, "", 0, nodes, edges, path);
            int patterns = 0;
            for (var entry : nodes.entrySet()) {
                JsonNode node = entry.getValue();
                if (node.has("pattern")) {
                    requireSafePattern(node.get("pattern"), path + entry.getKey() + "/pattern");
                    patterns++;
                }
                if (node.path("patternProperties").isObject()) {
                    for (var names = node.get("patternProperties").fieldNames(); names.hasNext();) {
                        String pattern = names.next();
                        requireSafePattern(JSON.getNodeFactory().textNode(pattern), path + entry.getKey() + "/patternProperties/" + escape(pattern));
                        patterns++;
                    }
                }
                if (patterns > 64) throw ExtensionDataValidationException.at(path, "schema may contain at most 64 patterns");
            }
            for (var entry : nodes.entrySet()) if (entry.getValue().has("$ref")) {
                var reference = entry.getValue().get("$ref");
                if (!reference.isTextual() || !reference.asText().startsWith("#/"))
                    throw ExtensionDataValidationException.at(path + entry.getKey() + "/$ref", "only local JSON pointer references are supported");
                String target = reference.asText().substring(1);
                if (!nodes.containsKey(target)) throw ExtensionDataValidationException.at(path + entry.getKey() + "/$ref", "reference must point to a declared schema");
                edges.get(entry.getKey()).add(target);
            }
            acyclic("", edges, new HashSet<>(), new HashSet<>(), 0, path);
            // Count repeated references too: a small acyclic DAG can unfold exponentially.
            requireBoundedExpansion("", edges, new int[1], 0, path);
            for (var node : nodes.values()) if (node.has("$schema")) ((com.fasterxml.jackson.databind.node.ObjectNode)node).put("$schema","http://json-schema.org/draft-07/schema#");
            requireValid(META, root, path);
            JsonSchema compiled = FACTORY.getSchema(root, CONFIG);
            compiled.initializeValidators();
            return compiled;
        } catch (ExtensionDataValidationException failure) { throw failure; }
        catch (IOException | RuntimeException failure) { throw ExtensionDataValidationException.at(path, "invalid schema: " + failure.getMessage()); }
    }
    private static void requireBoundedExpansion(String pointer, Map<String,List<String>> edges, int[] count, int depth, String path) {
        if (++count[0] > MAX_REFERENCE_EXPANSIONS || depth > 32)
            throw ExtensionDataValidationException.at(path + pointer, "schema reference expansion is too large or deeply nested");
        for (String target : edges.get(pointer)) requireBoundedExpansion(target, edges, count, depth + 1, path);
    }
    /** Conservative common JS/Java subset; native installation and saves must not run extension ReDoS patterns. */
    private static void requireSafePattern(JsonNode value, String path) {
        if (!value.isTextual()) throw ExtensionDataValidationException.at(path, "pattern must be text");
        String pattern = value.asText();
        if (pattern.length() > 256) throw ExtensionDataValidationException.at(path, "pattern exceeds 256 characters");
        boolean inClass = false;
        int quantifiers = 0;
        for (int index = 0; index < pattern.length(); index++) {
            char token = pattern.charAt(index);
            if (token == '\\') {
                if (++index >= pattern.length()) throw unsafePattern(path);
                char escaped = pattern.charAt(index);
                if ("dDsSwW\\.[]{}()*+?^$|-/".indexOf(escaped) < 0) throw unsafePattern(path);
                continue;
            }
            if (inClass) {
                if (token == '[' || token == '&') throw unsafePattern(path);
                if (token == ']') inClass = false;
                continue;
            }
            if (token == '[') { inClass = true; continue; }
            if (token == '(' || token == ')' || token == '|') throw unsafePattern(path);
            if (token == '*' || token == '+' || token == '?') quantifiers++;
            if (token == '{') {
                int end = pattern.indexOf('}', index + 1);
                if (end < 0) throw unsafePattern(path);
                String[] bounds = pattern.substring(index + 1, end).split(",", -1);
                if (bounds.length < 1 || bounds.length > 2) throw unsafePattern(path);
                for (int bound = 0; bound < bounds.length; bound++) {
                    String number = bounds[bound];
                    if (number.isEmpty() && bound == 1) continue;
                    if (number.isEmpty() || number.length() > 4 || !number.chars().allMatch(c -> c >= '0' && c <= '9')
                            || Integer.parseInt(number) > 1024) throw unsafePattern(path);
                }
                quantifiers++;
                index = end;
            }
            if (quantifiers > 1) throw unsafePattern(path);
        }
        int escapesBeforeEnd = 0;
        for (int index = pattern.length() - 2; index >= 0 && pattern.charAt(index) == '\\'; index--) escapesBeforeEnd++;
        // An unanchored single repetition can still force quadratic restart-and-scan behavior.
        if (inClass || (quantifiers > 0 && (!pattern.startsWith("^") || !pattern.endsWith("$") || escapesBeforeEnd % 2 != 0)))
            throw unsafePattern(path);
    }
    private static ExtensionDataValidationException unsafePattern(String path) {
        return ExtensionDataValidationException.at(path,
                "pattern must use the safe regex profile: no groups, alternation or complex escapes; at most one quantifier, anchored with ^ and $");
    }
    private static void collect(JsonNode node, String pointer, int depth, Map<String,JsonNode> nodes, Map<String,List<String>> edges, String path) {
        if (depth > 32 || nodes.size() >= 4096) throw ExtensionDataValidationException.at(path + pointer, "schema is too large or deeply nested");
        if (!node.isObject() && !node.isBoolean()) throw ExtensionDataValidationException.at(path + pointer, "must be a schema object or boolean");
        nodes.put(pointer, node); edges.put(pointer, new ArrayList<>());
        if (node.isBoolean()) return;
        for (var fields = node.fields(); fields.hasNext();) {
            var entry = fields.next(); String key = entry.getKey(); JsonNode child = entry.getValue();
            if (!KEYWORDS.contains(key)) throw ExtensionDataValidationException.at(path + pointer + "/" + escape(key), "unsupported schema keyword");
            if (key.equals("$schema") && (!child.isTextual() || !Set.of("http://json-schema.org/draft-07/schema#", "https://json-schema.org/draft-07/schema#").contains(child.asText())))
                throw ExtensionDataValidationException.at(path + pointer + "/$schema", "only Draft-07 is supported; omit $schema to use Draft-07");
            if (node.has("$ref") && !ANNOTATIONS.contains(key)) throw ExtensionDataValidationException.at(path + pointer, "$ref cannot have assertion siblings");
            if (MAPS.contains(key) && child.isObject()) {
                for (var fields2 = child.fields(); fields2.hasNext();) {
                    var sub = fields2.next();
                    if (!key.equals("dependencies") || !sub.getValue().isArray())
                        child(sub.getValue(), pointer + "/" + key + "/" + escape(sub.getKey()), pointer, depth, nodes, edges, path);
                }
            } else if (ARRAYS.contains(key) && child.isArray() || key.equals("items") && child.isArray()) {
                for (int index = 0; index < child.size(); index++) child(child.get(index), pointer + "/" + key + "/" + index, pointer, depth, nodes, edges, path);
            } else if (SINGLES.contains(key) || key.equals("items")) child(child, pointer + "/" + key, pointer, depth, nodes, edges, path);
        }
    }
    private static void child(JsonNode node, String pointer, String parent, int depth, Map<String,JsonNode> nodes, Map<String,List<String>> edges, String path) {
        collect(node, pointer, depth + 1, nodes, edges, path); edges.get(parent).add(pointer);
    }
    private static void acyclic(String pointer, Map<String,List<String>> edges, Set<String> active, Set<String> done, int depth, String path) {
        if (active.contains(pointer) || depth > 32) throw ExtensionDataValidationException.at(path + pointer, "cyclic or overly deep schema references are not supported");
        if (!done.add(pointer)) return;
        active.add(pointer);
        for (String target : edges.get(pointer)) acyclic(target, edges, active, done, depth + 1, path);
        active.remove(pointer);
    }
    private static String escape(String value) { return value.replace("~", "~0").replace("/", "~1"); }
    private static void requireValid(JsonSchema schema, JsonNode value, String path) {
        var issues = schema.validate(value).stream().map(error -> {
            String pointer = error.getInstanceLocation().toString();
            if (error.getProperty() != null && Set.of("required", "additionalProperties").contains(error.getType())) pointer += "/" + escape(error.getProperty());
            return new ExtensionDataValidationException.Issue(path + pointer, error.getMessage());
        }).sorted(Comparator.comparing(ExtensionDataValidationException.Issue::path)).limit(32).toList();
        if (!issues.isEmpty()) throw new ExtensionDataValidationException(issues);
    }
    @Override public void question(Map<String,Object> value) {
        io.quizforge.core.question.model.extension.ExtensionPayload.requireJsonData(value);
        for (String key : List.of("id", "type")) if (!(value.get(key) instanceof String text) || text.isBlank())
            throw ExtensionDataValidationException.at("/question/" + key, "must be nonempty text");
        if (!type.equals(value.get("type"))) throw ExtensionDataValidationException.at("/question/type", "does not match the registered type");
        for (String key : List.of("prompt", "payload", "answerSpec", "scoreSpec")) if (!(value.get(key) instanceof Map))
            throw ExtensionDataValidationException.at("/question/" + key, "must be an object");
        Object maximum = ((Map<?,?>) value.get("scoreSpec")).get("defaultMaxScore");
        if (!(maximum instanceof Number number) || !Double.isFinite(number.doubleValue()) || number.doubleValue() <= 0)
            throw ExtensionDataValidationException.at("/question/scoreSpec/defaultMaxScore", "must be positive and finite");
        requireValid(question, JSON.valueToTree(value), "/question");
    }
    @Override public void answer(Map<String,Object> value) {
        io.quizforge.core.question.model.extension.ExtensionPayload.requireJsonData(value);
        // {} is a reserved unanswered state, not a gradable extension answer.
        if (!value.isEmpty()) requireValid(answer, JSON.valueToTree(value), "/answer");
    }
}
