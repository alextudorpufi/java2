package com.example.bonus;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/** Measures repeated scans after warming up both implementations in one JVM. */
public final class ScannerBenchmark {
    private static final String SAMPLE_PACKAGE = "com.example.bonus.samples";

    private ScannerBenchmark() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 3) {
            throw new IllegalArgumentException(
                    "Usage: ScannerBenchmark [warmupRuns] [measuredRuns] [classesRoot]");
        }
        int warmupRuns = args.length > 0 ? Integer.parseInt(args[0]) : 10;
        int measuredRuns = args.length > 1 ? Integer.parseInt(args[1]) : 30;
        Path classesRoot = Path.of(args.length > 2 ? args[2] : "target/classes");
        if (warmupRuns < 1 || measuredRuns < 2) {
            throw new IllegalArgumentException("Use at least 1 warm-up run and 2 measured runs.");
        }

        var reflection = new ReflectionScanner();
        var asm = new AsmScanner();
        Set<String> expected = ScannerCorrectness.expectedGeneratedComponents();
        Scan reflectionScan = () -> reflection.scan(classesRoot, SAMPLE_PACKAGE, false);
        Scan asmScan = () -> asm.scan(classesRoot, SAMPLE_PACKAGE);

        System.out.printf("Java %s; %s %s%n", System.getProperty("java.version"),
                System.getProperty("os.name"), System.getProperty("os.arch"));
        System.out.printf("Warming up: %d scans per approach...%n", warmupRuns);
        for (int run = 0; run < warmupRuns; run++) {
            // Alternate order so neither approach always goes first.
            Set<String> reflectionResult;
            Set<String> asmResult;
            if (run % 2 == 0) {
                reflectionResult = reflectionScan.run();
                asmResult = asmScan.run();
            } else {
                asmResult = asmScan.run();
                reflectionResult = reflectionScan.run();
            }
            ScannerCorrectness.verify(expected, reflectionResult, asmResult);
        }

        System.out.printf("Measuring: %d scans per approach...%n", measuredRuns);
        long[] reflectionTimes = new long[measuredRuns];
        long[] asmTimes = new long[measuredRuns];
        for (int run = 0; run < measuredRuns; run++) {
            TimedScan reflectionResult;
            TimedScan asmResult;
            if (run % 2 == 0) {
                reflectionResult = measure(reflectionScan);
                asmResult = measure(asmScan);
            } else {
                asmResult = measure(asmScan);
                reflectionResult = measure(reflectionScan);
            }
            reflectionTimes[run] = reflectionResult.nanoseconds();
            asmTimes[run] = asmResult.nanoseconds();
            // Validate and consume results outside the measured interval.
            ScannerCorrectness.verify(expected, reflectionResult.components(), asmResult.components());
        }

        Statistics reflectionStats = Statistics.from(reflectionTimes);
        Statistics asmStats = Statistics.from(asmTimes);
        System.out.println("Correctness passed for every run: 100 expected components.");
        System.out.printf("%-24s %12s %12s %12s %12s%n",
                "Approach", "Median (ms)", "Mean (ms)", "Min (ms)", "Max (ms)");
        printStatistics("Reflection (no init)", reflectionStats);
        printStatistics("ASM", asmStats);
        System.out.printf(Locale.ROOT, "Median ratio (reflection / ASM): %.3f%n",
                reflectionStats.median() / asmStats.median());
        System.out.println("Repeated-scan timings: reflection can reuse classes loaded during warm-up.");

        Path csv = Path.of("target/bonus-timings.csv");
        Files.createDirectories(csv.getParent());
        StringBuilder rows = new StringBuilder("run,first_scanner,reflection_ns,asm_ns\n");
        for (int run = 0; run < measuredRuns; run++) {
            rows.append(run + 1).append(',').append(run % 2 == 0 ? "reflection" : "asm")
                    .append(',').append(reflectionTimes[run]).append(',').append(asmTimes[run]).append('\n');
        }
        Files.writeString(csv, rows);
        System.out.println("Raw measured timings written to " + csv);
    }

    private static TimedScan measure(Scan scan) throws Exception {
        long start = System.nanoTime();
        Set<String> result = scan.run();
        long elapsed = System.nanoTime() - start;
        return new TimedScan(result, elapsed);
    }

    private static void printStatistics(String label, Statistics stats) {
        System.out.printf(Locale.ROOT, "%-24s %12.3f %12.3f %12.3f %12.3f%n", label,
                stats.median() / 1_000_000.0, stats.mean() / 1_000_000.0,
                stats.min() / 1_000_000.0, stats.max() / 1_000_000.0);
    }

    @FunctionalInterface
    private interface Scan {
        Set<String> run() throws Exception;
    }

    private record TimedScan(Set<String> components, long nanoseconds) {
    }

    record Statistics(double median, double mean, long min, long max) {
        static Statistics from(long[] times) {
            if (times.length == 0) {
                throw new IllegalArgumentException("At least one timing is required.");
            }
            long[] sorted = times.clone();
            Arrays.sort(sorted);
            int middle = sorted.length / 2;
            double median = sorted.length % 2 == 0
                    ? sorted[middle - 1] / 2.0 + sorted[middle] / 2.0
                    : sorted[middle];
            return new Statistics(median, Arrays.stream(sorted).average().orElseThrow(),
                    sorted[0], sorted[sorted.length - 1]);
        }
    }
}
