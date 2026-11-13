# GAME-04-A — 라이어 채팅 실제 멀티플레이

기준: Google Drive 전체 서비스 기획 v1.0, GAME-00 공통 아키텍처, GAME-02 세션 엔진, GAME-03 익명 추리 구현.
대상: Android / Kotlin Compose, Firebase Authentication / Firestore / Functions. Play 및 운영 Firebase 배포 금지.

## 동작 규칙
- 참가: 온라인 방의 활성 회원 4~10명. 모든 참가자 준비 후 방장만 시작.
- 서버가 암호학적 난수로 라이어 1명과 단일 비밀 주제를 배정. 시민에게만 주제, 라이어에게는 역할만 제공.
- 채팅 180초: 메시지는 인증 UID로만 작성, 최대 500자, 750ms 속도 제한, 동일 UUID 멱등 전송.
- 투표 60초: 본인 제외 대상에게 1표, 서버가 타임아웃 또는 전체 제출 후 단계 진행. 미제출은 기권.
- 최고 득표 동점: 동점 후보만 재투표 45초 한 번. 재동점 시 라이어 생존.
- 단일 최고 득표: 라이어가 아니라면 라이어 승. 라이어라면 비밀 주제 추리 45초. 정답이면 라이어 승, 오답·미제출이면 시민 승.
- 결과 공개 10초 후 FINISHED; 서버 results/final 단일 문서로 멱등 정산.
- 점수(시즌 포인트와 별개): 승리 팀 100점, 시민의 정확한 최종 표 +20점. 시즌 점수 반영은 GAME-05.

## Firestore 데이터 보호
- rooms/{roomId}/gameSessions/{sessionId}: 공개 phase/revision/round/deadline/participant/notice/options, 주제·역할·표 원문 없음.
- privateAssignments/{uid}: 시민의 역할+비밀 주제, 라이어의 역할만. 방장도 클라이언트 SDK로 직접 읽기 거부.
- privateSubmissions/{hash}: 각 voting round별 vote 또는 라이어 주제 답, 전체 Firebase Client 읽기·쓰기 거부.
- processedActions/{hash}: 서버 멱등성, 클라이언트 접근 금지.
- publicMessages/{hash}: senderUid/text/createdAt. 라이어 대화는 실명 기반, 비밀 역할/주제 없음. 참가자만 읽기 가능, 30건 조회 상한.
- gameChatRateLimits/{uid}: 서버 전용.
- results/final: finished 이후 활성 방 멤버만 읽기, 직접 수정 불가.

## 공통 엔진 계약
- 기존 createGameSession/gameSessionAction/getMyGameSession/findActiveGameSession 재사용.
- 규칙 liar@1을 감사된 서버 레지스트리에 명시적으로 등록. forbidden/balance는 시작 거부 유지.
- 기존 상태 머신의 voting -> voting, round 증가를 재투표/주제 추리에 재사용.
- 각 voting round의 privateSubmissions를 서버에서만 다시 읽어 결과·점수 계산. 공개 결과 작성 전 정답·역할 공개 금지.
- 기존 서버 deadline sweep(매분)과 transaction 중복 정산 방지 유지. 결과 갱신은 스케줄러 실행 간격 때문에 초 단위 정확도 보장 없음.
- Android는 기존 온라인 방에 전용 진입점을 추가, LiarGameViewModel의 세션·재접속·요청 ID 재시도 패턴과 Firestore 리스너 재사용.
- 기존 너 누구야? 게임 화면에서 다른 gameId를 렌더링하지 않도록 확인.

## 검증
1. Node: 4/5/7/10명 라이어 단일 배정, 주제 비노출, 자기 투표 불가, 재투표 1회, 주제 추리, 팀/개인 점수, 무결성 실패.
2. Firebase Emulator: 실제 4인 세션·채팅·투표·정체 추리·최종 점수 1회, 10명 상한, 속도 제한, 지각 채팅·위조·권한 거부.
3. Rules Emulator: 비회원/대기·강퇴·종료 방 차단, 참가자 30건 읽기, Private role/vote/rate 직접 접근 금지.
4. Android: 준비, 채팅·역할 비공개, 투표, 라이어 주제 추리, 기존 게임간 분리; API29/35/36 휴대전화 및 API36 태블릿 QA.
5. 최신 HEAD 모든 CI, Codex 최신 리뷰, base SHA/mergeable 확인 전 PR 병합 금지.
6. 실 Google 계정 4~10개·실기기·Cloud Scheduler 실제 지연·비용, 운영 Firebase Rules/Functions 적용은 후속 승인 테스트. 완료로 주장하지 않음.

## 릴리스 금지
- AGENTS.md 및 OPS-08에 따라 PR/CI만 수행한다.
- Firebase 운영 Functions/Rules/Indexes, Cloud Scheduler 신규 배포, AAB 생성, Google Play internal/closed 트랙 업로드·승격 없음.
- Play 자동 배포 워크플로 및 서명 Secrets는 변경하지 않는다.
