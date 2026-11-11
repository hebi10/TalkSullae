#!/usr/bin/env python3
"""Release Firebase resources must match the real Google Services Gradle output."""
import json
import os
from pathlib import Path
import tempfile
import unittest

from verify_aab import verify_firebase


class ReleaseFirebaseResourceTests(unittest.TestCase):
    def setUp(self):
        self.work = tempfile.TemporaryDirectory()
        self.original_cwd = os.getcwd()
        os.chdir(self.work.name)
        app = Path("app")
        app.mkdir()
        payload = {
            "project_info": {"project_id": "talksullae"},
            "client": [{
                "client_info": {
                    "android_client_info": {"package_name": "com.hebi.talksullae"},
                    "mobilesdk_app_id": "1:123:android:abc"
                },
                "oauth_client": [{"client_type": 3, "client_id": "test.apps.googleusercontent.com"}]
            }]
        }
        (app / "google-services.json").write_text(json.dumps(payload), encoding="utf-8")
        build_config = app / "build/generated/source/buildConfig/release/com/hebi/talksullae/BuildConfig.java"
        build_config.parent.mkdir(parents=True)
        build_config.write_text("public static final boolean FIREBASE_CONFIGURED = true;\n", encoding="utf-8")
        self.values = app / "build/generated/res/google-services/release/values/values.xml"
        self.values.parent.mkdir(parents=True)
        self.values.write_text(
            '<resources><string name="google_app_id">1:123:android:abc</string>'
            '<string name="default_web_client_id">test.apps.googleusercontent.com</string></resources>',
            encoding="utf-8"
        )

    def tearDown(self):
        os.chdir(self.original_cwd)
        self.work.cleanup()

    def test_release_generated_resources_are_detected(self):
        self.assertTrue(verify_firebase())

    def test_missing_release_generated_resources_rejected(self):
        self.values.unlink()
        with self.assertRaisesRegex(ValueError, "Exactly one"):
            verify_firebase()

    def test_debug_resources_do_not_satisfy_release_check(self):
        self.values.unlink()
        debug_path = Path("app/build/generated/res/google-services/debug/values/values.xml")
        debug_path.parent.mkdir(parents=True)
        debug_path.write_text(
            '<resources><string name="google_app_id">1:123:android:abc</string>'
            '<string name="default_web_client_id">test.apps.googleusercontent.com</string></resources>',
            encoding="utf-8"
        )
        with self.assertRaisesRegex(ValueError, "Exactly one"):
            verify_firebase()

    def test_mismatched_web_oauth_resource_rejected(self):
        self.values.write_text(
            '<resources><string name="google_app_id">1:123:android:abc</string>'
            '<string name="default_web_client_id">wrong.apps.googleusercontent.com</string></resources>',
            encoding="utf-8"
        )
        with self.assertRaisesRegex(ValueError, "Exactly one"):
            verify_firebase()


if __name__ == "__main__":
    unittest.main()
