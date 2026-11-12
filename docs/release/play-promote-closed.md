# TalkSullae 0.3.1 (3): internal -> closed Play promotion

## Reason

The user uploaded the signed `TalkSullae_0.3.1_3_internal.aab` to
Google Play Console manually on 2026-11-12 after versionCode 2 was rejected.
The existing `play-closed.yml` requires a never-before-uploaded versionCode
and rebuilds/reuploads the bundle. Reusing versionCode 3 via that workflow
would be rejected if it has already been uploaded.

Google Play supports assigning an already-uploaded bundle to another track
through `edits.tracks.update` without uploading it again. Use the dedicated,
pinned `play-promote-closed.yml` workflow.

## Security gates

- Exact package `com.hebi.talksullae`, existing AAB `0.3.1 (3)`.
- Exact `main` branch; only `workflow_dispatch` or an explicit
  `[promote-closed]` commit touching the promotion workflow/scripts may
  request promotion.
- `PLAY_CLOSED_TRACK` must identify an existing closed-testing track, such
  as the actual `alpha` track as returned by Google Play (never guess an ID).
- The internal `qa` track must already have a **completed** release with
  versionCode 3, and `bundles` must list versionCode 3.
- An unfinished closed draft, a versionCode above 3, a missing track or
  incomplete internal release stops before changes.
- No AAB upload, no new signing keys, no production or internal track writes.
- The protected `play-closed` GitHub environment limits deployment.
- A successful API edit commit and closed-track readback are required for an
  API-level success report. Google review and tester availability are separate.

## Runbook

1. Merge the promotion PR only after all required GitHub checks pass.
2. Run `Play closed-testing promotion (existing internal AAB)` on `main`
   using `mode=check`, or submit an explicit authorized guarded
   `[promote-closed]` commit. Preflight uses existing Play WIF.
3. Review the `talksullae-play-closed-promotion-check` artifact. Missing
   internal release/closed track must be corrected in Play Console.
4. Only when eligible, run `mode=promote` (or let an approved guarded
   push proceed). Review the `talksullae-play-closed-promotion-result`.
5. Confirm `result=accepted_by_api`, `api_committed=true` and
   `verification=track_assigned` or `result=already_promoted`.
6. Confirm Google Play Console policy/review state, tester group and tester
   opt-in link. API assignment does not prove Play reviewer approval.

GitHub workflow:
https://github.com/hebi10/TalkSullae/actions/workflows/play-promote-closed.yml

Google Play docs:
https://developers.google.com/android-publisher/tracks
https://developers.google.com/android-publisher/edits
