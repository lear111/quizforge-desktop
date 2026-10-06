package io.quizforge.core.question.model.extension;

import io.quizforge.core.practice.PracticePayload;
import io.quizforge.core.question.type.extension.ExtensionDataValidationException;
import io.quizforge.core.question.model.QuestionPayload;
import java.util.Map;

/** Opaque, immutable JSON data owned by an installed question extension. */
public record ExtensionPayload(Map<String, Object> data) implements QuestionPayload {
    public ExtensionPayload {
        data = freeze(data);
    }
    /** Validate and copy JSON data so models, templates and rule boundaries share immutable values. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> freeze(Map<String, Object> value) {
        requireJsonData(value);
        return (Map<String, Object>) new PracticePayload(value).value();
    }
    public static void requireJsonData(Map<String,Object> value) {
        check(value,"",0,java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()));
    }
    private static void check(Object value, String path, int depth, java.util.Set<Object> active) {
        if (depth > 32) throw ExtensionDataValidationException.at(path,"JSON data is too deeply nested");
        if (value instanceof Number number && !Double.isFinite(number.doubleValue()))
            throw ExtensionDataValidationException.at(path,"number must be finite");
        if (value instanceof Map<?,?> map) {
            if (!active.add(value)) throw ExtensionDataValidationException.at(path,"JSON data cannot be cyclic");
            for (var entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) throw ExtensionDataValidationException.at(path,"JSON keys must be strings");
                check(entry.getValue(),path+"/"+key.replace("~","~0").replace("/","~1"),depth+1,active);
            }
            active.remove(value);
        } else if (value instanceof java.util.List<?> list) {
            if (!active.add(value)) throw ExtensionDataValidationException.at(path,"JSON data cannot be cyclic");
            for (int index=0;index<list.size();index++) check(list.get(index),path+"/"+index,depth+1,active);
            active.remove(value);
        }
    }
}
