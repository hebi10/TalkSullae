import tempfile
import unittest
from pathlib import Path

from play_closed import PlaySafetyError, check_preflight, deploy, release_code, track_name
from verify_aab import version_from_source


class FakeClient:
    def __init__(self, *, track="alpha", previous=1):
        self.track = track
        self.previous = previous
        self.current = previous
        self.created = 0
        self.deleted = []
        self.calls = []

    def create_edit(self):
        self.created += 1
        return "/androidpublisher/v3/applications/com.hebi.talksullae/edits/edit" + str(self.created)

    def delete_edit(self, edit):
        self.deleted.append(edit)

    def list_tracks(self, edit):
        return [{"track":self.track, "releases":[{"versionCodes":[str(self.current)],
                "status":"completed"}]}]

    def list_codes(self, edit, tracks):
        return {self.previous}

    def request(self, method, path, data=None, binary=False):
        self.calls.append((method, path, data if not binary else "[binary]"))
        if path.endswith("/bundles?uploadType=media"):
            return {"versionCode":2}
        if method == "PUT" and "/tracks/" in path:
            self.current = 2
        return {}


class TestPlaySafety(unittest.TestCase):
    def test_closed_track_rejects_production_open_internal_and_malformed(self):
        for track in ["production", "internal", "qa", "open", "beta",
                      "wear:production", "../alpha", "", "alpha/test"]:
            with self.subTest(track=track):
                with self.assertRaises(PlaySafetyError):
                    track_name(track)
        self.assertEqual(track_name("alpha"), "alpha")
        self.assertEqual(track_name("closed-custom-1"), "closed-custom-1")

    def test_version_code_bounds(self):
        self.assertEqual(release_code("2"), 2)
        for value in [0, -1, True, "00", "1.5", 2100000001, None]:
            with self.subTest(value=value):
                with self.assertRaises(PlaySafetyError):
                    release_code(value)

    def test_source_version_unique_and_expected(self):
        code, name = version_from_source(
            Path(__file__).resolve().parents[2] / "app/build.gradle.kts"
        )
        self.assertEqual(code, 2)
        self.assertEqual(name, "0.3.0")

    def test_preflight_never_uploads(self):
        fake = FakeClient()
        result = check_preflight(fake, "alpha")
        self.assertEqual(result["check"], "passed")
        self.assertEqual(result["current_max_code"], 1)
        self.assertEqual(len(fake.deleted), 1)
        self.assertEqual(fake.calls, [])

    def test_preflight_rejects_missing_closed_track(self):
        fake = FakeClient(track="internal")
        with self.assertRaises(PlaySafetyError):
            check_preflight(fake, "alpha")
        self.assertEqual(len(fake.deleted), 1)

    def test_duplicate_version_is_rejected_before_upload(self):
        fake = FakeClient(previous=2)
        with tempfile.TemporaryDirectory() as tmp:
            bundle = Path(tmp)/"test.aab"
            bundle.write_bytes(b"0" * 15000)
            with self.assertRaises(PlaySafetyError):
                deploy(fake, "alpha", bundle, 2, "0.3.0", "테스트")
        self.assertEqual(len(fake.deleted), 1)
        self.assertFalse(any("/bundles?uploadType" in path for _,path,_ in fake.calls))

    def test_failed_wrong_track_can_never_upload(self):
        fake = FakeClient(track="production")
        with tempfile.TemporaryDirectory() as tmp:
            bundle = Path(tmp)/"test.aab"
            bundle.write_bytes(b"0" * 15000)
            with self.assertRaises(PlaySafetyError):
                deploy(fake, "alpha", bundle, 2, "0.3.0", "테스트")
        self.assertEqual(len(fake.deleted), 1)

    def test_committed_but_unconfirmed_result_retains_commit_flag(self):
        class NoReadback(FakeClient):
            def list_tracks(self, edit):
                if self.created >= 2:
                    return [{"track":"alpha","releases":[{"versionCodes":["1"],"status":"completed"}]}]
                return super().list_tracks(edit)

        fake = NoReadback()
        report = {"api_committed": False}
        with tempfile.TemporaryDirectory() as tmp:
            bundle = Path(tmp) / "test.aab"
            bundle.write_bytes(b"0" * 15000)
            with self.assertRaises(PlaySafetyError):
                deploy(fake, "alpha", bundle, 2, "0.3.0", "비공개 테스트", report=report)
        self.assertTrue(report["api_committed"])
        self.assertEqual(report["verification"], "not_confirmed")

    def test_success_requires_commit_then_track_readback(self):
        fake = FakeClient()
        with tempfile.TemporaryDirectory() as tmp:
            bundle = Path(tmp)/"test.aab"
            bundle.write_bytes(b"0" * 15000)
            result = deploy(fake, "alpha", bundle, 2, "0.3.0", "비공개 테스트")
        self.assertTrue(result["api_committed"])
        # FakeClient reflects the track update after commit, as Play should.
        self.assertEqual(result["verification"], "track_assigned")


if __name__ == "__main__":
    unittest.main()
