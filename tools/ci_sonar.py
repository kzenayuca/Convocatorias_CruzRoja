"""Bootstrap disposable SonarQube and export verifiable evidence. Standard library only."""

import base64
import csv
import io
import json
import os
from pathlib import Path
import secrets
import sys
import time
import urllib.parse
import urllib.request


EVIDENCE = Path("backend/target/sonar-evidence")
TASK_FILE = Path("backend/target/sonar/report-task.txt")


def properties(path):
    return {
        key.strip(): value.strip()
        for line in Path(path).read_text(encoding="utf-8").splitlines()
        if line.strip() and not line.lstrip().startswith(("#", "!")) and "=" in line
        for key, value in [line.split("=", 1)]
    }


class Sonar:
    def __init__(self, url, token=None, password=None):
        self.url = url.rstrip("/")
        self.authorization = (
            "Bearer " + token if token else
            "Basic " + base64.b64encode(f"admin:{password}".encode()).decode()
        )

    def request(self, path, data=None):
        body = urllib.parse.urlencode(data).encode() if data is not None else None
        request = urllib.request.Request(
            self.url + path, data=body,
            headers={"Authorization": self.authorization},
        )
        with urllib.request.urlopen(request, timeout=30) as response:
            content = response.read()
        return json.loads(content) if content else {}


def save(name, data):
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    (EVIDENCE / name).write_text(
        json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
    )


def wait_for_server(client):
    deadline = time.monotonic() + 300
    while True:
        try:
            status = client.request("/api/system/status")
            if status.get("status") == "UP":
                return status
        except (OSError, json.JSONDecodeError):
            # The published Docker port can reset connections before HTTP is ready.
            pass
        if time.monotonic() >= deadline:
            raise ValueError("SonarQube no alcanzo estado UP en 300 segundos.")
        time.sleep(5)


def prepare():
    """Only for a NEW, disposable CI server; never run against a personal server."""
    if os.environ.get("GITHUB_ACTIONS") != "true":
        raise ValueError("prepare solo esta permitido en GitHub Actions.")
    for path in (
        "backend/target/classes", "backend/target/test-classes",
        "backend/target/surefire-reports", "backend/target/site/jacoco/jacoco.xml",
    ):
        if not Path(path).exists():
            raise ValueError(f"Falta entrada del job backend: {path}")
    if not list(Path("backend/target/sonar-libraries").glob("*.jar")):
        raise ValueError("Faltan dependencias Java para Scanner CLI.")
    client = Sonar(os.environ["SONAR_HOST_URL"], password="admin")
    status = wait_for_server(client)
    save("server.json", status)
    password = secrets.token_urlsafe(32)
    print(f"::add-mask::{password}", flush=True)
    client.request("/api/users/change_password", {
        "login": "admin", "previousPassword": "admin", "password": password,
    })
    client = Sonar(os.environ["SONAR_HOST_URL"], password=password)
    config = properties("sonar-project.properties")
    client.request("/api/projects/create", {
        "project": config["sonar.projectKey"], "name": config["sonar.projectName"],
        "visibility": "private",
    })
    token = client.request("/api/user_tokens/generate", {
        "name": "ci-" + os.environ["GITHUB_RUN_ID"],
    })["token"]
    print(f"::add-mask::{token}", flush=True)
    # Never save password/token in the repository, reports or artifacts.
    with open(os.environ["GITHUB_ENV"], "a", encoding="utf-8") as environment:
        environment.write(f"SONAR_TOKEN={token}\n")
    print("Servidor listo, proyecto creado y token temporal enmascarado.")


def all_pages(client, path, field):
    """Return every issue/hotspot, explicitly rejecting truncated exports."""
    results = []
    page = 1
    while True:
        data = client.request(f"{path}&ps=500&p={page}")
        total = data.get("paging", {}).get("total", data.get("total"))
        batch = data[field]
        results.extend(batch)
        if total is None:
            raise ValueError("La API no indica el total; no se puede verificar el reporte.")
        if len(results) >= total:
            if len(results) != total:
                raise ValueError("El total de issues cambio durante la exportacion.")
            return results
        if not batch or len(results) >= 10000:
            raise ValueError("Exportacion incompleta: dividir el reporte por componentes.")
        page += 1


def verify_gate(processing, gate):
    if processing["task"]["status"] != "SUCCESS":
        raise ValueError("El procesamiento del analisis no termino correctamente.")
    if gate["projectStatus"]["status"] != "OK":
        raise ValueError("Quality Gate rechazado: consultar quality-gate.json.")


