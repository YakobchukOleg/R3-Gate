#!/usr/bin/env python3
"""Consolidate separately verified branch QA results. No implicit integration."""
import argparse
import json
from pathlib import Path

BRANCHES = (
    "feature/prototype-p1-p4-native-menu", "feature/workmeter-core",
    "feature/workmeter-ui", "feature/workmeter-qa", "feature/workmeter-backup",
)
COUNT_KEYS = ("tests", "passed", "failures", "errors", "skipped")

def summarize(rows):
    if not isinstance(rows, list) or len(rows) != len(BRANCHES) or {r.get("branch") for r in rows} != set(BRANCHES):
        raise ValueError("Exactly one record is required for each of five branches")
    results = []
    totals = {key: 0 for key in COUNT_KEYS}
    for branch in BRANCHES:
        row = next(x for x in rows if x["branch"] == branch)
        head, run = row.get("head_sha"), row.get("run")
        if not isinstance(head, str) or len(head) != 40 or any(c not in "0123456789abcdef" for c in head):
            raise ValueError("Invalid HEAD SHA for " + branch)
        status = "UNVERIFIED"
        counts = None
        if run is not None:
            if not isinstance(run, dict): raise ValueError("Invalid run")
            if run.get("head_sha") != head: status = "STALE"
            elif run.get("status") != "completed": status = "PENDING"
            elif run.get("conclusion") != "success": status = "FAILED"
            else:
                candidate = run.get("counts")
                if isinstance(candidate, dict) and all(type(candidate.get(k)) is int and candidate[k] >= 0 for k in COUNT_KEYS) and candidate["tests"] > 0 and candidate["passed"] > 0 and candidate["tests"] == sum(candidate[k] for k in COUNT_KEYS if k != "tests"):
                    counts = {k: candidate[k] for k in COUNT_KEYS}
                    status = "PASS"
                    for key in COUNT_KEYS: totals[key] += counts[key]
                else: status = "NO_COUNTS"
        results.append({"branch": branch, "head_sha": head, "status": status, "counts": counts})
    return {"modules": results, "verified_totals": totals, "all_verified": all(x["status"] == "PASS" for x in results)}

def main():
    p = argparse.ArgumentParser()
    p.add_argument("input")
    p.add_argument("--output", default="qa-five-branch-summary.json")
    args = p.parse_args()
    output = summarize(json.loads(Path(args.input).read_text(encoding="utf-8")))
    Path(args.output).write_text(json.dumps(output, indent=2)+"\n", encoding="utf-8")
    print(json.dumps(output, indent=2))

if __name__ == "__main__":
    main()
