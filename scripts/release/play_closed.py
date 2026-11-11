#!/usr/bin/env python3
"""Fail-closed Android Publisher Edits client: existing closed testing tracks only.

Never publishes to production, open or internal tracks and never creates a Play app.
Production Play users need a completed Play Console first-app setup.
"""
import argparse
import hashlib
import json
import os
import re
import sys
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import quote
from urllib.request import HTTPRedirectHandler, Request, build_opener

PACKAGE = "com.hebi.talksullae"
BASE = "https://androidpublisher.googleapis.com"
EDITS = f"/androidpublisher/v3/applications/{PACKAGE}/edits"
DENIED_TRACKS = {
    "production", "internal", "qa", "beta", "open", "wear:production",
    "wear:beta", "wear:qa", "tv:production", "automotive:production"
}


class PlaySafetyError(Exception):
    pass


def track_name(value):
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]{0,99}", value or ""):
        raise PlaySafetyError("PLAY_CLOSED_TRACK must be an existing simple closed-track API ID")
    if value.lower() in DENIED_TRACKS:
        raise PlaySafetyError("Production, open, internal or form-factor testing tracks are prohibited")
    return value


def release_code(value):
    if isinstance(value, bool) or not re.fullmatch(r"[1-9]\d{0,9}", str(value)):
        raise PlaySafetyError("A strictly positive Android versionCode is required")
    code = int(value)
    if code > 2100000000:
        raise PlaySafetyError("Android versionCode exceeds the Play limit")
    return code


def json_object(response):
    data = json.loads(response) if response else {}
    if not isinstance(data, dict):
        raise PlaySafetyError("Play API returned a malformed response")
    return data


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, request, *args, **kwargs):
        return None


class PlayClient:
    def __init__(self, token, transport=None):
        if not token or any(c.isspace() for c in token):
            raise PlaySafetyError("Missing Play API authorization token")
        self.token = token
        self.transport = transport or build_opener(NoRedirect()).open

    def request(self, method, path, data=None, binary=False):
        if not (path.startswith(EDITS + "/") or path == EDITS or
                path.startswith("/upload" + EDITS + "/")):
            raise PlaySafetyError("Play request attempted outside the configured app")
        content = None if data is None else data if binary else json.dumps(data).encode("utf-8")
        headers = {"Authorization": "Bearer " + self.token, "Accept": "application/json"}
        if content is not None:
            headers["Content-Type"] = "application/octet-stream" if binary else "application/json"
        try:
            with self.transport(Request(BASE + path, headers=headers, data=content, method=method), timeout=150) as response:
                return json_object(response.read())
        except HTTPError as exc:
            raise PlaySafetyError(f"Google Play API HTTP {exc.code}; no release success assumed") from None
        except (URLError, OSError, ValueError, TypeError, json.JSONDecodeError):
            raise PlaySafetyError("Google Play API response unavailable or malformed; inspect Play Console") from None

    def create_edit(self):
        edit = self.request("POST", EDITS, {}).get("id")
        if not isinstance(edit, str) or not re.fullmatch(r"[A-Za-z0-9_-]+", edit):
            raise PlaySafetyError("Play API did not return a valid edit ID")
        return EDITS + "/" + edit

    def delete_edit(self, edit):
        try:
            self.request("DELETE", edit)
        except PlaySafetyError:
            pass

    def list_tracks(self, edit):
        items = self.request("GET", edit + "/tracks").get("tracks", [])
        if not isinstance(items, list) or not all(isinstance(t, dict) for t in items):
            raise PlaySafetyError("Play API returned an invalid list of tracks")
        return items

    def list_codes(self, edit, tracks):
        codes = set()
        for track in tracks:
            releases = track.get("releases", [])
            if not isinstance(releases, list):
                raise PlaySafetyError("Invalid Play release list")
            for release in releases:
                for value in release.get("versionCodes", []):
                    codes.add(release_code(value))
        for resource in ("bundles", "apks"):
            response = self.request("GET", edit + "/" + resource)
            items = response.get(resource, [])
            if not isinstance(items, list):
                raise PlaySafetyError("Invalid Play artifact list")
            for artifact in items:
                codes.add(release_code(artifact.get("versionCode")))
        return codes


