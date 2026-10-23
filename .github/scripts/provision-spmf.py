#!/usr/bin/env python3
"""Fetch only an operator-approved artifact and enforce the existing PASPM pin."""
import hashlib
import os
from pathlib import Path
import sys
import tempfile
import urllib.parse
import urllib.request

APPROVED_SHA256 = "4aa468384693b2016b457ea310a08cc24aa83abd42d3169ad65b9027778dfe6a"
MAX_BYTES = 64 * 1024 * 1024


def require_https(url):
    parsed = urllib.parse.urlsplit(url)
    if (parsed.scheme != "https" or not parsed.hostname or parsed.username or parsed.password
            or parsed.query or parsed.fragment):
        raise ValueError("An approved credential-free HTTPS artifact URL is required")


class HttpsRedirects(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, message, headers, new_url):
        require_https(new_url)
        return super().redirect_request(request, fp, code, message, headers, new_url)


def provision(url, destination):
    require_https(url)
    destination = Path(destination)
    if destination.exists() or destination.is_symlink():
        raise ValueError("Artifact destination must be new")
    destination.parent.mkdir(parents=True, exist_ok=True)
    fd, temporary = tempfile.mkstemp(prefix="spmf-verified-", dir=destination.parent)
    try:
        digest, size = hashlib.sha256(), 0
        opener = urllib.request.build_opener(HttpsRedirects())
        with os.fdopen(fd, "wb") as output, opener.open(url, timeout=60) as response:
            require_https(response.geturl())
            while chunk := response.read(65536):
                size += len(chunk)
                if size > MAX_BYTES:
                    raise ValueError("Artifact exceeds size limit")
                digest.update(chunk)
                output.write(chunk)
        if digest.hexdigest() != APPROVED_SHA256:
            raise ValueError("Artifact does not match the approved SPMF checksum")
        os.replace(temporary, destination)
    finally:
        Path(temporary).unlink(missing_ok=True)


if __name__ == "__main__":
    try:
        provision(os.environ.get("PASPM_CI_SPMF_ARTIFACT_URL", ""), os.environ["SPMF_JAR_PATH"])
    except Exception:
        # URLs may contain signed query parameters; never include them in diagnostics.
        print("::error::SPMF provisioning unavailable: configure an approved HTTPS artifact matching the project checksum. Tests have NOT run.", file=sys.stderr)
        sys.exit(1)
    print("Approved SPMF checksum verified; no artifact is committed or uploaded.")
