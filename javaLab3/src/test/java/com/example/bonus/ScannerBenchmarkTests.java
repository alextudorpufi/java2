package com.example.bonus;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ScannerBenchmarkTests {
    @Test
    void summarizesEvenNumberOfRunsWithoutReorderingRawTimings() {
        long[] timings = {40, 10, 30, 20};
        var summary = ScannerBenchmark.Statistics.from(timings);

        assertEquals(25.0, summary.median());
        assertEquals(25.0, summary.mean());
        assertEquals(10, summary.min());
        assertEquals(40, summary.max());
        assertArrayEquals(new long[]{40, 10, 30, 20}, timings);
    }

    @Test
    void medianResistsOneSlowOutlier() {
        var summary = ScannerBenchmark.Statistics.from(new long[]{1000, 10, 20});

        assertEquals(20.0, summary.median());
        assertEquals(1030.0 / 3, summary.mean());
    }
}
