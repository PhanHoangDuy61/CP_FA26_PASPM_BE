from pathlib import Path
import os
from unittest.mock import patch
import subprocess
import tempfile
import unittest


SCRIPT = Path(__file__).resolve().parents[1] / "test-summary.py"


class CiSummaryTest(unittest.TestCase):
    def run_summary(self, xml=None):
        with tempfile.TemporaryDirectory() as directory:
            if xml is not None:
                reports = Path(directory) / "target/surefire-reports"
                reports.mkdir(parents=True)
                (reports / "TEST-fixture.xml").write_text(xml)
            environment = os.environ.copy()
            environment.pop("GITHUB_STEP_SUMMARY", None)
            return subprocess.run(["python3", str(SCRIPT)], cwd=directory, env=environment, text=True, capture_output=True)

    def test_no_reports_is_not_green(self):
        result = self.run_summary()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("NOT_RUN", result.stdout)

    def test_zero_tests_is_not_green(self):
        self.assertNotEqual(0, self.run_summary('<testsuite tests="0"/>').returncode)

    def test_skips_failures_and_errors_are_not_green(self):
        for key in ("skipped", "failures", "errors"):
            with self.subTest(key=key):
                self.assertNotEqual(0, self.run_summary(f'<testsuite tests="1" {key}="1"/>').returncode)

    def test_pass_summary_does_not_emit_properties(self):
        result = self.run_summary('<testsuite tests="1"><properties><property name="private-value" value="DO_NOT_EMIT_FIXTURE_MARKER"/></properties></testsuite>')
        self.assertEqual(0, result.returncode)
        self.assertNotIn("DO_NOT_EMIT_FIXTURE_MARKER", result.stdout + result.stderr)

    def test_fixture_does_not_modify_real_job_summary(self):
        with tempfile.TemporaryDirectory() as directory:
            summary = Path(directory) / "actual-job-summary.md"
            summary.write_text("existing actual results\n")
            with patch.dict(os.environ, {"GITHUB_STEP_SUMMARY": str(summary)}):
                self.run_summary('<testsuite tests="1"/>')
            self.assertEqual("existing actual results\n", summary.read_text())
