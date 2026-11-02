#!/usr/bin/env python3
"""Aggregate existing JUnit XML artifacts without claiming absent suites passed."""
from pathlib import Path
import argparse
import xml.etree.ElementTree as ET

def summarize(paths):
    totals = dict(tests=0, failures=0, errors=0, skipped=0)
    files = 0
    invalid = []
    for path in sorted(set(Path(p) for p in paths)):
        try:
            root = ET.parse(path).getroot()
            suites = [root] if root.tag == "testsuite" else list(root.findall("testsuite"))
            if not suites:
                raise ValueError("No testsuite element")
            for suite in suites:
                for key in totals:
                    totals[key] += int(suite.get(key, "0"))
            if any(int(suite.get(key, "0")) < 0 for suite in suites for key in totals):
                raise ValueError("Negative JUnit count")
            if any(int(suite.get("tests", "0")) < sum(int(suite.get(k, "0")) for k in ("failures", "errors", "skipped")) for suite in suites):
                raise ValueError("JUnit result counts exceed number of tests")
            files += 1
        except (OSError, ET.ParseError, ValueError) as exc:
            invalid.append(f"{path}: {exc}")
    totals["passed"] = totals["tests"] - totals["failures"] - totals["errors"] - totals["skipped"]
    return files, totals, invalid

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", default="app/build/test-results")
    parser.add_argument("--sha", default="unknown")
    parser.add_argument("--output", default="qa-summary.md")
    args = parser.parse_args()
    paths = Path(args.root).rglob("TEST-*.xml")
    files, totals, invalid = summarize(paths)
    result = "NO_RESULTS" if not files else "INCOMPLETE" if invalid else "HAS_FAILURES" if totals["failures"] or totals["errors"] else "PASS"
    lines = ["# QA evidence", "", f"- Tested SHA: `{args.sha}`",
             f"- Result: **{result}**", f"- Parsed JUnit XML files: {files}"]
    lines += [f"- {k}: {v}" for k, v in totals.items()]
    if invalid:
        lines += ["", "## Invalid reports"] + [f"- {x}" for x in invalid]
    Path(args.output).write_text("\n".join(lines)+"\n", encoding="utf-8")
    print("\n".join(lines))
    if invalid: raise SystemExit(2)
    if files == 0: raise SystemExit(3)
    if totals["failures"] or totals["errors"]: raise SystemExit(1)

if __name__ == "__main__":
    main()
