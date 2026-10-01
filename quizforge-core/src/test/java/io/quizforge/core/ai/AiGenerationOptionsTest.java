package io.quizforge.core.ai;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AiGenerationOptionsTest {
    @Test void nonFiniteTemperatureCannotReachTheProvider() {
        for(double value:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY})
            assertThrows(IllegalArgumentException.class,()->new AiGenerationOptions(value));
        assertEquals(0,new AiGenerationOptions(0).temperature());
        assertEquals(2,new AiGenerationOptions(2).temperature());
    }
}
