#!/usr/bin/env python3
import importlib.util
from pathlib import Path
import unittest
spec = importlib.util.spec_from_file_location("summary", Path(__file__).with_name("qa-five-branch-summary.py"))
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)
SHA = "a" * 40
COUNTS = dict(tests=5, passed=4, failures=0, errors=0, skipped=1)

def rows():
    return [{"branch": branch, "head_sha": SHA, "run": None} for branch in mod.BRANCHES]

class SummaryTest(unittest.TestCase):
    def test_missing_evidence_is_not_pass(self):
        x = mod.summarize(rows())
        self.assertFalse(x["all_verified"])
        self.assertEqual(x["verified_totals"]["tests"], 0)

    def test_stale_success_counts_are_not_included(self):
        data = rows()
        data[0]["run"] = dict(head_sha="b"*40, status="completed", conclusion="success", counts=COUNTS)
        x = mod.summarize(data)
        self.assertEqual(x["modules"][0]["status"], "STALE")
        self.assertEqual(x["verified_totals"]["tests"], 0)

    def test_valid_counts_are_added_only_for_exact_sha(self):
        data = rows()
        for r in data:
            r["run"] = dict(head_sha=SHA, status="completed", conclusion="success", counts=COUNTS)
        x = mod.summarize(data)
        self.assertTrue(x["all_verified"])
        self.assertEqual(x["verified_totals"]["tests"], 25)
        self.assertEqual(x["verified_totals"]["passed"], 20)

    def test_success_without_counts_not_verified(self):
        data = rows()
        data[0]["run"] = dict(head_sha=SHA, status="completed", conclusion="success")
        self.assertEqual(mod.summarize(data)["modules"][0]["status"], "NO_COUNTS")

    def test_invalid_counts_cannot_claim_success(self):
        data = rows()
        data[0]["run"] = dict(head_sha=SHA, status="completed", conclusion="success", counts=dict(tests=1,passed=2,failures=0,errors=0,skipped=0))
        self.assertEqual(mod.summarize(data)["modules"][0]["status"], "NO_COUNTS")

    def test_zero_tests_cannot_pass(self):
        data = rows()
        data[0]["run"] = dict(head_sha=SHA, status="completed", conclusion="success", counts=dict(tests=0, passed=0, failures=0, errors=0, skipped=0))
        self.assertEqual(mod.summarize(data)["modules"][0]["status"], "NO_COUNTS")

    def test_all_skipped_cannot_pass(self):
        data = rows()
        data[0]["run"] = dict(head_sha=SHA, status="completed", conclusion="success", counts=dict(tests=3, passed=0, failures=0, errors=0, skipped=3))
        self.assertEqual(mod.summarize(data)["modules"][0]["status"], "NO_COUNTS")

if __name__ == "__main__":
    unittest.main()
