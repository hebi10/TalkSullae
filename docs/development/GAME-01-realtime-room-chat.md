# GAME-01 — Firebase 실시간 일반 채팅 구현

상태: **코드 및 테스트 PR 검증 대기** (Firebase 운영 배포/Play 재출시 미실시)
기준: GAME-00 PR #27의 HEAD `629675e3` (GAME-01은 GAME-00 기반 stacked PR)
Android 앱 패키지: `com.hebi.talksullae`
대상: Firebase로 인증되고 **활성 멤버로 승인된 온라인 방**. 기존 로컬 데모 채팅은 변경하지 않습니다.

## 1. 구현 범위

- 온라인 방 관리 화면의 **일반 채팅 열기** 버튼으로 실시간 채팅 진입.
- Firestore `rooms/{roomId}/messages/{messageId}`의 최근 30건을 최신 순 구독하고, UI는 오래된 메시지부터 보이도록 구성.
- 기록은 최근 30건 이후에 필요할 때 `startAfter(DocumentSnapshot)` 커서 기반으로 최대 30건씩 불러옴. LazyColumn 사용.
- Android 앱은 서버의 Callable `sendRoomMessage`만 호출해 새 메시지를 발송. Firestore 직접 쓰기는 보안 규칙으로 전면 금지.
- 전송 성공 시 입력창을 비우고, 응답이 실패/불명확하면 **동일 UUID v4**로 재시도하여 중복 저장을 방지.
- 다른 텍스트로 다시 보내려면 사용자가 기존 미확인 요청을 명시적으로 포기하고 새 요청 ID를 발급받도록 함(중복 가능성 안내).
- 서버는 방/멤버 활성 상태, 입력 1~1000자, 호출 사용자 인증, 허용 입력 필드, 재전송 동일성, 750ms 메시지 간격을 검증.
- 메타데이터는 서버가 저장: 실제 `senderUid`·방 멤버 프로필 `senderName`·`FieldValue.serverTimestamp()`. 클라이언트가 이름·작성 시각·보안 권한을 지정할 수 없음.
- 방에서 강퇴·탈퇴할 경우 기존 방 목록 권한 확인에 따라 채팅 화면을 닫고, Firestore Rules 또한 실시간 열람을 거부함.

## 2. 정확한 데이터 구조

```text
rooms/{roomId}
  members/{uid}                          # 이미 존재, 서버 권한의 기준
  messages/{sha256([roomId, uid, UUID])}
    senderUid: uid
    senderName: 방 멤버 이름
    text: 1~1000자 본문
    createdAt: serverTimestamp
  chatRateLimits/{uid}
    lastSentAt: Timestamp
```

- 일반 채팅 경로는 GAME-00에서 계획한 **게임별 익명 `gameSessions/{sessionId}/publicMessages`와 완전히 별개**.
- `chatRateLimits` 클라이언트 조회·쓰기 모두 금지. 오직 Cloud Functions Admin SDK에서 트랜잭션으로 갱신.
- UUID v4는 요청 식별자일 뿐 인증 정보가 아님. `request.auth.uid`와 `members/{uid}.status == active`를 매 호출마다 재검증.
- 같은 요청 ID/같은 본문은 기존 메시지 ID만 반환(멱등), 같은 ID/다른 본문은 `already-exists` 오류.
- 요청 처리 중 경쟁 상태를 대비해 room/member/existing/rate를 트랜잭션에서 읽고, 새 message와 rate를 같은 트랜잭션에 기록.
- 메시지당 대략 4개 문서 조회 및 2개 문서 쓰기가 발생할 수 있으므로 사용량·핫스팟은 별도 측정 후 개선해야 함.

## 3. 읽기 권한·쿼리 제한

`firestore.rules`:
- `rooms/{roomId}/messages/{messageId}` 단일 get: 활성 멤버만.
- list/query: 활성 멤버이고 `request.query.limit <= 30` 조건을 충족해야 함.
- 클라이언트 `create/update/delete`: 모두 거부.
- 대기·거절·강퇴·탈퇴·비로그인 사용자, 다른 방 계정에 대한 읽기·쓰기 거부.
- `rooms/{roomId}/chatRateLimits/{uid}` 전면 차단.
- 기존 `members`, `invites`, `userRooms` 권한 규칙, 기타 하위 컬렉션 deny 유지.
- Firestore 캐시는 기기별 일시 보존될 수 있으므로 **서버 권한 변경의 오프라인 즉시 차단 보장**을 주장하지 않음. 재연결 시 권한 오류/상위 멤버십 변경을 통해 화면을 종료·목록 데이터를 지움.

## 4. 클라이언트 구조

