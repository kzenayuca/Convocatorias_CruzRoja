"""Summarize actual Surefire/JaCoCo XML, without conflating skipped tests with passes."""

import os
from pathlib import Path
import xml.etree.ElementTree as ET


def summarize(target):
    reports = sorted((target / "surefire-reports").glob("TEST-*.xml"))
    if not reports:
        raise ValueError("No hay XML Surefire: no se puede demostrar que hubo pruebas.")
    totals = dict.fromkeys(("tests", "failures", "errors", "skipped"), 0)
    rows = []
    for report in reports:
        suite = ET.parse(report).getroot()
        counts = {key: int(suite.get(key, "0")) for key in totals}
        for key, value in counts.items():
            totals[key] += value
        rows.append(
            f"| {suite.get('name', report.stem)} | "
            + " | ".join(str(counts[key]) for key in totals)
            + " |"
        )
    executed = totals["tests"] - totals["skipped"]
    passed = executed - totals["failures"] - totals["errors"]
    lines = [
        "# Backend: resultados reales",
        "",
        f"Commit: `{os.environ.get('GITHUB_SHA', 'ejecución local')}`",
        "",
        f"Total: {totals['tests']}; ejecutadas: {executed}; aprobadas: {passed}; "
        f"fallos: {totals['failures']}; errores: {totals['errors']}; "
        f"omitidas: {totals['skipped']}.",
        "",
        "Las omitidas NO cuentan como aprobadas. Selenium se ejecuta en otro job.",
        "",
        "| Suite | Total | Fallos | Errores | Omitidas |",
        "|---|---:|---:|---:|---:|",
        *rows,
        "",
        "## JaCoCo (backend, sin sumar cobertura E2E)",
        "",
    ]
    coverage = target / "site/jacoco/jacoco.xml"
    if not coverage.is_file():
        raise ValueError("Falta jacoco.xml: no hay cobertura verificable.")
    counters = {
        item.get("type"): item for item in ET.parse(coverage).getroot().findall("counter")
    }
    if "LINE" not in counters:
        raise ValueError("JaCoCo no contiene contador de líneas.")
    for kind in ("LINE", "BRANCH"):
        if kind not in counters:
            continue
        counter = counters[kind]
        covered, missed = int(counter.get("covered")), int(counter.get("missed"))
        total = covered + missed
        percent = f"{covered / total:.2%}" if total else "N/A"
        lines.append(f"- {kind}: {covered}/{total} ({percent}).")
    text = "\n".join(lines) + "\n"
    healthy = executed > 0 and not (totals["failures"] or totals["errors"])
    return text, healthy


def main():
    target = Path("backend/target")
    try:
        text, healthy = summarize(target)
    except (ValueError, ET.ParseError) as error:
        text, healthy = f"# Backend: evidencia incompleta\n\n{error}\n", False
    target.mkdir(parents=True, exist_ok=True)
    (target / "ci-summary.md").write_text(text, encoding="utf-8")
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as summary:
            summary.write(text)
    print(text)
    return 0 if healthy else 1


if __name__ == "__main__":
    raise SystemExit(main())
