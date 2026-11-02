#!/usr/bin/env python3
"""Regression tests for central branch evidence classification."""
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("qa_branch_status", Path(__file__).with_name("qa-branch-status.py"))
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)

BRANCH = "feature/workmeter-qa"
SHA = "a" * 40

class BranchEvidenceTest(unittest.TestCase):
    def record(self, run=None):
        return {"branch": BRANCH, "head_sha": SHA, "run": run}

    def test_missing_run_is_unverified(self):
        self.assertEqual("UNVERIFIED", mod.classify(self.record()))

    def test_prior_commit_success_is_stale(self):
        self.assertEqual("STALE", mod.classify(self.record({
            "head_sha": "b" * 40, "status": "completed", "conclusion": "success"})))

    def test_running_current_commit_is_pending(self):
        self.assertEqual("PENDING", mod.classify(self.record({
            "head_sha": SHA, "status": "in_progress", "conclusion": None})))

    def test_current_success_is_pass(self):
        self.assertEqual("PASS", mod.classify(self.record({
            "head_sha": SHA, "status": "completed", "conclusion": "success"})))

    def test_current_failure_is_failed(self):
        self.assertEqual("FAILED", mod.classify(self.record({
            "head_sha": SHA, "status": "completed", "conclusion": "failure"})))

    def test_duplicate_branch_is_rejected(self):
        rows = [self.record()] * 5
        with self.assertRaises(ValueError):
            mod.validate(rows)

if __name__ == "__main__":
    unittest.main()
