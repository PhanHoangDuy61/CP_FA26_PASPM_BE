import hashlib
import importlib.util
import io
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch


def load(name):
    path = Path(__file__).resolve().parents[1] / ("provision-" + name + ".py")
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


spmf, restore = load("spmf"), load("restore")


class Response(io.BytesIO):
    def geturl(self):
        return "https://approved.example/spmf.jar"


class CiHelpersTest(unittest.TestCase):
    def test_missing_or_unsafe_artifact_urls_refuse(self):
        for value in ("", "http://example/a", "https://user:pass@example/a", "file:///tmp/a", "https://example/a?token=x", "https://example/a#x"):
            with self.subTest(value=value), self.assertRaises(ValueError):
                spmf.require_https(value)

    def test_https_downgrade_refuses(self):
        with self.assertRaises(ValueError):
            spmf.HttpsRedirects().redirect_request(None, None, 302, "", {}, "http://example/a")

    def test_wrong_hash_leaves_no_artifact(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(spmf.urllib.request, "build_opener") as opener:
            opener.return_value.open.return_value = Response(b"wrong")
            with self.assertRaises(ValueError):
                spmf.provision("https://approved.example/a", Path(directory) / "spmf.jar")
            self.assertEqual([], list(Path(directory).iterdir()))

    def test_oversized_response_refuses(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(spmf, "MAX_BYTES", 1), patch.object(spmf.urllib.request, "build_opener") as opener:
            opener.return_value.open.return_value = Response(b"xx")
            with self.assertRaises(ValueError):
                spmf.provision("https://approved.example/a", Path(directory) / "spmf.jar")

    def test_approved_bytes_are_accepted(self):
        # Requires the same externally provisioned artifact as Backend integration tests.
        content = Path(os.environ["SPMF_JAR_PATH"]).read_bytes()
        self.assertEqual(spmf.APPROVED_SHA256, hashlib.sha256(content).hexdigest())
        with tempfile.TemporaryDirectory() as directory, patch.object(spmf.urllib.request, "build_opener") as opener:
            opener.return_value.open.return_value = Response(content)
            destination = Path(directory) / "spmf.jar"
            spmf.provision("https://approved.example/a", destination)
            self.assertEqual(content, destination.read_bytes())
            self.assertEqual(0o600, destination.stat().st_mode & 0o777)

    def test_approved_bytes_via_signed_release_response(self):
        content = Path(os.environ["SPMF_JAR_PATH"]).read_bytes()
        self.assertEqual(spmf.APPROVED_SHA256, hashlib.sha256(content).hexdigest())
        class SignedResponse(Response):
            def geturl(self):
                return "https://release-assets.githubusercontent.com/github-production-release-asset/1/asset?sig=FIXTURE"
        with tempfile.TemporaryDirectory() as directory, patch.object(spmf.urllib.request, "build_opener") as opener:
            opener.return_value.open.return_value = SignedResponse(content)
            destination = Path(directory) / "spmf.jar"
            spmf.provision("https://github.com/approved-owner/runtime/releases/download/spmf-2.67a/spmf.jar", destination)
            self.assertEqual(content, destination.read_bytes())
            self.assertEqual(0o600, destination.stat().st_mode & 0o777)

    def test_existing_destination_not_overwritten(self):
        with tempfile.TemporaryDirectory() as directory:
            destination = Path(directory) / "spmf.jar"
            destination.write_bytes(b"keep")
            with self.assertRaises(ValueError):
                spmf.provision("https://approved.example/a", destination)
            self.assertEqual(b"keep", destination.read_bytes())

    def test_missing_disposable_optin_refuses_before_cli(self):
        with patch.dict(os.environ, {}, clear=True), patch.object(restore.subprocess, "run") as run:
            with self.assertRaises(ValueError):
                restore.provision({})
            run.assert_not_called()

    def test_nonloopback_and_injection_refuse_before_cli(self):
        base = {"schema": "paspm_p0_test_" + "a" * 32, "username": "bc_restore_" + "b" * 12, "password": "c" * 32}
        variants = (("server", "jdbc:mysql://database.example:3306/"), ("server", "jdbc:mysql://127.0.0.1:99999/"),
                    ("schema", "paspm"), ("username", "x'; DROP DATABASE y; --"), ("password", "x'; DROP DATABASE y; --"))
        for key, value in variants:
            env = {"PASPM_CI_DISPOSABLE_MYSQL": "true", "PASPM_TEST_MYSQL_SERVER": "jdbc:mysql://127.0.0.1:3306/"}
            data = base.copy()
            if key == "server":
                env["PASPM_TEST_MYSQL_SERVER"] = value
            else:
                data[key] = value
            with self.subTest(key=key), patch.dict(os.environ, env, clear=True), patch.object(restore.subprocess, "run") as run:
                with self.assertRaises(ValueError):
                    restore.provision(data)
                run.assert_not_called()
