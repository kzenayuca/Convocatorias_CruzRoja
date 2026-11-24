"""Exercise wrapper bootstrap retries without network access or real waits."""

import os
from pathlib import Path
import subprocess
import tempfile
import unittest


SCRIPT = Path(__file__).with_name("ci_maven_bootstrap.sh").resolve()


class MavenBootstrapTest(unittest.TestCase):
    def run_bootstrap(self, failures):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "mvnw").write_text(
                'echo "$*" >> calls\n'
                'count=$(wc -l < calls)\n'
                f'[ "$count" -gt {failures} ]\n',
                encoding="utf-8",
            )
            sleep = root / "sleep"
            sleep.write_text('#!/bin/sh\necho "$1" >> delays\n', encoding="utf-8")
            sleep.chmod(0o755)
            result = subprocess.run(
                ["bash", str(SCRIPT)], cwd=root, capture_output=True, text=True,
                env={**os.environ, "PATH": str(root) + os.pathsep + os.environ["PATH"]},
            )
            calls = (root / "calls").read_text().splitlines()
            delays = (root / "delays").read_text().splitlines() if (root / "delays").exists() else []
        self.assertTrue(all(call == "-B -ntp --version" for call in calls))
        return result, calls, delays

    def test_success_does_not_retry(self):
        result, calls, delays = self.run_bootstrap(0)
        self.assertEqual(0, result.returncode)
        self.assertEqual(1, len(calls))
        self.assertEqual([], delays)

    def test_transient_failure_retries_with_backoff(self):
        result, calls, delays = self.run_bootstrap(2)
        self.assertEqual(0, result.returncode)
        self.assertEqual(3, len(calls))
        self.assertEqual(["15", "30"], delays)

    def test_exhausted_retries_remain_a_failure(self):
        result, calls, delays = self.run_bootstrap(4)
        self.assertEqual(1, result.returncode)
        self.assertEqual(4, len(calls))
        self.assertEqual(["15", "30", "60"], delays)


if __name__ == "__main__":
    unittest.main()
