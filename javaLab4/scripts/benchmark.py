#!/usr/bin/env python3
"""Build JMH, then launch it with a real classpath inherited by its forked JVMs."""
from pathlib import Path
import os
import subprocess
import sys

root = Path(__file__).resolve().parents[1]
subprocess.run([
    str(root / "mvnw"), "-q", "-Pbenchmark", "-DskipTests", "test-compile",
    "dependency:build-classpath", "-DincludeScope=test",
    "-Dmdep.outputFile=target/benchmark-classpath.txt",
], cwd=root, check=True)
classpath = os.pathsep.join([
    str(root / "target/benchmark-build/test-classes"), str(root / "target/benchmark-build/classes"),
    (root / "target/benchmark-classpath.txt").read_text().strip(),
])
subprocess.run([
    "java", "-cp", classpath, "org.openjdk.jmh.Main", "DeliverySearchBenchmark",
    "-rf", "json", "-rff", "target/jmh-results.json", "-foe", "true", *sys.argv[1:],
], cwd=root, check=True)
