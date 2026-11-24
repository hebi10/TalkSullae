"""Prepare static invite hosting files; never deploy or obtain credentials."""
import argparse
import json
import re
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PACKAGE = "com.hebi.talksullae"
HOST = "talksullae.web.app"


def normalize_fingerprint(value: str) -> str:
    if re.fullmatch(r"(?:[0-9a-fA-F]{2}:){31}[0-9a-fA-F]{2}", value):
        value = value.replace(":", "")
    if not re.fullmatch(r"[0-9a-fA-F]{64}", value):
        raise ValueError("Expected Play app-signing certificate SHA-256")
    value = value.upper()
    return ":".join(value[i:i + 2] for i in range(0, 64, 2))


def statement(fingerprints: list[str]) -> list[dict]:
    normalized = sorted({normalize_fingerprint(value) for value in fingerprints})
    if not normalized:
        raise ValueError("At least one verified Play app-signing certificate is required")
    return [{
        "relation": ["delegate_permission/common.handle_all_urls"],
        "target": {
            "namespace": "android_app",
            "package_name": PACKAGE,
            "sha256_cert_fingerprints": normalized,
        },
    }]


def prepare(destination: Path, fingerprints: list[str], root: Path = ROOT) -> Path:
    association = statement(fingerprints)
    if destination.exists():
        raise FileExistsError("Choose an empty output path; existing hosting files are not overwritten")
    shutil.copytree(root / "hosting/invite/public", destination)
    well_known = destination / ".well-known"
    well_known.mkdir()
    path = well_known / "assetlinks.json"
    path.write_text(json.dumps(association, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return path


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--play-app-signing-sha256", action="append", required=True,
                        help="Public SHA-256 certificate fingerprint from Play App Signing; not the upload key")
    parser.add_argument("--output", type=Path, default=ROOT / "build/invite-hosting")
    args = parser.parse_args()
    prepare(args.output, args.play_app_signing_sha256)
    print("Invite hosting files prepared; no hosting deployment or domain verification performed.")


if __name__ == "__main__":
    main()
