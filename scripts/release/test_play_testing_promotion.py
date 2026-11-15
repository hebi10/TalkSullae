"""Offline tests for the combined Play testing workflow's closed stage."""
import unittest
from unittest.mock import Mock

import play_testing_promotion as testing
from ops05.promotion import Stop

SHA = "a" * 40
DIGEST = "b" * 64
CERTIFICATE = "c" * 64
PACKAGE = "com.hebi.talksullae"


def request_env(**updates):
    return {
        "GITHUB_EVENT_NAME": "workflow_dispatch",
        "GITHUB_REPOSITORY": "hebi10/TalkSullae",
        "GITHUB_REF": "refs/heads/main",
        "GITHUB_SHA": SHA,
        "EXPECTED_MAIN_SHA": SHA,
        "BATCH_ID": "2026-11-16-talksullae",
        "CLOSED_APPROVED": "true",
        "CLOSED_TRACK": "alpha",
        "VERSION_CODE": "6",
        "PLAY_UPLOAD_CERT_SHA256": CERTIFICATE,
        **updates,
    }


def verified_manifest(**updates):
    return {
        "package": PACKAGE,
        "sourceSha": SHA,
        "versionCode": 6,
        "aabSha256": DIGEST,
        "signerCertificateSha256": CERTIFICATE,
        "signed": True,
        "bundletoolValidated": True,
        "firebaseConfigured": True,
        **updates,
    }


def internal_receipt(**updates):
    return {
        "package": PACKAGE,
        "track": "internal",
        "version_code": 6,
        "bundle_sha256": DIGEST,
        "api_committed": True,
        "verification": "internal_internal_track_assigned",
        "upload_stage": "readback_confirmed",
        "result": "accepted_by_api",
        **updates,
    }


class ClosedTestingTests(unittest.TestCase):
    def test_inspection_needs_exact_main_without_release_inputs(self):
        env = request_env()
        for key in ("CLOSED_TRACK", "CLOSED_APPROVED", "VERSION_CODE", "BATCH_ID"):
            del env[key]
        self.assertEqual(testing.parse_inspection(env)["sha"], SHA)
        env["GITHUB_REF"] = "refs/heads/feature"
        with self.assertRaises(Stop):
            testing.parse_inspection(env)

    def test_inspection_lists_only_candidate_closed_track_ids_read_only(self):
        client = Mock()
        client.start.return_value = "/temporary"
        client.request.return_value = {
            "tracks": [{"track": track} for track in
                       ("custom-closed", "alpha", "qa", "production", "beta", "wear:alpha")]
        }
        self.assertEqual(testing.inspect(client), ["alpha", "custom-closed"])
        client.request.assert_called_once_with("GET", "/temporary/tracks")
        client.discard.assert_called_once_with("/temporary")

    def test_only_exact_main_of_talksullae_with_separate_closed_approval(self):
        for update in (
            {"GITHUB_EVENT_NAME": "push"},
            {"GITHUB_REPOSITORY": "hebi10/GalliPick"},
            {"GITHUB_REF": "refs/heads/feature"},
            {"EXPECTED_MAIN_SHA": "d" * 40},
            {"CLOSED_APPROVED": "false"},
            {"CLOSED_TRACK": "production"},
            {"CLOSED_TRACK": "internal"},
            {"CLOSED_TRACK": "beta"},
            {"CLOSED_TRACK": "wear:alpha"},
        ):
            with self.subTest(update=update), self.assertRaises(Stop):
                testing.parse_preflight(request_env(**update))

    def test_preflight_only_reads_existing_closed_track(self):
        client = Mock()
        client.start.return_value = "/temporary"
        client.request.return_value = {"tracks": [{"track": "alpha"}]}
        self.assertEqual(
            testing.preflight(client, testing.parse_preflight(request_env())),
            "ready",
        )
        client.request.assert_called_once_with("GET", "/temporary/tracks")
        client.discard.assert_called_once_with("/temporary")

    def test_preflight_rejects_missing_pending_or_newer_closed_release(self):
        for tracks in (
            [{"track": "beta"}],
            [{"track": "alpha"}, {"track": "alpha"}],
            [{"track": "alpha", "releases": [{"status": "draft", "versionCodes": []}]}],
            [{"track": "alpha", "releases": [{"status": "completed", "versionCodes": ["7"]}]}],
            [{"track": "alpha", "releases": [{"status": "completed", "versionCodes": ["5", "4"]}]}],
        ):
            with self.subTest(tracks=tracks), self.assertRaises(Stop):
                client = Mock()
                client.start.return_value = "/temporary"
                client.request.return_value = {"tracks": tracks}
                try:
                    testing.preflight(client, testing.parse_preflight(request_env()))
                finally:
                    client.discard.assert_called_once_with("/temporary")

    def test_promotion_uses_the_verified_internal_artifact(self):
        req = testing.parse_promotion(request_env(), verified_manifest(), internal_receipt())
        self.assertEqual(req["internal"], "internal")
        self.assertEqual(req["closed"], "alpha")
        self.assertEqual(req["code"], 6)
        self.assertEqual(req["sha256"], DIGEST)

    def test_promotion_rejects_wrong_artifact_or_unconfirmed_internal(self):
        for manifest, receipt in (
            (verified_manifest(signed=False), internal_receipt()),
            (verified_manifest(sourceSha="d" * 40), internal_receipt()),
            (verified_manifest(signerCertificateSha256="d" * 64), internal_receipt()),
            (verified_manifest(versionCode=5), internal_receipt()),
            (verified_manifest(), internal_receipt(bundle_sha256="d" * 64)),
            (verified_manifest(), internal_receipt(api_committed=False)),
            (verified_manifest(), internal_receipt(track="alpha")),
            (verified_manifest(), internal_receipt(upload_stage="edit_committed")),
            (verified_manifest(), internal_receipt(verification="not_confirmed")),
            (verified_manifest(), internal_receipt(package="com.example.other")),
        ):
            with self.subTest(manifest=manifest, receipt=receipt), self.assertRaises(Stop):
                testing.parse_promotion(request_env(), manifest, receipt)


if __name__ == "__main__":
    unittest.main()
