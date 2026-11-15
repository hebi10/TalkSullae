"""Offline regression guards for the TalkSullae GalliPick-style release pipeline."""
import importlib.util
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
WF = ROOT / ".github" / "workflows"


def workflow(name):
    return (WF / name).read_text(encoding="utf-8")


class ReleaseWorkflowContractTests(unittest.TestCase):
    def test_full_device_matrix_runs_on_main_and_feature_pr(self):
        qa = workflow("qa-2d.yml")
        self.assertIn("  push:\n    branches: [main]", qa)
        self.assertIn("  pull_request:", qa)
        self.assertIn("  workflow_call:", qa)
        for label in ("api29-phone", "api35-phone", "api36-phone", "api36-tablet"):
            with self.subTest(label=label):
                self.assertIn(label, qa)
        self.assertIn("emulators:exec", qa)
        self.assertNotIn("firebase deploy", qa)
        self.assertNotIn("PLAY_ACCESS_TOKEN", qa)

    def test_approved_build_needs_exact_main_ci_and_never_deploys(self):
        build = workflow("release-build.yml")
        for name in (
            "Android verification",
            "2-D Android and Firebase multi-environment QA",
            "OPS-08 final main source and release policy verification",
        ):
            with self.subTest(check=name):
                self.assertIn(name, build)
        self.assertIn('if os.environ["FINAL_BATCH_APPROVED"]!="true"', build)
        self.assertIn('sha!=os.environ["GITHUB_SHA"]', build)
        self.assertIn('inputs[mode]=build', build)
        self.assertIn('inputs[ops01_release_approved]=true', build)
        self.assertNotIn('inputs[mode]=deploy', build)

    def test_push_only_audits_and_cannot_sign_or_publish(self):
        internal = workflow("play-internal.yml")
        self.assertIn("  push:\n    branches: [main]", internal)
        self.assertIn("    if: github.event_name == 'push'", internal)
        self.assertIn("github.event_name == 'workflow_dispatch'", internal)
        self.assertNotIn("contains(github.event.head_commit.message", internal)
        self.assertIn('MODE="$REQUESTED_MODE"', internal)
        self.assertIn('"$REQUESTED_APPROVED" != "true"', internal)
        self.assertIn("inputs.ops01_release_approved", internal)
        self.assertIn("play_internal.py check", internal)
        self.assertIn("play_internal.py deploy", internal)
        self.assertIn("needs.qa.result == 'success'", internal)
        self.assertIn("environment: play-internal", internal)

    def test_internal_stage_uses_existing_signed_artifact(self):
        signed = workflow("play-internal-release.yml")
        self.assertIn("  workflow_dispatch:", signed)
        self.assertIn("  BUILD_RUN_ID:", signed)
        self.assertIn("  AAB_SHA256:", signed)
        self.assertIn("  BUILD_WORKFLOW_FILE: .github/workflows/play-internal.yml", signed)
        self.assertIn("PUBLISH_VERIFIED_INTERNAL_AAB", signed)
        self.assertIn("actions/download-artifact@v4", signed)
        self.assertIn("scripts/ops04/internal.py verify", signed)
        self.assertNotIn("bundleRelease", signed)
        self.assertNotIn("  push:", signed)

    def test_one_dispatch_requires_both_approvals_and_reuses_verified_internal_aab(self):
        internal = workflow("play-internal.yml")
        self.assertIn("options: [check, build, deploy, deploy-and-promote]", internal)
        self.assertIn("needs: [source, internal-publish]", internal)
        closed = internal.split("\n  closed-publish:\n", 1)[1]
        self.assertIn("github.event_name == 'workflow_dispatch'", closed)
        self.assertIn("inputs.ops01_release_approved == true", closed)
        self.assertIn("inputs.closed_promotion_approved == true", closed)
        self.assertIn("inputs.firebase_compatibility_verified == true", closed)
        self.assertIn("environment: play-closed", closed)
        self.assertIn("name: talksullae-play-internal-api-result", closed)
        self.assertIn("play_testing_promotion.py promote", closed)
        self.assertNotIn("bundleRelease", closed)
        self.assertNotIn("play_internal.py deploy", closed)

    def test_closed_preflight_and_production_compatibility_gate_internal_publish(self):
        internal = workflow("play-internal.yml")
        preflight, publish = internal.split("\n  internal-publish:\n", 1)
        publish = publish.split("\n  closed-publish:\n", 1)[0]
        self.assertIn("play_testing_promotion.py preflight", preflight)
        self.assertIn("play_testing_promotion.py inspect", preflight)
        self.assertIn("check_firebase_callables.py", preflight)
        self.assertIn("needs.play-preflight.result == 'success'", publish)
        self.assertIn("inputs.firebase_compatibility_verified == true", publish)
        self.assertIn('gh api "repos/$GITHUB_REPOSITORY/git/ref/heads/main"', publish)

    def test_closed_stage_defaults_to_actual_internal_and_requires_real_closed(self):
        closed = workflow("play-closed-release.yml")
        self.assertRegex(
            closed,
            r"(?s)      internal_track:\n.*?        default: internal\n"
            r"        options: \[internal, qa\]",
        )
        closed_input = closed.split("      closed_track:", 1)[1].split(
            "      final_batch_approved:", 1
        )[0]
        self.assertIn("required: true", closed_input)
        self.assertNotIn("default: alpha", closed_input)
        self.assertIn("PROMOTE_VERIFIED_INTERNAL_VERSION", closed)
        self.assertIn("scripts/ops05/promotion.py check", closed)
        self.assertIn("scripts/ops05/promotion.py promote", closed)
        self.assertNotIn("actions/download-artifact", closed)
        self.assertNotIn("  push:", closed)

    def test_no_unapproved_auto_publisher_introduced(self):
        path = ROOT / "scripts" / "ops07" / "audit.py"
        spec = importlib.util.spec_from_file_location("release_audit", path)
        mod = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(mod)
        self.assertEqual(mod.findings(), [])


if __name__ == "__main__":
    unittest.main()
