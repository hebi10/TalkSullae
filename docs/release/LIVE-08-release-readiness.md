# LIVE-08 — 통합 QA 및 출시 후보 최종 게이트

## 코드 변경 범위

- Firebase Emulator 두 계정 통합 사용자 여정: 개인 닉네임 → 친구방 생성 → 초대 확인 → 가입 승인 → 활성 멤버 채팅 → 동일 요청 멱등성 → 외부인 채팅/세션 거절 → 신고·운영자 전용 검토 → 차단 → 방별 개인 전적 → 게임 세션 → 계정 삭제 전제조건.
- 기존 GAME-06: 4명이 동일한 방에서 정체 추리·라이어·금지어·밸런스 예측을 순서대로 진행하고, 중복 정산·익명/비밀 역할 분리·시즌 원장을 검증합니다. 새 코드에서도 유지합니다.
- Android 기존 CI: API29/35/36 휴대전화·API36 태블릿, 다크/라이트·글꼴 100/200% 캡처 총 48장, 앱 계측/접근성 회귀. 실제 단말 테스트나 Play 서명 OAuth는 아닙니다.
- read-only LIVE-08 source gate: 버전코드 6·패키지·서버 Callable·Firestore 보안·개인정보/알림 고지·멀티계정 Emulator 회귀·OPS-04 수동 승인 정책을 차단형으로 검사하며, 소스 통과와 운영 승인 상태를 별도 표시합니다.
- Android 최신 릴리스 소스 후보 0.5.0 / versionCode 6. 마지막으로 Google Play API 게시 성공이 확인된 버전은 0.4.1 / versionCode 5이며 신규 AAB는 미업로드입니다.

## 운영 Firebase 검증 (자동 QA 완료와 별개)

1. 운영 Firebase Authentication OAuth/Play 앱 서명 SHA, Functions 서울 리전 리비전, Firestore Rules/Indexes 및 Scheduler 현재 배포 상태 확인. 기존 0.4.1 설치본과 신규 서비스 계약의 호환성을 체크하고 단계별 서버→클라이언트 적용·롤백 수립.
2. 실제 Google 계정 2~4개를 사용하는 휴대전화로 방장/멤버 권한, 초대 만료·가입 승인·강퇴·알림, 채팅 멱등 전송, 게임 4종 연속 4~10명 플레이, 재접속·기기 회전·백그라운드 FCM, 삭제 작업/운영 신고를 E2E 수행.
3. 360/393/412dp 휴대전화, 600dp 이상 태블릿/폴더블, 다크/라이트, 글꼴 200%, TalkBack 한국어·키보드/IME 및 실제 Play 설치본 화면을 별도 점검.
4. Privacy/UGC: Google Play 데이터 보안·콘텐츠 등급·연령 대상, 신고/차단 운영 처리 기한, 계정 삭제 웹 URL, 장기 보관 메시지 만료 정책과 운영자 책임자 확인. 해당 Play Console 항목을 코드 구현만으로 통과 표시하지 않음.
5. HTTPS Android App Links에 필요한 검증된 소유 도메인·assetlinks.json 및 미설치 사용자의 링크 복귀는 아직 구현되지 않았으며, 현재 앱 전용 스킴+초대 코드 수동 입력을 유지.

## Play internal → closed 최종 순서

1. LIVE-07 최신 PR #57 및 LIVE-08 PR의 Android/Firebase/OPS/Code Review 통과 후 안전 병합.
2. 최신 main에서 전체 CI와 변경 파일 정합성 확인. 운영 Firebase 호환성 검증 및 인가된 서버 적용 확인.
3. OPS-03 서명 AAB 빌드(승인된 고정 업로드키, SHA-256 검증) → OPS-04 정확한 main SHA·아티팩트·versionCode 6을 승인해 `play-internal` 환경에서 게시 → Play API commit/readback 확인.
4. Google Play Console 심사·테스터 실제 설치 및 Google 로그인 확인, 실제 다중 기기 기능 검증. 기존 내부 코드5보다 높은 코드6이 다른 트랙에도 사용되지 않았는지 API로 확인.
5. Play 비공개 테스트 트랙의 실제 ID, 사용자 그룹, 정책 등록정보/앱 삭제 URL 및 심사 상태를 조회한 뒤 별도 승인으로 승격. 과거 0.3.1(3) 고정 승격 워크플로를 0.5.0(6)에 그대로 재사용하지 않음.

이 PR은 코드/자동 QA/문서만 변경합니다. LIVE-08의 외부 검증 항목은 모두 *미확인*으로 남기며, Firebase 운영 배포·Play AAB 업로드·비공개 테스트 승격을 수행하지 않습니다.
