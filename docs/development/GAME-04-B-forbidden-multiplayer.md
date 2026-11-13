# GAME-04-B — 금지어 채팅 멀티플레이

기준: Google Drive 톡술래 서비스 기획 v1.0, GAME-00~04-A, SAFETY-01(서버 삭제 요청 보호 포함).
대상: Kotlin Compose/Firestore/Cloud Functions, no production deployment.

## 실제 게임
- 온라인 방 3~10명, 방장 시작, 비밀 금지어는 서버 난수로 중복 없이 배정.
- 각자 본인의 금지어는 알 수 없고, 서버 검증 후 다른 참가자의 금지어만 반환한다.
- 상대의 금지어를 말하도록 유도하는 채팅 180초. 서버에서 NFC/NFKC 정규화·공백/문장부호 제거 후 문구 포함 여부 판정.
- 금지어가 감지되면 해당 플레이어 즉시 탈락, 공개 세션에는 탈락 UID만 등록. 이후 채팅 전송 차단.
- 결과 10초 공개, 서버 점수 생존자 100점/탈락자 0점, 결과 문서는 1회 정산. 시즌 점수는 GAME-05에서 별도 표준화.
- 새 게임과 기존 게임의 활성 세션을 1개로 제한하고 다른 게임 화면 진입 시 서버 상태 재확인.

## 데이터·보안
- privateAssignments/{uid}.data.forbiddenWord: 클라이언트 직접 접근 금지.
- getMyGameSession: 자기 UID의 금지어는 반환하지 않고 다른 참가자의 (UID, 금지어)만 서버에서 안전하게 투영.
- publicMessages: senderUid, text, triggered, createdAt, expiresAt. 작성자·임의 탈락 값 위조 거부. 원본 금지어 값 자체는 메시지에 저장하지 않음.
- 서버에서만 게임 세션의 eliminatedUids를 트랜잭션으로 갱신. 직접 Firestore 쓰기 금지. 30건 메시지 조회 제한.
- 500자, 750ms 전송 간격, 동일 UUID 요청 멱등성, 마감 이후 전송 차단. SAFETY-01 계정 삭제 중 사용자 게임 입력 거부.

## 검증
- Node: 3/4/7/10명 중복 없는 금지어, 자기 역할 노출 차단, 정규화 감지, 생존 점수·무결성.
- Firebase Emulator: 3인 실제 게임·탈락/재전송·마감·서버 1회 정산, 10명 최대, 11번째 차단, outsider 차단.
- Rules Emulator: privateAssignments/하위 데이터 접근 금지, active 참가자만 메시지 읽기, 30건 제한, 강퇴/종료 방 권한 회수.
- Android: 로비, 친구 금지어 목록, 금지어 비공개, 메시지 전송/탈락, 다른 게임 화면 차단. 기존 20개+신규 3개=최소 23개 계측; API29/35/36 phone, API36 tablet.
- 모든 최신 HEAD CI/Codex 리뷰 성공 시에만 단계별 안전 병합.

운영 Firebase Functions/Rules/Indexes/Scheduler 배포 금지, AAB/Play 업로드 금지, 스토어 변경 금지.
