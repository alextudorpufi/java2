# Bonus experiment: sample classes

`com.example.bonus.MyComponent` is a runtime-retained annotation that can be
applied to classes. It marks samples for the scanning experiment.

Generate or regenerate the sample sources from the project root:

```sh
python3 scripts/generate_bonus_classes.py
```

The script uses only the Python standard library and resolves the project path
from its own location, so it also works from another working directory.
It writes 1,000 classes, `Sample0001` through `Sample1000`, into
`src/main/java/com/example/bonus/samples`. Every tenth class (`Sample0010`,
`Sample0020`, ..., `Sample1000`) has `@MyComponent`, giving 100 annotated classes.
Rerunning the script overwrites these generated source files.

The annotation and samples are outside the application's `com.example.demo`
package. Maven compiles them with the other main sources; the resulting sample
class files are under `target/classes/com/example/bonus/samples`.

## Scanners

`ReflectionScanner` enumerates the compiled files, converts paths to class names,
loads each class with `Class.forName(name, initialize, loader)`, and checks
`isAnnotationPresent(MyComponent.class)`. Pass `true` to execute static
initializers or `false` to load classes without initializing them. Loading errors
are propagated, including errors caused by missing runtime dependencies.

`AsmScanner` reads those same files with ASM's `ClassReader`. Its `ClassVisitor`
checks runtime-visible class annotations for `Lcom/example/bonus/MyComponent;`.
Method bodies, debug information, and stack map frames are skipped. Candidate
classes are never loaded by this scanner. Both scanners return sets of fully
qualified class names and include subpackages. This implementation scans a
directory of compiled classes, not JAR contents.

Run `com.example.bonus.ScannerDemo` from your IDE with the project dependency
classpath, or build and run from the project root (macOS/Linux):

```sh
./mvnw compile dependency:build-classpath -Dmdep.outputFile=target/bonus-classpath.txt
java -cp "target/classes:$(cat target/bonus-classpath.txt)" com.example.bonus.ScannerDemo
```

The default `compare` mode uses `ScannerCorrectness` to verify that both scanners
return the same set and exactly the 100 expected generated components:
`Sample0010`, `Sample0020`, ..., `Sample1000`. It compares complete class names,
not just counts, and fails even if both scanners return the same incorrect set.
On failure, it reports missing and unexpected names for each scanner and exits
with an exception. On success, it prints `Correctness check passed`.
The expected set describes the original generated fixtures; update it if you
deliberately change which samples are annotated.

Arguments `reflection`,
`reflection-no-init`, and `asm` run only the selected scanner. An optional second
argument overrides the compiled classes root (default: `target/classes`).

For example, use fresh JVMs to compare candidate class loading:

```sh
java -Xlog:class+load=info:file=target/reflection-load.log -cp "target/classes:$(cat target/bonus-classpath.txt)" com.example.bonus.ScannerDemo reflection
java -Xlog:class+load=info:file=target/asm-load.log -cp "target/classes:$(cat target/bonus-classpath.txt)" com.example.bonus.ScannerDemo asm
rg -c 'com\.example\.bonus\.samples\.' target/reflection-load.log target/asm-load.log
```

Expect 1,000 sample class loads for reflection and no matching entries for ASM.
`rg` omits a file with zero matches. The reflection loader delegates to the
application loader first, so repeated scans may reuse already loaded classes;
use separate JVMs for first-scan comparisons.

Run the correctness test with `./mvnw -Dtest=ScannerTests test`.
Static-initializer behavior is tested by `StaticInitializationDemo` (see the
summary script below).

## Execution-time benchmark

Run `com.example.bonus.ScannerBenchmark` from your IDE without a debugger, or:

```sh
./mvnw compile dependency:build-classpath -Dmdep.outputFile=target/bonus-classpath.txt
java -cp "target/classes:$(cat target/bonus-classpath.txt)" com.example.bonus.ScannerBenchmark
```

