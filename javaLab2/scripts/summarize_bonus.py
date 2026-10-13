#!/usr/bin/env python3
"""Run the bonus experiments and produce a Markdown report from measured results."""

import argparse
import csv
from datetime import datetime
import os
from pathlib import Path
import re
import statistics
import subprocess
import tempfile
from zoneinfo import ZoneInfo


ROOT = Path(__file__).resolve().parent.parent


def positive_int(value: str) -> int:
    number = int(value)
    if number < 1:
        raise argparse.ArgumentTypeError("must be positive")
    return number


def run(command: list[str], log: Path) -> str:
    result = subprocess.run(command, cwd=ROOT, capture_output=True, text=True)
    log.write_text(result.stdout + result.stderr, encoding="utf-8")
    if result.returncode:
        raise RuntimeError(f"Command failed. See {log}\n{result.stdout}{result.stderr}")
    return result.stdout


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--warmup-runs", type=positive_int, default=20)
    parser.add_argument("--measured-runs", type=positive_int, default=50)
    parser.add_argument("--first-scan-runs", type=positive_int, default=5)
    parser.add_argument("--offline", action="store_true", help="use cached Maven dependencies")
    parser.add_argument("--output", type=Path, default=Path("docs/bonus-results.md"))
    args = parser.parse_args()
    if args.measured_runs < 2:
        parser.error("--measured-runs must be at least 2")

    (ROOT / "target").mkdir(exist_ok=True)
    evidence = Path(tempfile.mkdtemp(prefix="bonus-summary-", dir=ROOT / "target"))
    print("Building the project...", flush=True)
    build = [str(ROOT / "mvnw")]
    if args.offline:
        build.append("-o")
    build.extend(["compile", "dependency:build-classpath",
                  "-Dmdep.outputFile=target/bonus-classpath.txt"])
    run(build, evidence / "build.log")
    dependencies = (ROOT / "target/bonus-classpath.txt").read_text().strip()
    classpath = str(ROOT / "target/classes") + os.pathsep + dependencies

    def java(class_name: str, arguments: list[str], log_name: str,
             options: list[str] | None = None) -> str:
        return run(["java", *(options or []), "-cp", classpath,
                    "com.example.bonus." + class_name, *arguments], evidence / log_name)

    run(["java", "-version"], evidence / "java-version.txt")
    print("Checking correctness and warmed-up timings...", flush=True)
    java("ScannerDemo", ["compare"], "correctness.txt")
    java("ScannerBenchmark", [str(args.warmup_runs), str(args.measured_runs)], "warm-timing.txt")
    warm_csv = evidence / "warm-timings.csv"
    warm_csv.write_bytes((ROOT / "target/bonus-timings.csv").read_bytes())
    with warm_csv.open() as stream:
        warm_rows = list(csv.DictReader(stream))
    if len(warm_rows) != args.measured_runs:
        raise RuntimeError("Unexpected number of measured timing rows")
    warm_medians = {
        mode: statistics.median(int(row[mode + "_ns"]) for row in warm_rows) / 1_000_000
        for mode in ["reflection", "asm"]
    }

    print("Measuring first scans in fresh JVMs...", flush=True)
    first_rows = []
    for iteration in range(1, args.first_scan_runs + 1):
        modes = ["reflection", "asm"] if iteration % 2 else ["asm", "reflection"]
        for mode in modes:
            output = java("FirstScanBenchmark", [mode], f"first-{iteration}-{mode}.txt")
            match = re.search(r"\((\d+) ns\); 100 components", output)
            if not match:
                raise RuntimeError(f"Unrecognized first-scan output: {output}")
            first_rows.append([iteration, mode, int(match[1])])
    with (evidence / "first-timings.csv").open("w", newline="") as stream:
        writer = csv.writer(stream)
        writer.writerow(["run", "scanner", "elapsed_ns"])
        writer.writerows(first_rows)
    first_medians = {
        mode: statistics.median(row[2] for row in first_rows if row[1] == mode) / 1_000_000
        for mode in ["reflection", "asm"]
    }

    print("Checking class loading, static initialization, and missing dependencies...", flush=True)
    loads = {}
    for mode in ["reflection", "reflection-no-init", "asm"]:
        loading_log = evidence / f"{mode}-load.log"
        java("ScannerDemo", [mode], f"{mode}-loading-output.txt",
             [f"-Xlog:class+load=info:file={loading_log}::filecount=0"])
        lines = [line for line in loading_log.read_text().splitlines() if "[class,load]" in line]
        sample_count = sum("com.example.bonus.samples." in line for line in lines)
        expected_count = 0 if mode == "asm" else 1000
        if sample_count != expected_count:
            raise RuntimeError(f"Unexpected sample class-load count for {mode}: {sample_count}")
        loads[mode] = (sample_count, len(lines))

    initialization = {}
    for mode in ["reflection", "reflection-no-init", "asm"]:
        output = java("StaticInitializationDemo", [mode], f"{mode}-initialization.txt")
        match = re.search(r"Static initialization executed: (true|false)", output)
        if not match:
            raise RuntimeError(f"Unrecognized initialization output: {output}")
        initialization[mode] = "Yes" if match[1] == "true" else "No"

    missing = java("MissingDependencyDemo", [], "missing-dependency.txt")
    if "Reflection failed with NoClassDefFoundError" not in missing or "Experiment passed." not in missing:
        raise RuntimeError("Missing-dependency experiment did not produce the expected results")

    timestamp = datetime.now(ZoneInfo("Europe/Bucharest")).isoformat(timespec="seconds")
    java_version = (evidence / "java-version.txt").read_text().strip()
    report = f"""# Bonus experiment results

Generated: {timestamp}

Both scanners discovered exactly the 100 expected annotated classes among 1,000 samples.
Correctness was checked separately and after every warm-up and measured scan.

| Approach | Median after warm-up (ms) | Median first scan (ms) | Sample classes loaded | Static initializer executed | Missing superclass dependency |
| --- | ---: | ---: | ---: | --- | --- |
| Reflection (`initialize=false`) | {warm_medians['reflection']:.3f} | {first_medians['reflection']:.3f} | {loads['reflection-no-init'][0]} | {initialization['reflection-no-init']} | `NoClassDefFoundError` |
| ASM | {warm_medians['asm']:.3f} | {first_medians['asm']:.3f} | {loads['asm'][0]} | {initialization['asm']} | Discovers both fixture components |
| Reflection (`initialize=true`), initialization control | Not measured | Not measured | {loads['reflection'][0]} | {initialization['reflection']} | Not tested in this mode |

## Measurement setup

- Warm-up: {args.warmup_runs} scans per approach, followed by {args.measured_runs} measured scans per approach in one JVM. Scanner order alternates.
- First scan: {args.first_scan_runs} fresh JVM launches per approach, no warm-up, alternating launch order.
- Timings include scanner work and file discovery. First-scan timings also include scanner/library loading within the timed interval; JVM launch time is excluded.
- Class-loading logs are collected in separate JVMs, outside the timing measurements.
- Total logged class-load entries: reflection with initialization disabled {loads['reflection-no-init'][1]}, reflection with initialization enabled {loads['reflection'][1]}, ASM {loads['asm'][1]}. Totals include JVM and library classes; the table counts only the sample package.
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
{java_version}
```

Raw CSVs, JVM logs, and experiment output: `{evidence.relative_to(ROOT)}`.
"""
    destination = args.output if args.output.is_absolute() else ROOT / args.output
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(report, encoding="utf-8")
    print(f"Summary written to {destination}\nEvidence saved to {evidence}", flush=True)


if __name__ == "__main__":
    main()
