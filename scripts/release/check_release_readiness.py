#!/usr/bin/env python3
"""Report *configuration presence* for a closed Play release without exporting secrets.

This deliberately does not upload or sign an Android App Bundle and does not
claim that configured credentials have been authenticated by Google Play.
"""
import argparse
import json
import os
import re
from pathlib import Path

from play_closed import PACKAGE, PlaySafetyError, track_name
from verify_aab import version_from_source

PROJECT = "talksullae"
SIGNING_SECRETS = (
    "PLAY_UPLOAD_KEYSTORE_BASE64",
    "PLAY_UPLOAD_STORE_PASSWORD",
    "PLAY_UPLOAD_KEY_ALIAS",
    "PLAY_UPLOAD_KEY_PASSWORD",
)


def configured(value):
    return bool(str(value or "").strip())


def firebase_matches(raw):
    try:
        data = json.loads(raw)
        if data.get("project_info", {}).get("project_id") != PROJECT:
            return False
        clients = [
            c for c in data.get("client", [])
            if c.get("client_info", {}).get("android_client_info", {}).get("package_name") == PACKAGE
        ]
        return len(clients) == 1 and any(
            item.get("client_type") == 3 and configured(item.get("client_id"))
            for item in clients[0].get("oauth_client", [])
        )
    except (ValueError, AttributeError, TypeError):
        return False


def audit(env, source="app/build.gradle.kts"):
    code, version = version_from_source(source)
    source_text = Path(source).read_text(encoding="utf-8")
    if not re.search(r'(?m)^\s*applicationId\s*=\s*"com\.hebi\.talksullae"\s*$', source_text):
        raise ValueError("Source applicationId does not match the approved Play package")
    blockers = []
    try:
        track_name(env.get("PLAY_CLOSED_TRACK"))
        track_ok = True
    except PlaySafetyError:
        track_ok = False
        blockers.append("PLAY_CLOSED_TRACK (existing closed testing ID)")
    auth_wif = all(configured(env.get(k)) for k in ("PLAY_WORKLOAD_IDENTITY_PROVIDER", "PLAY_SERVICE_ACCOUNT"))
    auth_json = configured(env.get("GOOGLE_PLAY_SERVICE_ACCOUNT_JSON"))
    auth = "wif" if auth_wif else "json" if auth_json else "none"
    if auth == "none":
        blockers.append("PLAY_WORKLOAD_IDENTITY_PROVIDER + PLAY_SERVICE_ACCOUNT or GOOGLE_PLAY_SERVICE_ACCOUNT_JSON")
    firebase_ok = firebase_matches(env.get("FIREBASE_ANDROID_GOOGLE_SERVICES_JSON", ""))
    if not firebase_ok:
        blockers.append("FIREBASE_ANDROID_GOOGLE_SERVICES_JSON (correct project/package/web OAuth)")
    for key in SIGNING_SECRETS:
        if not configured(env.get(key)):
            blockers.append(key)
    fingerprint = re.sub(r":", "", env.get("PLAY_UPLOAD_CERT_SHA256", "")).lower()
    fingerprint_ok = bool(re.fullmatch(r"[0-9a-f]{64}", fingerprint))
    if not fingerprint_ok:
        blockers.append("PLAY_UPLOAD_CERT_SHA256 (64 hex characters)")
    signing_ok = firebase_ok and fingerprint_ok and all(configured(env.get(k)) for k in SIGNING_SECRETS)
    api_ok = track_ok and auth != "none"
    return {
        "package": PACKAGE,
        "versionName": version,
        "versionCode": code,
        "config_status": "present_but_api_unverified" if (api_ok and signing_ok) else "blocked",
        "can_run_play_api_check": api_ok,
        "can_attempt_signed_build": signing_ok,
        "can_attempt_closed_deploy_after_qa": api_ok and signing_ok,
        "auth_method": auth,
        "track_name_format_valid": track_ok,
        "firebase_project_package_oauth_format_valid": firebase_ok,
        "upload_certificate_sha256_format_valid": fingerprint_ok,
        "blocking_configuration": blockers,
        "play_api_access_verified": False,
        "signed_aab_verified": False,
        "play_closed_release_uploaded": False,
    }


def write_outputs(report, path):
    if not path:
        return
    with Path(path).open("a", encoding="utf-8") as f:
        for key, value in (
            ("auth_method", report["auth_method"]),
            ("can_check_play", str(report["can_run_play_api_check"]).lower()),
            ("can_sign", str(report["can_attempt_signed_build"]).lower()),
            ("version_code", str(report["versionCode"])),
        ):
            f.write(f"{key}={value}\n")


def write_summary(report, path):
    if not path:
        return
    with Path(path).open("a", encoding="utf-8") as f:
        f.write("### 2-F Play closed-testing readiness (no publish)\n\n")
        f.write(f"Configuration: **{report['config_status']}**. This is not Play approval.\n\n")
        f.write(f"Play API check possible: {report['can_run_play_api_check']}\n\n")
        f.write(f"Signed AAB build configuration present: {report['can_attempt_signed_build']}\n\n")
        if report["blocking_configuration"]:
            f.write("Missing or invalid configuration names (no values):\n")
            for name in report["blocking_configuration"]:
                f.write(f"- {name}\n")
        f.write("\nNo Play release was uploaded by this preflight.\n")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", default="app/build.gradle.kts")
    parser.add_argument("--report", type=Path, default=Path("2f-readiness.json"))
    args = parser.parse_args()
    result = audit(os.environ, args.source)
    args.report.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    write_outputs(result, os.environ.get("GITHUB_OUTPUT"))
    write_summary(result, os.environ.get("GITHUB_STEP_SUMMARY"))
    print(json.dumps(result, ensure_ascii=False))


if __name__ == "__main__":
    main()
