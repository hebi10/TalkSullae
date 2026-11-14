# 2-H Google Play 내부 테스트 전용 배포

## 기본 원칙
- 배포 대상은 Google Play API에서 확인한 기존 내부 테스트 트랙입니다.
- 소스 패키지: `com.hebi.talksullae` · GAME-05 내부 테스트 준비 버전 `0.4.0` / `versionCode=4` (Play API 등록 전).
- 비공개 \`alpha\`, 공개 \`beta\`, \`production\` 및 기기별 테스트 트랙은 내부 배포 스크립트에서 **거부**합니다.
- 기존 \`.github/workflows/play-closed.yml\` 및 비공개 트랙 설정은 변경하지 않습니다.
- 서명 및 Play API 인증을 갖추지 못했다면 **AAB를 업로드하지 않고**, 무엇이 부족한지 안전한 보고서로 남깁니다.
- Play API의 \`track_assigned\` 확인은 Play Console 게시·테스터 설치가 승인됐다는 의미가 아닙니다.

## 실행 방법
- OPS-03의 `release-build.yml`을 수동 실행하면 검증한 `main`에서 서명 AAB 빌드를 요청합니다.
- `Play internal-testing guarded publisher`의 `mode=check`는 앱 `com.hebi.talksullae`의 Android Publisher API 접근, 내부 테스트 트랙 및 등록 버전을 조회합니다.
- \`mode=build\`: Android/Firebase 전체 QA 통과 후 기존 고정 업로드키로 Release AAB를 생성·서명·무결성 확인해 비공개 아티팩트로 보관합니다. **Play 업로드 없음**.
- OPS-04는 기존 서명 AAB를 검증한 뒤 `publish` 모드와 `play-internal` 환경을 통해 확인된 내부 테스트 트랙에만 게시합니다. Play API에서 커밋한 버전을 다시 조회합니다.
- 서명 빌드와 내부 테스트 게시를 각각 수동으로 실행합니다.

## Secrets 및 Variables
- Firebase Android 구성: \`FIREBASE_ANDROID_GOOGLE_SERVICES_JSON\` (기존 연결 이력 존재).
- 고정 Play 업로드키: \`PLAY_UPLOAD_KEYSTORE_BASE64\`, \`PLAY_UPLOAD_STORE_PASSWORD\`, \`PLAY_UPLOAD_KEY_ALIAS\`, \`PLAY_UPLOAD_KEY_PASSWORD\`.
- 키 지문: Variables \`PLAY_UPLOAD_CERT_SHA256\`.
- Google Play API: WIF Variables \`PLAY_WORKLOAD_IDENTITY_PROVIDER\` + \`PLAY_SERVICE_ACCOUNT\`, 또는 \`GOOGLE_PLAY_SERVICE_ACCOUNT_JSON\` Secret. Play Console에서 서비스 계정에 앱의 테스트 릴리스 권한이 필요합니다.
- 내부 테스트는 Android Publisher API에 존재하는 `qa` 또는 `internal`을 확인합니다. `PLAY_CLOSED_TRACK`은 비공개 테스트에 사용합니다.
- 모든 비밀값은 Secrets에 등록하고 공개 GitHub/문서/로그에 표시하지 않습니다. GalliPick 등 다른 앱의 업로드키를 승인 없이 복사하지 않습니다.

## 현재 검증 상태 및 남은 작업
- 최신 내부 테스트 감사 아티팩트(2026-11-11, run #43038827532): `config_status=blocked`, `auth_method=none`, `can_attempt_signed_aab=true`, `can_check_play_api=false`, `versionCode=3`. GitHub WIF/Play API 자격 설정만 주요 배포 인증 차단으로 보고됐습니다.
- Play Console 앱 생성 및 이전 Alpha 초안 기록은 있으나, 초기 업로드키와 \`qa\` 트랙 접근 권한·최초 AAB 등록 성공 여부는 직접 조회되지 않았습니다.
- `versionCode=3`은 모든 기존 Play 업로드/트랙에서 사용된 최대 코드보다 커야 합니다. API `check`로 실조회 전에는 업로드 가능하다고 단정하지 않습니다.
- 개인정보처리방침의 이전 PR #16은 충돌·중복으로 종료했고, 최신 정책·안전 신고를 보존한 RELEASE-01 PR #20을 `main`(`5133a470`)에 병합했습니다. Android API 29/35/36 휴대전화·API36 태블릿 QA가 통과했습니다.
- 내부 테스트는 완전한 스토어 설정 이전에 시작할 수 있으며 최대 100명의 테스터에게 배포 가능합니다. 다만 첫 배포가 승인되지 않았거나 테스트 링크가 활성화되지 않으면 실제 설치 성공으로 표시하지 않습니다.

공식 자료:
https://developers.google.com/android-publisher/tracks
https://support.google.com/googleplay/android-developer/answer/9845334

## RELEASE-02 — Google Cloud WIF 및 Play Publisher 인증 복구 (2026-11-12)

**판정: 외부 IAM/Play/GitHub 인증 설정 대기. 코드·서명 파일 준비와 Play API 인증 성공을 혼동하지 않습니다.**

- 기준: `main=5133a4706b7d11ce487e3ccda42ef3a3544faaff`, Android `0.3.1 (3)`.
- 2026-11-11 최신 비밀값 비출력 감사: [Actions #43038827532](https://github.com/hebi10/TalkSullae/actions/runs/43038827532) → `auth_method=none`, `config_status=blocked`, 서명 설정은 준비됨.
- [WIF 인증 실패 #42997998369](https://github.com/hebi10/TalkSullae/actions/runs/42997998369): `unauthorized_client` / `The given credential is rejected by the attribute condition.`. GalliPick의 성공한 제공자가 톡술래 저장소를 받아들이지 않아 **Google Cloud 토큰 발급 이전**에 차단됨.
- [GalliPick 정상 인증/배포 #42947356135](https://github.com/hebi10/GalliPick/actions/runs/42947356135): WIF 경로 작동 확인. 기존 GalliPick의 배포 설정을 훼손하거나 무제한으로 넓히지 않습니다.
- 현재 연결 도구에서 Google Cloud IAM/Play Console 권한과 GitHub Actions Variables를 직접 변경하거나 Play API 호출을 수행할 수 없으므로 인증 **완료로 표기하지 않음**.

### A. Google Cloud IAM (관리자 권한 필요)

1. Google Cloud Console → IAM 및 관리자 → Workload Identity Federation에서 GalliPick이 사용 중인 Provider의 `Attribute conditions`를 읽기 전용으로 확인합니다. 정확한 기존 식을 검토하지 않고 덮어쓰지 않습니다.
2. 권장: GalliPick과 별도의 최소 권한 TalkSullae 전용 Pool/Provider 및 서비스 계정(또는 명확히 격리된 신뢰 구간)을 마련합니다. 기존 Provider 조건 수정이 꼭 필요한 경우에도 GalliPick 접근을 유지하면서 추가 저장소·브랜치만 명시적으로 허용합니다.
3. 톡술래 전용 Provider는 GitHub OIDC issuer `https://token.actions.githubusercontent.com/`를 사용하고, Repository ID `1452186114`, Owner ID `80847404`, `main`만 허용하도록 구성합니다. 예시 CEL 조건:

