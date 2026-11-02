#!/usr/bin/env python3
"""Unit tests for the cross-module QA evidence report."""
import importlib.util
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

spec = importlib.util.spec_from_file_location("qa_report", Path(__file__).with_name("qa-report.py"))
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)

class EvidenceTest(unittest.TestCase):
    def test_no_files_never_claims_pass(self):
        files, totals, invalid = mod.summarize([])
        self.assertEqual(files, 0)
        self.assertEqual(totals["passed"], 0)
        self.assertFalse(invalid)

    def test_aggregates_counts_from_multiple_suites(self):
        with TemporaryDirectory() as temp:
            p = Path(temp) / "TEST-one.xml"
            p.write_text('<testsuites><testsuite tests="5" failures="1" errors="0" skipped="1"/><testsuite tests="3" failures="0" errors="1" skipped="0"/></testsuites>')
            files, totals, invalid = mod.summarize([p])
            self.assertEqual(files, 1)
            self.assertEqual(totals["tests"], 8)
            self.assertEqual(totals["passed"], 5)
            self.assertEqual(totals["failures"], 1)
            self.assertEqual(totals["errors"], 1)
            self.assertEqual(totals["skipped"], 1)
            self.assertFalse(invalid)

    def test_malformed_report_is_reported(self):
        with TemporaryDirectory() as temp:
            p = Path(temp) / "TEST-bad.xml"
            p.write_text('<testsuite')
            files, _, invalid = mod.summarize([p])
            self.assertEqual(files, 0)
            self.assertEqual(len(invalid), 1)

if __name__ == "__main__":
    unittest.main()
