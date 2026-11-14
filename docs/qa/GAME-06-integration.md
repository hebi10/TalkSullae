# GAME-06 — 4종 게임·기록·보안 통합 QA

기준: `main` 2626871c9a40c68a2ed67e5af04c1cbdf8aa03f5 (0.4.0 / versionCode 4).
범위: Firebase Emulator와 기존 Android 검증의 실제 증빙을 사용하며 운영 Firebase 및 Google Play에는 변경을 가하지 않습니다.

## 자동 검증

1. `functions/test/game06IntegrationEmulator.test.js`:
   - 하나의 유지되는 방에서 identity → liar → forbidden → balance(async)를 차례로 생성·4명 준비·시작·서버 타임아웃 마감·종료
   - 이전 게임이 활성 상태인 경우 새 게임 생성 거부, 다른 gameId로 현재 세션 조작 거부, 외부 사용자 세션/복구 접근 거부
   - 개인 역할 투영과 공개 세션 분리, 라이어 주제·본인 금지어 미노출
   - 각 게임의 최종 결과·정산 원장·방 이벤트 1회 생성, 멤버 4명의 게임별 전적 및 동일 월 시즌 기록 누적
   - 종료 후 활성 세션 포인터 해제·방 멤버 보존, 완료된 세션 재정산 거부
   - 중간 취소는 정산 없이 방을 해제하고 다음 게임을 허용
2. 기존 Firestore Emulator 보안/기능 테스트: 각 게임별 주제·투표·금지어·예측·재접속·권한·안전 신고·계정 삭제 검증
3. 기존 Android CI: API 29·35·36 휴대전화, API 36 태블릿에서 Compose UI/IME/캡처와 테스트 APK 빌드. `qa-2d.yml` 검증 게이트 보존.
4. OPS-07/OPS-08: Play 자동 게시 차단과 정확한 SHA 승인 게이트 보존.

## 결과 판정 및 잔여 작업

- 새 PR의 해당 HEAD에서 Firebase Emulator·Android 기기 매트릭스·릴리스 안전 검사 전부 success일 때만 **자동화 통합 QA 통과**로 판정합니다. PR 생성만으로 통과 처리하지 않습니다.
- 운영 Firebase Functions·Rules·Indexes·Scheduler는 GAME-03~05 코드와의 일치 여부 미확인. 실서버에 배포하지 않습니다.
- 실제 Google 계정 4~10명, 서로 다른 실제 단말, 네트워크 단절·재접속, FCM 및 카카오톡 초대 App Links는 현장 E2E가 필요합니다.
- TalkBack/폴더블 개폐·200% 글꼴/IME 검사는 에뮬레이터 시각 검사를 대체할 실기기 수동 QA가 추가로 필요합니다.
- 실제 Cloud Scheduler 지연·복구·비용과 신고/삭제 처리 운영 감사, 대량 동시 접속·읽기 과금 부하는 별도 운영 QA 대상으로 둡니다.
- Google Play `qa` 트랙 미초기화 및 비공개 승격 WIF 인증 문제는 릴리스 단계 차단 사항으로 분리합니다.

## 기존 근거

- GAME-05 병합 PR: https://github.com/hebi10/TalkSullae/pull/44
- 0.4.0 릴리스 준비 병합 PR: https://github.com/hebi10/TalkSullae/pull/45
- 0.4.0 PR Android/Firebase/4환경 QA: https://github.com/hebi10/TalkSullae/actions/runs/43550061835
- main Android 검증: https://github.com/hebi10/TalkSullae/actions/runs/43551411388
- Google Drive 구현·검증 기록: https://docs.google.com/document/d/1c4Dw31nyPYFvqZQA9Do8fqfe2lwxR0fe80P97RFNPzA/edit

## 배포 경계

이 QA는 Play internal/closed/production, 등록정보 변경, 운영 Firebase 배포를 요청하거나 수행하지 않습니다. 기존 AGENTS.md와 OPS-01~08 수동 승인 정책을 유지합니다.
