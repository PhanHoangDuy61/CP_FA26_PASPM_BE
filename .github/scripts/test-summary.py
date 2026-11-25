"""Print sanitized counts only; raw Surefire properties may contain credentials.

Strict by default: any failure, error, skipped test or zero tests fails the step.
An explicit allowlist (DEC-20261125-05) may name skipped tests that are expected because an opt-in flag is not enabled:

    python3 .github/scripts/test-summary.py --allowed-skips .github/scripts/allowed-skips.txt --profile pr

Allowlist line: ``target | flag | profiles | runtime | reason``. ``target`` is ``Class`` (every skipped case of that
class) or ``Class#method``. ``profiles`` is a comma list of ``pr`` (the regular integration job) and ``full`` (the manual
full workflow). A skip that is not allowed for the selected profile, or that cannot be identified, still fails.
"""
from pathlib import Path
import argparse
import os
import sys
import xml.etree.ElementTree as ET


def load_allowlist(path, profile):
    allowed = {}
    for number, raw in enumerate(Path(path).read_text().splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        parts = [part.strip() for part in line.split("|")]
        if len(parts) != 5 or not all(parts):
            raise SystemExit(f"{path}:{number}: expected 'target | flag | profiles | runtime | reason'")
        target, flag, profiles, runtime, reason = parts
        unknown = set(profiles.split(",")) - {"pr", "full"}
        if unknown:
            raise SystemExit(f"{path}:{number}: unknown profile {sorted(unknown)}")
        if profile in profiles.split(","):
            allowed[target] = (flag, runtime, reason)
    return allowed


def main(argv=None):
    parser = argparse.ArgumentParser()
    parser.add_argument("--allowed-skips")
    parser.add_argument("--profile", default="pr", choices=("pr", "full"))
    args = parser.parse_args(argv)
    allowed = load_allowlist(args.allowed_skips, args.profile) if args.allowed_skips else {}

    reports = list(Path("target/surefire-reports").glob("TEST-*.xml"))
    counts = dict.fromkeys(("tests", "failures", "errors", "skipped"), 0)
    skipped_cases = []        # (class, method) of every testcase carrying <skipped>
    reported_by_class = {}    # skipped count declared by each suite, to catch skips without a testcase
    for report in reports:
        root = ET.parse(report).getroot()
        for key in counts:
            counts[key] += int(root.get(key, 0))
        suite = root.get("name", "").split(".")[-1]
        reported_by_class[suite] = reported_by_class.get(suite, 0) + int(root.get("skipped", 0))
        for case in root.iter("testcase"):
            if case.find("skipped") is not None:
                skipped_cases.append((case.get("classname", suite).split(".")[-1], case.get("name", "?")))

    allowed_hits, unexpected = [], []
    for cls, method in skipped_cases:
        entry = allowed.get(f"{cls}#{method}") or allowed.get(cls)
        key = f"{cls}#{method}" if f"{cls}#{method}" in allowed else cls
        (allowed_hits if entry else unexpected).append((cls, method, key, entry))
    identified = {}
    for cls, _method, _key, _entry in allowed_hits + unexpected:
        identified[cls] = identified.get(cls, 0) + 1
    for cls, declared in reported_by_class.items():
        if declared > identified.get(cls, 0):
            unexpected.append((cls, f"<{declared - identified.get(cls, 0)} unidentified skipped>", cls, None))

    text = "Backend tests: " + (str(counts) if reports else "NOT_RUN — prerequisite or Maven failure") + "\n"
    if allowed_hits:
        text += f"Allowed skips (profile {args.profile}, {len(allowed_hits)}):\n"
        for cls, method, key, (flag, runtime, reason) in sorted(allowed_hits):
            text += f"  - {cls}#{method} [{flag}; {runtime}] {reason}\n"
    if unexpected:
        text += f"NOT ALLOWED skips ({len(unexpected)}):\n"
        for cls, method, _key, _entry in sorted(unexpected):
            text += f"  - {cls}#{method}\n"
    text += "All populated fixtures are synthetic. Raw reports, binaries, credentials and backups are not uploaded.\n"
    if not any(Path("src/test/java").rglob("*Jpa*Test.java")):
        text += "Product JPA/shared JPA-JDBC transaction suites: NOT_AVAILABLE_IN_THIS_REVISION; Product integration gate pending.\n"
    print(text)
    if summary := os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(summary, "a") as output:
            output.write(text)
    if not reports or counts["tests"] == 0 or counts["failures"] or counts["errors"] or unexpected:
        raise SystemExit("Verification requires nonzero tests, zero failures/errors and no skip outside the allowlist")


if __name__ == "__main__":
    main(sys.argv[1:])
