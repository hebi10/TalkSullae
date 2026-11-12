#!/usr/bin/env python3
"""Promote an existing, completed Play internal AAB to an existing closed test.

This operation never uploads an AAB, changes the internal track, or touches
production. The exact app and version are pinned to the verified release.
"""
import argparse
import json
import os
import sys
from pathlib import Path

from play_closed import PACKAGE, PlayClient, PlaySafetyError, release_code, track_name

INTERNAL_TRACK = "qa"
EXPECTED_CODE = 3
EXPECTED_VERSION = "0.3.1"


def _active_codes(track):
    releases = track.get("releases", [])
    if not isinstance(releases, list):
        raise PlaySafetyError("Malformed track release list")
    codes = []
    for release in releases:
        if not isinstance(release, dict):
            raise PlaySafetyError("Malformed track release")
        values = release.get("versionCodes", [])
        if not isinstance(values, list):
            raise PlaySafetyError("Malformed version code list")
        codes.extend((release_code(value), release.get("status")) for value in values)
    return codes


def inspect(client, target, code=EXPECTED_CODE):
    target = track_name(target)
    if code != EXPECTED_CODE:
        raise PlaySafetyError("Only the verified TalkSullae versionCode 3 may be promoted")
    edit = client.create_edit()
    try:
        tracks = client.list_tracks(edit)
        internal = next((item for item in tracks if item.get("track") == INTERNAL_TRACK), None)
        closed = next((item for item in tracks if item.get("track") == target), None)
        if internal is None:
            raise PlaySafetyError("Internal qa track is not initialized in Play Console")
        if closed is None:
            raise PlaySafetyError("Target closed-testing track does not exist in Play Console")
        internal_releases = _active_codes(internal)
        if (code, "completed") not in internal_releases:
            raise PlaySafetyError("VersionCode 3 is not a completed internal qa release")
        bundle_items = client.request("GET", edit + "/bundles").get("bundles", [])
        if not isinstance(bundle_items, list):
            raise PlaySafetyError("Malformed Play App Bundle list")
        if code not in [release_code(item.get("versionCode")) for item in bundle_items]:
            raise PlaySafetyError("VersionCode 3 is not an existing Play App Bundle")
        closed_releases = _active_codes(closed)
        if any(status not in ("completed", "draft") for _, status in closed_releases):
            raise PlaySafetyError("Closed track has a release in progress; refusing to overwrite")
        if any(value > code for value, _ in closed_releases):
            raise PlaySafetyError("Closed track already contains a higher version; refusing rollback")
        if any(status == "draft" for _, status in closed_releases):
            raise PlaySafetyError("Closed track has an unfinished draft; finish it in Play Console first")
        already_assigned = (code, "completed") in closed_releases
        return {
            "package": PACKAGE,
            "source_track": INTERNAL_TRACK,
            "target_track": target,
            "version": EXPECTED_VERSION,
            "version_code": code,
            "internal_completed": True,
            "bundle_exists": True,
            "target_track_exists": True,
            "already_promoted": already_assigned,
            "eligible": True,
        }
    finally:
        client.delete_edit(edit)


def promote(client, track, notes, report):
    report.update(inspect(client, track))
    if report["already_promoted"]:
        report["verification"] = "already_assigned"
        return report
    if not notes or len(notes) > 500 or any(c in notes for c in "<>"):
        raise PlaySafetyError("Release notes must be plain text under 500 characters")
    edit = client.create_edit()
    committed = False
    report["stage"] = "preparing"
    try:
        tracks = client.list_tracks(edit)
        internal = next((item for item in tracks if item.get("track") == INTERNAL_TRACK), None)
        closed = next((item for item in tracks if item.get("track") == track), None)
        if internal is None or closed is None:
            raise PlaySafetyError("Testing track disappeared before commit")
        if (EXPECTED_CODE, "completed") not in _active_codes(internal):
            raise PlaySafetyError("Internal release changed before commit")
        closed_releases = _active_codes(closed)
        if closed_releases:
            raise PlaySafetyError("Closed track is not empty; review existing release before promotion")
        report["stage"] = "track_update"
        client.request("PUT", edit + "/tracks/" + track, {
            "track": track,
            "releases": [{
                "name": "TalkSullae " + EXPECTED_VERSION,
                "versionCodes": [str(EXPECTED_CODE)],
                "status": "completed",
                "releaseNotes": [{"language": "ko-KR", "text": notes}],
            }],
        })
        report["stage"] = "validate"
        client.request("POST", edit + ":validate")
        report["stage"] = "commit"
        client.request("POST", edit + ":commit")
        committed = True
        report["api_committed"] = True
    finally:
        if not committed:
            client.delete_edit(edit)
    report["stage"] = "readback"
    read_edit = client.create_edit()
    try:
        tracks = client.list_tracks(read_edit)
        target = next((t for t in tracks if t.get("track") == track), None)
        if target is None or (EXPECTED_CODE, "completed") not in _active_codes(target):
            raise PlaySafetyError("Commit accepted but closed-track readback was not confirmed")
        report["verification"] = "track_assigned"
        report["stage"] = "complete"
        return report
    finally:
        client.delete_edit(read_edit)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("mode", choices=("check", "promote"))
    parser.add_argument("--track", required=True)
    parser.add_argument("--notes-file", type=Path, default=Path("play/release-notes/ko-KR.txt"))
    parser.add_argument("--report", type=Path, default=Path("play-closed-promotion.json"))
    args = parser.parse_args()
    report = {"result": "unverified", "api_committed": False,
              "package": PACKAGE, "version_code": EXPECTED_CODE}
    try:
        track = track_name(args.track)
        client = PlayClient(os.environ.get("PLAY_ACCESS_TOKEN"))
        if args.mode == "check":
            report.update(inspect(client, track))
            report["result"] = "eligible"
        else:
            notes = args.notes_file.read_text(encoding="utf-8").strip()
            promote(client, track, notes, report)
            report["result"] = ("already_promoted" if report.get("already_promoted")
                                else "accepted_by_api")
        print(json.dumps(report, ensure_ascii=False))
    except (PlaySafetyError, OSError, ValueError, TypeError) as exc:
        report["result"] = "failed_or_unknown"
        report["reason"] = str(exc)
        print(str(exc), file=sys.stderr)
        raise SystemExit(1)
    finally:
        args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n",
                               encoding="utf-8")


if __name__ == "__main__":
    main()
