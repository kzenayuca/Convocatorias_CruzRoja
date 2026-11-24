"""Test isolation and cleanup without requiring a Docker daemon or Jenkins."""

import json
import os
from pathlib import Path
import unittest
from unittest.mock import Mock, patch

import jenkins_ci


class JenkinsHelpersTest(unittest.TestCase):
    def test_container_name_is_safe_unique_and_requires_build_identity(self):
        names = []
        for build in ("1", "2"):
            with patch.dict(os.environ, {"BUILD_URL": f"https://jenkins/job/backend/{build}/"}):
                names.append(jenkins_ci.container_name())
        self.assertNotEqual(*names)
        self.assertRegex(names[0], r"^convocatorias-ci-[a-f0-9]{16}$")
        with patch.dict(os.environ, {}, clear=True), self.assertRaisesRegex(ValueError, "BUILD_URL"):
            jenkins_ci.container_name()

    def test_only_loopback_port_mappings_are_accepted(self):
        self.assertEqual(
            "jdbc:postgresql://127.0.0.1:55433/convocatorias",
            jenkins_ci.database_url("127.0.0.1:55433\n"),
        )
        for mapping in ("0.0.0.0:5432", "[::]:5432", "127.0.0.1:0",
                        "127.0.0.1:70000", "127.0.0.1:5432\n0.0.0.0:5432"):
            with self.subTest(mapping=mapping), self.assertRaises(ValueError):
                jenkins_ci.database_url(mapping)

    def test_database_start_creates_only_an_isolated_labelled_container(self):
        result = Mock(returncode=0, stdout="127.0.0.1:55433\n")
        with patch.dict(os.environ, {"BUILD_URL": "https://jenkins/job/backend/1/"}), \
                patch.object(jenkins_ci, "docker", return_value=result) as docker:
            url = jenkins_ci.db_start()
        creation = docker.call_args_list[0].args
        self.assertIn("--rm", creation)
        self.assertIn("127.0.0.1::5432", creation)
        self.assertIn("pe.cruzroja.ci.owner=https://jenkins/job/backend/1/", creation)
        self.assertNotIn("--volume", creation)
        self.assertIn(":55433/", url)

    def test_cleanup_refuses_to_remove_someone_elses_container(self):
        inspected = Mock(returncode=0, stdout=json.dumps([
            {"Config": {"Labels": {jenkins_ci.OWNER_LABEL: "other-build"}}},
        ]))
        with patch.dict(os.environ, {"BUILD_URL": "https://jenkins/job/backend/1/"}), \
                patch.object(jenkins_ci, "docker", return_value=inspected) as docker, \
                self.assertRaisesRegex(ValueError, "ajeno"):
            jenkins_ci.db_stop()
        self.assertEqual(1, docker.call_count)

    def test_cleanup_removes_only_its_own_container(self):
        build = "https://jenkins/job/backend/1/"
        inspected = Mock(returncode=0, stdout=json.dumps([
            {"Config": {"Labels": {jenkins_ci.OWNER_LABEL: build}}},
        ]))
        with patch.dict(os.environ, {"BUILD_URL": build}), \
                patch.object(jenkins_ci, "docker", return_value=inspected) as docker:
            name = jenkins_ci.container_name()
            jenkins_ci.db_stop()
        self.assertEqual(("rm", "--force", "--volumes", name), docker.call_args.args)

    def test_missing_container_still_checks_daemon_availability(self):
        with patch.dict(os.environ, {"BUILD_URL": "https://jenkins/job/backend/1/"}), \
                patch.object(jenkins_ci, "docker", return_value=Mock(returncode=1)) as docker:
            jenkins_ci.db_stop()
        self.assertEqual(("info", "--format", "{{.ServerVersion}}"), docker.call_args.args)

    def test_database_readiness_has_a_deadline(self):
        with patch.dict(os.environ, {"BUILD_URL": "https://jenkins/job/backend/1/"}), \
                patch.object(jenkins_ci, "docker", return_value=Mock(returncode=1)), \
                patch.object(jenkins_ci.time, "monotonic", side_effect=[0, 120]), \
                self.assertRaisesRegex(ValueError, "120 segundos"):
            jenkins_ci.db_start()

    def test_docker_commands_do_not_use_a_shell(self):
        result = Mock(returncode=0)
        with patch.object(jenkins_ci.subprocess, "run", return_value=result) as run:
            jenkins_ci.docker("info")
        self.assertEqual(["docker", "info"], run.call_args.args[0])
        self.assertNotIn("shell", run.call_args.kwargs)

    def test_smoke_test_rejects_an_external_database(self):
        with patch.dict(os.environ, {"TEST_DB_URL": "jdbc:postgresql://production/db"}), \
                self.assertRaisesRegex(ValueError, "aislada"):
            jenkins_ci.smoke_test()

    def test_smoke_test_never_starts_over_an_occupied_port(self):
        with patch.dict(os.environ, {
            "TEST_DB_URL": "jdbc:postgresql://127.0.0.1:55433/convocatorias",
        }), patch.object(jenkins_ci, "verify_database_url"), \
                patch.object(jenkins_ci.Path, "glob", return_value=[Path("test.jar")]), \
                patch.object(jenkins_ci.socket, "socket") as socket, \
                patch.object(jenkins_ci.subprocess, "Popen") as start, \
                self.assertRaises(OSError):
            socket.return_value.__enter__.return_value.bind.side_effect = OSError("Port occupied")
            jenkins_ci.smoke_test()
        start.assert_not_called()

    def test_smoke_rejects_even_a_local_database_not_owned_by_this_build(self):
        build = "https://jenkins/job/backend/1/"
        inspection = Mock(stdout=json.dumps([
            {"Config": {"Labels": {jenkins_ci.OWNER_LABEL: build}}},
        ]))
        port = Mock(stdout="127.0.0.1:55433\n")
        with patch.dict(os.environ, {"BUILD_URL": build}), \
                patch.object(jenkins_ci, "docker", side_effect=[inspection, port]), \
                self.assertRaisesRegex(ValueError, "no coincide"):
            jenkins_ci.verify_database_url("jdbc:postgresql://127.0.0.1:5433/convocatorias")

    def test_smoke_test_always_stops_the_process_it_started(self):
        process = Mock()
        process.poll.return_value = None
        response = Mock()
        response.__enter__ = Mock(return_value=response)
        response.__exit__ = Mock(return_value=False)
        response.read.return_value = b'{"status":"UP"}'
        with patch.dict(os.environ, {
            "TEST_DB_URL": "jdbc:postgresql://127.0.0.1:55433/convocatorias",
            "TEST_DB_USER": "convocatorias", "TEST_DB_PASSWORD": "convocatorias",
        }), patch.object(jenkins_ci, "verify_database_url"), \
                patch.object(jenkins_ci.Path, "glob", return_value=[Path("test.jar")]), \
                patch.object(jenkins_ci.socket, "socket") as socket, \
                patch.object(jenkins_ci.Path, "mkdir"), \
                patch.object(jenkins_ci.Path, "open"), \
                patch.object(jenkins_ci.subprocess, "Popen", return_value=process), \
                patch.object(jenkins_ci.urllib.request, "urlopen", return_value=response), \
                patch("builtins.print"):
            socket.return_value.__enter__ = Mock()
            socket.return_value.__exit__ = Mock()
            jenkins_ci.smoke_test()
        process.terminate.assert_called_once()
        process.wait.assert_called_once_with(timeout=20)

    def test_jenkinsfile_has_real_stages_publishers_and_no_embedded_token(self):
        text = (Path(__file__).resolve().parents[1] / "Jenkinsfile").read_text()
        for marker in ("checkout scm", "isUnix()", "-Pquality verify", "junit testResults:",
                       "archiveArtifacts", "sonarqube-token", "pollSCM(", "smoke-test"):
            self.assertIn(marker, text)
        self.assertNotIn("squ_", text)
        self.assertNotIn("-Dsonar.token=", text)
        self.assertIn("SCANNER_OUTCOME", text)


if __name__ == "__main__":
    unittest.main()
