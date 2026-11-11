#!/usr/bin/env python3
"""Restore fixed Play signing and Firebase Android config from GitHub secrets safely."""
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess

APP = "com.hebi.talksullae"
PROJECT = "talksullae"


def required(name):
    value = os.environ.get(name, "")
    if not value:
        raise SystemExit("Required Play release secret or variable is missing: " + name)
    return value


def write_private(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    handle = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(handle, "wb") as fp:
        fp.write(data)


def restore():
    raw = required("FIREBASE_ANDROID_GOOGLE_SERVICES_JSON")
    try:
        settings = json.loads(raw)
        clients = [x for x in settings["client"]
                   if x["client_info"]["android_client_info"]["package_name"] == APP]
        if (settings["project_info"]["project_id"] != PROJECT or len(clients) != 1 or
            not any(x.get("client_type") == 3 and x.get("client_id")
                    for x in clients[0].get("oauth_client", []))):
            raise ValueError()
    except (ValueError, KeyError, TypeError):
        raise SystemExit("Firebase config does not match the approved project or app") from None

    key_source = required("PLAY_UPLOAD_KEYSTORE_BASE64")
    try:
        keystore_data = base64.b64decode(key_source, validate=True)
        if len(keystore_data) < 1000 or len(keystore_data) > 1000000:
            raise ValueError()
    except (ValueError, base64.binascii.Error):
        raise SystemExit("Fixed Play keystore backup is invalid") from None

    password = required("PLAY_UPLOAD_STORE_PASSWORD")
    alias = required("PLAY_UPLOAD_KEY_ALIAS")
    key_password = required("PLAY_UPLOAD_KEY_PASSWORD")
    expected = required("PLAY_UPLOAD_CERT_SHA256").replace(":", "").lower()
    if not re.fullmatch(r"[0-9a-f]{64}", expected) or not re.fullmatch(r"[A-Za-z0-9_.-]{1,100}", alias):
        raise SystemExit("Invalid pinned upload certificate fingerprint or key alias")
    temp_dir = Path(required("RUNNER_TEMP"))
    keystore = temp_dir / "talksullae-upload-keystore"
    write_private(keystore, keystore_data)
    try:
        cert = subprocess.run(
            ["keytool", "-exportcert", "-keystore", str(keystore),
             "-alias", alias, "-storepass", password, "-keypass", key_password],
            check=True, capture_output=True, timeout=40
        ).stdout
    except (OSError, subprocess.CalledProcessError, subprocess.TimeoutExpired):
        keystore.unlink(missing_ok=True)
        raise SystemExit("Could not open the original Play upload signing identity") from None
    actual = hashlib.sha256(cert).hexdigest()
    if actual != expected:
        keystore.unlink(missing_ok=True)
        raise SystemExit("Play upload certificate identity mismatches pinned fingerprint")
    write_private(Path("app/google-services.json"), raw.encode("utf-8"))
    env_file = Path(required("GITHUB_ENV"))
    with env_file.open("a", encoding="utf-8") as file:
        file.write("TALK_PLAY_KEYSTORE_FILE=" + str(keystore) + "\n")
    print("Original upload certificate and Firebase Android config validated privately.")


if __name__ == "__main__":
    restore()
