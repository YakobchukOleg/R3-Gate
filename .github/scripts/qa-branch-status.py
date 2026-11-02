#!/usr/bin/env python3
"""Validate branch CI evidence without conflating stale runs with current HEAD."""
import argparse
import json
from pathlib import Path

BRANCHES = {
    "feature/prototype-p1-p4-native-menu",
    "feature/workmeter-core",
    "feature/workmeter-ui",
    "feature/workmeter-qa",
    "feature/workmeter-backup",
}

def classify(record):
    branch = record.get("branch")
    head = record.get("head_sha")
    run = record.get("run")
    if branch not in BRANCHES or not isinstance(head, str) or len(head) != 40 or any(c not in "0123456789abcdef" for c in head):
        raise ValueError("Invalid branch or HEAD SHA")
    if run is None:
        return "UNVERIFIED"
    if not isinstance(run, dict):
        raise ValueError("Invalid run")
    if run.get("head_sha") != head:
        return "STALE"
    if run.get("status") != "completed":
        return "PENDING"
    return "PASS" if run.get("conclusion") == "success" else "FAILED"

def validate(rows):
    if len(rows) != len(BRANCHES) or {r.get("branch") for r in rows} != BRANCHES:
        raise ValueError("Exactly one entry per owned branch required")
    return {r["branch"]: classify(r) for r in rows}

def main():
    p = argparse.ArgumentParser()
    p.add_argument("input")
    p.add_argument("--output", default="qa-branch-status.md")
    args = p.parse_args()
    rows = json.loads(Path(args.input).read_text(encoding="utf-8"))
    statuses = validate(rows)
    lines = ["# Five-branch QA status", "", "| Branch | HEAD | CI evidence |",
             "|---|---|---|"]
    for row in rows:
        lines.append(f'| `{row["branch"]}` | `{row["head_sha"][:10]}` | **{statuses[row["branch"]]}** |')
    Path(args.output).write_text("\n".join(lines)+"\n", encoding="utf-8")
    print("\n".join(lines))

if __name__ == "__main__":
    main()
