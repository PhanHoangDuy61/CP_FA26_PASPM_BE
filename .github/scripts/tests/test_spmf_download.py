"""Downloader policy/negative tests; no JAR, secret or external request required."""
import contextlib
import importlib.util
import io
import os
from pathlib import Path
import runpy
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
import urllib.request

SCRIPT = Path(__file__).resolve().parents[1] / 'provision-spmf.py'
spec = importlib.util.spec_from_file_location('spmf_download', SCRIPT)
spmf = importlib.util.module_from_spec(spec)
spec.loader.exec_module(spmf)
SOURCE = 'https://github.com/approved-owner/runtime/releases/download/spmf-2.67a/spmf.jar'
SIGNED = 'https://release-assets.githubusercontent.com/github-production-release-asset/1/asset?sig=TEST_SIGNED_MARKER&se=fixture'


class Response(io.BytesIO):
    def __init__(self, content, url):
        super().__init__(content)
        self.url = url

    def geturl(self):
        return self.url


class SpmfDownloadTest(unittest.TestCase):
    def redirect(self, target, source=SOURCE, method='GET', handler=None):
        return (handler or spmf.HttpsRedirects()).redirect_request(
            urllib.request.Request(source, method=method), None, 302, '', {}, target)

    def test_github_fixed_release_allows_signed_official_asset_redirect(self):
        result = self.redirect(SIGNED)
        self.assertEqual(SIGNED, result.full_url)

    def test_signed_release_final_response_reaches_checksum_check(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(spmf.urllib.request, 'build_opener') as opener:
            response = Response(b'not the approved jar', SIGNED)
            opener.return_value.open.return_value = response
            with self.assertRaisesRegex(ValueError, 'checksum'):
                spmf.provision(SOURCE, Path(directory) / 'spmf.jar')
            self.assertEqual([], list(Path(directory).iterdir()))

    def test_unrelated_https_redirect_is_rejected(self):
        with self.assertRaises(ValueError):
            self.redirect('https://unapproved.example/spmf.jar')

    def test_official_cdn_is_not_trusted_for_custom_origin(self):
        with self.assertRaises(ValueError):
            self.redirect(SIGNED, source='https://approved.example/spmf.jar')

    def test_same_origin_unsigned_redirect_is_allowed(self):
        target = 'https://approved.example/fixed/sha256/spmf.jar'
        self.assertEqual(target, self.redirect(target, source='https://approved.example/spmf.jar').full_url)

    def test_input_signed_query_remains_rejected(self):
        with self.assertRaises(ValueError):
            spmf.require_https(SIGNED)

    def test_input_userinfo_fragment_and_http_are_rejected(self):
        for source in ('http://approved.example/a', 'https://user:pass@approved.example/a',
                       'https://approved.example/a#fragment', 'https://approved.example/a?token=x'):
            with self.subTest(source=source), self.assertRaises(ValueError):
                spmf.require_https(source)

    def test_empty_userinfo_is_rejected(self):
        with self.assertRaises(ValueError):
            spmf.require_https('https://@approved.example/spmf.jar')

    def test_redirect_http_downgrade_rejected(self):
        with self.assertRaises(ValueError):
            self.redirect('http://release-assets.githubusercontent.com/asset?sig=x')

    def test_redirect_userinfo_fragment_and_port_are_rejected(self):
        for target in ('https://user:pass@release-assets.githubusercontent.com/a?sig=x',
                       'https://release-assets.githubusercontent.com/a?sig=x#fragment',
                       'https://release-assets.githubusercontent.com:8443/a?sig=x'):
            with self.subTest(target=target), self.assertRaises(ValueError):
                self.redirect(target)

    def test_spoofed_cdn_host_and_trailing_dot_rejected(self):
        for host in ('release-assets.githubusercontent.com.evil.example',
                     'evil-release-assets.githubusercontent.com', 'release-assets.githubusercontent.com.'):
            with self.subTest(host=host), self.assertRaises(ValueError):
                self.redirect('https://' + host + '/a?sig=x')

    def test_github_latest_and_nonrelease_origin_cannot_delegate_to_cdn(self):
        for source in ('https://github.com/approved-owner/runtime/releases/latest/download/spmf.jar',
                       'https://github.com/approved-owner/runtime/blob/main/spmf.jar',
                       'https://github.com/approved-owner/runtime/releases/download/latest/spmf.jar'):
            with self.subTest(source=source), self.assertRaises(ValueError):
                self.redirect(SIGNED, source=source)

    def test_query_is_not_allowed_on_same_origin_redirect(self):
        with self.assertRaises(ValueError):
            self.redirect('https://approved.example/asset?sig=x', source='https://approved.example/spmf.jar')

    def test_non_get_head_redirect_is_rejected(self):
        with self.assertRaises(ValueError):
            self.redirect(SIGNED, method='POST')

    def test_chain_cannot_leave_official_cdn(self):
        handler = spmf.HttpsRedirects()
        first = self.redirect(SIGNED, handler=handler)
        with self.assertRaises(ValueError):
            handler.redirect_request(first, None, 302, '', {}, 'https://unapproved.example/a')

    def test_wrong_bytes_are_removed_after_download(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(spmf.urllib.request, 'build_opener') as opener:
            opener.return_value.open.return_value = Response(b'fake jar', SOURCE)
            with self.assertRaises(ValueError):
                spmf.provision(SOURCE, Path(directory) / 'spmf.jar')
            self.assertEqual([], list(Path(directory).iterdir()))

    def test_oversized_signed_response_leaves_no_file(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(spmf, 'MAX_BYTES', 1), patch.object(spmf.urllib.request, 'build_opener') as opener:
            opener.return_value.open.return_value = Response(b'xx', SIGNED)
            with self.assertRaises(ValueError):
                spmf.provision(SOURCE, Path(directory) / 'spmf.jar')
            self.assertEqual([], list(Path(directory).iterdir()))

    def test_existing_destination_is_not_overwritten(self):
        with tempfile.TemporaryDirectory() as directory:
            destination = Path(directory) / 'spmf.jar'
            destination.write_bytes(b'keep')
            with self.assertRaises(ValueError):
                spmf.provision(SOURCE, destination)
            self.assertEqual(b'keep', destination.read_bytes())

    def test_cli_never_logs_signed_url_from_transport_error(self):
        with tempfile.TemporaryDirectory() as directory, patch.dict(os.environ, {
                'PASPM_CI_SPMF_ARTIFACT_URL': SOURCE, 'SPMF_JAR_PATH': str(Path(directory) / 'spmf.jar')}), \
                patch.object(urllib.request, 'build_opener', side_effect=RuntimeError(SIGNED)):
            stderr = io.StringIO()
            with contextlib.redirect_stderr(stderr), self.assertRaises(SystemExit) as result:
                runpy.run_path(str(SCRIPT), run_name='__main__')
            self.assertEqual(1, result.exception.code)
            self.assertNotIn('TEST_SIGNED_MARKER', stderr.getvalue())
            self.assertNotIn('https://', stderr.getvalue())
            self.assertEqual([], list(Path(directory).iterdir()))

    def test_missing_url_exits_before_any_request(self):
        with tempfile.TemporaryDirectory() as directory, patch.dict(os.environ, {
                'PASPM_CI_SPMF_ARTIFACT_URL': '', 'SPMF_JAR_PATH': str(Path(directory) / 'spmf.jar')}), \
                patch.object(urllib.request, 'build_opener') as opener:
            with contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit):
                runpy.run_path(str(SCRIPT), run_name='__main__')
            opener.assert_not_called()
            self.assertEqual([], list(Path(directory).iterdir()))
