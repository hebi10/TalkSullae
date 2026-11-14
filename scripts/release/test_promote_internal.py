#!/usr/bin/env python3
"""No-network safety tests for the existing-AAB internal -> closed release."""
import unittest
from pathlib import Path

from play_closed import PlaySafetyError
from promote_internal import EXPECTED_CODE, inspect, promote
from verify_aab import version_from_source


class FakeClient:
    def __init__(self, *, code=3, internal_status="completed", closed=None,
                 include_internal=True, include_closed=True, bundle=True):
        self.code = code
        self.bundle = bundle
        self.created = 0
        self.deleted = []
        self.calls = []
        self.tracks = []
        if include_internal:
            self.tracks.append({"track": "qa", "releases": [
                {"versionCodes": [str(code)], "status": internal_status}]})
        if include_closed:
            self.tracks.append({"track": "alpha", "releases": closed or []})

    def create_edit(self):
        self.created += 1
        return "/androidpublisher/v3/applications/com.hebi.talksullae/edits/edit" + str(self.created)

    def delete_edit(self, edit):
        self.deleted.append(edit)

    def list_tracks(self, edit):
        return self.tracks

    def request(self, method, path, data=None, binary=False):
        self.calls.append((method, path, data))
        if method == "GET" and path.endswith("/bundles"):
            return {"bundles": [{"versionCode": self.code}] if self.bundle else []}
        if method == "PUT" and path.endswith("/tracks/alpha"):
            for item in self.tracks:
                if item["track"] == "alpha":
                    item["releases"] = data["releases"]
        return {}


class PromotionTests(unittest.TestCase):
    def test_exact_release_matches_gradle(self):
        gradle = Path(__file__).resolve().parents[2] / "app/build.gradle.kts"
        self.assertEqual(EXPECTED_CODE, 3)
        code, name = version_from_source(gradle)
        self.assertGreater(code, EXPECTED_CODE)
        self.assertEqual((code, name), (4, "0.4.0"))

    def test_missing_track_variable_uses_only_verified_alpha_fallback(self):
        workflow = (Path(__file__).resolve().parents[2]
                    / ".github/workflows/play-promote-closed.yml").read_text(encoding="utf-8")
        fallback = "PLAY_CLOSED_TRACK: ${{ vars.PLAY_CLOSED_TRACK || 'alpha' }}"
        self.assertEqual(workflow.count(fallback), 4)
        self.assertNotIn("PLAY_CLOSED_TRACK: ${{ vars.PLAY_CLOSED_TRACK }}", workflow)
        self.assertIn("--track \"$PLAY_CLOSED_TRACK\"", workflow)

    def test_check_only_reads(self):
        fake = FakeClient()
        result = inspect(fake, "alpha")
        self.assertTrue(result["eligible"])
        self.assertTrue(result["bundle_exists"])
        self.assertEqual(len(fake.deleted), 1)
        self.assertFalse(any(method in ("PUT", "POST") for method, _, _ in fake.calls))

    def test_success_promotes_without_upload_and_reads_back(self):
        fake = FakeClient()
        report = {"api_committed": False}
        result = promote(fake, "alpha", "톡술래 비공개 테스트", report)
        self.assertTrue(result["api_committed"])
        self.assertEqual(result["verification"], "track_assigned")
        self.assertEqual(result["stage"], "complete")
        self.assertFalse(any("/upload/" in path or "uploadType" in path
                             for _, path, _ in fake.calls))
        self.assertEqual(
            [method for method, _, _ in fake.calls if method in ("PUT", "POST")],
            ["PUT", "POST", "POST"],
        )

    def test_already_promoted_is_noop(self):
        fake = FakeClient(closed=[{"versionCodes": ["3"], "status": "completed"}])
        report = {"api_committed": False}
        result = promote(fake, "alpha", "톡술래 비공개 테스트", report)
        self.assertTrue(result["already_promoted"])
        self.assertFalse(result["api_committed"])
        self.assertEqual(result["verification"], "already_assigned")
        self.assertFalse(any(method in ("PUT", "POST") for method, _, _ in fake.calls))

    def test_disallowed_destination(self):
        for track in ("qa", "production", "beta", "internal", "open", "wear:production"):
            with self.subTest(track=track):
                fake = FakeClient()
                with self.assertRaises(PlaySafetyError):
                    inspect(fake, track)
                self.assertEqual(fake.calls, [])

    def test_wrong_source_version_is_rejected(self):
        fake = FakeClient()
        with self.assertRaises(PlaySafetyError):
            inspect(fake, "alpha", 4)
        self.assertEqual(fake.calls, [])

    def test_uninitialized_tracks_are_rejected(self):
        for kwargs in ({"include_internal": False}, {"include_closed": False}):
            with self.subTest(kwargs=kwargs):
                fake = FakeClient(**kwargs)
                with self.assertRaises(PlaySafetyError):
                    inspect(fake, "alpha")
                self.assertEqual(len(fake.deleted), 1)

    def test_incomplete_internal_or_missing_bundle_rejected(self):
        for kwargs in ({"internal_status": "draft"}, {"bundle": False}):
            with self.subTest(kwargs=kwargs):
                fake = FakeClient(**kwargs)
                with self.assertRaises(PlaySafetyError):
                    inspect(fake, "alpha")
                self.assertEqual(len(fake.deleted), 1)

    def test_closed_draft_or_newer_version_rejected(self):
        for release in ({"versionCodes": ["2"], "status": "draft"},
                        {"versionCodes": ["4"], "status": "completed"},
                        {"versionCodes": ["2"], "status": "inProgress"}):
            with self.subTest(release=release):
                fake = FakeClient(closed=[release])
                with self.assertRaises(PlaySafetyError):
                    inspect(fake, "alpha")
                self.assertFalse(any(method == "PUT" for method, _, _ in fake.calls))

    def test_unfinished_existing_closed_release_never_overwritten(self):
        fake = FakeClient(closed=[{"versionCodes": ["2"], "status": "completed"}])
        report = {"api_committed": False}
        with self.assertRaises(PlaySafetyError):
            promote(fake, "alpha", "톡술래 비공개 테스트", report)
        self.assertFalse(report["api_committed"])
        self.assertFalse(any(method == "PUT" for method, _, _ in fake.calls))

    def test_invalid_release_notes_cannot_update(self):
        fake = FakeClient()
        with self.assertRaises(PlaySafetyError):
            promote(fake, "alpha", "<test>", {"api_committed": False})
        self.assertFalse(any(method == "PUT" for method, _, _ in fake.calls))


if __name__ == "__main__":
    unittest.main()

# RELEASE-04 explicit one-time deployment request: internal 0.3.1 (3) -> existing closed alpha.
# Production, internal writes, and AAB reuploads remain forbidden by the guarded workflow.
