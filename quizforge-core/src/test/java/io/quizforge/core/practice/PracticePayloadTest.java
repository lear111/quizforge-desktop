package io.quizforge.core.practice;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PracticePayloadTest {
    @Test void copiesNestedCollectionsSoSnapshotsCannotChangeAfterConstruction() {
        List<Object> values = new ArrayList<>(List.of("opt_a"));
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("selectedOptionIds", values);
        input.put("feedback", null);
        var payload = new PracticePayload(input);
        values.add("opt_b");
        input.put("text", "changed");
        Map<?, ?> frozen = (Map<?, ?>) payload.value();
        assertEquals(List.of("opt_a"), frozen.get("selectedOptionIds"));
        assertEquals(2, frozen.size());
        assertThrows(UnsupportedOperationException.class, () -> frozen.clear());
        assertThrows(UnsupportedOperationException.class, () -> ((List<?>) frozen.get("selectedOptionIds")).clear());
    }

    @Test void normalizesNumbersWithoutLosingDecimalPrecision() {
        assertEquals(new PracticePayload(List.of(2, 0.25)),
                new PracticePayload(List.of(2L, new BigDecimal("0.2500"))));
        assertEquals(new BigDecimal("0.12345678901234567890123456789"),
                new PracticePayload(new BigDecimal("0.12345678901234567890123456789")).value());
    }

    @Test void rejectsNonPortableValuesBeforeAnySerialization() {
        assertThrows(IllegalArgumentException.class, () -> new PracticePayload(Map.of(1, "invalid key")));
        assertThrows(IllegalArgumentException.class, () -> new PracticePayload(new Object()));
        assertThrows(IllegalArgumentException.class, () -> new PracticePayload(Double.NaN));
    }
}
