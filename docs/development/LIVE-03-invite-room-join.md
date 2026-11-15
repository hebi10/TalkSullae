# LIVE-03 — 실제 방·카카오톡 공유·가입 승인·멤버 관리 연계

기준: LIVE-01 PR #50 → LIVE-02 PR #52 → LIVE-03. Google Drive 톡술래 001 전체서비스기획서 v1.0 및 전체화면 디자인설계의 S03~S05/S15 기준.

## 구현 범위

1. **실제 온라인 방 생성**: 기존 \`createRoom\` Callable의 서버 확인이 완료된 후에만 입력 폼을 닫고, 해당 방의 멤버 인덱스가 Firestore에서 확인되면 채팅으로 이동합니다. 실패 시 작성 중인 내용을 유지하고 오류 메시지를 보여줍니다.
2. **초대 발급·공유**: 방장만 \`createInvitation\` Callable을 호출하여 24시간 유효한 코드를 받습니다. Android Sharesheet에서 카카오톡 등 설치된 앱으로 링크·코드를 공유할 수 있습니다. 발급 코드는 방장 화면에서만 표시되며 새 발급 시 이전 코드가 무효화됩니다.
3. **초대 링크 수신**: \`talksullae://invite?code=<code>\` custom scheme을 Android VIEW/BROWSABLE로 처리합니다. 설치된 앱에서 링크를 열면 코드 형식을 검증하고, 로그인/프로필 설정이 필요하면 완료 후 초대 미리보기로 연결합니다.
4. **가입 확인**: 새 인증 전용 \`previewInvitation\` Callable은 유효한 미만료 초대의 방 이름·소개·인원·가입 승인 필요 여부만 제공합니다. 가입 승인 전 메시지·멤버 목록·게임 기록·초대 토큰 해시는 제공하지 않습니다. 수락 후 기존 \`requestJoin\` 호출에 따라 가입 완료 또는 방장 승인 대기 상태를 표시합니다.
5. **멤버 관리**: 기존 \`changeMember\`의 방장 승인/거절/강퇴/방장 위임과 \`leaveRoom\`의 서버 권한 검사 및 확인 다이얼로그 유지. 승인 전 방 내용은 기존 Firestore 규칙으로 차단합니다.

## 인터넷 링크·앱 미설치 처리 제약

- 앱 전용 링크는 **설치된 Android 앱에서만** 열릴 수 있는 custom scheme입니다. 링크는 HTTPS Verified Android App Link가 아니며 Chrome/미설치 사용자에게 자동 Play 설치/이후 라우팅을 제공하지 않습니다.
- 미설치 사용자에게는 공유 문자에 포함된 **초대 코드를 보존하고 앱 설치 뒤 다시 열거나 수동 입력**하도록 안내합니다. 실제 운영 도메인·Hosting·assetlinks.json과 Play 서명 인증서를 확정한 뒤 검증된 HTTPS App Links로 승격해야 합니다. 검증되지 않은 웹 호스트는 사용하지 않습니다.
- 초대 코드(32바이트 난수)는 클라이언트 메모리로만 취급하고 Android saved instance state·로그·계정 프로필에 저장하지 않습니다. UI에서 확인 후 Activity Intent data를 소거합니다. 링크 전달 중 다른 앱/사용자의 열람은 공유 자체의 위험이므로 링크 공개 범위를 안내합니다.

## QA

- 로컬: 링크 생성/파싱·형식 오류·허위 도메인/중복 인수/위조 차단.
- Firebase Emulator: 유효한 초대 정보 미리보기, 타 사용자/비인증 차단, 재발급 이전 코드 거부, 승인 대기 처리, 삭제 진행 중 조회 차단.
- Android: 링크 수신 후 초대 확인 시트, 가입 요청 동작, 실제 생성 성공 후 이동, 방장 메뉴 접근성.
- E2E 미완료: 실제 Google 계정 2~4개, 카카오톡 앱 공유/재설치/다른 기기 초대, Google OAuth·운영 Functions·App Links HTTPS 도메인 검증.

## 배포 금지·병합 순서

- LIVE-01 → LIVE-02 → LIVE-03 순서대로 최신 main에 정합화한 후 **각 PR 최신 HEAD의 필수 Android/Firebase/OPS CI 및 Codex P1/P2 0건**이 확인될 때만 병합합니다.
- Firebase 운영 Functions/Rules/Indexes/Scheduler 배포, Play internal/closed 승격, AAB 업로드, 스토어 정책·이미지 변경 없음.
