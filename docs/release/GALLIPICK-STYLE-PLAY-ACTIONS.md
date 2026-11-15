# 톡술래 — GalliPick 방식 GitHub Actions 테스트 배포

## 현황

앱 패키지: com.hebi.talksullae.
마지막으로 API 게시 이력이 확인된 Play 내부 테스트는 0.4.1, versionCode 5입니다. 현재 main 소스의 0.5.0, versionCode 6은 서명 AAB·Play 게시·테스터 설치가 검증되지 않았습니다.

## 배포 구조

QA → 고정키 서명 AAB → 내부 테스트 → 비공개 테스트 순서로 진행합니다. 한 배치에 내부 게시와 비공개 승격을 각각 승인했다면, [테스트 배포 워크플로](https://github.com/hebi10/TalkSullae/actions/workflows/play-internal.yml)를 한 번 실행해 전체 단계를 진행할 수 있습니다. 운영 Firebase 반영은 Play와 별도 승인 대상입니다.

## 단일 실행

1. 워크플로의 `check` 모드에서 기존 내부 트랙과 비공개 트랙 ID 후보를 조회합니다. Play Console의 비공개 트랙과 대조한 정확한 ID를 `closed_track`에 입력하거나 저장소 변수 `PLAY_CLOSED_TRACK`에 지정합니다.
2. 운영 Firebase의 릴리스 호환성과 기존 설치본 동작을 확인합니다. `main`에서 `deploy-and-promote`를 선택하고, 릴리스 승인·비공개 승격 승인·Firebase 호환성 확인을 선택합니다.
3. 실행이 최신 main과 Android·배포 정책 CI를 검사합니다. 운영 Callable의 경로를 읽기 전용으로 확인하고, 2-D QA·Play 사전 점검을 통과하면 기존 고정키로 AAB를 서명합니다. Callable 응답 확인은 Rules·색인·구버전 호환성 검사를 대신하지 않습니다.
4. `play-internal` 보호 환경에서 내부 테스트를 게시하고, `play-closed` 보호 환경에서 동일한 버전코드와 AAB SHA-256을 Play API에서 다시 확인해 기존 비공개 트랙으로 승격합니다. 버전코드·파일 해시·소스 SHA·배치 ID는 실행이 전달합니다.
5. Actions에서 `talksullae-play-internal-api-result`와 `talksullae-play-closed-api-result-*`를 확인합니다. 내부 게시까지 성공하고 비공개 단계에서 중단되면 원인을 해결한 뒤 같은 실행의 실패 잡을 재실행합니다. 커밋 결과가 불명확하면 다시 실행하기 전에 Play Console에서 상태를 확인합니다.

현재 Play WIF 제공자는 `main`의 `play-internal.yml` 경로를 허용합니다. 단일 실행도 이 경로를 사용하므로 별도 비공개 워크플로를 IAM 허용 목록에 추가할 필요가 없습니다.

## 독립 단계 실행

| 단계 | GitHub Actions | 자동 여부 | Play 변경 |
|---|---|---|---|
| Android/Firebase 및 API 29·35·36 휴대전화, API36 태블릿 QA | [2-D QA](https://github.com/hebi10/TalkSullae/actions/workflows/qa-2d.yml) | PR/main 자동 또는 수동 재실행 | 없음 |
| Android 단위/Lint 및 원본 확인 | [Android verification](https://github.com/hebi10/TalkSullae/actions/workflows/android.yml), [OPS-08 source freeze](https://github.com/hebi10/TalkSullae/actions/workflows/ops08-final-policy.yml) | main/PR | 없음 |
| 서명 AAB 요청 | [OPS-03](https://github.com/hebi10/TalkSullae/actions/workflows/release-build.yml) | 1차 수동 승인 | 없음 |
| 기존 서명 AAB 검증 후 내부 게시 | [OPS-04](https://github.com/hebi10/TalkSullae/actions/workflows/play-internal-release.yml) | 2차 수동 승인 | internal만 |
| 검증된 동일 AAB 비공개 승격 | [OPS-05](https://github.com/hebi10/TalkSullae/actions/workflows/play-closed-release.yml) | 3차 수동 승인 | 기존 비공개 트랙만 |

main Push 시 내부 게시 워크플로가 실행하더라도 **인증·트랙 읽기 전용 점검**만 합니다. 커밋 메시지의 deploy 마커로 바이너리를 서명·업로드할 수 없도록 차단했습니다.

## 필수 설정 점검

GitHub Settings → Secrets and variables → Actions에 톡술래 앱 전용으로 등록되어 있어야 합니다.

- Android Firebase Secret: FIREBASE_ANDROID_GOOGLE_SERVICES_JSON
- 고정 서명키 Secrets: PLAY_UPLOAD_KEYSTORE_BASE64, PLAY_UPLOAD_STORE_PASSWORD, PLAY_UPLOAD_KEY_ALIAS, PLAY_UPLOAD_KEY_PASSWORD
- 공개 서명 인증서 Repository Variable: PLAY_UPLOAD_CERT_SHA256
- Play Publisher WIF Repository Variables: PLAY_WORKLOAD_IDENTITY_PROVIDER, PLAY_SERVICE_ACCOUNT 또는 승인된 GOOGLE_PLAY_SERVICE_ACCOUNT_JSON Secret
- GitHub Environments: play-internal, play-closed에 승인자 지정 권장
- Play Console 앱 com.hebi.talksullae에 실제 내부 테스트 트랙 internal과 비공개 테스트 트랙의 **정확한 API ID**, 테스트 릴리스 권한·테스터·정책 정보 확인

GalliPick의 업로드키, Workload Identity Provider 설정, 서명 인증서를 톡술래에 복사하지 않습니다. 기존 톡술래 서비스 계정의 저장소/브랜치 식별자와 앱별 Play API 권한을 확인해야 합니다.

현재까지 **기존 보호 구성 점검에서 Play API 인증/트랙 readback이 검증되지 않았으며** 새 버전 게시 기록도 없습니다. 성공한 코드 CI와 별개로 실제 인증을 확인해야 합니다.

## OPS-03: 서명 AAB 생성

[OPS-03 서명 빌드](https://github.com/hebi10/TalkSullae/actions/workflows/release-build.yml) → Run workflow / main.

- expected_main_sha: 정확한 최신 main 40자리 SHA
- batch_id: KST 배포 배치 이름 (예: 2026-11-15-talksullae)
- final_batch_approved: 소스·서버 호환성 및 전체 QA 확인 후 true

OPS-03는 **같은 main SHA에서 Android verification, 2-D Android/Firebase 다환경 QA, OPS-08** 세 워크플로의 성공을 요구합니다. 문서만 수정한 main이므로 자동 QA가 생략됐다면 [2-D QA](https://github.com/hebi10/TalkSullae/actions/workflows/qa-2d.yml)를 정확한 main으로 수동 실행해 성공을 확보합니다.

OPS-03은 승인된 빌드 전용 모드만 요청합니다. 성공한 요청서만으로 AAB가 만들어졌다고 볼 수 없습니다. 후속 [Play internal-testing guarded publisher](https://github.com/hebi10/TalkSullae/actions/workflows/play-internal.yml)의 빌드 run ID, 아티팩트, SHA-256, 서명 인증서, 버전/패키지를 직접 확인합니다.

## OPS-04: 내부 테스트 게시

[OPS-04](https://github.com/hebi10/TalkSullae/actions/workflows/play-internal-release.yml)에서 source SHA, build_run_id (OPS-03 요청 run 아님), artifact_name, aab_sha256, version_code, version_name, batch_id를 입력합니다.

1. mode=check: 이미 서명된 AAB와 원본 검증. 게시 없음.
2. mode=publish: final_release_approved=true, approval_phrase=PUBLISH_VERIFIED_INTERNAL_AAB, play-internal 환경 승인 후 정확한 internal 트랙에 제출.
3. Android Publisher API commit/readback에서 버전이 확인돼야 업로드 성공. Google Play Console 심사·실제 테스터 설치 확인은 별도입니다.

## OPS-05: 비공개 테스트 승격

[OPS-05](https://github.com/hebi10/TalkSullae/actions/workflows/play-closed-release.yml)에서 승인된 main SHA·배치 ID·버전코드·AAB SHA-256을 그대로 사용합니다.

- internal_track=internal. 기존 0.4.1의 실제 Play 영수증과 일치하도록 기본값을 수정했습니다.
- closed_track: Play Console/API에서 확인한 정확한 기존 비공개 트랙 ID를 **직접 입력**합니다. 미확인 alpha를 자동 선택하지 않습니다.
- mode=check: completed 내부 릴리스·동일 번들·비공개 트랙 검증. Play 변경 없음.
- mode=promote: final_batch_approved=true, approval_phrase=PROMOTE_VERIFIED_INTERNAL_VERSION을 별도 확인하고 동일 versionCode를 비공개로 승격합니다. 기존 내부 AAB 재업로드 없음.

## 출시 전 서버 및 실기기 검사

[OPS-09 Firebase 읽기 전용 점검](https://github.com/hebi10/TalkSullae/actions/workflows/ops09-firebase-readonly.yml)에서 실제 Cloud Functions/Firestore 규칙·색인/Scheduler 운영 리비전을 확인하고 구버전 설치본과의 API 호환성을 점검합니다. 인증 연결이 없는 경우 배포가 가능하다고 주장하지 않습니다.

Google 계정 2~4개·4종 게임·채팅·초대/가입·FCM·계정 삭제/신고·기기별 IME와 접근성 QA를 별도로 검증합니다. Play API commit은 심사 완료나 실제 설치 성공을 보장하지 않습니다.

**이 문서와 PR은 CI/배포 설정만 변경합니다. Firebase 운영 데이터와 Google Play 트랙은 변경하지 않습니다.**
