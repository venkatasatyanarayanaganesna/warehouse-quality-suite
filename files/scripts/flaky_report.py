#!/usr/bin/env python3
"""Compare several test runs and list tests whose outcome was not stable.

Usage: python3 scripts/flaky_report.py <folder-with-one-subfolder-per-run>

Each subfolder holds the surefire XML reports (TEST-*.xml) of one run.
A test is reported as unstable when
  * its final outcome differs between runs (passed in one, failed in another), or
  * a run needed a retry (the same test shows up as skipped and then passed or failed).
The script always exits 0, so it can feed a job summary without failing the build.
"""
import glob
import os
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict


def read_run(run_dir):
    """Returns {test name: [outcome, ...]} for one run, in file order."""
    outcomes = defaultdict(list)
    pattern = os.path.join(run_dir, "**", "TEST-*.xml")
    for path in sorted(glob.glob(pattern, recursive=True)):
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError:
            continue
        for case in root.iter("testcase"):
            name = f"{case.get('classname', '?')}.{case.get('name', '?')}"
            if case.find("failure") is not None or case.find("error") is not None:
                outcomes[name].append("fail")
            elif case.find("skipped") is not None:
                outcomes[name].append("skip")
            else:
                outcomes[name].append("pass")
    return outcomes


def main():
    base = sys.argv[1] if len(sys.argv) > 1 else "runs"
    run_dirs = sorted(d for d in glob.glob(os.path.join(base, "*")) if os.path.isdir(d))
    if not run_dirs:
        print("### Flaky-test check\n\nNo run results found.")
        return

    per_run = [read_run(d) for d in run_dirs]
    names = sorted(set().union(*[set(r) for r in per_run]))

    unstable = []
    for name in names:
        finals, retried = [], False
        for run in per_run:
            seq = run.get(name)
            if not seq:
                finals.append("missing")
                continue
            finals.append(seq[-1])
            if len(seq) > 1 and len(set(seq)) > 1:
                retried = True
        real = [f for f in finals if f != "missing"]
        if len(set(real)) > 1 or retried:
            unstable.append((name, finals, retried))

    total_tests = len(names)
    print("### Flaky-test check")
    print(f"\n{len(run_dirs)} runs compared, {total_tests} distinct tests.\n")
    if not unstable:
        print("No unstable tests found: every test gave the same result in every run.")
        return

    print(f"**{len(unstable)} unstable test(s)**\n")
    header = "| Test | " + " | ".join(f"Run {i + 1}" for i in range(len(run_dirs))) + " | Needed retry |"
    print(header)
    print("|" + "---|" * (len(run_dirs) + 2))
    for name, finals, retried in unstable:
        print(f"| `{name}` | " + " | ".join(finals) + f" | {'yes' if retried else 'no'} |")


if __name__ == "__main__":
    main()
