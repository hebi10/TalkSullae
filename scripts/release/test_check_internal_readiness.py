import json
import unittest
from pathlib import Path

from check_internal_readiness import audit

SOURCE = Path(__file__).resolve().parents[2] / "app/build.gradle.kts"


def full_env():
    data = {
        "project_info": {"project_id": "talksullae"},
        "client": [{
            "client_info": {"android_client_info": {"package_name": "com.hebi.talksullae"}},
            "oauth_client": [{"client_type": 3, "client_id": "example.invalid"}]
        }]
    }
    env = {
        "FIREBASE_ANDROID_GOOGLE_SERVICES_JSON": json.dumps(data),
        "PLAY_UPLOAD_CERT_SHA256": "f" * 64,
        "PLAY_WORKLOAD_IDENTITY_PROVIDER": "not-real",
        "PLAY_SERVICE_ACCOUNT": "not-real@example.invalid",
    }
    env.update({
        name: "SENSITIVE-SYNTHETIC-VALUE" for name in (
            "PLAY_UPLOAD_KEYSTORE_BASE64", "PLAY_UPLOAD_STORE_PASSWORD",
            "PLAY_UPLOAD_KEY_ALIAS", "PLAY_UPLOAD_KEY_PASSWORD"
        )
    })
    return env


class InternalReadinessTests(unittest.TestCase):
    def test_blocked_without_required_configuration(self):
        result = audit({}, SOURCE)
        self.assertEqual(result["config_status"], "blocked")
        self.assertEqual(result["track"], "qa")
        self.assertFalse(result["can_check_play_api"])
        self.assertFalse(result["can_attempt_signed_aab"])
        self.assertFalse(result["internal_release_uploaded"])

    def test_synthetic_presence_does_not_claim_play_access(self):
        result = audit(full_env(), SOURCE)
        self.assertEqual(result["config_status"], "present_but_api_unverified")
        self.assertTrue(result["can_attempt_signed_aab"])
        self.assertTrue(result["can_check_play_api"])
        self.assertFalse(result["play_api_access_verified"])
        self.assertNotIn("SENSITIVE-SYNTHETIC-VALUE", json.dumps(result))

    def test_firebase_mismatch_blocks_aab(self):
        env = full_env()
        data = json.loads(env["FIREBASE_ANDROID_GOOGLE_SERVICES_JSON"])
        data["project_info"]["project_id"] = "wrong"
        env["FIREBASE_ANDROID_GOOGLE_SERVICES_JSON"] = json.dumps(data)
        result = audit(env, SOURCE)
        self.assertFalse(result["can_attempt_signed_aab"])

    def test_json_credentials_supported(self):
        env = full_env()
        env.pop("PLAY_WORKLOAD_IDENTITY_PROVIDER")
        env.pop("PLAY_SERVICE_ACCOUNT")
        env["GOOGLE_PLAY_SERVICE_ACCOUNT_JSON"] = "not-real"
        self.assertEqual(audit(env, SOURCE)["auth_method"], "json")

    def test_bad_cert_pin_blocks_aab(self):
        env = full_env()
        env["PLAY_UPLOAD_CERT_SHA256"] = "not-a-sha"
        self.assertFalse(audit(env, SOURCE)["can_attempt_signed_aab"])


if __name__ == "__main__":
    unittest.main()
