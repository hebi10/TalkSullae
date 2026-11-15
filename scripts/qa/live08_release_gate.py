#!/usr/bin/env python3
"""LIVE-08 read-only candidate source gate, never a production approval."""
import argparse
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
INTERNAL_PUBLISHED_VERSION_CODE = 5
FUNCTIONS = (
    "saveMyProfile", "previewInvitation", "getMyCareer", "registerPushToken",
    "getModerationInbox", "setModerationStatus", "notifyRoomMessage",
    "notifyGameStart", "notifyMemberApproval", "auditSafetyBacklog",
    "processAccountDeletionQueue", "expireDueMessages", "expireGameSessionDeadlines"
)
EMULATOR_PROOFS = (
    "live08UserJourneyEmulator.test.js",
    "game06IntegrationEmulator.test.js",
    "userProfileRulesEmulator.test.js",
    "recordServiceRules.test.js",
    "moderationServiceEmulator.test.js",
    "pushServiceEmulator.test.js"
)


def read(root, path):
    target = root / path
    return target.read_text(encoding="utf-8") if target.is_file() else ""


def version(text):
    code = re.search(r"(?m)^\s*versionCode\s*=\s*(\d+)\s*$", text)
    name = re.search(r'(?m)^\s*versionName\s*=\s*"([^"]+)"\s*$', text)
    return int(code.group(1)) if code else None, name.group(1) if name else None


def audit(root=ROOT):
    gradle = read(root, "app/build.gradle.kts")
    manifest = read(root, "app/src/main/AndroidManifest.xml")
    app = read(root, "app/src/main/java/com/hebi/talksullae/ui/TalkSullaeApp.kt")
    activity = read(root, "app/src/main/java/com/hebi/talksullae/MainActivity.kt")
    functions = read(root, "functions/index.js")
    package = read(root, "functions/package.json")
    rules = read(root, "firestore.rules")
    firebase = read(root, "firebase.json")
    ops04 = read(root, ".github/workflows/play-internal-release.yml")
    closed = read(root, ".github/workflows/play-closed-release.yml")
    capture = read(root, "scripts/qa/capture_matrix.py")
    qa = read(root, ".github/workflows/qa-2d.yml")
    privacy = read(root, "docs/privacy-policy.md")
    code, name = version(gradle)

    checks = {
        "android_package": 'applicationId = "com.hebi.talksullae"' in gradle,
        "new_internal_version_code": code is not None
            and code > INTERNAL_PUBLISHED_VERSION_CODE and name == "0.5.0",
        "firebase_server_exports": all(
            re.search(r"exports\." + re.escape(f) + r"\s*=", functions)
            for f in FUNCTIONS
        ),
        "full_emulator_coverage": all(x in package for x in EMULATOR_PROOFS),
        "real_authenticated_home": (
            "UserProfileRepository" in app and "OnlineRoomsHost(" in app
            and "BuildConfig.DEBUG && !configured" in app
        ),
        "installed_app_invitation_handler": (
            'android:scheme="talksullae"' in manifest
            and "InviteLinkCodec.read" in activity
        ),
        "game_secret_access_restricted": (
            "activeMember" in rules and "gameSessions" in rules
            and "allow read, write: if false;" in rules
        ),
        "server_only_profile_and_moderation": (
            "match /users/{userId}" in rules
            and "match /moderationAudit/{auditId}" in rules
            and "allow list, create, update, delete: if false;" in rules
        ),
        "user_opt_in_notification_disclosure": (
            "POST_NOTIFICATIONS" in manifest
            and "TalkSullaeMessagingService" in manifest
            and "FCM" in privacy
        ),
        "firebase_rules_indexes_defined": (
            '"firestore.rules"' in firebase
            and '"firestore.indexes.json"' in firebase
        ),
        "android_four_device_and_large_font_qa": (
            all(name in qa for name in
                ("api29-phone", "api35-phone", "api36-phone", "api36-tablet"))
            and '(2.0, "200")' in capture
        ),
        "play_internal_manual_approval": (
            "workflow_dispatch:" in ops04
            and "environment: play-internal" in ops04
            and "expected_main_sha" in ops04
            and "final_release_approved" in ops04
            and "PUBLISH_VERIFIED_INTERNAL_AAB" in ops04
        ),
        "play_closed_independently_gated": (
            "workflow_dispatch:" in closed
            and "play-closed" in closed
            and "refs/heads/main" in closed
        ),
    }
    external = {
        "operational_firebase_revision": "not_compared",
        "backward_compatibility_with_0_4_1": "not_tested",
        "real_google_accounts_2_or_more": "not_tested",
        "real_device_chat_games_push_and_deletion": "not_tested",
        "https_app_links_and_install_fallback": "not_implemented",
        "signed_aab_and_play_api_readback": "not_performed",
        "play_review_testers_and_closed_track": "not_verified",
        "play_data_safety_and_content_age_review": "not_verified",
    }
    return {
        "stage": "LIVE-08",
        "status": "source_pass" if all(checks.values()) else "source_blocked",
        "source_checks": checks,
        "failed_source_checks": [k for k, ok in checks.items() if not ok],
        "candidate_version": {"name": name, "code": code},
        "external_gates": external,
        "firebase_deployed": False,
        "play_uploaded": False,
        "release_complete": False,
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    result = audit(args.root)
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n",
                               encoding="utf-8")
    print(json.dumps({
        "stage": result["stage"],
        "status": result["status"],
        "failed_source_checks": result["failed_source_checks"],
        "external_gates_remaining": len(result["external_gates"]),
        "release_complete": False,
    }, ensure_ascii=False))
    return 0 if result["status"] == "source_pass" else 2


if __name__ == "__main__":
    raise SystemExit(main())
