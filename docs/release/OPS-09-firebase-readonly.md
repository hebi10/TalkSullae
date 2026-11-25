# OPS-09 — Firebase 운영 리비전 읽기 전용 점검

## 목적

LIVE-01~08의 GitHub 소스 검증과 **현재 배포된** Firebase 프로젝트 상태를 구분합니다. 인벤토리는 로그인 계정·Firestore 사용자 문서를 조회하지 않으며, Cloud Functions 메타데이터·Firestore 데이터베이스·컴포지트 색인·Rules 리비전·Cloud Scheduler 작업만 읽습니다.

- 코드 대상: TalkSullae main, Android 신규 후보 0.5.0(6), 서울 Functions 리전 asia-northeast3.
- 이전 Google Play internal API 게시본: 0.4.1(5), 실제 Actions 원본 commit c9a80eb09a70a27ce2bd500b0aedf7c2a0924987.
- 구버전 21개 Functions export는 최신 main에 이름이 유지되지만, 실제 배포 리비전과 입력/응답 호환성은 아직 확인되지 않았습니다.

## GitHub Actions로 실행

- 신규 수동 워크플로: `.github/workflows/ops09-firebase-readonly.yml`.
- 자동 PR CI에서는 읽기 전용 파서의 로컬 단위 테스트만 실행합니다. **Cloud 인증은 수동 workflow_dispatch에서만** 요청합니다.
- Repository Variables에 정확한 Firebase 운영 프로젝트 ID를 `FIREBASE_AUDIT_PROJECT_ID`로 설정합니다. 기존 Google Play용 WIF가 아닌 **별도의 읽기 전용** WIF provider `FIREBASE_AUDIT_WIF_PROVIDER`와 서비스 계정 `FIREBASE_AUDIT_SERVICE_ACCOUNT`를 연결해야 합니다.
- 서비스 계정에는 필요한 읽기 전용 권한만 부여합니다: Cloud Functions Viewer (`roles/cloudfunctions.viewer`), Cloud Scheduler Viewer (`roles/cloudscheduler.viewer`), Firebase Rules Viewer (`roles/firebaserules.viewer`)와 Firestore **메타데이터 조회만 허용하는 사용자 지정 IAM 역할**(`datastore.databases.get`, `datastore.indexes.list` 등 필요한 최소 권한). `roles/datastore.viewer`는 사용자 데이터 읽기 권한까지 포함하므로 감사 전용 계정에는 부여하지 않습니다. OIDC 사용자에게는 이 감사용 계정의 `roles/iam.workloadIdentityUser`만 최소 범위로 허용합니다. Firebase Admin / Editor / Play Publisher 권한 금지.
- GitHub Environment `firebase-audit`에 운영자 승인자를 설정할 수 있습니다. 사전 변수·WIF·권한이 없으면 읽기 전용 점검은 실패하며 **배포를 시작하지 않습니다**.
- Actions → **OPS-09 Firebase production inventory (read-only)** → Run workflow(main)에서 `expected_main_sha`에 정확한 최신 main SHA, `project_id`에 운영 Firebase 프로젝트 ID를 입력합니다. 둘은 보호된 변수 및 실행 커밋과 일치해야 합니다.

## 판정 및 개인정보

- Cloud Functions 목록을 Firebase 소스 export와 비교하고 누락 함수만 이름으로 기록합니다. **함수 이름이 일치해도 소스 리비전 동일 여부는 증명되지 않습니다.**
- Firestore `(default)` 데이터베이스 존재, 필요한 컴포지트 색인 READY 여부, 현재 `cloud.firestore` Rules 소스의 정규화된 일치 여부를 확인합니다. 색인 생성·Rules 게시/수정은 하지 않습니다.
- 삭제/메시지 만료/게임 자동 마감/신고 대기열 4종 Scheduler 작업 존재와 ENABLED 상태를 확인합니다. 작업을 실행·변경하지 않습니다.
- 출력 JSON에는 인벤토리 일치 여부·누락 함수/색인 그룹·작업 상태만 기록합니다. 실제 Rules 원문, 함수 환경 변수, FCM 토큰, 사용자 메시지, 사용자 ID, 서비스 계정 인증값은 출력·아티팩트에 포함하지 않습니다.
- API 권한/응답 부재, 색인 생성 중, Rules 불일치, 함수/스케줄러 누락은 모두 **차단**으로 판정합니다.

