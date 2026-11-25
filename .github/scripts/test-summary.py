"""Print sanitized counts only; raw Surefire properties may contain credentials."""
from pathlib import Path
import os
import xml.etree.ElementTree as ET

reports = list(Path("target/surefire-reports").glob("TEST-*.xml"))
counts = dict.fromkeys(("tests", "failures", "errors", "skipped"), 0)
for report in reports:
    root = ET.parse(report).getroot()
    for key in counts:
        counts[key] += int(root.get(key, 0))
text = "Backend tests: " + (str(counts) if reports else "NOT_RUN — prerequisite or Maven failure") + "\n"
text += "All populated fixtures are synthetic. Raw reports, binaries, credentials and backups are not uploaded.\n"
if not any(Path("src/test/java").rglob("*Jpa*Test.java")):
    text += "Product JPA/shared JPA-JDBC transaction suites: NOT_AVAILABLE_IN_THIS_REVISION; Product integration gate pending.\n"
print(text)
if summary := os.environ.get("GITHUB_STEP_SUMMARY"):
    with open(summary, "a") as output:
        output.write(text)
if not reports or counts["tests"] == 0 or any(counts[key] for key in ("failures", "errors", "skipped")):
    raise SystemExit("Verification requires nonzero tests and zero failures/errors/skips")
