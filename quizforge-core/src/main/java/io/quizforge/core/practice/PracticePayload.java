package io.quizforge.core.practice;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable structured snapshot/answer data. No dependency on a JSON library or QBank version. */
public record PracticePayload(Object value) {
    public PracticePayload {
        value = freeze(Objects.requireNonNull(value));
    }

    private static Object freeze(Object value) {
        if (value == null || value instanceof String || value instanceof Boolean) return value;
        if (value instanceof Enum<?> item) return item.name();
        if (value instanceof Number number) {
            // One numeric representation makes database round trips independent of Java number classes.
            return new BigDecimal(number.toString()).stripTrailingZeros();
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object item : list) copy.add(freeze(item));
            return Collections.unmodifiableList(copy);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (var entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new IllegalArgumentException("Practice payload keys must be strings.");
                }
                copy.put(key, freeze(entry.getValue()));
            }
            return Collections.unmodifiableMap(copy);
        }
        throw new IllegalArgumentException("Unsupported practice payload value: " + value.getClass().getName());
    }
}
