#!/usr/bin/env python3
"""Verify and promote the signed artifact from the same TalkSullae testing run."""
import argparse
import json
import os
from pathlib import Path
import re
import sys

from verify_aab import PACKAGE

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from ops05 import promotion

REPOSITORY = "hebi10/TalkSullae"


def parse_inspection(env):
    return promotion.parse_exact_source(dict(env, EXPECTED_REPO=REPOSITORY))


def parse_preflight(env):
    promotion.require(env.get("CLOSED_APPROVED") == "true", "Separate closed promotion approval is required")
    return promotion.parse_source(dict(env, EXPECTED_REPO=REPOSITORY, APP_PACKAGE=PACKAGE))


def read_tracks(client):
    edit = client.start()
    try:
        return promotion.list_value(client.request("GET", edit + "/tracks"), "tracks")
    finally:
        client.discard(edit)


def inspect(client):
    tracks = read_tracks(client)
    candidates = [track["track"] for track in tracks if promotion.is_closed_track(track.get("track"))]
    promotion.require(len(set(candidates)) == len(candidates), "Duplicate closed track identifiers")
    return sorted(candidates)


def preflight(client, request):
    return promotion.closed_state(read_tracks(client), request)


def read_object(path):
    promotion.require(path is not None and path.is_file() and not path.is_symlink(),
                      "A same-run verification report is missing")
    contents = json.loads(path.read_text(encoding="utf-8"))
    promotion.require(isinstance(contents, dict), "Invalid verification report")
    return contents


def parse_promotion(env, manifest, receipt):
    candidate = parse_preflight(env)
    cert = env.get("PLAY_UPLOAD_CERT_SHA256", "").replace(":", "").lower()
    promotion.require(bool(re.fullmatch("[a-f0-9]{64}", cert)), "Pinned upload certificate is required")
    promotion.require(
        manifest.get("package") == PACKAGE
        and manifest.get("sourceSha") == candidate["sha"]
        and type(manifest.get("versionCode")) is int
        and manifest["versionCode"] == candidate["code"]
        and manifest.get("signerCertificateSha256") == cert
        and all(manifest.get(key) is True for key in ("signed", "bundletoolValidated", "firebaseConfigured")),
        "Signed AAB manifest does not match the approved source, version, package and certificate",
    )
    digest = manifest.get("aabSha256")
    track = receipt.get("track")
    promotion.require(
        receipt.get("package") == PACKAGE
        and receipt.get("bundle_sha256") == digest
        and type(receipt.get("version_code")) is int
        and receipt["version_code"] == candidate["code"]
        and track in ("internal", "qa")
        and receipt.get("api_committed") is True
        and receipt.get("verification") == f"internal_{track}_track_assigned"
        and receipt.get("upload_stage") == "readback_confirmed"
        and receipt.get("result") == "accepted_by_api",
        "The internal deployment receipt does not confirm this exact signed artifact",
    )
    return promotion.parse_request(dict(
        env, EXPECTED_REPO=REPOSITORY, APP_PACKAGE=PACKAGE, INTERNAL_TRACK=track,
        AAB_SHA256=digest or "", MODE="promote", FINAL_BATCH_APPROVED="true",
        APPROVAL_PHRASE=promotion.APPROVAL,
    ))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("operation", choices=("inspect", "preflight", "promote"))
    parser.add_argument("--manifest", type=Path)
    parser.add_argument("--internal-receipt", type=Path)
    args = parser.parse_args()
    env = os.environ
    try:
        if args.operation == "inspect":
            request = parse_inspection(env)
            promotion.verify_main(request, env)
            client = promotion.Play(env.get("PLAY_ACCESS_TOKEN"), PACKAGE)
            print(json.dumps({"package": PACKAGE, "closed_track_candidates": inspect(client)}))
        elif args.operation == "preflight":
            request = parse_preflight(env)
            promotion.verify_main(request, env)
            client = promotion.Play(env.get("PLAY_ACCESS_TOKEN"), PACKAGE)
            print("Closed track preflight:", preflight(client, request))
        else:
            request = parse_promotion(env, read_object(args.manifest), read_object(args.internal_receipt))
            promotion.verify_main(request, env)
            receipt = promotion.execute("promote", request, env.get("PLAY_ACCESS_TOKEN"), env)
            promotion.persist(receipt)
            print("Closed track promotion:", receipt["status"], "versionCode", request["code"])
        return 0
    except promotion.Stop as error:
        print("Closed testing blocked:", error, file=sys.stderr)
        return 1
    except (OSError, ValueError, TypeError, KeyError):
        print("Closed testing blocked: invalid release input or Play response", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
