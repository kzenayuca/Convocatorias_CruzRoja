"""Regression checks for the CI evidence summarizer (only standard library)."""

from pathlib import Path
import tempfile
import unittest

from ci_backend_report import summarize


class BackendReportTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.target = Path(self.temp.name)
        (self.target / "surefire-reports").mkdir()
        (self.target / "site/jacoco").mkdir(parents=True)

    def reports(self, tests=5, skipped=1, failures=0, errors=0):
        (self.target / "surefire-reports/TEST-example.xml").write_text(
            f'<testsuite name="Example" tests="{tests}" skipped="{skipped}" '
            f'failures="{failures}" errors="{errors}"/>', encoding="utf-8"
        )
        (self.target / "site/jacoco/jacoco.xml").write_text(
            '<report><package><counter type="LINE" covered="900" missed="0"/>'
            '</package><counter type="LINE" covered="8" missed="2"/>'
            '<counter type="BRANCH" covered="1" missed="1"/></report>',
            encoding="utf-8",
        )

    def test_counts_executed_separately_and_uses_only_global_coverage(self):
        self.reports()
        text, healthy = summarize(self.target)
        self.assertTrue(healthy)
        self.assertIn("ejecutadas: 4; aprobadas: 4", text)
        self.assertIn("LINE: 8/10 (80.00%)", text)
        self.assertIn("BRANCH: 1/2 (50.00%)", text)

    def test_failed_or_errored_tests_are_not_successful(self):
        for counts in ({"failures": 1}, {"errors": 1}):
            with self.subTest(counts=counts):
                self.reports(**counts)
                text, healthy = summarize(self.target)
                self.assertFalse(healthy)
                self.assertIn("aprobadas: 3", text)

    def test_all_skipped_is_not_a_successful_execution(self):
        self.reports(tests=5, skipped=5)
        self.assertFalse(summarize(self.target)[1])

    def test_missing_surefire_fails(self):
        with self.assertRaisesRegex(ValueError, "Surefire"):
            summarize(self.target)

    def test_missing_coverage_fails(self):
        self.reports()
        (self.target / "site/jacoco/jacoco.xml").unlink()
        with self.assertRaisesRegex(ValueError, "jacoco.xml"):
            summarize(self.target)

    def test_missing_line_counter_fails(self):
        self.reports()
        (self.target / "site/jacoco/jacoco.xml").write_text("<report/>", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "contador"):
            summarize(self.target)


if __name__ == "__main__":
    unittest.main()
