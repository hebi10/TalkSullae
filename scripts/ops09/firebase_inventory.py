#!/usr/bin/env python3
"""OPS-09: read deployed Firebase metadata without writing cloud resources.

The report intentionally contains no rules source, credential, user documents,
function environment variables, scheduler target URLs or user IDs.
"""
import argparse
import hashlib
import json
import re
import subprocess
import sys
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen, build_opener, HTTPRedirectHandler
from urllib.parse import quote

ROOT = Path(__file__).resolve().parents[2]
PROJECT_PATTERN = re.compile(r"^[a-z][a-z0-9-]{5,62}$")
EXPORT_PATTERN = re.compile(r"exports\.([A-Za-z_][A-Za-z0-9_]*)\s*=")
REGION = "asia-northeast3"
SCHEDULED = (
    "processAccountDeletionQueue", "expireDueMessages",
    "expireGameSessionDeadlines", "auditSafetyBacklog"
)


class AuditUnavailable(Exception):
    pass


def validate_project(project):
    if not isinstance(project, str) or not PROJECT_PATTERN.fullmatch(project):
        raise ValueError("Firebase project ID must be a safe Google Cloud project identifier")
    return project


def exported_functions(source):
    return sorted(set(EXPORT_PATTERN.findall(source)))


def function_names(items):
    return sorted({
        str(entry.get("name", "")).rsplit("/", 1)[-1]
        for entry in items if isinstance(entry, dict) and entry.get("name")
    })


def index_signature(item):
    group = item.get("collectionGroup")
    if not group:
        # Firestore's live Index resource identifies its collection in "name";
        # Firebase source index config uses the top-level collectionGroup key.
        name = item.get("name", "")
        match = re.search(r"/collectionGroups/([^/]+)/indexes/[^/]+$", name)
        group = match.group(1) if match else None
    fields = tuple(
        (field.get("fieldPath"), field.get("order"))
        for field in item.get("fields", [])
        if field.get("fieldPath") != "__name__"
    )
    return group, item.get("queryScope", "COLLECTION"), fields


def missing_indexes(expected, deployed):
    ready = {
        index_signature(item) for item in deployed
        if item.get("state") == "READY"
    }
    return sorted({
        item["collectionGroup"] for item in expected
        if index_signature(item) not in ready
    })


def missing_field_overrides(expected, deployed):
    missing = []
    for item in expected:
        group, field = item["collectionGroup"], item["fieldPath"]
        key = group + "/" + field
        cfg = deployed.get(key, {}).get("indexConfig", {})
        ready = {
            (i.get("queryScope", "COLLECTION"), f.get("order"), f.get("arrayConfig"))
            for i in cfg.get("indexes", []) if i.get("state") == "READY"
            for f in i.get("fields", []) if f.get("fieldPath") == field
        }
        required = {(i.get("queryScope", "COLLECTION"), i.get("order"), i.get("arrayConfig"))
                    for i in item.get("indexes", [])}
        if cfg.get("reverting") is True or not required.issubset(ready):
            missing.append(key)
    return sorted(missing)


def same_rules(source, deployed):
    def canonical(value):
        return value.replace("\r\n", "\n").rstrip("\n") + "\n"
    return canonical(source) == canonical(deployed)


def summarize(expected, live, indexes_expected, indexes_deployed,
              rules_source, rules_deployed, scheduler_jobs, database,
              field_expected=(), field_deployed=None, appcheck=None):
    names = set(function_names(live))
    missing = sorted(set(expected) - names)
    bad_indexes = missing_indexes(indexes_expected, indexes_deployed)
    bad_fields = missing_field_overrides(field_expected, field_deployed or {})
    schedule_names = [str(item.get("name", "")).rsplit("/", 1)[-1]
                      for item in scheduler_jobs]
    enabled = {
        str(item.get("name", "")).rsplit("/", 1)[-1]
        for item in scheduler_jobs if item.get("state") == "ENABLED"
    }
    not_scheduled = [
        name for name in SCHEDULED
        if not any(name in job for job in schedule_names)
    ]
    paused = [
        name for name in SCHEDULED
        if name not in not_scheduled and not any(name in job for job in enabled)
    ]
    matches = (
        not missing and not bad_indexes and not bad_fields and
        same_rules(rules_source, rules_deployed) and
        not not_scheduled and not paused and bool(database.get("name"))
    )
    return {
        "observed": {
            "expected_source_function_count": len(expected),
            "deployed_function_count": len(names),
            "missing_source_functions": missing,
            "index_groups_missing_or_unready": bad_indexes,
            "field_indexes_missing_or_unready": bad_fields,
            "app_check": appcheck or {"registration": "not_checked", "firestore_enforcement": "not_checked"},
            "firestore_rules_equal_checked_out_source":
                same_rules(rules_source, rules_deployed),
            "firestore_database_present": bool(database.get("name")),
            "scheduled_functions_missing": not_scheduled,
            "scheduled_functions_disabled_or_paused": paused,
            "scheduler_job_count": len(schedule_names),
        },
        "resource_inventory_matches_source": matches,
        "deployed_function_revision_matches_source": "not_proven",
        "old_0_4_1_client_compatibility": "not_proven",
        "published_0_5_0_client_compatibility": "not_proven",
        "production_ready": False,
        "play_uploaded": False,
        "firebase_mutated": False,
    }


