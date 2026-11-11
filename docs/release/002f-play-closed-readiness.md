# 2-F — Google Play 비공개 테스트 배포 준비·검증

## 범위
- 패키지: `com.hebi.talksullae`
- 현재 소스: `0.3.0`, `versionCode=2`. Google Play의 업로드된 최대 버전코드보다 커야 합니다.
- `.github/workflows/play-preflight-2f.yml`: Secrets 없는 릴리스 컴파일·릴리스 로직 단위 테스트 및 보호 설정/Play API 읽기 검사. **절대 서명 AAB 업로드하지 않음**.
- `.github/workflows/play-closed.yml`: 승인된 `main`의 `check → build → deploy`만 수행. 공개·오픈·내부 트랙 사용 금지.

## 현재 확인 사항 (2026-11-11)
- Android/Firebase 전체 QA: https://github.com/hebi10/TalkSullae/actions/runs/42957304091 성공.
- 최신 병합 `main`: `766ba74c64280533bf5dc601eb2f03e7be14217e`; Android 빌드: https://github.com/hebi10/TalkSullae/actions/runs/42958764872 성공.
- Google Drive `92_서명키_비공개` 폴더에는 Firebase 설정 파일만 확인됨. **Play 업로드 keystore 보관 여부는 확인되지 않음**. 다른 안전한 위치나 GitHub Actions Secrets 등록 가능성은 별도 확인 필요.
- Play Console 계정·앱·비공개 트랙 실조회 불가; 비공개 배포 이력 없음. GitHub Secret 값은 읽지 않음.

## 1. Play Console 최초 설정
1. 톡술래 Android 앱 `com.hebi.talksullae`가 생성됐는지 검증합니다. 앱 패키지명은 첫 등록 후 변경 불가합니다.
2. 비공개 테스트 트랙과 테스터 그룹·국가를 준비합니다. Console의 실제 트랙 API ID를 확인합니다.
3. 스토어 기본 정보, 개인정보처리방침·데이터 보안·콘텐츠 등급·연령·광고·앱 접근 등 필요한 선언과 첫 앱 출시 설정을 완료합니다.
4. 최초 앱 등록·필수 선언·Play 앱 서명 등록은 Play Console에서 수행합니다. API만으로 신규 앱 생성과 정책 선언을 대체할 수 없습니다.

## 2. 비공개 GitHub Actions 설정
Actions Secrets:
- `FIREBASE_ANDROID_GOOGLE_SERVICES_JSON` — Firebase 설정(기존 연결 기록 존재, 실제 Secret 값 미조회)
- `PLAY_UPLOAD_KEYSTORE_BASE64` — Play에 등록된 **고정 업로드 키의** keystore Base64
- `PLAY_UPLOAD_STORE_PASSWORD`, `PLAY_UPLOAD_KEY_ALIAS`, `PLAY_UPLOAD_KEY_PASSWORD`
- Google 인증: `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` **또는** WIF 변수 사용

Actions Variables:
- `PLAY_UPLOAD_CERT_SHA256` — 업로드 인증서 SHA-256 (지문, private key 아님)
- `PLAY_CLOSED_TRACK` — 기존 비공개 테스트 트랙의 정확한 API ID
- WIF 사용 시 `PLAY_WORKLOAD_IDENTITY_PROVIDER`, `PLAY_SERVICE_ACCOUNT`

권한:
- Android Publisher API 활성화 및 Play Console의 서비스 계정에 **비공개 테스트 릴리스 권한** 부여.
- WIF를 사용하는 경우 GitHub에서 서비스 계정으로 인증할 수 있는 Workload Identity Provider 및 IAM 설정 확인.
- `play-closed` GitHub Environment에 필수 승인자(Required Reviewers) 설정 권장.
- Google Play 앱 서명 인증서의 SHA-1·SHA-256도 Firebase에 등록하고 실제 Play 설치본 OAuth 로그인 확인.

비밀키·암호·인증서 원문·서비스 계정 JSON은 커밋/문서/공개 로그에 넣지 않습니다. Play에 이미 사용 중인 업로드 키를 새 임시 키로 대체하지 않습니다.

## 3. 검증 절차
1. `2-F Play closed release readiness (never publishes)` 워크플로를 `main`에서 실행합니다. `talksullae-2f-readiness` 보고서의 `config_status`, `blocking_configuration`을 확인합니다.
2. 설정이 있으면 동일 워크플로에서 **기존 앱/트랙 및 현재 소스 versionCode > Play 최대 버전코드**를 API로 점검합니다. 이것은 업로드가 아닙니다.
3. `Play closed-testing guarded publisher`의 `check`로 재확인합니다.
4. `build`는 **전체 Android/Firebase QA** 통과 후에만 기존 고정 키로 서명한 AAB를 만들고 인증서·Firebase 리소스·Bundletool·패키지/버전을 검증합니다.
5. `deploy`는 동일 조건을 충족해야 비공개 트랙만 API로 제출합니다. API `track_assigned`는 심사 승인이나 테스터 설치 완료가 아닙니다.
6. Play Console에서 심사·릴리스 상태와 테스터 링크를 확인합니다. 그다음 두 Google 계정/두 단말의 로그인·방 관리 E2E를 수행합니다.

## 안전 기준
- `2-F` 진단에서는 로그인 설정의 존재와 형식만 확인합니다. 실제 비밀번호/인증서/키 유효성 검증은 서명 AAB `build`에서 수행합니다.
- `2-F`의 unsigned AAB는 컴파일 전용이며 Play에 제출하거나 QA 통과 결과를 대신하지 않습니다.
- App Check·신고/차단·계정 삭제 기능, 카카오 공유와 4종 게임 등은 여전히 후속 구현 범위입니다.