- `firebase/FirestoreRoomChatRepository.kt`: 실시간 리스너, 최근 30건, 스냅샷 커서 페이징, Callable API, 입력 정책.
- `firebase/RoomChatViewModel.kt`: 최신/과거 메시지 병합·중복 ID 제거·오류 분리·전송 중 단일 요청 처리·동일 키 재시도.
- `ui/rooms/OnlineRoomChatScreen.kt`: 별도의 전체 채팅 화면, LazyColumn, 입력창, 전송/재시도/이전 메시지 버튼.
- `ui/rooms/OnlineRoomScreens.kt`: 승인된 온라인 방에서만 진입 버튼 노출. 기존 화면 구성·로컬 데모 경로는 유지.
- 중첩 스크롤을 피하도록 온라인 방 관리 화면의 `verticalScroll`과 채팅 메시지 `LazyColumn`을 서로 다른 화면으로 분리.

## 5. 오류·복구 정책

| 상황 | 동작 |
|---|---|
| 온라인 방/멤버 탈퇴·강퇴 | 라이브 메시지 권한 거부, 채팅 화면 종료 및 캐시 비우기 |
| 서버 전송은 성공했으나 응답 손실 | 같은 `requestId`로 재호출하면 중복 저장 없이 기존 메시지 ID 반환 |
| 송신 속도 제한 | 서버 `resource-exhausted`, 원본 입력과 ID 유지하여 재시도 가능 |
| 과거 내역 페이징 실패 | 이전 기록 오류만 표시, 새 메시지 입력은 계속 허용 |
| 실시간 메시지 구독 권한/연결 실패 | 메시지 목록 비우고 입력 중단; 방 목록으로 복귀 후 재접속 |
| 오프라인 메시지 캐시 표시 | 실제 최신 메시지가 아닐 수 있음을 안내 |
| 앱 프로세스 강제 종료 | 미완료 전송 작업/UUID를 로컬 영구 저장하지 않음(후속 개선 항목) |

## 6. CI와 테스트

- `functions/test/chatService.test.js`: UUID·데이터 범위·메타 필드 위조·중복 ID 생성 검사.
- `functions/test/chatServiceEmulator.test.js`: Firestore 트랜잭션 실사용으로 정상 송신·동일 ID 재시도·같은 ID 다른 내용·강퇴·가입 대기·속도 제한·동시 요청 검증.
- `functions/test/rules.test.js`: 활성 멤버 get, 30건 제한 쿼리, 초과/무제한 쿼리 거절, 비회원/강퇴·비로그인 거절, 클라이언트 직접 쓰기/속도제한 문서 거절.
- `app/src/test/java/.../RoomChatInputPolicyTest.kt`: 한글·줄바꿈/빈 본문/초과/제어문자.
- `app/src/androidTest/.../RoomChatComposeTest.kt`: Fake Repository를 통한 화면·발송 요청 확인. `qa-2d.yml`의 4환경 계측에 포함.
- GitHub Actions 전체 검증이 성공하기 전 **병합 금지**.

## 7. 출시 전 필수 체크

1. GAME-00 PR #27 병합 → GAME-01 stacked PR의 base를 main으로 변경하고 충돌/최신 HEAD CI 검증.
2. Firebase Emulator 규칙/트랜잭션/Android CI 전부 통과, 2개 실제 계정/휴대전화로 방 가입·메시지/강퇴·오프라인·재접속 E2E.
3. 승인된 Firebase 프로젝트에 **Firestore Rules와 Functions를 먼저 함께 배포**. 규칙은 읽기 제한을 풀기 때문에 검증 전에 운영 배포 금지.
4. 기존 Google Play 버전 `0.3.1 (3)`의 내부/비공개 테스트를 변경하지 않음. 새 Android AAB로 배포하려면 **사용하지 않은 더 높은 versionCode**와 기존 업로드키로 새 버전 제작.
5. 게임별 익명 채팅·판정·실시간 세션은 후속 GAME-02/03이며 GAME-01에 포함하지 않음.
6. 메시지 삭제/신고 연계·FCM 알림·읽지 않음·과거 메시지 자동 TTL/개인정보 보존 정책·지속형 오프라인 전송 큐·App Check는 후속 배포 차단/운영 과제로 분리.

## 8. 예상 후속 작업

- `GAME-01-FINAL`: 2기기 운영 Firebase E2E, 메시지 30건 페이징·과부하·차단/신고·오프라인 복원, 기능 릴리스 승인.
- `GAME-02`: 게임 세션 공통 엔진(서버 판정·타이머·비밀 역할·메시지/투표·1회 정산).
- `GAME-03`: 너 누구야? 실제 멀티플레이.
