"""OPS-09 inventory parser and fail-closed contract tests; no cloud calls."""
import importlib.util
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name("firebase_inventory.py")
spec = importlib.util.spec_from_file_location("ops09_inventory", SCRIPT)
audit = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit)


class InventoryTests(unittest.TestCase):
    def test_project_identifier_rejects_shell_metacharacters(self):
        self.assertEqual(audit.validate_project("talksullae-prod"), "talksullae-prod")
        for value in ("", "../prod", "example;rm", "A-p", "http://google.com"):
            with self.subTest(value=value):
                with self.assertRaises(ValueError):
                    audit.validate_project(value)

    def test_functions_are_distinct_without_exposing_source(self):
        code = "exports.createRoom = onCall(a);\nexports.saveMyProfile=onCall(b);\n"
        self.assertEqual(
            audit.exported_functions(code), ["createRoom", "saveMyProfile"]
        )
        self.assertEqual(
            audit.function_names([
                {"name": "projects/p/locations/asia-northeast3/functions/createRoom"},
                {"name": "projects/p/locations/asia-northeast3/functions/saveMyProfile"}
            ]),
            ["createRoom", "saveMyProfile"]
        )

    def test_index_allows_implicit_name_order_and_rejects_building(self):
        src = [{
            "collectionGroup": "safetyReports",
            "fields": [{"fieldPath": "status", "order": "ASCENDING"},
                       {"fieldPath": "__name__", "order": "ASCENDING"}]
        }]
        server = [{
            "name": "projects/demo/databases/(default)/collectionGroups/"
                    "safetyReports/indexes/abc123",
            "queryScope": "COLLECTION", "state": "READY",
            "fields": [{"fieldPath": "status", "order": "ASCENDING"},
                       {"fieldPath": "__name__", "order": "ASCENDING"}]
        }]
        self.assertEqual(audit.missing_indexes(src, server), [])
        server[0]["queryScope"] = "COLLECTION_GROUP"
        self.assertEqual(audit.missing_indexes(src, server), ["safetyReports"])
        server[0]["queryScope"] = "COLLECTION"
        server[0]["state"] = "CREATING"
        self.assertEqual(audit.missing_indexes(src, server), ["safetyReports"])

    def test_rules_checksum_normalization_and_mismatch(self):
        self.assertTrue(audit.same_rules("rules\r\n", "rules\n"))
        self.assertFalse(audit.same_rules("allow read: if false;", "allow read: if true;"))

    def test_full_parity_does_not_claim_release_or_client_compatibility(self):
        wanted = ["createRoom", "saveMyProfile"]
        deployed = [
            {"name": "projects/p/locations/asia-northeast3/functions/" + name}
            for name in wanted
        ]
        schedules = [
            {"name": "projects/p/locations/asia-northeast3/jobs/firebase-schedule-" +
                     name, "state": "ENABLED"}
            for name in audit.SCHEDULED
        ]
        result = audit.summarize(
            wanted, deployed, [], [], "rules\n", "rules\n",
            schedules, {"name": "projects/p/databases/(default)"}
        )
        self.assertTrue(result["resource_inventory_matches_source"])
        self.assertEqual(result["deployed_function_revision_matches_source"], "not_proven")
        self.assertFalse(result["production_ready"])
        self.assertFalse(result["firebase_mutated"])

    def test_missing_functions_scheduler_and_index_fail_closed(self):
        report = audit.summarize(
            ["createRoom"], [], [{"collectionGroup": "safetyReports",
                                   "fields": [{"fieldPath": "status",
                                               "order": "ASCENDING"}]}],
            [], "rules", "different rules", [], {"name": ""}
        )
        self.assertFalse(report["resource_inventory_matches_source"])
        self.assertIn("createRoom", report["observed"]["missing_source_functions"])
        self.assertIn("auditSafetyBacklog", report["observed"]["scheduled_functions_missing"])

    def test_required_field_index_is_not_satisfied_by_composite_or_building_index(self):
        expected = [{"collectionGroup":"messages","fieldPath":"expiresAt",
                     "indexes":[{"queryScope":"COLLECTION_GROUP","order":"ASCENDING"}]}]
        cfg = {"messages/expiresAt":{"indexConfig":{"indexes":[
            {"state":"READY","queryScope":"COLLECTION_GROUP","fields":[{"fieldPath":"expiresAt","order":"ASCENDING"}]}
        ]}}}
        self.assertEqual(audit.missing_field_overrides(expected,cfg), [])
        cfg["messages/expiresAt"]["indexConfig"]["indexes"][0]["state"]="CREATING"
        self.assertEqual(audit.missing_field_overrides(expected,cfg), ["messages/expiresAt"])
        self.assertEqual(audit.missing_field_overrides(expected,{}), ["messages/expiresAt"])
        report = audit.summarize([], [], [], [], "rules", "rules", [], {"name":"db"},
                                 expected, cfg, {"registration":"unverified"})
        self.assertFalse(report["resource_inventory_matches_source"])
        self.assertFalse(report["production_ready"])

    def test_no_mutating_cloud_command_or_dangerous_output(self):
        source = SCRIPT.read_text(encoding="utf-8")
        for forbidden in (
            "gcloud functions deploy", "gcloud firestore indexes composite create",
            "gcloud scheduler jobs run", "firebase deploy",
            "print(credential)", "print(proc.stdout)", "read_secret",
            "credentials.json", "private_key",
        ):
            with self.subTest(forbidden=forbidden):
                self.assertNotIn(forbidden, source)


if __name__ == "__main__":
    unittest.main()
