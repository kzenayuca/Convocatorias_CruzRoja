"""Cross-platform Jenkins helpers. Never connect tests to the personal database."""

import hashlib
import json
import os
from pathlib import Path
import socket
import subprocess
import sys
import time
import urllib.parse
import urllib.request

from ci_sonar import Sonar, properties, wait_for_server


OWNER_LABEL = "pe.cruzroja.ci.owner"


def owner():
    value = os.environ.get("BUILD_URL")
    if not value:
        raise ValueError("Falta BUILD_URL: ejecutar estas operaciones desde Jenkins.")
    return value


def container_name():
    return "convocatorias-ci-" + hashlib.sha256(owner().encode()).hexdigest()[:16]


def docker(*arguments, check=True):
    result = subprocess.run(
        ["docker", *arguments], capture_output=True, text=True, check=False, timeout=180,
    )
    if check and result.returncode:
        # Arguments contain only disposable test credentials, never a Sonar token.
        raise ValueError(f"Docker fallo: {result.stderr.strip() or result.stdout.strip()}")
    return result


def database_url(mapping):
    lines = mapping.strip().splitlines()
    if len(lines) != 1 or not lines[0].startswith("127.0.0.1:"):
        raise ValueError("PostgreSQL no tiene un unico puerto publicado solo en localhost.")
    port = int(lines[0].rsplit(":", 1)[1])
    if not 1 <= port <= 65535:
        raise ValueError("Puerto de PostgreSQL invalido.")
    return f"jdbc:postgresql://127.0.0.1:{port}/convocatorias"


def db_start():
    name = container_name()
    docker(
        "run", "--detach", "--rm", "--name", name,
        "--label", f"{OWNER_LABEL}={owner()}",
        "--publish", "127.0.0.1::5432",
        "--env", "POSTGRES_DB=convocatorias",
        "--env", "POSTGRES_USER=convocatorias",
        "--env", "POSTGRES_PASSWORD=convocatorias",
        "postgres:16.4-alpine",
    )
    deadline = time.monotonic() + 120
    while True:
        ready = docker("exec", name, "pg_isready", "-U", "convocatorias",
                       "-d", "convocatorias", check=False)
        if ready.returncode == 0:
            return database_url(docker("port", name, "5432/tcp").stdout)
        if time.monotonic() >= deadline:
            raise ValueError("PostgreSQL no estuvo listo en 120 segundos.")
        time.sleep(3)


def db_stop():
    name = container_name()
    inspection = docker("container", "inspect", name, check=False)
    if inspection.returncode:
        # Distinguish a missing container from an unavailable daemon.
        docker("info", "--format", "{{.ServerVersion}}")
        return
    containers = json.loads(inspection.stdout)
    labels = containers[0]["Config"].get("Labels") or {}
    if labels.get(OWNER_LABEL) != owner():
        raise ValueError("Contenedor ajeno: no se elimina porque su etiqueta no coincide.")
    docker("rm", "--force", "--volumes", name)


def sonar_check():
    client = Sonar(os.environ["SONAR_HOST_URL"], token=os.environ["SONAR_TOKEN"])
    wait_for_server(client)
    project = properties("sonar-project.properties")["sonar.projectKey"]
    client.request("/api/components/show?" + urllib.parse.urlencode({"component": project}))
    # A reused Jenkins workspace must never export the previous build's analysis.
    task = Path("backend/target/sonar/report-task.txt")
    if task.exists():
        task.unlink()
    print("SonarQube listo y proyecto existente; no se modifica su configuracion.")


def verify_database_url(url):
    name = container_name()
    containers = json.loads(docker("container", "inspect", name).stdout)
    labels = containers[0]["Config"].get("Labels") or {}
    if labels.get(OWNER_LABEL) != owner():
        raise ValueError("La base no pertenece a este build.")
    expected = database_url(docker("port", name, "5432/tcp").stdout)
    if url != expected:
        raise ValueError("TEST_DB_URL no coincide con el puerto de la base de este build.")


def smoke_test():
    url = os.environ["TEST_DB_URL"]
    if not url.startswith("jdbc:postgresql://127.0.0.1:"):
        raise ValueError("La prueba del JAR solo admite la base aislada del build.")
    verify_database_url(url)
    jars = list(Path("backend/target").glob("convocatorias-backend-*.jar"))
    if len(jars) != 1:
        raise ValueError("Se esperaba exactamente un JAR ejecutable del backend.")
    # Fail rather than testing some unrelated application already using 8083.
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", 8083))
    environment = {
        **os.environ, "DB_URL": url,
        "DB_USER": os.environ["TEST_DB_USER"],
        "DB_PASSWORD": os.environ["TEST_DB_PASSWORD"],
    }
    evidence = Path("backend/target/jenkins-evidence")
    evidence.mkdir(parents=True, exist_ok=True)
    with (evidence / "application.log").open("w", encoding="utf-8") as log:
        process = subprocess.Popen(
            ["java", "-jar", str(jars[0].resolve()),
             "--server.address=127.0.0.1", "--server.port=8083"],
            env=environment, stdout=log, stderr=subprocess.STDOUT,
        )
        try:
            deadline = time.monotonic() + 90
            while True:
                if process.poll() is not None:
                    raise ValueError("El JAR termino antes de estar listo; revisar application.log.")
                try:
                    with urllib.request.urlopen(
                        "http://127.0.0.1:8083/api/v1/health", timeout=3,
                    ) as response:
                        health = json.load(response)
                    if health.get("status") == "UP":
                        print("JAR publicado: HTTP 200 y status UP en puerto 8083.")
                        return
                except (OSError, json.JSONDecodeError):
                    pass
                if time.monotonic() >= deadline:
                    raise ValueError("El JAR no estuvo listo en 90 segundos.")
                time.sleep(2)
        finally:
            process.terminate()
            try:
                process.wait(timeout=20)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=10)


def main():
    operations = {
        "db-start": db_start, "db-stop": db_stop,
        "sonar-check": sonar_check, "smoke-test": smoke_test,
    }
    try:
        if len(sys.argv) != 2 or sys.argv[1] not in operations:
            raise ValueError("Uso: jenkins_ci.py db-start|db-stop|sonar-check|smoke-test")
        result = operations[sys.argv[1]]()
        if result:
            print(result)
        return 0
    except (ValueError, KeyError, OSError, subprocess.SubprocessError) as error:
        print(f"Jenkins CI: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