```cel
assertion.repository_id == '1452186114' &&
assertion.repository_owner_id == '80847404' &&
assertion.ref == 'refs/heads/main'
```

4. WIF의 attribute mapping은 최소 `google.subject=assertion.sub`, 필요 시 `attribute.repository_id=assertion.repository_id`를 포함합니다. Cloud 서비스 계정의 권한에는 정확히 해당 저장소 신원(예: `principalSet://iam.googleapis.com/projects/PROJECT_NUMBER/locations/global/workloadIdentityPools/POOL_ID/attribute.repository_id/1452186114`)에만 `roles/iam.workloadIdentityUser`를 부여합니다. GitHub 저장소 전체나 Pool 전체에 무제한 권한을 주지 않습니다.
5. Android Publisher API가 서비스 계정이 속한 Google Cloud 프로젝트에서 활성화되어 있는지 확인합니다.

### B. Google Play Console (소유자 권한 필요)

1. Play Console → 사용자 및 권한에서 승인한 서비스 계정 이메일을 등록/확인합니다. GalliPick 앱의 기존 권한이 톡술래에 자동 적용되었다고 가정하지 않습니다.
2. 앱 `com.hebi.talksullae`에 대한 앱 정보 조회 권한과 **테스트 트랙 앱 출시(Release apps to testing tracks)** 권한을 최소 범위로 부여합니다. 공개/프로덕션 출시 권한은 부여하지 않습니다.
3. 내부 테스트 트랙 `qa`가 앱에 존재하는지와 기존 업로드된 최고 `versionCode`를 확인합니다. 앱의 초기 등록/심사 제한은 Play Console에서 별도 확인합니다.

### C. GitHub Actions 인증 연결

1. 톡술래 저장소 Settings → Secrets and variables → Actions → **Repository variables**에 다음 두 값을 설정합니다. 키 문자열 원문을 PR/이슈/로그에 출력하지 않습니다.
   - `PLAY_WORKLOAD_IDENTITY_PROVIDER`: 톡술래에 허용된 Provider의 전체 리소스 이름(`projects/PROJECT_NUMBER/locations/global/workloadIdentityPools/POOL_ID/providers/PROVIDER_ID`).
   - `PLAY_SERVICE_ACCOUNT`: Play Console에서 해당 앱 권한을 부여한 서비스 계정 이메일.
