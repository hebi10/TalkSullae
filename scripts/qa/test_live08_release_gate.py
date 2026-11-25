"""LIVE-08 source gate regression tests. No network, Play or Firebase writes."""
import importlib.util
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/qa/live08_release_gate.py"
spec = importlib.util.spec_from_file_location("live08_gate", SCRIPT)
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)


class Live08ReleaseGateTests(unittest.TestCase):
    def test_current_candidate_passes_source_but_not_external_gates(self):
        result = gate.audit()
        self.assertEqual(result["status"], "source_pass", result["failed_source_checks"])
        self.assertEqual(result["candidate_version"], {"name": "0.6.0", "code": 7})
        self.assertFalse(result["firebase_deployed"])
        self.assertFalse(result["play_uploaded"])
        self.assertFalse(result["release_complete"])
        self.assertGreaterEqual(len(result["external_gates"]), 7)

    def test_missing_files_always_block_release(self):
        with tempfile.TemporaryDirectory() as folder:
            result = gate.audit(Path(folder))
        self.assertEqual(result["status"], "source_blocked")
        self.assertIn("firebase_server_exports", result["failed_source_checks"])
        self.assertIn("play_internal_manual_approval", result["failed_source_checks"])

    def test_reusing_published_code_fails_monotonic_rule(self):
        code, name = gate.version('versionCode = 6\nversionName = "0.5.0"\n')
        self.assertEqual(code, gate.INTERNAL_PUBLISHED_VERSION_CODE)
        self.assertEqual(name, "0.5.0")
        self.assertFalse(code > gate.INTERNAL_PUBLISHED_VERSION_CODE)

    def test_audit_has_no_upload_or_token_api_calls(self):
        content = SCRIPT.read_text(encoding="utf-8")
        for forbidden in ("google.auth", "requests.post", "firebase deploy",
                          "play.publish", "id-token: write", "urllib.request"):
            with self.subTest(text=forbidden):
                self.assertNotIn(forbidden, content)


if __name__ == "__main__":
    unittest.main()
