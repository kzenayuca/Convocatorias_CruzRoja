"""Verify evidence export and failure semantics without needing Docker or a token."""

import json
import io
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import Mock, patch
import urllib.error

import ci_sonar


class SonarEvidenceTest(unittest.TestCase):
    def test_jenkins_provenance_is_not_reported_as_github_or_ephemeral(self):
        with patch.dict(os.environ, {
            "CI_PROVIDER": "jenkins", "GIT_COMMIT": "jenkins-commit",
            "BUILD_URL": "http://localhost:8090/job/backend/1/",
        }, clear=True):
            context = ci_sonar.analysis_context()
        self.assertEqual("jenkins-commit", context["commit"])
        self.assertEqual("jenkins", context["provider"])
        self.assertFalse(context["ephemeral"])
        self.assertIn("/job/backend/1/", context["run"])

    def test_password_always_meets_server_policy_even_with_uniform_random_data(self):
        with patch.object(ci_sonar.secrets, "token_urlsafe", return_value="x" * 43):
            password = ci_sonar.temporary_password()
        self.assertGreaterEqual(len(password), 12)
        for pattern in ("[A-Z]", "[a-z]", "[0-9]", "[^a-zA-Z0-9]"):
            self.assertRegex(password, pattern)

    def test_http_error_identifies_endpoint_without_exposing_credentials(self):
        error = urllib.error.HTTPError(
            "http://localhost/api/users/change_password", 400, "",
            {}, io.BytesIO(b'{"result":"sensitive-password"}'),
        )
        with patch.object(ci_sonar.urllib.request, "urlopen", side_effect=error):
            with self.assertRaises(OSError) as raised:
                ci_sonar.Sonar("http://localhost", password="secret").request(
                    "/api/users/change_password", {"password": "sensitive-password"},
                )
        self.assertEqual("/api/users/change_password: HTTP 400", str(raised.exception))

    def test_startup_tolerates_reset_and_starting_status(self):
        client = Mock()
        client.request.side_effect = [
            ConnectionResetError("Connection reset by peer"),
            {"status": "STARTING"},
            {"status": "UP"},
        ]
        with patch.object(ci_sonar.time, "sleep") as sleep:
            self.assertEqual("UP", ci_sonar.wait_for_server(client)["status"])
        self.assertEqual(2, sleep.call_count)

    def test_startup_has_a_finite_deadline(self):
        client = Mock()
        client.request.side_effect = ConnectionRefusedError()
        with patch.object(ci_sonar.time, "monotonic", side_effect=[0, 300]), \
                patch.object(ci_sonar.time, "sleep"), \
                self.assertRaisesRegex(ValueError, "300 segundos"):
            ci_sonar.wait_for_server(client)

    def test_properties_preserve_equals_in_value(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "sonar.properties"
            path.write_text("# Comment\n\nkey=value=extra\n", encoding="utf-8")
            self.assertEqual({"key": "value=extra"}, ci_sonar.properties(path))

    def test_versioned_config_is_backend_only_and_has_no_token(self):
        config = ci_sonar.properties(Path(__file__).resolve().parents[1] / "sonar-project.properties")
        self.assertNotIn("sonar.token", config)
        self.assertNotIn("sonar.login", config)
        self.assertEqual("backend/src/main/java", config["sonar.sources"])
        self.assertEqual("backend/src/test/java", config["sonar.tests"])
        self.assertEqual("true", config["sonar.qualitygate.wait"])
        for key in ("sonar.sources", "sonar.tests", "sonar.java.binaries",
                    "sonar.java.test.binaries", "sonar.coverage.jacoco.xmlReportPaths"):
            self.assertFalse(Path(config[key]).is_absolute())

    def test_exports_all_pages(self):
        client = Mock()
        client.request.side_effect = [
            {"paging": {"total": 3}, "issues": [{"key": "a"}, {"key": "b"}]},
            {"paging": {"total": 3}, "issues": [{"key": "c"}]},
        ]
        issues = ci_sonar.all_pages(client, "/api/issues/search?componentKeys=test", "issues")
        self.assertEqual(3, len(issues))
        self.assertTrue(client.request.call_args.args[0].endswith("&ps=500&p=2"))

    def test_empty_report_is_valid_but_missing_total_is_not(self):
        client = Mock()
        client.request.return_value = {"paging": {"total": 0}, "hotspots": []}
        self.assertEqual([], ci_sonar.all_pages(client, "/search?project=test", "hotspots"))
        client.request.return_value = {"issues": []}
        with self.assertRaisesRegex(ValueError, "total"):
            ci_sonar.all_pages(client, "/search?project=test", "issues")

    def test_truncated_issue_report_fails(self):
        client = Mock()
        client.request.return_value = {"paging": {"total": 2}, "issues": []}
        with self.assertRaisesRegex(ValueError, "incompleta"):
            ci_sonar.all_pages(client, "/search?project=test", "issues")

    def test_gate_must_be_ok_and_processing_successful(self):
        success = {"task": {"status": "SUCCESS"}}
        ci_sonar.verify_gate(success, {"projectStatus": {"status": "OK"}})
        for status in ("ERROR", "NONE"):
            with self.subTest(status=status), self.assertRaises(ValueError):
                ci_sonar.verify_gate(success, {"projectStatus": {"status": status}})
        with self.assertRaises(ValueError):
            ci_sonar.verify_gate({"task": {"status": "FAILED"}}, {"projectStatus": {"status": "OK"}})

    def test_rejected_gate_still_exports_issues_and_exact_analysis(self):
        self.check_collect("ERROR", "success", "Quality Gate rechazado")

    def test_scanner_failure_cannot_be_hidden_by_a_green_gate(self):
        self.check_collect("OK", "failure", "scanner fallo")

    def test_collect_requires_imported_coverage(self):
        self.check_collect("OK", "success", "No hay cobertura", measures=[])

    def test_success_exports_provenance_and_safe_csv(self):
        self.check_collect("OK", "success", None)

    def check_collect(self, gate_status, scanner_status, error, measures=None):
        with tempfile.TemporaryDirectory() as directory:
            evidence = Path(directory) / "evidence"
            task = Path(directory) / "report-task.txt"
            task.write_text("ceTaskId=task-123\n", encoding="utf-8")
            client = Mock()
            client.request.side_effect = [
                {"task": {"status": "SUCCESS", "analysisId": "analysis-123"}},
                {"projectStatus": {"status": gate_status, "conditions": []}},
                {"component": {"measures": (
                    [{"metric": "coverage", "value": "85.0"}] if measures is None else measures
                )}},
                {"paging": {"total": 1}, "issues": [{
                    "key": "issue-123", "type": "CODE_SMELL", "message": "=unsafe()",
                }]},
                {"paging": {"total": 0}, "hotspots": []},
            ]
            environment = {
                "CI_PROVIDER": "github",
                "SONAR_HOST_URL": "http://localhost:9000", "SONAR_TOKEN": "test-token",
                "GITHUB_SHA": "abc123", "GITHUB_RUN_ID": "1",
                "GITHUB_SERVER_URL": "https://github.com", "GITHUB_REPOSITORY": "owner/repo",
                "SCANNER_OUTCOME": scanner_status,
            }
            with patch.object(ci_sonar, "EVIDENCE", evidence), \
                    patch.object(ci_sonar, "TASK_FILE", task), \
                    patch.object(ci_sonar, "Sonar", return_value=client), \
                    patch.dict(os.environ, environment), patch("builtins.print"):
                if error:
                    with self.assertRaisesRegex(ValueError, error):
                        ci_sonar.collect()
                else:
                    ci_sonar.collect()
            gate_call = client.request.call_args_list[1].args[0]
            self.assertIn("analysisId=analysis-123", gate_call)
            self.assertEqual(1, json.loads((evidence / "issues.json").read_text())["total"])
            self.assertEqual("abc123", json.loads((evidence / "provenance.json").read_text())["commit"])
            self.assertIn("'=unsafe()", (evidence / "issues.csv").read_text(encoding="utf-8-sig"))
            self.assertNotIn("test-token", "".join(path.read_text(encoding="utf-8-sig") for path in evidence.iterdir()))


if __name__ == "__main__":
    unittest.main()
