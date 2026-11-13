# GAME-04-C — 밸런스 예측 2~10인 멀티플레이

기준: Google Drive 전체 서비스 기획·GAME-00~04-B·SAFETY-01. Kotlin Compose / Firebase 공통 엔진.

## 규칙
- 활성 멤버 2~10명, 서버 질문 추첨, 각 개인이 A/B 비공개 선택 후 다른 실제 친구 1명의 선택을 비공개 예측.
- 선택·예측 제한 시간은 실시간 90초, 비동기 각 24시간, 서버 기준.
- 공개 단계 이전 다른 사람의 선택과 예측 원문 접근 차단. 제출 중복/점수 위조 거부.
- 서버는 친구 예측 적중 100점, 본인 선택 참여 20점을 단독 계산. 10초 결과 공개 후 최종 1회 정산.
- 개인 getMyGameSession은 본인 제출 여부와 본인 선택만 돌려줌, 결과 전 상대방 값은 노출하지 않음.

## 코드 및 검증
- balanceRules.js를 서버 Rule registry에 명시 등록, 기존 sessionService에서 playing/voting phase별 비공개 제출을 읽어 정산.
- BalanceGameViewModel/OnlineBalanceGameScreen, 기존 온라인 방 게임 진입점 및 자기 선택·친구 예측·결과.
- Node 유효성, Firebase Emulator 2인 실시간 완료·비동기 마감·외부인 차단, Android 3건 계측.
- 기존 Android QA에 3건 추가, API29/35/36 전화/API36 태블릿에서 최소 26건 계측.
- SAFETY-01/게임04-B가 main에 안전 병합된 후 새 HEAD 전체 CI·Codex 통과 시에만 본 PR 병합.

## 운영 제한
Firebase 운영 Functions/Rules/Indexes/Scheduler 배포, Play AAB 생성·업로드·트랙 변경 및 스토어 수정 없음.