def collect():
    config = properties("sonar-project.properties")
    if not TASK_FILE.is_file():
        raise ValueError("Falta report-task.txt: el scanner no completo el envio.")
    task = properties(TASK_FILE)
    client = Sonar(os.environ["SONAR_HOST_URL"], token=os.environ["SONAR_TOKEN"])
    processing = client.request("/api/ce/task?" + urllib.parse.urlencode({
        "id": task["ceTaskId"],
    }))
    save("task.json", processing)
    if processing["task"]["status"] != "SUCCESS":
        raise ValueError(f"Procesamiento Sonar: {processing['task']['status']}.")
    analysis_id = processing["task"]["analysisId"]
    gate = client.request("/api/qualitygates/project_status?" + urllib.parse.urlencode({
        "analysisId": analysis_id,
    }))
    save("quality-gate.json", gate)
    project = config["sonar.projectKey"]
    encoded_project = urllib.parse.quote(project, safe="")
    measures = client.request(
        f"/api/measures/component?component={encoded_project}&metricKeys="
        "coverage,line_coverage,branch_coverage,tests,test_failures,test_errors,skipped_tests,ncloc"
    )
    save("measures.json", measures)
    issues = all_pages(client, f"/api/issues/search?componentKeys={encoded_project}", "issues")
    save("issues.json", {"total": len(issues), "issues": issues})
    hotspots = all_pages(client, f"/api/hotspots/search?projectKey={encoded_project}", "hotspots")
    save("hotspots.json", {"total": len(hotspots), "hotspots": hotspots})
    output = io.StringIO()
    writer = csv.writer(output)
    writer.writerow(["key", "type", "severity", "rule", "component", "line", "message"])
    for issue in issues:
        # Avoid executable formulas when opening a code-derived report in Excel.
        writer.writerow([
            "'" + str(value) if str(value).startswith(("=", "+", "-", "@")) else value
            for value in (issue.get(key, "") for key in (
                "key", "type", "severity", "rule", "component", "line", "message"
            ))
        ])
    (EVIDENCE / "issues.csv").write_text(output.getvalue(), encoding="utf-8-sig")
    save("provenance.json", {
        "commit": os.environ["GITHUB_SHA"],
        "run": f"{os.environ['GITHUB_SERVER_URL']}/{os.environ['GITHUB_REPOSITORY']}"
               f"/actions/runs/{os.environ['GITHUB_RUN_ID']}",
        "analysisId": analysis_id, "project": project,
        "scannerOutcome": os.environ.get("SCANNER_OUTCOME"),
        "ephemeral": True,
    })
    status = gate["projectStatus"]
    lines = [
        "# Backend: SonarQube temporal",
        "",
        f"- Commit: `{os.environ['GITHUB_SHA']}`",
        f"- Analisis: `{analysis_id}`",
        f"- Quality Gate: **{status['status']}**",
        f"- Issues: **{len(issues)}**; security hotspots: **{len(hotspots)}**.",
        "",
        "| Condicion | Estado | Valor | Umbral |",
        "|---|---|---|---|",
        *[
            f"| {item['metricKey']} | {item['status']} | "
            f"{item.get('actualValue', 'N/A')} | {item.get('errorThreshold', 'N/A')} |"
            for item in status.get("conditions", [])
        ],
        "",
        "## Metricas importadas",
        *[f"- {item['metric']}: {item['value']}" for item in measures["component"]["measures"]],
        "",
        "**Servidor desechable:** no conserva historial ni dashboard al terminar.",
        "El Gate por defecto no se modifica. En el primer analisis puede no haber "
        "base para evaluar codigo nuevo; Gate OK no significa cero issues ni deuda reducida.",
        "Los JSON y CSV son la evidencia persistente. La revision manual sigue siendo necesaria.",
    ]
    summary = "\n".join(lines) + "\n"
    (EVIDENCE / "summary.md").write_text(summary, encoding="utf-8")
    print(summary)
    verify_gate(processing, gate)
    if not any(item["metric"] == "coverage" for item in measures["component"]["measures"]):
        raise ValueError("No hay cobertura en Sonar: revisar importacion JaCoCo.")
    if os.environ.get("SCANNER_OUTCOME") != "success":
        raise ValueError("El scanner fallo, aunque se haya podido recuperar el Gate.")


def main():
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    try:
        if sys.argv[1:] == ["prepare"]:
            prepare()
        elif sys.argv[1:] == ["collect"]:
            collect()
        else:
            raise ValueError("Uso: ci_sonar.py prepare|collect")
        return 0
    except (ValueError, KeyError, OSError) as error:
        # Do not include API response bodies/headers containing credentials.
        message = f"SonarQube: {error}\n"
        print(message, file=sys.stderr)
        with (EVIDENCE / "summary.md").open("a", encoding="utf-8") as summary:
            summary.write("\n**ERROR:** " + message)
        return 1
    finally:
        summary = EVIDENCE / "summary.md"
        if summary.is_file() and os.environ.get("GITHUB_STEP_SUMMARY"):
            with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as destination:
                destination.write(summary.read_text(encoding="utf-8"))


if __name__ == "__main__":
    raise SystemExit(main())
