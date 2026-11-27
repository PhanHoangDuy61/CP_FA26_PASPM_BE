"""Unit tests for the demo mining policy step (mining_policy.py): one update only when it differs, HTTP layer faked.

Run from the repository root:  python3 -I -m unittest discover -s scripts/seed/tests -v
"""
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))  # python -I does not add the script directory

import mining_policy as mp  # noqa: E402


class FakeApiError(Exception):
    def __init__(self, status, body="boom"):
        super().__init__(f"HTTP {status}: {body}")
        self.status = status


class FakeBackend:
    """GET/PUT /api/system/mining/config of the System Admin, with the optimistic version of the real endpoint."""

    def __init__(self, version=0, mode=None, value=None, fail_get=None, fail_put=None):
        self.version, self.mode, self.value = version, mode, value
        self.fail_get, self.fail_put = fail_get, fail_put
        self.calls = []

    def __call__(self, method, path, token=None, body=None):
        self.calls.append((method, path, token, body))
        assert path == "/api/system/mining/config", path
        if method == "GET":
            if self.fail_get:
                raise FakeApiError(self.fail_get)
            return {"version": self.version, "algorithm": "EFIM", "minUtilMode": self.mode, "minUtilValue": self.value,
                    "changedBy": None, "changedAt": None}
        assert method == "PUT", method
        if self.fail_put:
            raise FakeApiError(self.fail_put)
        if body["expectedVersion"] != self.version:
            raise FakeApiError(409, "Mining configuration version conflict")
        self.version += 1
        self.mode, self.value = body["minUtilMode"], body["minUtilValue"]
        return {"version": self.version, "algorithm": "EFIM", "minUtilMode": self.mode, "minUtilValue": self.value}

    def puts(self):
        return [c for c in self.calls if c[0] == "PUT"]


class SeedWiringTest(unittest.TestCase):
    def test_the_seed_calls_the_step_with_its_own_api_function_and_the_system_admin_token(self):
        source = (Path(__file__).resolve().parent.parent / "seed_demo.py").read_text(encoding="utf-8")
        self.assertIn("import mining_policy", source)
        self.assertIn("mining_policy.ensure_mining_policy(api, admin)", source)
        self.assertIn("mining_policy.PolicyError", source)  # a failure ends the seed with a clear message


class PolicyConstantsTest(unittest.TestCase):
    def test_the_demo_policy_is_percentage_0_12(self):
        self.assertEqual((mp.MODE, str(mp.VALUE)), ("PERCENTAGE", "0.12"))


class EnsureMiningPolicyTest(unittest.TestCase):
    def test_a_different_value_gives_exactly_one_update_with_the_current_version(self):
        backend = FakeBackend(version=3, mode="PERCENTAGE", value=0.25)
        result = mp.ensure_mining_policy(backend, "admin-token")
        self.assertEqual(len(backend.puts()), 1)
        method, path, token, body = backend.puts()[0]
        self.assertEqual(token, "admin-token")
        self.assertEqual(body, {"minUtilMode": "PERCENTAGE", "minUtilValue": 0.12, "expectedVersion": 3})  # no other field (strict DTO)
        self.assertEqual((backend.version, backend.mode, backend.value), (4, "PERCENTAGE", 0.12))
        self.assertIn("updated", result)
        self.assertIn("version 4", result)

    def test_before_the_first_configuration_the_update_expects_version_0(self):
        backend = FakeBackend(version=0, mode=None, value=None)
        mp.ensure_mining_policy(backend, "t")
        self.assertEqual([c[3]["expectedVersion"] for c in backend.puts()], [0])
        self.assertEqual(backend.version, 1)

    def test_a_different_mode_with_the_same_number_is_updated(self):
        backend = FakeBackend(version=2, mode="ABSOLUTE", value=0.12)
        mp.ensure_mining_policy(backend, "t")
        self.assertEqual(len(backend.puts()), 1)
        self.assertEqual(backend.mode, "PERCENTAGE")

    def test_already_percentage_0_12_makes_no_write(self):
        backend = FakeBackend(version=5, mode="PERCENTAGE", value=0.12)
        result = mp.ensure_mining_policy(backend, "t")
        self.assertEqual(backend.puts(), [])
        self.assertEqual(backend.version, 5)
        self.assertIn("already", result)
        self.assertIn("version 5", result)

    def test_the_number_is_compared_as_a_decimal_not_as_text(self):
        for stored in (0.12, 0.120000, "0.120000", 0.12 * 1):
            backend = FakeBackend(version=1, mode="PERCENTAGE", value=stored)
            mp.ensure_mining_policy(backend, "t")
            self.assertEqual(backend.puts(), [], stored)

    def test_a_close_but_different_value_is_updated(self):
        backend = FakeBackend(version=1, mode="PERCENTAGE", value=0.121)
        mp.ensure_mining_policy(backend, "t")
        self.assertEqual(len(backend.puts()), 1)

    def test_a_second_run_changes_nothing(self):
        backend = FakeBackend(version=7, mode="PERCENTAGE", value=0.5)
        mp.ensure_mining_policy(backend, "t")
        after_first = (backend.version, backend.mode, backend.value, len(backend.puts()))
        mp.ensure_mining_policy(backend, "t")
        mp.ensure_mining_policy(backend, "t")
        self.assertEqual((backend.version, backend.mode, backend.value, len(backend.puts())), after_first)
        self.assertEqual(after_first[3], 1)


class ErrorHandlingTest(unittest.TestCase):
    def test_an_error_reading_the_policy_stops_with_a_clear_message_and_no_write(self):
        backend = FakeBackend(fail_get=500)
        with self.assertRaises(mp.PolicyError) as caught:
            mp.ensure_mining_policy(backend, "t")
        self.assertIn("GET /api/system/mining/config", str(caught.exception))
        self.assertEqual(backend.puts(), [])

    def test_an_error_on_the_update_stops_with_a_clear_message_and_changes_nothing(self):
        for status in (400, 403, 409, 500):
            backend = FakeBackend(version=2, mode="PERCENTAGE", value=0.25, fail_put=status)
            with self.assertRaises(mp.PolicyError) as caught:
                mp.ensure_mining_policy(backend, "t")
            message = str(caught.exception)
            self.assertIn("PUT /api/system/mining/config", message)
            self.assertIn(str(status), message)
            self.assertEqual(len(backend.puts()), 1, "no retry loop")
            self.assertEqual((backend.version, backend.value), (2, 0.25), "the stored policy is unchanged")

    def test_a_concurrent_change_is_reported_not_overwritten_blindly(self):
        class Racing(FakeBackend):
            def __call__(self, method, path, token=None, body=None):
                result = super().__call__(method, path, token, body)
                if method == "GET":
                    self.version += 1  # somebody else saved a new version after we read
                return result
        backend = Racing(version=2, mode="PERCENTAGE", value=0.25)
        with self.assertRaises(mp.PolicyError) as caught:
            mp.ensure_mining_policy(backend, "t")
        self.assertIn("409", str(caught.exception))
        self.assertEqual(len(backend.puts()), 1)


if __name__ == "__main__":
    unittest.main()
