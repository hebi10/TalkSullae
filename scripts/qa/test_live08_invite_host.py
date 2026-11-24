"""Local contract checks for the opt-in HTTPS invitation feature."""
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("invite_host", ROOT / "scripts/release/prepare_invite_host.py")
host = importlib.util.module_from_spec(spec)
spec.loader.exec_module(host)


class InviteHostingTests(unittest.TestCase):
    def test_certificate_validation_and_association(self):
        value = "ab" * 32
        expected = ":".join(["AB"] * 32)
        self.assertEqual(host.normalize_fingerprint(value), expected)
        self.assertEqual(host.statement([value, expected])[0]["target"]["sha256_cert_fingerprints"], [expected])
        for invalid in ("", "11:22", "Z" * 64):
            with self.assertRaises(ValueError):
                host.normalize_fingerprint(invalid)
        with self.assertRaises(ValueError):
            host.statement([])

    def test_prepare_is_local_and_never_overwrites(self):
        with tempfile.TemporaryDirectory() as folder:
            dest = Path(folder) / "site"
            path = host.prepare(dest, ["ab" * 32])
            self.assertEqual(json.loads(path.read_text(encoding="utf-8"))[0]["target"]["package_name"], host.PACKAGE)
            self.assertTrue((dest / "talksullae/invite.html").is_file())
            self.assertTrue((dest / "talksullae/invite.js").is_file())
            with self.assertRaises(FileExistsError):
                host.prepare(dest, ["cd" * 32])

    def test_hosting_is_separate_and_secret_never_in_http_query(self):
        firebase = json.loads((ROOT / "firebase.json").read_text(encoding="utf-8"))
        self.assertNotIn("hosting", firebase)
        config = json.loads((ROOT / "firebase.invite-hosting.json").read_text(encoding="utf-8"))
        self.assertEqual(config["hosting"]["public"], "build/invite-hosting")
        self.assertEqual(config["hosting"]["rewrites"][0]["source"], "/talksullae/invite")
        code = (ROOT / "hosting/invite/public/talksullae/invite.js").read_text(encoding="utf-8")
        self.assertIn("window.location.hash", code)
        self.assertNotIn("fetch(", code)
        self.assertNotIn("localStorage", code)


if __name__ == "__main__":
    unittest.main()