Defaults are 10 warm-up scans and 30 measured scans **per approach**. To use
20 warm-up scans and 50 measured scans:

```sh
java -cp "target/classes:$(cat target/bonus-classpath.txt)" com.example.bonus.ScannerBenchmark 20 50
```

An optional third argument changes the compiled classes root. Warm-up scans
exercise class loading, library initialization, JVM compilation, and filesystem
caches before measurement. A fixed number of warm-ups does not guarantee that
the JVM has reached stable performance; increase it if timings keep changing.

Each measurement uses `System.nanoTime()` around one complete scanner call,
including file discovery and result collection. Reflection runs with static
initialization disabled. The benchmark alternates which scanner goes first,
checks the exact 100 expected components after each pair, and keeps correctness
checks, printing, statistics, and CSV writing outside the measured interval.

The table reports median, mean, minimum, and maximum milliseconds per scan.
The median is the middle duration (the average of the middle two for an even
number of runs); it is less affected by occasional slow runs than the mean.
The ratio is reflection median / ASM median: greater than 1 means ASM had the
lower median, less than 1 means reflection had the lower median.
Raw measured durations are saved to `target/bonus-timings.csv`, overwriting the
previous benchmark's CSV. Warm-up durations are not included.

This measures **repeated scanning in one JVM**. With the normal project
classpath, reflection reuses classes loaded during warm-up, whereas ASM rereads
each class file on every scan. Reflection can therefore be faster in this setup.
The result does not measure first-time class loading or establish which scanner
is faster in every situation. If you supply a classes root outside the runtime
classpath, the reflection loader may load fresh classes each scan, changing the
experiment. Use the default root and classpath for the repeated-scan comparison.

Run the benchmark without class-loading logs, a debugger, or concurrent builds.
Repeat it in fresh JVMs to see how stable the results are; record Java version,
machine, warm-up count, measured count, and whether classes were already loaded
alongside the table. Use the separate class-loading experiment above to report
the benefit of ASM avoiding candidate class loads.

Run scanner and timing-summary tests with:

```sh
./mvnw -Dtest=ScannerTests,ScannerBenchmarkTests test
```

### Recorded example results

On October 12, 2026, using Java 25 on macOS aarch64, three separate JVM runs
with 20 warm-ups and 50 measured scans per approach produced these medians:

| JVM run | Reflection, no initialization (ms) | ASM (ms) |
| --- | ---: | ---: |
| 1 | 9.791 | 28.744 |
| 2 | 10.086 | 29.265 |
| 3 | 9.873 | 28.596 |

Every warm-up and measured scan returned the exact expected component set.
Reflection's median was roughly 2.9 times faster in this repeated-scan setup.
It reused loaded class metadata; ASM reopened and parsed 1,000 class files on
each scan. These results support that specific observation and do not negate
ASM's ability to avoid loading candidate classes during discovery.

The local run summaries are `target/bonus-benchmark-run-1.txt` through
`target/bonus-benchmark-run-3.txt`, with corresponding raw measurements in
`target/bonus-timings-run-1.csv` through `target/bonus-timings-run-3.csv`.
These are build-output artifacts and are not tracked by Git.

## First-scan timing without warm-up

`FirstScanBenchmark` times exactly one scan of one approach. Launch it in a fresh
JVM for every measurement so sample classes have not been loaded by an earlier
scan. In your IDE, run its `main` with program argument `reflection`, then start
a new run with argument `asm`. From the project root:

```sh
./mvnw compile dependency:build-classpath -Dmdep.outputFile=target/bonus-classpath.txt
java -cp "target/classes:$(cat target/bonus-classpath.txt)" com.example.bonus.FirstScanBenchmark reflection
java -cp "target/classes:$(cat target/bonus-classpath.txt)" com.example.bonus.FirstScanBenchmark asm
```

