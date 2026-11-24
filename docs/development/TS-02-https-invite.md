# TS-02 HTTPS 초대 및 설치 안내

## 소스 구현
- 예상 호스트: 기존 Firebase 프로젝트 talksullae의 talksullae.web.app (Hosting 사이트 소유 및 현재 제공 상태는 미확인).
- Android App Links 필터와 정확한 호스트/경로/fragment 검증, 기존 custom scheme 지원. 토큰은 URL query 대신 fragment로 전달해 웹 요청 및 Referrer에 노출하지 않는다.
- 설치 안내 웹페이지: 유효한 코드만 표시, 복사, 기존 앱 열기, Play 설치, 설치 후 수동 코드 입력 안내. 자동 설치 후 복구를 보장하지 않는다.
- 기본 공유 링크는 기존 custom scheme. 실제 HTTPS 공유는 검증 완료 전 비활성(`talksullaeVerifiedInviteLinks=false`).

## 운영 활성화 조건 (미수행)
1. Firebase Hosting 콘솔에서 talksullae.web.app 소유·대상 사이트·기존 콘텐츠 및 도메인 확인. 기존 사이트를 덮어쓰지 않는다.
2. Play Console 앱 서명 인증서 SHA-256 (업로드 인증서와 구분)을 직접 확인하고 해당 공개 지문으로 `scripts/release/prepare_invite_host.py --play-app-signing-sha256 <확인된 지문>` 실행. 새 로컬 출력만 생성한다.
3. 별도 승인 후 전용 `firebase.invite-hosting.json` 대상으로 Hosting 적용. 기존 Firebase Functions/Rules/Play와 분리.
4. 정확한 HTTPS 호스트의 /.well-known/assetlinks.json 과 안내 페이지 HTTP 응답·내용 확인, Play 서명 설치 앱의 verified link 상태 검증. 인증서 및 호스트가 불일치하면 활성화 금지.
5. release build의 `-PtalksullaeVerifiedInviteLinks=true`는 1~4의 증빙 확보 후에만 적용. 기존 배포 승인 조건을 유지하고 실기기에서 KakaoTalk/WebView, 설치/미설치, 코드 복사, 만료/폐기/변조 검증.
6. 본 PR은 소스 및 로컬 준비 작업만 포함. 운영 Hosting/Firebase/Play 변경 없음.
