# GAME-05 — 전적·월간 시즌·랭킹·업적·중복 없는 정산

기준: Google Drive 톡술래 서비스 기획 v1.0, GAME-00/02/03/04-A/B/C, SAFETY-01 개인정보 요구. 기존 Kotlin Compose/Firestore/Functions, OPS-08 배포 동결 보존.

## 경기 정산
- 클라이언트에서 승패·점수·시즌 점수를 직접 제출하지 않는다. 게임별 서버 규칙의 scoresByUid만 사용한다.
- GAME-02 gameSessionAction의 finished 단계 트랜잭션에서 results/final과 기록 원장 recordSettlements/{sessionId}를 동시에 create한다.
- 개인 playerStats/{uid}: 경기·승리·원점수·시즌점·연승/최고 연승·게임별 전적·누적 업적.
- playerStats/{uid}/games/{sessionId}: 개인 최근 경기의 게임 종류·정산점·승패·시즌 ID.
- seasons/{yyyy-MM}/scores/{uid}: 월간 시즌 누적 점수, 경기·승수·최근 경기.
- seasons/{yyyy-MM}: 생성된 시즌의 총 경기 수. recordEvents/{sessionId}: 비식별 게임 이벤트와 참가 인원.
- 아시아/서울 기준 월간 시즌 ID. 과거 월의 자료는 변경하지 않고 보관하며 이전 시즌은 읽기 시 종료로 분류한다.

## 공통 시즌 점수
참여 점수 5 + 100점 만점 게임별 원점수 정규화 + 승리 보너스 10. 최대 115점.
게임별 원점수 최대: identity는 참가 수·위장 보너스에 따라, liar 120, forbidden 100, balance 120.
원점수는 results/final에 그대로 보존하며 시즌 누적 계산에 원점수를 직접 더하지 않는다.
라이어·금지어는 팀 승리 점수 >=100, 밸런스 예측은 적중 >=100, 너 누구야?는 최고 득점자를 승자로 정한다.
업적: 첫 게임·10경기·5승·3연승·5연승·라이어 5승·정체 추리 10승. 동일 경기의 재요청/자동마감 중복 점수 지급 거부.

## 화면·보안
- 온라인 방에서 기록 화면 진입, 개인 누적 기록·획득 업적, 현재/과거 최대 24개월 시즌 랭킹 상위 20명.
- 최근 내 경기 30건과 방 연대기 20건 조회. 다른 참가자의 개인 playerStats 및 개인 경기 목록은 직접 접근 거부.
- Firestore Rules는 active 멤버만 방별 시즌 점수·이벤트 읽기 가능. 기록/원장/점수의 모든 Client 쓰기는 거부.
- 계정 삭제: 본인의 playerStats/경기 기록 및 월간 순위 항목 제거, 공동 방의 다른 구성원 기록은 보존. 신고 및 익명 메시지 처리에는 SAFETY-01 기준 유지.

## 자동 검증
- 단위: KST 월 경계, normalize 범위, 승자·업적 계산.
- Firebase Emulator: 4인 identity 실제 게임·자동 1회 정산·누적 연승, 재전송시 double-credit 방지.
- Rules Emulator: 본인 기록만 직접 읽기, active 방 멤버 월간 순위 조회 가능, 남의 개인 전적·원장·직접 쓰기 금지.
- Android: 기록 화면·랭킹/업적·시즌 이동 2개 계측 추가; API29/35/36 휴대전화·API36 태블릿 최소 28건 계측.
- 선행 SAFETY-01/게임04-B/04-C 병합 및 최신 HEAD 필수 CI/Codex 리뷰 성공 전 main 병합 금지.

## 미포함
- 실 Google 계정 4~10명 실기기 E2E, 운영 Firebase Functions/Rules/Indexes/Scheduler 배포, Google Play/AAB/스토어 변경은 수행하지 않는다.
- 기존 과거 GAME-03/04-A 결과 이관, 과도한 시즌 기록 정리, 불법 콘텐츠 신고 운영·삭제 정책 고도화는 별도 운영 승인 검증 대상.
