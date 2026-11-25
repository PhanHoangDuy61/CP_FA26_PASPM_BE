from pathlib import Path
import os
from unittest.mock import patch
import subprocess
import tempfile
import unittest


SCRIPT = Path(__file__).resolve().parents[1] / "test-summary.py"


class CiSummaryTest(unittest.TestCase):
    def run_summary(self, xml=None, allowlist=None, profile=None):
        with tempfile.TemporaryDirectory() as directory:
            if xml is not None:
                reports = Path(directory) / "target/surefire-reports"
                reports.mkdir(parents=True)
                (reports / "TEST-fixture.xml").write_text(xml)
            arguments = []
            if allowlist is not None:
                (Path(directory) / "allowed-skips.txt").write_text(allowlist)
                arguments += ["--allowed-skips", "allowed-skips.txt"]
            if profile is not None:
                arguments += ["--profile", profile]
            environment = os.environ.copy()
            environment.pop("GITHUB_STEP_SUMMARY", None)
            return subprocess.run(["python3", str(SCRIPT), *arguments], cwd=directory, env=environment, text=True, capture_output=True)

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


CASE = '<testcase classname="vn.edu.fpt.Opt" name="{name}"><skipped/></testcase>'
ALLOW = "Opt#slow | paspm.test.slow | pr,full | Linux, Java 17 | needs the slow opt-in\n"
FULL_ONLY = "Opt#slow | paspm.test.slow | full | Linux, Java 17 | manual workflow only\n"


class CiSummaryAllowedSkipsTest(unittest.TestCase):
    """Same fixture runner, plus --allowed-skips/--profile (DEC-20261125-05)."""
    run_summary = CiSummaryTest.run_summary

    def suite(self, *names, declared=None):
        declared = len(names) if declared is None else declared
        return (f'<testsuite name="vn.edu.fpt.Opt" tests="{len(names) + 1}" skipped="{declared}">'
                '<testcase classname="vn.edu.fpt.Opt" name="runs"/>'
                + "".join(CASE.format(name=n) for n in names) + "</testsuite>")

    def test_allowed_skip_passes_and_is_printed(self):
        result = self.run_summary(self.suite("slow"), ALLOW, "pr")
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("Opt#slow", result.stdout)
        self.assertIn("paspm.test.slow", result.stdout)

    def test_class_level_entry_allows_every_skipped_case_of_the_class(self):
        result = self.run_summary(self.suite("a", "b"), "Opt | paspm.test.x | pr | Linux | whole class opt-in\n", "pr")
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def test_skip_not_in_allowlist_fails_even_with_other_allowed_skips(self):
        result = self.run_summary(self.suite("slow", "surprise"), ALLOW, "pr")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("NOT ALLOWED", result.stdout)
        self.assertIn("Opt#surprise", result.stdout)

    def test_without_allowlist_any_skip_still_fails(self):
        self.assertNotEqual(0, self.run_summary(self.suite("slow")).returncode)

    def test_entry_of_another_profile_does_not_apply(self):
        self.assertNotEqual(0, self.run_summary(self.suite("slow"), FULL_ONLY, "pr").returncode)
        self.assertEqual(0, self.run_summary(self.suite("slow"), FULL_ONLY, "full").returncode)

    def test_declared_skip_without_a_skipped_testcase_is_unidentified_and_fails(self):
        result = self.run_summary(self.suite("slow", declared=2), ALLOW, "pr")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("unidentified", result.stdout)

    def test_failures_errors_and_zero_tests_fail_even_with_an_allowlist(self):
        for xml in ('<testsuite name="vn.edu.fpt.Opt" tests="1" failures="1"/>',
                    '<testsuite name="vn.edu.fpt.Opt" tests="1" errors="1"/>',
                    '<testsuite name="vn.edu.fpt.Opt" tests="0"/>'):
            with self.subTest(xml=xml):
                self.assertNotEqual(0, self.run_summary(xml, ALLOW, "pr").returncode)

    def test_malformed_allowlist_fails(self):
        self.assertNotEqual(0, self.run_summary(self.suite("slow"), "Opt#slow | missing fields\n", "pr").returncode)
        self.assertNotEqual(0, self.run_summary(self.suite("slow"), "Opt#slow | f | nope | r | why\n", "pr").returncode)