2. `play-preflight`와 메인 Push `audit`은 GitHub Environment를 사용하지 않으므로 **Repository variables**로 등록해야 합니다. `play-internal` Environment에만 변수를 두면 사전 검사에서 읽지 못합니다.
3. 서비스 계정 JSON Secret `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`은 승인된 별도 경로이지만, 가능한 한 장기 키를 만들지 않는 WIF를 우선합니다. 기존 톡술래 업로드키 Secrets와 지문 변수는 변경하지 않습니다.
4. 내부 테스트의 트랙 ID는 Play API 조회로 확인합니다.

### D. 무배포 인증 재검증 및 성공 기준

1. [Play internal-testing guarded publisher](https://github.com/hebi10/TalkSullae/actions/workflows/play-internal.yml) → `Run workflow` → Branch `main` → Mode `check`를 실행합니다. **AAB 업로드 없음**.
2. WIF 인증 성공, Android Publisher API 앱 `com.hebi.talksullae` 접근 성공, `qa` 트랙 조회, 기존 업로드 최고 코드 대비 신규 `versionCode=3` 사용 가능성 확인이 모두 필요합니다.
3. 해당 run의 `play-preflight.json`에서 인증·앱·트랙 접근 성공을 확인하고 이슈 #18에 **비밀값 없이** 증빙을 남긴 경우에만 RELEASE-02 완료 처리합니다. 이 단계에서 빌드/배포를 임의 실행하지 않습니다.
4. 이후 RELEASE-03은 기존 고정 업로드키로 최신 AAB를 생성하여 `build` 검증하고, 별도 승인 범위에서 `deploy` → Android Publisher API commit/readback → Play Console 테스터 설치 검증 순서로 진행합니다.

공식 자료:
- https://docs.cloud.google.com/iam/docs/workload-identity-federation-with-deployment-pipelines
- https://developers.google.com/android-publisher/getting_started
- https://support.google.com/googleplay/android-developer/answer/9859348

## GAME-05 내부 테스트 릴리스 준비 — 2026-11-14

- 소스: GAME-05까지 병합된 `main` 37dcc52fe5c246d603375b044dfeb0baabb15d67에서 준비한 신규 버전 `0.4.0 (4)`.
- Android 서명 AAB를 게시했다고 주장하지 않습니다. 이 문서/PR/CI는 출시가 아니며 Google Play API 승인 전입니다.
- 앞선 RELEASE-03 시도 #43145896908은 AAB 업로드 acknowledgement 이후 `qa` 트랙에서 HTTP 404가 발생, `api_committed=false`. 당시 read-only check의 `internal_track_exists=false`는 과거 사실이며 최신 Play Console 상태는 재조회해야 합니다.
- 내부 테스트 워크플로는 Play API에 이미 존재하는 `qa` 또는 `internal` 중 정확히 하나를 확인합니다. 두 ID가 동시에 나타나면 배포 전에 중단합니다.
- `check` 모드는 확인한 내부 트랙 ID와 등록된 최고 versionCode를 기록합니다. API에서 내부 트랙이 보이지 않으면 `ready_for_deploy=false`를 기록하며, Console의 내부 출시 상태와 API 권한을 대조해야 합니다.
- `deploy`/OPS-04 `publish` 모드는 내부 트랙이 확인된 경우에만 기존 서명 정책을 적용한 AAB를 해당 트랙에 업로드합니다.
- 배포 전에 Play Console 소유자가 앱 `com.hebi.talksullae`의 내부 테스트 트랙을 확인하고, 필요하면 최초 릴리스/테스터 설정을 완료해야 합니다.
- `0.4.0 (4)` 버전의 최종 사용 가능 여부는 실시간 Android Publisher API `check` 결과로 결정해야 하며 확인 없이 최신 버전으로 단정하지 않습니다.
- 기능: 실제 게임 4종(너 누구야, 라이어 채팅, 금지어 채팅, 밸런스 예측), 친구 방·채팅, 신고·차단·계정 삭제, 전적·시즌·업적.
- 새로운 서버 Callable, Firestore Rules, 인덱스, Cloud Scheduler가 운영에 필요한 기능이므로 운영 Firebase가 해당 소스와 호환되는지 확인·승인 배포하지 않은 상태에서는 Play 내부 테스트를 진행해도 기능 정상 동작을 보장할 수 없습니다.
- 현재 연결된 GitHub 도구는 workflow_dispatch 요청을 제공하지 않습니다. 수동 Run workflow 또는 GitHub에서 승인된 Work 모드가 필요합니다. 커밋 메시지 배포 마커로 승인 게이트를 우회하지 않습니다.
- 정확한 최신 main SHA에서 `Android verification` CI 성공 → OPS-03 서명 AAB 빌드 전용 → SHA256·서명·패키지·versionCode 확인 → OPS-04 독립 승인 내부 테스트 업로드 → API commit/readback → Console 게시·테스터 설치 확인 순서를 준수합니다.
- 비공개 `alpha` 및 프로덕션 트랙은 변경하지 않습니다. 기존 0.3.1 (3) 비공개 승격 워크플로는 역사적 버전에 고정돼 있으므로 새 코드 4를 임의 승격하지 않습니다.
