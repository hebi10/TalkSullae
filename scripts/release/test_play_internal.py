import tempfile
import unittest
from pathlib import Path

from play_closed import PlaySafetyError
from play_internal import check_preflight, deploy, track_name, TRACK


class FakeClient:
    def __init__(self, prior=1, has_qa=True, upload_code=2):
        self.prior = prior
        self.has_qa = has_qa
        self.upload_code = upload_code
        self.created = 0
        self.deleted = []
        self.calls = []
        self.assigned = False
        self.readback_ok = True

    def create_edit(self):
        self.created += 1
        return "/androidpublisher/v3/applications/com.hebi.talksullae/edits/abc" + str(self.created)

    def delete_edit(self, edit):
        self.deleted.append(edit)

    def list_tracks(self, edit):
        if self.assigned and self.readback_ok:
            return [{"track": TRACK, "releases": [{"versionCodes": ["2"]}]}]
        if self.has_qa:
            return [{"track": TRACK, "releases": [{"versionCodes": [str(self.prior)]}]}]
        return [{"track": "alpha", "releases": []}]

    def list_codes(self, edit, tracks):
        return {self.prior} if self.prior else set()

    def request(self, method, path, data=None, binary=False):
        self.calls.append((method, path, "[binary]" if binary else data))
        if path.endswith("/bundles?uploadType=media"):
            return {"versionCode": self.upload_code}
        if method == "PUT" and path.endswith("/tracks/qa"):
            self.assigned = True
        return {}


class InternalPlayTests(unittest.TestCase):
    def test_exact_qa_only(self):
        self.assertEqual(track_name("qa"), "qa")
        for name in ("alpha", "internal", "beta", "production", "wear:qa", "", "qa/other"):
            with self.subTest(name=name):
                with self.assertRaises(PlaySafetyError):
                    track_name(name)

    def test_preflight_never_posts_or_publishes(self):
        fake = FakeClient()
        result = check_preflight(fake, "qa", 2)
        self.assertEqual(result["track"], "qa")
        self.assertEqual(result["current_max_code"], 1)
        self.assertEqual(fake.calls, [])
        self.assertEqual(len(fake.deleted), 1)

    def test_initial_internal_track_without_existing_release(self):
        fake = FakeClient(prior=0, has_qa=False)
        result = check_preflight(fake, "qa", 2)
        self.assertFalse(result["internal_track_exists"])
        self.assertFalse(result["api_committed"])

    def test_duplicate_code_preflight_fails(self):
        fake = FakeClient(prior=2)
        with self.assertRaisesRegex(PlaySafetyError, "versionCode"):
            check_preflight(fake, "qa", 2)
        self.assertEqual(fake.calls, [])

    def test_deploy_cannot_touch_non_qa(self):
        fake = FakeClient()
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "app.aab"
            path.write_bytes(b"x" * 15000)
            for target in ("alpha", "production", "beta", "wear:qa"):
                with self.assertRaises(PlaySafetyError):
                    deploy(fake, target, path, 2, "0.3.0", "내부 테스트")
        self.assertEqual(fake.calls, [])

    def test_deploy_duplicate_code_never_uploads(self):
        fake = FakeClient(prior=2)
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "app.aab"
            path.write_bytes(b"x" * 15000)
            with self.assertRaisesRegex(PlaySafetyError, "versionCode"):
                deploy(fake, "qa", path, 2, "0.3.0", "내부 테스트")
        self.assertFalse(any("/bundles?" in req[1] for req in fake.calls))

    def test_deploy_only_qa_and_verify_readback(self):
        fake = FakeClient(prior=1)
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "app.aab"
            path.write_bytes(b"x" * 15000)
            result = deploy(fake, "qa", path, 2, "0.3.0", "내부 테스트")
        self.assertTrue(result["api_committed"])
        self.assertEqual(result["verification"], "internal_qa_track_assigned")
        self.assertEqual([x[1] for x in fake.calls if x[0] == "PUT"],
                         [fake.calls[1][1]] if len(fake.calls) > 1 else [])
        self.assertTrue(all("/tracks/qa" in x[1] for x in fake.calls if x[0] == "PUT"))

    def test_edit_commit_without_readback_not_claimed_success(self):
        fake = FakeClient(prior=1)
        fake.readback_ok = False
        report = {}
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "app.aab"
            path.write_bytes(b"x" * 15000)
            with self.assertRaises(PlaySafetyError):
                deploy(fake, "qa", path, 2, "0.3.0", "내부 테스트", report=report)
        self.assertTrue(report["api_committed"])
        self.assertEqual(report["verification"], "not_confirmed")

    def test_invalid_bundle_refused(self):
        fake = FakeClient()
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "app.aab"
            path.write_bytes(b"x")
            with self.assertRaises(PlaySafetyError):
                deploy(fake, "qa", path, 2, "0.3.0", "내부 테스트")
        self.assertEqual(fake.calls, [])


if __name__ == "__main__":
    unittest.main()