def gcloud_json(label, *args):
    command = ["gcloud", *args, "--format=json", "--quiet"]
    try:
        completed = subprocess.run(
            command, check=True, capture_output=True, text=True, timeout=90
        )
        data = json.loads(completed.stdout)
        return data
    except (subprocess.CalledProcessError, subprocess.TimeoutExpired,
            OSError, ValueError):
        # Never expose gcloud stderr: it may contain project metadata.
        raise AuditUnavailable(label + ":cannot_read") from None


def firebase_rules(project):
    try:
        proc = subprocess.run(
            ["gcloud", "auth", "print-access-token"],
            check=True, capture_output=True, text=True, timeout=20
        )
        credential = proc.stdout.strip()
        if not credential:
            raise AuditUnavailable("rules:authorization_unavailable")
        prefix = "https://firebaserules.googleapis.com/v1/"
        def read(path):
            request = Request(
                prefix + path,
                headers={"Authorization": "Bearer " + credential,
                         "Accept": "application/json"},
                method="GET"
            )
            with urlopen(request, timeout=35) as response:
                return json.loads(response.read())
        release_data = read("projects/" + project + "/releases")
        releases = release_data.get("releases", [])
        if release_data.get("nextPageToken"):
            raise AuditUnavailable("rules:release_list_incomplete")
        selected = next(
            (item for item in releases if
             item.get("name") == "projects/" + project +
             "/releases/cloud.firestore"), None
        )
        if not selected or not selected.get("rulesetName"):
            raise AuditUnavailable("rules:firestore_release_not_found")
        name = selected["rulesetName"]
        if not re.fullmatch(
            r"projects/" + re.escape(project) + r"/rulesets/[A-Za-z0-9_-]+",
            name
        ):
            raise AuditUnavailable("rules:ruleset_reference_invalid")
        contents = read(name)
        files = contents.get("source", {}).get("files", [])
        source_files = [entry.get("content") for entry in files
                        if entry.get("name", "").endswith("firestore.rules")]
        if len(source_files) != 1 or not isinstance(source_files[0], str):
            raise AuditUnavailable("rules:source_file_unavailable")
        return source_files[0]
    except AuditUnavailable:
        raise
    except (subprocess.CalledProcessError, subprocess.TimeoutExpired,
            HTTPError, URLError, OSError, ValueError, KeyError, TypeError):
        raise AuditUnavailable("rules:cannot_read") from None


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def read_metadata(project, overrides):
    # Read metadata only. Do not log authorization, app identifiers, certificate
    # values, cloud responses or function environment variables.
    validate_project(project)
    try:
        proc = subprocess.run(["gcloud", "auth", "print-access-token"],
                              check=True, capture_output=True, text=True, timeout=20)
        credential = proc.stdout.strip()
        if not credential:
            raise AuditUnavailable("metadata:authorization_unavailable")
    except (OSError, subprocess.SubprocessError):
        raise AuditUnavailable("metadata:authorization_unavailable") from None

    def read(url):
        req = Request(url, headers={"Authorization": "Bearer " + credential,
                                    "Accept": "application/json"}, method="GET")
        with build_opener(NoRedirect()).open(req, timeout=35) as response:
            value = json.loads(response.read())
        if not isinstance(value, dict):
            raise AuditUnavailable("metadata:unexpected_shape")
        return value

    field_metadata = {}
    for entry in overrides:
        group, field = entry["collectionGroup"], entry["fieldPath"]
        key = group + "/" + field
        target = ("https://firestore.googleapis.com/v1/projects/" + project +
                  "/databases/(default)/collectionGroups/" + quote(group, safe="") +
                  "/fields/" + quote(field, safe=""))
        try:
            field_metadata[key] = read(target)
        except (HTTPError, URLError, OSError, ValueError, AuditUnavailable):
            # Missing or unready indexes fail the source inventory comparison.
            field_metadata[key] = {}

    appcheck = {"registration": "unverified", "firestore_enforcement": "unverified",
                "sha256_registration_count": None, "play_signing_certificate_match": "not_proven",
                "callable_enforcement": "not_proven"}
    try:
        base = "https://firebase.googleapis.com/v1beta1/projects/" + project
        info = read(base)
        project_number = str(info.get("projectNumber", ""))
        if not re.fullmatch(r"[0-9]+", project_number):
            raise AuditUnavailable("appcheck:project_number_unavailable")
        apps = read(base + "/androidApps")
        if apps.get("nextPageToken"):
            raise AuditUnavailable("appcheck:app_list_incomplete")
        found = [app for app in apps.get("apps", []) if app.get("packageName") == "com.hebi.talksullae"]
        if len(found) != 1:
            appcheck["registration"] = "android_app_missing_or_ambiguous"
        else:
            app_id = found[0].get("appId", "")
            if not re.fullmatch(r"1:[0-9]+:android:[a-fA-F0-9]+", app_id):
                raise AuditUnavailable("appcheck:app_id_invalid")
            app_root = "https://firebaseappcheck.googleapis.com/v1/projects/" + project_number
            try:
                config = read(app_root + "/apps/" + quote(app_id, safe="") + "/playIntegrityConfig")
                appcheck["registration"] = "configured" if config.get("name") else "unverified"
            except HTTPError as error:
                if error.code == 404:
                    appcheck["registration"] = "not_configured"
                else:
                    appcheck["registration"] = "unverified"
            try:
                sha = read(base + "/androidApps/" + quote(app_id, safe="") + "/sha")
                if not sha.get("nextPageToken"):
                    appcheck["sha256_registration_count"] = sum(
                        c.get("certType") == "SHA_256" for c in sha.get("certificates", []))
            except (HTTPError, URLError, OSError, ValueError, AuditUnavailable):
                pass
        service = read("https://firebaseappcheck.googleapis.com/v1/projects/" + project_number +
                       "/services/firestore.googleapis.com")
        mode = service.get("enforcementMode")
        if mode in ("OFF", "UNENFORCED", "ENFORCED"):
            appcheck["firestore_enforcement"] = mode
    except (HTTPError, URLError, OSError, ValueError, AuditUnavailable, TypeError, AttributeError):
        pass
    return field_metadata, appcheck