`inventory_matches_source`가 반환되더라도 **운영 준비 완료가 아닙니다**. 구버전 0.4.1 설치본의 API 호환성, Google OAuth 실계정 2~4개, 채팅/게임/FCM/계정 삭제 실기기 E2E, signed AAB/Play 게시·심사·테스터 설치 및 비공개 테스트는 독립 검증이 필요합니다.

## 안전성 경계

이 워크플로에는 Firebase deploy, Functions/Rules/Indexes/Scheduler create/update/delete, 사용자 문서 읽기, Play Publisher 호출이 없습니다. 프로덕션 배포/서명 AAB/Play 업로드는 AGENTS.md의 **별도 명시적 승인 게이트**를 따릅니다. 이 PR의 병합은 운영 클라우드에 변경을 가하지 않습니다.

공식 명세:
- https://docs.cloud.google.com/sdk/gcloud/reference/functions/list
- https://docs.cloud.google.com/sdk/gcloud/reference/firestore/indexes/composite/list
- https://docs.cloud.google.com/sdk/gcloud/reference/scheduler/jobs/list
- https://firebase.google.com/docs/reference/rules/rest/v1/projects.releases/list
- https://docs.cloud.google.com/iam/docs/roles-permissions/firebaserules

## TS-R2-02 추가 운영 메타데이터 및 구버전 호환성
- 단일 필드 `fieldOverrides`의 READY 상태도 확인한다. 컴포지트 색인만 일치해도 만료/게임 마감 쿼리가 준비됐다고 간주하지 않는다.
- Firebase Management Android 앱 목록에서 패키지 1개를 확인하고, App Check Play Integrity 등록 여부 및 Firestore enforcement 상태, SHA-256 등록 개수만 출력한다. 공개 지문이라도 실제 값·앱 ID·API 응답·인증 정보는 감사 파일에 출력하지 않는다.
- 해당 REST GET에 대한 기존 IAM 권한이 부족하면 미확인으로 남는다. 승인된 감사 전용 계정의 필요한 메타데이터 권한만 운영자가 검토한다. 새로운 역할/설정 변경/강제 적용을 이 PR이 수행하지 않는다.
- 배포된 0.5.0(6)은 3종 아바타만 허용한다. 새 서버는 프로필의 `avatar`를 기존 3종 값으로 유지하고 `avatarV2`를 별도로 보관한다. 신규 앱은 우선 V2를 읽고 기존 데이터에도 대응한다. 방 멤버는 실제 선택을 유지한다. 기존에 8종이 `avatar`에 직접 저장된 운영 데이터는 미확인으로, 실제 인벤토리 확인 후 필요한 범위만 별도 마이그레이션한다.
- 새 Functions를 먼저 호환 모드로 검증한 후 새 클라이언트를 배포하고 App Check 강제 전 구버전 업데이트 경로를 확보한다. 메타데이터 일치가 운영 리비전 동일/토큰 정상/Play 서명 일치를 증명하지 않는다.
- 공식 API: https://firebase.google.com/docs/reference/firebase-management/rest/v1beta1/projects.androidApps/list ; https://firebase.google.com/docs/reference/appcheck/rest/v1/projects.apps.playIntegrityConfig/get ; https://firebase.google.com/docs/reference/appcheck/rest/v1/projects.services/get ; https://cloud.google.com/firestore/docs/reference/rest/v1/projects.databases.collectionGroups.fields/get
