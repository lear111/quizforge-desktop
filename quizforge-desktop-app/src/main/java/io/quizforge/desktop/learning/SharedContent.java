package io.quizforge.desktop.learning;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quizforge.core.question.content.QuestionContentData;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/** Resolve only frozen, owned resource bytes; never read the current bank during replay. */
public final class SharedContent {
    private static final ObjectMapper JSON = io.quizforge.infrastructure.json.DocumentJson.mapper();
    private SharedContent() { }
    /** Display-only copy for extension-owned rich fields; persisted JSON remains opaque. */
    static Object resolveNested(Object value, Map<?,?> scope) {
        if (value instanceof Map<?,?> map) {
            if (isContent(map)) {
                var text = read(value, scope);
                var resolved = new LinkedHashMap<String,Object>();
                map.forEach((key,item) -> resolved.put((String)key,item));
                resolved.put("text",text.text());
                if (text.document()!=null) resolved.put("document",text.document());
                if (text.images()!=null) resolved.put("images",text.images());
                return resolved;
            }
            var resolved = new LinkedHashMap<String,Object>();
            map.forEach((key,item) -> resolved.put((String)key,resolveNested(item,scope)));
            return resolved;
        }
        if (value instanceof java.util.List<?> list) return list.stream().map(item -> resolveNested(item,scope)).toList();
        return value;
    }
    private static boolean isContent(Map<?,?> value) {
        return "TEXT".equals(value.get("kind")) && value.get("text") instanceof String
                || "RICH".equals(value.get("kind")) && value.get("document") instanceof Map<?,?>
                || "DOCUMENT".equals(value.get("kind")) && value.get("resourceId") instanceof String && value.get("text") instanceof String;
    }
    static Map<?,?> scope(String type, Map<?,?> metadata) {
        if (metadata.get("extensionPresentation") instanceof Map<?,?> extension) {
            var scope = new LinkedHashMap<Object,Object>();scope.putAll(extension);
            if (extension.get("question") instanceof Map<?,?> question) scope.putAll(question);
            return scope;
        }
        String family = switch(type) {
            case "ESSAY" -> "essay"; case "READING" -> "reading"; case "CLOZE" -> "cloze";
            case "MATCHING" -> "matching"; case "TRANSLATION" -> "translation"; default -> "choice";
        };
        if (metadata.get(family + "Presentation") instanceof Map<?,?> data) return data;
        if (metadata.get(family) instanceof Map<?,?> data) return data;
        return metadata;
    }
    public static SharedPracticeViewModel.Text read(Object value, Map<?,?> scope) {
        if (!(value instanceof Map<?,?> content)) throw new IllegalArgumentException("Missing content");
        String kind = (String)content.get("kind");
        if ("TEXT".equals(kind) && content.get("text") instanceof String text)
            return SharedPracticeViewModel.Text.of(text);
        if ("DOCUMENT".equals(kind)) {
            var data = scope.get("resourceData") instanceof Map<?,?> bytes ? bytes : Map.of();
            if (!(data.get(content.get("resourceId")) instanceof String encoded))
                throw new IllegalArgumentException("富文本文档资源缺失");
            return new SharedPracticeViewModel.Text(kind, (String)content.get("text"), parse(Base64.getDecoder().decode(encoded)), Map.of());
        }
        if ("RICH".equals(kind)) {
            var rich = QuestionContentData.decode(content);
            var imageIds = QuestionContentData.imageIds(rich);
            var images = new LinkedHashMap<String,String>();
            var bytes = scope.get("resourceData") instanceof Map<?,?> data ? data : Map.of();
            if (scope.get("resources") instanceof java.util.List<?> resources) for (var entry : resources) {
                var resource = (Map<?,?>)entry;
                if (imageIds.contains(resource.get("id"))) {
                    String mime = (String)resource.get("mediaType");
                    if (mime == null || !mime.startsWith("image/") || !(bytes.get(resource.get("id")) instanceof String encoded))
                        throw new IllegalArgumentException("图片资源缺失");
                    images.put((String)resource.get("id"), "data:" + mime + ";base64," + encoded);
                }
            }
            if (!images.keySet().containsAll(imageIds)) throw new IllegalArgumentException("图片资源缺失");
            return new SharedPracticeViewModel.Text(kind, QuestionContentData.plainText(rich), content.get("document"), images);
        }
        throw new IllegalArgumentException("Unsupported shared content: " + kind);
    }
    private static Object parse(byte[] bytes) {
        try {
            var data = JSON.readValue(bytes, Map.class);
            if (!(data.get("data") instanceof Map<?,?> body) || !(body.get("main") instanceof java.util.List<?>))
                throw new IllegalArgumentException("Invalid Canvas document");
            return data;
        } catch (java.io.IOException failure) { throw new IllegalArgumentException("无法读取富文本文档", failure); }
    }
}