def run(project):
    validate_project(project)
    source = exported_functions(
        (ROOT / "functions/index.js").read_text(encoding="utf-8")
    )
    index_config = json.loads((ROOT / "firestore.indexes.json").read_text(encoding="utf-8"))
    expected_indexes = index_config["indexes"]
    field_expected = index_config.get("fieldOverrides", [])
    rules_source = (ROOT / "firestore.rules").read_text(encoding="utf-8")
    functions = gcloud_json(
        "functions", "functions", "list", "--v2",
        "--regions=" + REGION, "--project=" + project
    )
    database = gcloud_json(
        "firestore_database", "firestore", "databases", "describe",
        "--database=(default)", "--project=" + project
    )
    indexes = gcloud_json(
        "firestore_indexes", "firestore", "indexes", "composite", "list",
        "--database=(default)", "--project=" + project
    )
    scheduler = gcloud_json(
        "scheduler", "scheduler", "jobs", "list",
        "--location=" + REGION, "--project=" + project
    )
    rules_deployed = firebase_rules(project)
    if not (isinstance(functions, list) and isinstance(database, dict) and
            isinstance(indexes, list) and isinstance(scheduler, list)):
        raise AuditUnavailable("firebase_inventory:unexpected_shape")
    fields, appcheck = read_metadata(project, field_expected)
    return summarize(
        source, functions, expected_indexes, indexes,
        rules_source, rules_deployed, scheduler, database,
        field_expected, fields, appcheck
    )


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--project", required=True)
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()
    result = {
        "stage": "OPS-09-readonly",
        "result": "blocked",
        "source_release": "0.6.0 (7)",
        "cloud_state_confirmed": False,
        "production_ready": False,
        "play_uploaded": False,
        "firebase_mutated": False,
    }
    try:
        result.update(run(args.project))
        result["result"] = (
            "inventory_matches_source" if
            result["resource_inventory_matches_source"] else
            "inventory_mismatch"
        )
        result["cloud_state_confirmed"] = True
    except (AuditUnavailable, ValueError) as exc:
        result["blocker"] = str(exc)
    except (KeyError, TypeError, OSError):
        result["blocker"] = "local_source:unreadable"
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(
        json.dumps(result, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8"
    )
    print(json.dumps(
        {"stage": result["stage"], "result": result["result"],
         "cloud_state_confirmed": result["cloud_state_confirmed"],
         "production_ready": False, "firebase_mutated": False},
        ensure_ascii=False
    ))
    return 0 if result["result"] == "inventory_matches_source" else 2


if __name__ == "__main__":
    sys.exit(main())
