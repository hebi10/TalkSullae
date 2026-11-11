# 2-H Google Play 내부 테스트 전용 배포

## 기본 원칙
- 배포 대상: **내부 테스트만**, Google Play Android Publisher API 트랙 \`qa\`.
- 소스 패키지: \`com.hebi.talksullae\` · 현재 릴리스 버전 \`0.3.0\` / 코드 \`2\`.
- 비공개 \`alpha\`, 공개 \`beta\`, \`production\` 및 기기별 테스트 트랙은 내부 배포 스크립트에서 **거부**합니다.
- 기존 \`.github/workflows/play-closed.yml\` 및 비공개 트랙 설정은 변경하지 않습니다.
- 서명 및 Play API 인증을 갖추지 못했다면 **AAB를 업로드하지 않고**, 무엇이 부족한지 안전한 보고서로 남깁니다.
- Play API의 \`track_assigned\` 확인은 Play Console 게시·테스터 설치가 승인됐다는 의미가 아닙니다.

## 실행 방법
- 코드를 \`main\`에 병합하면 GitHub Actions의 \`Play internal-testing guarded publisher\`가 자동으로 보호 설정의 **존재만** 읽고 \`talksullae-play-internal-readiness\` 아티팩트를 만듭니다. 이 동작은 **업로드하지 않습니다**.
- 해당 Actions 워크플로의 \`Run workflow\`에서 \`mode=check\` 선택: 앱 \`com.hebi.talksullae\`의 Android Publisher API 접근 및 \`qa\` 트랙 버전코드를 조회합니다. **업로드 없음**.
- \`mode=build\`: Android/Firebase 전체 QA 통과 후 기존 고정 업로드키로 Release AAB를 생성·서명·무결성 확인해 비공개 아티팩트로 보관합니다. **Play 업로드 없음**.
- \`mode=deploy\`: 전체 QA, Play API preflight, 서명·AAB provenance가 모두 성공한 경우에만 트랙 \`qa\`에 업로드 → commit → readback을 실행합니다. \`play-internal\` GitHub Environment 승인자 설정 권장.
- 명시적인 \`[deploy-internal]\` 커밋과 변경 대상 파일이 맞는 \`main\` push만 수동 배포 요청과 동등하게 인식합니다. 무관한 커밋/다른 브랜치에서는 절대 자동 배포하지 않습니다.

## Secrets 및 Variables
- Firebase Android 구성: \`FIREBASE_ANDROID_GOOGLE_SERVICES_JSON\` (기존 연결 이력 존재).
- 고정 Play 업로드키: \`PLAY_UPLOAD_KEYSTORE_BASE64\`, \`PLAY_UPLOAD_STORE_PASSWORD\`, \`PLAY_UPLOAD_KEY_ALIAS\`, \`PLAY_UPLOAD_KEY_PASSWORD\`.
- 키 지문: Variables \`PLAY_UPLOAD_CERT_SHA256\`.
- Google Play API: WIF Variables \`PLAY_WORKLOAD_IDENTITY_PROVIDER\` + \`PLAY_SERVICE_ACCOUNT\`, 또는 \`GOOGLE_PLAY_SERVICE_ACCOUNT_JSON\` Secret. Play Console에서 서비스 계정에 앱의 테스트 릴리스 권한이 필요합니다.
- **\`PLAY_CLOSED_TRACK\`은 내부 테스트에 사용하지 않습니다.** 내부 트랙은 Google 공식 API의 \`qa\` 고정 값입니다.
- 모든 비밀값은 Secrets에 등록하고 공개 GitHub/문서/로그에 표시하지 않습니다. GalliPick 등 다른 앱의 업로드키를 승인 없이 복사하지 않습니다.

## 현재 검증 상태 및 남은 작업
- 현재 \`main\`의 마지막 2-F 설정 보고는 **blocked**였습니다. 사용자 계정에서 최근 Secrets 추가 여부는 이 워크플로의 보호 설정 진단이 실행되어야 확인할 수 있습니다.
- Play Console 앱 생성 및 이전 Alpha 초안 기록은 있으나, 초기 업로드키와 \`qa\` 트랙 접근 권한·최초 AAB 등록 성공 여부는 직접 조회되지 않았습니다.
- \`versionCode=2\`는 모든 기존 트랙 및 업로드된 AAB의 최고 코드보다 커야 합니다. 이미 업로드한 버전코드라면 릴리스 버전을 올리고 QA를 다시 실행해야 합니다.
- \`feat/play-privacy-policy\` PR #16은 미병합이고 2-D 태블릿 QA 실패 상태로 확인됐으므로, QA 통과 전 릴리스에 포함하지 않습니다.
- 내부 테스트는 완전한 스토어 설정 이전에 시작할 수 있으며 최대 100명의 테스터에게 배포 가능합니다. 다만 첫 배포가 승인되지 않았거나 테스트 링크가 활성화되지 않으면 실제 설치 성공으로 표시하지 않습니다.

공식 자료:
https://developers.google.com/android-publisher/tracks
https://support.google.com/googleplay/android-developer/answer/9845334
