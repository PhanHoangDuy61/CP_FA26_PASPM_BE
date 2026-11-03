#!/usr/bin/env python3
"""Fetch only an operator-approved artifact and enforce the existing PASPM pin."""
import hashlib
import os
import re
from pathlib import Path
import sys
import tempfile
import urllib.parse
import urllib.request

APPROVED_SHA256 = "4aa468384693b2016b457ea310a08cc24aa83abd42d3169ad65b9027778dfe6a"
MAX_BYTES = 64 * 1024 * 1024


def _https_url(url, allow_query=False):
    if not isinstance(url, str) or any(ord(char) <= 32 or ord(char) == 127 for char in url) or "\\" in url:
        raise ValueError("Invalid artifact URL")
    parsed = urllib.parse.urlsplit(url)
    if (parsed.scheme != "https" or not parsed.hostname or parsed.username is not None or parsed.password is not None
            or parsed.port not in (None, 443) or parsed.fragment or (parsed.query and not allow_query)):
        raise ValueError("An approved credential-free HTTPS artifact URL is required")
    return parsed


def require_https(url):
    # Owner-configured input stays query-free; signed CDN URLs are never configuration.
    return _https_url(url)


class _DownloadPolicy:
    def __init__(self, source):
        parsed = require_https(source)
        self.origin = parsed.hostname
        release = re.fullmatch(r"/[^/]+/[^/]+/releases/download/([^/]+)/[^/]+", parsed.path)
        self.github_release = (self.origin == "github.com" and release is not None
                               and urllib.parse.unquote(release[1]).lower() not in ("latest", ".", ".."))

    def require_destination(self, url):
        parsed = _https_url(url, allow_query=True)
        if parsed.hostname == self.origin and not parsed.query:
            return
        if self.github_release and parsed.hostname == "release-assets.githubusercontent.com":
            return
        raise ValueError("Artifact redirect is outside the approved origin/CDN policy")


class HttpsRedirects(urllib.request.HTTPRedirectHandler):
    def __init__(self, source_url=None):
        super().__init__()
        self.policy = _DownloadPolicy(source_url) if source_url is not None else None

    def redirect_request(self, request, fp, code, message, headers, new_url):
        if request is None or request.get_method() not in ("GET", "HEAD"):
            raise ValueError("Only artifact GET/HEAD redirects are supported")
        if self.policy is None:
            self.policy = _DownloadPolicy(request.full_url)
        self.policy.require_destination(new_url)
        return super().redirect_request(request, fp, code, message, headers, new_url)


def provision(url, destination):
    require_https(url)
    destination = Path(destination)
    if destination.exists() or destination.is_symlink():
        raise ValueError("Artifact destination must be new")
    destination.parent.mkdir(parents=True, exist_ok=True)
    redirects = HttpsRedirects(url)
    opener = urllib.request.build_opener(redirects)
    fd, temporary = tempfile.mkstemp(prefix="spmf-verified-", dir=destination.parent)
    try:
        digest, size = hashlib.sha256(), 0
        with os.fdopen(fd, "wb") as output, opener.open(url, timeout=60) as response:
            redirects.policy.require_destination(response.geturl())
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