def check_preflight(client, track, requested_code=None):
    track = track_name(track)
    edit = client.create_edit()
    try:
        tracks = client.list_tracks(edit)
        if not any(t.get("track") == track for t in tracks):
            raise PlaySafetyError("The exact closed-testing track does not yet exist in Play Console")
        codes = client.list_codes(edit, tracks)
        current_max = max(codes, default=0)
        if requested_code is not None and release_code(requested_code) <= current_max:
            raise PlaySafetyError("Source versionCode is not greater than the existing Play versionCodes")
        return {"package":PACKAGE, "track":track, "current_max_code":current_max,
                "requested_version_code":requested_code,
                "check":"passed", "note":"Release API cannot create a new Play app or complete Play Console declarations"}
    finally:
        client.delete_edit(edit)


def deploy(client, track, bundle, code, version, notes, report=None):
    report = report if report is not None else {}
    track = track_name(track)
    code = release_code(code)
    if not re.fullmatch(r"0\.[0-9]+\.[0-9]+", version):
        raise PlaySafetyError("Invalid testing release version")
    if not notes or len(notes) > 500 or any(c in notes for c in "<>"):
        raise PlaySafetyError("Release notes must be plain text under 500 characters")
    if bundle.is_symlink() or not bundle.is_file() or not bundle.name.endswith(".aab"):
        raise PlaySafetyError("A verified, signed AAB is required")
    payload = bundle.read_bytes()
    if len(payload) < 10000:
        raise PlaySafetyError("The release AAB is unexpectedly small")
    report.update({"package":PACKAGE, "track":track, "version_code":code,
                   "bundle_sha256":hashlib.sha256(payload).hexdigest(),
                   "api_committed":False, "verification":"not_confirmed",
                   "upload_stage":"not_started"})
    edit = client.create_edit()
    committed = False
    try:
        tracks = client.list_tracks(edit)
        if not any(t.get("track") == track for t in tracks):
            raise PlaySafetyError("No exact closed testing track; refusing to create a new track")
        used = client.list_codes(edit, tracks)
        if used and code <= max(used):
            raise PlaySafetyError("The new versionCode must exceed every Play track and uploaded artifact")
        report["upload_stage"] = "requested"
        api_response = client.request(
            "POST", "/upload" + edit + "/bundles?uploadType=media", payload, binary=True
        )
        report["upload_stage"] = "acknowledged"
        if release_code(api_response.get("versionCode")) != code:
            raise PlaySafetyError("Play bundle versionCode does not match the validated AAB")
        client.request("PUT", edit + "/tracks/" + quote(track, safe=""), {
            "track":track,
            "releases":[{
                "name":"TalkSullae " + version,
                "versionCodes":[str(code)],
                "status":"completed",
                "releaseNotes":[{"language":"ko-KR","text":notes}]
            }]
        })
        client.request("POST", edit + ":validate")
        client.request("POST", edit + ":commit")
        committed = True
        report["api_committed"] = True
    finally:
        if not committed:
            client.delete_edit(edit)
    # A successful edit commit does not guarantee review approval or tester installability.
    review_edit = client.create_edit()
    try:
        matching = [t for t in client.list_tracks(review_edit) if t.get("track") == track]
        if len(matching) != 1 or not any(
            code in [release_code(value) for value in release.get("versionCodes", [])]
            for release in matching[0].get("releases", [])
        ):
            raise PlaySafetyError("Edit committed but closed-track readback is not confirmed")
        report["verification"] = "track_assigned"
    finally:
        client.delete_edit(review_edit)
    return report


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("mode", choices=["check", "deploy"])
    parser.add_argument("--track", required=True)
    parser.add_argument("--bundle", type=Path)
    parser.add_argument("--version-code", type=int)
    parser.add_argument("--version", default="0.3.0")
    parser.add_argument("--notes-file", type=Path)
    parser.add_argument("--report", type=Path, default=Path("play-result.json"))
    args = parser.parse_args()
    report = {"result":"unverified", "api_committed":False, "package":PACKAGE}
    try:
        client = PlayClient(os.environ.get("PLAY_ACCESS_TOKEN"))
        if args.mode == "check":
            report = check_preflight(client, args.track, args.version_code)
        else:
            if args.bundle is None or args.version_code is None or args.notes_file is None:
                raise PlaySafetyError("Verified bundle, version code and notes file are mandatory")
            report = deploy(client, args.track, args.bundle, args.version_code, args.version,
                            args.notes_file.read_text(encoding="utf-8").strip(), report=report)
        report["result"] = "accepted_by_api"
        print(json.dumps(report, ensure_ascii=False))
    except (PlaySafetyError, OSError) as err:
        report["result"] = "failed_or_unknown"
        report["reason"] = str(err)
        print(str(err), file=sys.stderr)
        raise SystemExit(1)
    finally:
        args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2)+"\n", encoding="utf-8")


if __name__ == "__main__":
    main()
