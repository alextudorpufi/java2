# Bonus experiment results

Generated: 2026-10-12T17:15:20+03:00

Both scanners discovered exactly the 100 expected annotated classes among 1,000 samples.
Correctness was checked separately and after every warm-up and measured scan.

| Approach | Median after warm-up (ms) | Median first scan (ms) | Sample classes loaded | Static initializer executed | Missing superclass dependency |
| --- | ---: | ---: | ---: | --- | --- |
| Reflection (`initialize=false`) | 7.066 | 115.283 | 1000 | No | `NoClassDefFoundError` |
| ASM | 25.898 | 65.807 | 0 | No | Discovers both fixture components |
| Reflection (`initialize=true`), initialization control | Not measured | Not measured | 1000 | Yes | Not tested in this mode |

## Measurement setup

- Warm-up: 20 scans per approach, followed by 50 measured scans per approach in one JVM. Scanner order alternates.
- First scan: 5 fresh JVM launches per approach, no warm-up, alternating launch order.
- Timings include scanner work and file discovery. First-scan timings also include scanner/library loading within the timed interval; JVM launch time is excluded.
- Class-loading logs are collected in separate JVMs, outside the timing measurements.
- Total logged class-load entries: reflection with initialization disabled 2071, reflection with initialization enabled 2071, ASM 1051. Totals include JVM and library classes; the table counts only the sample package.
- Static initialization uses a separate one-component fixture whose static block sets a system property. Each mode runs in a fresh JVM.
- Missing dependencies use two separate annotated fixtures, one extending `OptionalBase`. Both scanners pass with the dependency present; the superclass is then excluded from the runtime search path. Reflection is tested with initialization disabled.

## Interpretation

ASM discovers components without loading any candidate classes or executing their static initializers, and can inspect directly declared annotations despite a missing superclass dependency.
Reflection can avoid initialization, but still loads every candidate and requires its superclass to be available.
Warmed-up reflection reuses already loaded classes; ASM rereads files each scan. Keep those measurements separate from first-scan results and report the observed times without assuming either approach always wins.
A fresh JVM does not clear filesystem caches. Warm-up counts do not guarantee fully stable JVM performance.
ASM discovery does not make a class with missing dependencies usable or instantiable.

## Runtime and evidence

```text
openjdk version "25" 2025-09-16 LTS
OpenJDK Runtime Environment Temurin-25+36 (build 25+36-LTS)
OpenJDK 64-Bit Server VM Temurin-25+36 (build 25+36-LTS, mixed mode, sharing)
```

Raw CSVs, JVM logs, and experiment output: `target/bonus-summary-x2ufy31x`.