Repeat these launches several times, alternating which approach goes first,
then compare the median first-scan times. Do not run a correctness scan first
inside the same process; this runner checks the expected set after measurement.
It includes scanner construction, scanner/library class loading within the timed
interval, file discovery, and annotation discovery. Reflection avoids static
initialization of candidates. JVM process startup is outside the timer.

A fresh JVM does not clear operating-system filesystem caches, so describe this
as a first scan with no JVM warm-up, not a guaranteed cold-disk measurement.
Keep these results separate from the warmed-up repeated-scan benchmark. The
assignment still asks for the warmed-up measurements; this is an additional
startup-oriented experiment. Report whichever approach is faster as measured.

## Missing runtime dependency

Run `MissingDependencyDemo.main()` in IntelliJ with no program arguments. Set
the working directory to this project root and select a JDK 21 or newer. The
runner compiles its isolated fixtures automatically. From a terminal:

```sh
./mvnw compile dependency:build-classpath -Dmdep.outputFile=target/bonus-classpath.txt
java -cp "target/classes:$(cat target/bonus-classpath.txt)" com.example.bonus.MissingDependencyDemo
```

The sources are under `fixtures/bonus-missing-dependency`, outside Maven's main
and test source directories. Do not mark that directory as a source root or add
the experiment's compiled fixture directories to the application classpath.

`BrokenComponent` has `@MyComponent` and extends `OptionalBase`.
`HealthyComponent` has the annotation and no optional dependency. The runner:

1. Compiles all three classes into a fresh `target/missing-dependency-*/with-dependency`
   directory and verifies that both scanners find the two components.
2. Copies only the two candidate class files into `without-dependency`, leaving
   `OptionalBase.class` out of this runtime search path.
3. Scans that directory: reflection fails with `NoClassDefFoundError` naming
   `OptionalBase`, while ASM discovers both annotated components.

The reflection scanner creates a loader for the selected directory. Once that
loader is closed, the baseline directory is not used by the next scan. The runner
also checks that its application loader cannot supply `OptionalBase`, ensuring
the dependency really is unavailable in the missing-dependency scenario.

Using a superclass makes the failure reliable: loading the candidate requires
loading its superclass, even with static initialization disabled. ASM only reads
the directly declared annotation from the file and does not resolve that
superclass. Discovering the broken component does not mean it can be instantiated;
actual use still requires the dependency.

| Dependency | Reflection | ASM |
| --- | --- | --- |
| Available | Finds both components | Finds both components |
| Missing | Fails with `NoClassDefFoundError` | Finds both components |

Run this experiment's automated test with:

```sh
./mvnw -Dtest=MissingDependencyTests test
```

## Generate the complete results summary

Run from the project root:

```sh
python3 scripts/summarize_bonus.py
```

The script builds the project, checks correctness, runs both timing experiments,
collects JVM class-loading logs, checks static initialization in three fresh
JVMs, and runs the missing-dependency experiment. It writes a measured summary
table to `docs/bonus-results.md`, replacing that report on successful completion.
Each execution keeps its raw evidence in a fresh `target/bonus-summary-*`
directory. The script works from other working directories as well.

Defaults are 20 warm-ups, 50 measured scans, and 5 first-scan launches per
approach. To customize them or use cached Maven dependencies:

```sh
python3 scripts/summarize_bonus.py --offline --warmup-runs 10 --measured-runs 30 --first-scan-runs 3
```

Use `--output path/to/report.md` to change the report destination. It requires
Python 3.10+ and a JDK 21+ available as `java` (also used by Maven).

For a direct static-initialization demonstration in IntelliJ, run
`StaticInitializationDemo.main()` with `reflection`, `reflection-no-init`, or
`asm` as its program argument, with the project root as working directory.
`StaticComponent` sets a system property in its static block. The runner checks
the property after scanning: true for initializing reflection, false for
non-initializing reflection and ASM. Each runner invocation must use a fresh JVM.
