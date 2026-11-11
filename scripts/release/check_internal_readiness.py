#!/usr/bin/env python3
"""Assess internal Play release configuration without exposing any credential values."""
import argparse
import json
import os
import re
from pathlib import Path

from check_release_readiness import SIGNING_SECRETS, firebase_matches
from play_closed import PACKAGE
from verify_aab import version_from_source

TRACK = "qa"


def audit(env, source="app/build.gradle.kts"):
    code, version = version_from_source(source)
    contents = Path(source).read_text(encoding="utf-8")
    if not re.search(r'(?m)^\s*applicationId\s*=\s*"com\.hebi\.talksullae"\s*$', contents):
        raise ValueError("Android applicationId must be com.hebi.talksullae")
    present = lambda name: bool(str(env.get(name, "")).strip())
    wif = present("PLAY_WORKLOAD_IDENTITY_PROVIDER") and present("PLAY_SERVICE_ACCOUNT")
    json_auth = present("GOOGLE_PLAY_SERVICE_ACCOUNT_JSON")
    auth_method = "wif" if wif else "json" if json_auth else "none"
    missing = []
    if auth_method == "none":
        missing.append("PLAY_WORKLOAD_IDENTITY_PROVIDER + PLAY_SERVICE_ACCOUNT or GOOGLE_PLAY_SERVICE_ACCOUNT_JSON")
    firebase_ok = firebase_matches(env.get("FIREBASE_ANDROID_GOOGLE_SERVICES_JSON", ""))
    if not firebase_ok:
        missing.append("FIREBASE_ANDROID_GOOGLE_SERVICES_JSON")
    for key in SIGNING_SECRETS:
        if not present(key):
            missing.append(key)
    fingerprint = str(env.get("PLAY_UPLOAD_CERT_SHA256", "")).replace(":", "").lower()
    fingerprint_ok = bool(re.fullmatch(r"[a-f0-9]{64}", fingerprint))
    if not fingerprint_ok:
        missing.append("PLAY_UPLOAD_CERT_SHA256")
    signed_build_configured = firebase_ok and fingerprint_ok and all(present(k) for k in SIGNING_SECRETS)
    api_configured = auth_method != "none"
    return {
        "package": PACKAGE, "track": TRACK, "versionName": version, "versionCode": code,
        "config_status": "present_but_api_unverified" if signed_build_configured and api_configured else "blocked",
        "auth_method": auth_method,
        "firebase_project_package_oauth_format_valid": firebase_ok,
        "upload_certificate_sha256_format_valid": fingerprint_ok,
        "can_check_play_api": api_configured,
        "can_attempt_signed_aab": signed_build_configured,
        "can_attempt_internal_deploy_after_qa": api_configured and signed_build_configured,
        "blocking_configuration": missing,
        "play_api_access_verified": False,
        "signed_aab_verified": False,
        "internal_release_uploaded": False,
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--report", type=Path, default=Path("play-internal-readiness.json"))
    args = parser.parse_args()
    report = audit(os.environ)
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    output_path = os.environ.get("GITHUB_OUTPUT")
    if output_path:
        with Path(output_path).open("a", encoding="utf-8") as stream:
            stream.write("auth_method=" + report["auth_method"] + "\n")
            stream.write("can_check_play=" + str(report["can_check_play_api"]).lower() + "\n")
            stream.write("version_code=" + str(report["versionCode"]) + "\n")
    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_path:
        with Path(summary_path).open("a", encoding="utf-8") as stream:
            stream.write("### TalkSullae internal testing (qa) — no upload\n\n")
            stream.write("Configuration: **" + report["config_status"] + "**\n\n")
            stream.write("Missing/invalid configuration keys (no values):\n")
            for key in report["blocking_configuration"]:
                stream.write("- " + key + "\n")
            stream.write("\nThis report does not prove Play access or upload success.\n")
    print(json.dumps(report, ensure_ascii=False))


if __name__ == "__main__":
    main()
