#!/usr/bin/env python3
"""Validate signed Play AAB against source, pinned upload certificate and Firebase config."""
import argparse
import hashlib
import json
import os
import re
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path

PACKAGE = "com.hebi.talksullae"
PROJECT = "talksullae"


def run(*cmd):
    p = subprocess.run(cmd, check=True, capture_output=True, text=True, timeout=180)
    return p.stdout.strip()


def version_from_source(path):
    content = Path(path).read_text(encoding="utf-8")
    match_code = re.search(r"(?m)^\s*versionCode\s*=\s*([1-9]\d*)\s*$", content)
    match_name = re.search(r'(?m)^\s*versionName\s*=\s*"([0-9]+\.[0-9]+\.[0-9]+)"\s*$', content)
    if not match_code or not match_name:
        raise ValueError("Unambiguous release versionCode/versionName not found in Gradle")
    return int(match_code.group(1)), match_name.group(1)


def verify_firebase():
    path = Path("app/google-services.json")
    if not path.is_file():
        raise ValueError("Firebase Android configuration is required for Play release")
    data = json.loads(path.read_text(encoding="utf-8"))
    if data["project_info"]["project_id"] != PROJECT:
        raise ValueError("Release Firebase project mismatch")
    client = [x for x in data["client"]
              if x["client_info"]["android_client_info"]["package_name"] == PACKAGE]
    if len(client) != 1 or not client[0]["client_info"].get("mobilesdk_app_id"):
        raise ValueError("Release package not uniquely registered in Firebase")
    app_id = client[0]["client_info"]["mobilesdk_app_id"]
    web_ids = {x.get("client_id") for x in client[0].get("oauth_client", [])
               if x.get("client_type") == 3 and x.get("client_id")}
    if not web_ids:
        raise ValueError("Firebase Google web OAuth client is missing")
    config_file = Path("app/build/generated/source/buildConfig/release/com/hebi/talksullae/BuildConfig.java")
    if not config_file.is_file() or not re.search(
        r"FIREBASE_CONFIGURED\s*=\s*true\s*;", config_file.read_text()
    ):
        raise ValueError("Release BuildConfig has Firebase disabled")
    matches = []
    # AGP / Google Services plugin may emit processReleaseGoogleServices or a release resource directory.
    release_roots = (
        Path("app/build/generated/res/processReleaseGoogleServices"),
        Path("app/build/generated/res/google-services/release"),
    )
    for root in release_roots:
        if not root.is_dir():
            continue
        for file in root.rglob("values.xml"):
            values = {item.attrib.get("name"):item.text for item in ET.parse(file).getroot()}
            if values.get("google_app_id") == app_id and values.get("default_web_client_id") in web_ids:
                matches.append(str(file))
    if len(matches) != 1:
        raise ValueError("Exactly one release Firebase App ID + web OAuth resource is required")
    return True


def certificate_sha256(aab):
    output = run("keytool", "-printcert", "-jarfile", str(aab))
    matches = re.findall(r"SHA256:\s*([0-9A-Fa-f:]{64,110})", output)
    hashes = {value.replace(":", "").lower() for value in matches}
    if len(hashes) != 1 or len(next(iter(hashes))) != 64:
        raise ValueError("AAB signer SHA-256 was missing or ambiguous")
    return next(iter(hashes))


def run_bundletool(jar, bundle, xpath):
    return run("java", "-jar", str(jar), "dump", "manifest", "--bundle="+str(bundle),
               "--xpath="+xpath)


def verify(aab, bundletool, source, output):
    if aab.is_symlink() or not aab.is_file() or aab.stat().st_size < 10000:
        raise ValueError("A nonempty, non-symlink AAB is required")
    code, version = version_from_source(source)
    expected = os.environ.get("PLAY_UPLOAD_CERT_SHA256", "").replace(":", "").lower()
    if not re.fullmatch(r"[a-f0-9]{64}", expected):
        raise ValueError("Pinned PLAY_UPLOAD_CERT_SHA256 repository variable is required")
    verify_firebase()
    run("java", "-jar", str(bundletool), "validate", "--bundle="+str(aab))
    package = run_bundletool(bundletool, aab, "/manifest/@package")
    app_code = run_bundletool(bundletool, aab, "/manifest/@android:versionCode")
    if package.strip('"') != PACKAGE or app_code.strip('"') != str(code):
        raise ValueError("AAB package name/versionCode do not match source")
    signed = run("jarsigner", "-verify", str(aab))
    if "jar verified." not in signed:
        raise ValueError("AAB is not verified as a signed JAR")
    digest = certificate_sha256(aab)
    if digest != expected:
        raise ValueError("AAB signer does not match the pinned Play upload certificate")
    source_sha = os.environ.get("GITHUB_SHA", "")
    if not re.fullmatch(r"[a-f0-9]{40}", source_sha):
        raise ValueError("GitHub source commit is required")
    report = {
        "package": PACKAGE, "versionCode":code, "versionName":version,
        "sourceSha":source_sha, "firebaseConfigured":True, "signerCertificateSha256":digest,
        "aabSha256":hashlib.sha256(aab.read_bytes()).hexdigest(),
        "aabBytes":aab.stat().st_size, "bundletoolValidated":True, "signed":True,
    }
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2)+"\n", encoding="utf-8")
    print(json.dumps(report))
    return report


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--aab", type=Path, required=True)
    parser.add_argument("--bundletool", type=Path, required=True)
    parser.add_argument("--source", default="app/build.gradle.kts")
    parser.add_argument("--output", type=Path, default=Path("play-dist/manifest.json"))
    args = parser.parse_args()
    verify(args.aab, args.bundletool, args.source, args.output)


if __name__ == "__main__":
    main()
