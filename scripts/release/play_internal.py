#!/usr/bin/env python3
"""Fail-closed Play internal-test publisher for com.hebi.talksullae."""
import argparse
import hashlib
import json
import os
import re
import sys
from pathlib import Path

from play_closed import PACKAGE, PlayClient, PlaySafetyError, release_code

TRACK = "qa"
INTERNAL_TRACK_IDS = frozenset({TRACK, "internal"})
AUTO_TRACK = "auto"


def track_name(value):
    if value not in (TRACK, AUTO_TRACK):
        raise PlaySafetyError("Select 'qa' or discover the existing phone/tablet internal testing track with 'auto'")
    return value


def existing_track(tracks, selection):
    allowed = INTERNAL_TRACK_IDS if selection == AUTO_TRACK else {selection}
    matching = [track["track"] for track in tracks if track.get("track") in allowed]
    if len(matching) > 1:
        raise PlaySafetyError("Ambiguous internal testing track identifiers from Google Play")
    return matching[0] if matching else None


def check_preflight(client, track, requested_code=None, require_existing_qa=False):
    track = track_name(track)
    edit = client.create_edit()
    try:
        tracks = client.list_tracks(edit)
        current_max = max(client.list_codes(edit, tracks), default=0)
        selected = existing_track(tracks, track)
        internal_exists = selected is not None
        if require_existing_qa and not internal_exists:
            raise PlaySafetyError(
                "Initialize the internal testing track in Play Console or verify its API identifier; "
                "no AAB upload was attempted."
            )
        if requested_code is not None and release_code(requested_code) <= current_max:
            raise PlaySafetyError("Source versionCode must exceed every uploaded or published Play versionCode")
        return {
            "package": PACKAGE, "track": selected or track,
            "visible_internal_track_ids": sorted(INTERNAL_TRACK_IDS.intersection(t.get("track") for t in tracks)),
            "internal_track_exists": internal_exists,
            "ready_for_deploy": internal_exists,
            "requires_play_console_initialization": not internal_exists,
            "current_max_code": current_max, "requested_version_code": requested_code,
            "check": "passed", "api_committed": False,
            "note": "Read-only app access and version check; no upload or review approval"
        }
    finally:
        client.delete_edit(edit)


def deploy(client, track, bundle, code, version, notes, report=None):
    track = track_name(track)
    code = release_code(code)
    if not re.fullmatch(r"0\.[0-9]+\.[0-9]+", version):
        raise PlaySafetyError("Invalid internal testing release version")
    if not notes or len(notes) > 500 or any(c in notes for c in "<>"):
        raise PlaySafetyError("Internal release notes must be plain text under 500 characters")
    if bundle.is_symlink() or not bundle.is_file() or bundle.suffix != ".aab":
        raise PlaySafetyError("A verified signed AAB file is required")
    payload = bundle.read_bytes()
    if len(payload) < 10000:
        raise PlaySafetyError("The internal testing AAB is unexpectedly small")
    report = report if report is not None else {}
    report.update({
        "package": PACKAGE, "track": track, "version_code": code,
        "bundle_sha256": hashlib.sha256(payload).hexdigest(),
        "api_committed": False, "verification": "not_confirmed",
        "upload_stage": "not_started"
    })
    edit = client.create_edit()
    committed = False
    try:
        tracks = client.list_tracks(edit)
        selected = existing_track(tracks, track)
        if selected is None:
            report["upload_stage"] = "internal_track_missing"
            raise PlaySafetyError(
                "Google Play internal testing track is not initialized or visible to the API. "
                "No AAB upload was attempted."
            )
        report["track"] = selected
        used = client.list_codes(edit, tracks)
        if used and code <= max(used):
            raise PlaySafetyError("Internal AAB versionCode must exceed all existing Play releases and uploaded bundles")
        report["upload_stage"] = "requested"
        uploaded = client.request(
            "POST", "/upload" + edit + "/bundles?uploadType=media", payload, binary=True
        )
        report["upload_stage"] = "acknowledged"
        if release_code(uploaded.get("versionCode")) != code:
            raise PlaySafetyError("Internal Play bundle versionCode does not match the signed artifact")
        report["upload_stage"] = "track_update_requested"
        client.request("PUT", edit + "/tracks/" + selected, {
            "track": selected,
            "releases": [{
                "name": "TalkSullae " + version,
                "versionCodes": [str(code)],
                "status": "completed",
                "releaseNotes": [{"language": "ko-KR", "text": notes}]
            }]
        })
        report["upload_stage"] = "track_update_acknowledged"
        report["upload_stage"] = "edit_validation_requested"
        client.request("POST", edit + ":validate")
        report["upload_stage"] = "edit_commit_requested"
        client.request("POST", edit + ":commit")
        committed = True
        report["api_committed"] = True
        report["upload_stage"] = "edit_committed"
    finally:
        if not committed:
            client.delete_edit(edit)

    readback = client.create_edit()
    try:
        matches = [t for t in client.list_tracks(readback) if t.get("track") == selected]
        if len(matches) != 1 or not any(
            code in [release_code(v) for v in release.get("versionCodes", [])]
            for release in matches[0].get("releases", [])
        ):
            raise PlaySafetyError("Internal edit committed but track readback not confirmed")
        report["verification"] = "internal_" + selected + "_track_assigned"
        report["upload_stage"] = "readback_confirmed"
    finally:
        client.delete_edit(readback)
    return report


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("mode", choices=["check", "deploy"])
    parser.add_argument("--track", default=TRACK)
    parser.add_argument("--require-existing-qa", action="store_true")
    parser.add_argument("--bundle", type=Path)
    parser.add_argument("--version-code", type=int)
    parser.add_argument("--version", default="0.3.0")
    parser.add_argument("--notes-file", type=Path)
    parser.add_argument("--report", type=Path, default=Path("play-internal-result.json"))
    args = parser.parse_args()
    report = {"package": PACKAGE, "track": TRACK, "result": "unverified", "api_committed": False}
    try:
        report["track"] = track_name(args.track)
        client = PlayClient(os.environ.get("PLAY_ACCESS_TOKEN"))
        if args.mode == "check":
            report = check_preflight(
                client, args.track, args.version_code, require_existing_qa=args.require_existing_qa
            )
        else:
            if args.bundle is None or args.version_code is None or args.notes_file is None:
                raise PlaySafetyError("Verified AAB, version and internal release notes are mandatory")
            report = deploy(client, args.track, args.bundle, args.version_code, args.version,
                            args.notes_file.read_text(encoding="utf-8").strip(), report)
        report["result"] = "accepted_by_api"
        print(json.dumps(report, ensure_ascii=False))
    except (PlaySafetyError, OSError) as exc:
        report["result"] = "failed_or_unknown"
        report["reason"] = str(exc)
        print(str(exc), file=sys.stderr)
        raise SystemExit(1)
    finally:
        args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
