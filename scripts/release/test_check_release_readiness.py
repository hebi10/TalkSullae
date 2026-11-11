#!/usr/bin/env python3
"""Test closed testing readiness without needing genuine keys or Play access."""
import json
from pathlib import Path
import unittest

from check_release_readiness import audit, firebase_matches

SOURCE = Path(__file__).resolve().parents[2] / "app/build.gradle.kts"


def fake_firebase():
    return json.dumps({
        "project_info": {"project_id": "talksullae"},
        "client": [{
            "client_info": {"android_client_info": {"package_name": "com.hebi.talksullae"}},
            "oauth_client": [{"client_type": 3, "client_id": "example.invalid"}],
        }],
    })


def full_synthetic_env():
    env = {
        "PLAY_CLOSED_TRACK": "alpha",
        "PLAY_WORKLOAD_IDENTITY_PROVIDER": "synthetic-provider",
        "PLAY_SERVICE_ACCOUNT": "test@example.invalid",
        "FIREBASE_ANDROID_GOOGLE_SERVICES_JSON": fake_firebase(),
        "PLAY_UPLOAD_CERT_SHA256": "a" * 64,
    }
    env.update({key: "sample" for key in (
        "PLAY_UPLOAD_KEYSTORE_BASE64", "PLAY_UPLOAD_STORE_PASSWORD",
        "PLAY_UPLOAD_KEY_ALIAS", "PLAY_UPLOAD_KEY_PASSWORD"
    )})
    return env


class PlayReadinessTests(unittest.TestCase):
    def test_unconfigured_repository_is_blocked(self):
        result = audit({}, SOURCE)
        self.assertEqual(result["config_status"], "blocked")
        self.assertFalse(result["can_run_play_api_check"])
        self.assertFalse(result["can_attempt_closed_deploy_after_qa"])
        self.assertNotIn("sample", json.dumps(result))

    def test_synthetic_configuration_is_only_presence_checked(self):
        result = audit(full_synthetic_env(), SOURCE)
        self.assertEqual(result["config_status"], "present_but_api_unverified")
        self.assertTrue(result["can_run_play_api_check"])
        self.assertTrue(result["can_attempt_signed_build"])
        self.assertFalse(result["play_api_access_verified"])
        self.assertFalse(result["play_closed_release_uploaded"])
        self.assertNotIn("synthetic-provider", json.dumps(result))

    def test_production_track_is_blocked(self):
        env = full_synthetic_env()
        env["PLAY_CLOSED_TRACK"] = "production"
        result = audit(env, SOURCE)
        self.assertFalse(result["can_run_play_api_check"])
        self.assertIn("PLAY_CLOSED_TRACK (existing closed testing ID)", result["blocking_configuration"])

    def test_wrong_firebase_package_is_blocked(self):
        config = json.loads(fake_firebase())
        config["client"][0]["client_info"]["android_client_info"]["package_name"] = "wrong.app"
        env = full_synthetic_env()
        env["FIREBASE_ANDROID_GOOGLE_SERVICES_JSON"] = json.dumps(config)
        result = audit(env, SOURCE)
        self.assertFalse(result["can_attempt_signed_build"])

    def test_invalid_fingerprint_is_blocked(self):
        env = full_synthetic_env()
        env["PLAY_UPLOAD_CERT_SHA256"] = "invalid"
        result = audit(env, SOURCE)
        self.assertFalse(result["can_attempt_signed_build"])

    def test_json_authorization_supported_without_wif(self):
        env = full_synthetic_env()
        del env["PLAY_WORKLOAD_IDENTITY_PROVIDER"]
        del env["PLAY_SERVICE_ACCOUNT"]
        env["GOOGLE_PLAY_SERVICE_ACCOUNT_JSON"] = "sample"
        result = audit(env, SOURCE)
        self.assertEqual(result["auth_method"], "json")

    def test_no_secret_bytes_are_in_report(self):
        env = full_synthetic_env()
        env["PLAY_UPLOAD_KEYSTORE_BASE64"] = "UNIQUE-SECRET-MUST-NOT-LEAK"
        self.assertNotIn("UNIQUE-SECRET-MUST-NOT-LEAK", json.dumps(audit(env, SOURCE)))


if __name__ == "__main__":
    unittest.main()
