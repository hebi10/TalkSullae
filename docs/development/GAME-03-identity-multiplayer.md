# GAME-03 — 너 누구야? 실제 멀티플레이 구현

상태: **서버·Android 실제 플레이 코드 및 테스트 작성 / CI·실기기 E2E 검증 대기**  
기준: 2026-11-12 / GAME-02 PR #30 HEAD `ce6fc75ced89f2cbeb81e14d524f7f8d3f6add1b`를 선행하는 stacked PR  
서비스 기획: https://docs.google.com/document/d/13BMHPicBwI1kN1XrPsjHYfR4wkXmBz4lUDzcPecptvg/edit

## 1. 범위와 구체적 규칙

**게임 식별자**: `identity@1`. 기존 라이어 채팅·금지어 채팅·밸런스 예측은 기존 상태대로 **게임 시작 차단**.  
**참가 인원**: 정확히 4~10명의 활성 온라인 방 멤버, 같은 방의 활성 게임 1개만 유지.  
**익명 배정**: 서버의 암호학적 난수 `crypto.randomInt` + Fisher-Yates 셔플, 총 10종의 고유 동물 별칭. 게임 시작 후 본인 별칭은 인증된 본인에게만 Callable로 반환하고, 전체 별칭→실제 UID 매핑은 서버 전용 Firestore 문서에 보관.
**공개 대화 주제**: 기획서의 친구 추리 주제 5종에서 서버 랜덤 선택.
**채팅**: 180초, 동물 별칭과 메시지 본문만 공개, UID·역할·사용자 계정 정보는 공개 메시지에 포함하지 않음. 게임 채팅은 일반 방 채팅과 완전히 별도 Firestore 경로로 저장.
**추리**: 90초, 사용자 본인 별칭/본인 UID를 제외하고 나머지 N-1개 별칭과 N-1명의 친구를 **중복 없이 일대일** 연결. 인원 부족·중복 대상·서버 마감 이후 제출 모두 거부.
**결과 공개**: 실제 친구 이름과 동물 별칭을 연결한 텍스트 및 원점수를 10초간 표시한 뒤 서버가 `finished` 확정.
**원점수**: 친구 별칭을 맞힌 건당 +100점; 다른 친구가 내 별칭을 틀리게 매칭하면 +10점, 이 위장 보너스는 라운드 최대 +50점. 서버만 계산하며 비참여·미제출 사용자는 정상 참여 점수만 추후 시즌 환산 대상입니다.
**최종 정산**: 게임 세션당 결과 문서 `results/final` 하나만 생성. 리더보드/시즌 포인트 실제 지급은 GAME-05로 분리.

## 2. 서버·데이터 경계

```text
rooms/{roomId}
  activeGameSessionId: <sha256 sessionId>

  gameSessions/{sessionId}
    gameId: "identity"
    phase: waiting|ready|playing|voting|reveal|finished|cancelled
    participants: { uid: {ready: boolean} }     # 공개 로비 참가자
    publicState:
      prompt: string
      options: string[]                         # 익명 별칭만
      publishedResult?: string                  # REVEAL/FINISHED 전엔 없음
    phaseEndsAt: Firestore Timestamp

    privateAssignments/{uid}
      data.alias: animal                          # Firestore Client SDK 읽기 거부
    privateSubmissions/{hash(uid,round,phase)}
      data.matches: {animal:otherUid}              # 제출한 본인 외 앱 직접 읽기 금지
    processedActions/{hash(uid,requestId)}
      fingerprint/result                           # 서버 전용
    publicMessages/{hash(roomId,sessionId,uid,requestId)}
      alias: string, text: string, createdAt        # **senderUid 없음**
    gameChatRateLimits/{uid}
      lastSentAt                                    # 서버 전용
    results/final
      scoresByUid, settlementId, createdAt         # FINISHED 뒤 읽기
```

보안 원칙:
- `functions/src/game/identityRules.js`: 익명 매핑·선택 유효성·서버 점수 계산·공개 결과 문구. `productionRules`에 `identity@1`만 등록합니다.
- `functions/src/game/sessionService.js`: GAME-02 공통 엔진을 확장해 `validateSubmission`에 **해당 인증 사용자의 실제 비밀 별칭**만 주입. 공개 후 판정 시에만 서버에서 모든 개인 별칭·비공개 투표를 함께 조회하고 점수를 확정합니다.
- `functions/src/game/identityChatService.js`: 인증 UID·활성 방/멤버·활성 세션/참가·비밀 별칭을 서버에서 다시 확인, 1~500자 입력, 클라이언트가 지정한 UID/alias/timestamp 거부, 동일 UUID 재시도 중복 저장 방지, 사용자당 750ms 최소 전송 간격.
- `firestore.rules`: `publicMessages` 최대 30개 읽기 쿼리/단일 get은 **활성 방의 해당 세션 실제 참가자**만 허용. 모든 앱 직접 쓰기 금지. 비밀 별칭·추리 원문·제한 기록은 방장을 포함해 누구도 직접 읽지 못함.
- 메시지 ID는 UID와 비공개 요청 UUID 등을 포함한 SHA-256 결과로 생성되지만, 생성 입력이나 실제 UID는 메시지 공개 문서에 저장되지 않습니다.
- 종료 후 `REVEAL`부터 실제 정체를 공개하고, `FINISHED` 후 서버 확정 점수 조회를 허용합니다.

## 3. 자동 마감 (서버 전용)

- Cloud Functions v2 `expireGameSessionDeadlines`는 **매분** 실행되는 Cloud Scheduler 기반 워커입니다. `collectionGroup("gameSessions")`에서 `phaseEndsAt <= 서버 기준 현재 시각`인 최대 40개를 검사합니다.
- 각 세션 전이 실행은 기존 `gameSessionAction`의 Firestore 트랜잭션/조건 검사를 재사용합니다. 관리자만 만든 합성 요청을 사용하며, 외부 클라이언트가 예약 워커의 권한으로 행동할 수 없습니다.
- 참가 멤버 중 누군가 게임 중 강퇴되거나 비활성화되면 마감 시점에 세션을 `cancelled`로 변경하고 방의 활성 세션 포인터를 정리합니다.
- 리비전 충돌과 이미 처리된 요청은 중복 점수 정산 없이 안전하게 건너뜁니다.
- `firestore.indexes.json`에 `gameSessions.phaseEndsAt` COLLECTION_GROUP 인덱스를 추가합니다.
- **중요**: 매분 크론 방식이므로 화면상 제한시간과 정확히 같은 초에 다음 단계가 표시된다고 보장하지 않습니다. 하지만 제한시간 경과 뒤 서버에서 신규 채팅·추리를 거절합니다. 운영에서 정확한 마감/과부하가 필요하면 Cloud Tasks 등으로 개선해야 합니다. Cloud Scheduler는 Firebase 배포 시 별도 리소스·비용이 발생하므로 실제 배포는 사용자 승인 뒤 수행합니다.
- 누적 지연 세션이 40개를 초과할 경우 후속 크론 실행에서 나눠 처리합니다. 동시 방 규모·비용은 GAME-06 부하 테스트에서 확인해야 합니다.

## 4. Android 실제 온라인 게임 화면

- 기존 `OnlineRoomScreens.kt`의 **활성 온라인 방**에만 '너 누구야? 온라인 게임' 버튼을 추가했습니다. 기존 로컬 데모 게임/채팅은 유지합니다.
- `OnlineIdentityGameScreen.kt`: 4~10명 로비 참가·준비, 방장 시작, 비밀 별칭 표시, 실시간 익명 메시지/최근30건·과거 페이지, 각 별칭별 친구 선택, 중복 없는 정체 추리 제출, 결과 공개, 게임 취소.
- `IdentityGameViewModel.kt`: 온라인 세션 ID 재발견·실시간 공개 상태 구독·자신의 비밀 역할만 복구, 네트워크 실패 시 동일 `requestId` 재시도, 실제 가입 상태 변경 시 메시지·별칭 제거.
- `OnlineGameSessionRepository.kt`: 게임 전용 공개 메시지 listener·cursor paging, 익명 메시지 Callable.
- 실제 시간 판정은 서버만 수행하고 UI는 서버의 마감 Epoch 시각으로 **시각적** 카운트다운을 보여줍니다.

## 5. 자동 QA와 검증 경계

- `functions/test/identityRules.test.js`: 4·5·7·10명 고유 별칭, 자기 자신 제외 일대일 선택 검증, 위장 보너스·정답 점수, 조기 결과 비공개.
- `functions/test/identityChat.test.js`: 500자 경계, 텍스트 정규화, 잘못된 UID·alias·timestamp 입력 거부.
- `functions/test/identityMultiplayerEmulator.test.js`: 4인 실제 Firestore 게임 전 과정, 동물 별칭·메시지 서버 저장, 투표·결과 단 한 번 정산·서버 마감, 10명 수용/11번째 차단, 속도제한·다시 시도, 참가자 오프라인 자동 마감, 강퇴 시 취소.
- `functions/test/identityChatRules.test.js`: 실제 참가자만 공개 채팅 get/list 최대 30, 비참가자·가입대기·강퇴·방 종료 차단, 본인/방장 포함 모든 앱의 비밀 매핑·투표/속도제한 문서 접근 금지.
- `IdentityGameComposeTest.kt`: 4인 로비·준비 액션, 익명 별칭 표시·메시지 전송을 Fake Repository로 검증.
- `qa-2d.yml`에서는 기존 10개 계측에 신규 2개를 더해 API29/35/36 휴대전화·API36 태블릿 4환경에서 **최소 12개 계측**을 실행하도록 변경.
- 현 단계에서 자동 테스트는 **코드 작성만 완료**한 상태입니다. PR의 실제 Firebase Emulator·Android CI가 완료됐다는 주장은 하지 않습니다.
- Google 계정 4~10개를 이용한 **실기기/Play 서명 앱의 실전 게임 E2E는 수행하지 않았습니다.** 단위 테스트/Emulator 신원은 실계정 테스트와 다릅니다.

## 6. GAME-02 및 배포 선행 관계

1. [GAME-02 PR #30](https://github.com/hebi10/TalkSullae/pull/30) 정확한 HEAD `ce6fc75c`의 필수 검사 모두 Success 확인 → SHA 고정 `main` 병합. 조회 당시 UI 검사는 `in_progress`, 2-D QA는 `queued`였으므로 먼저 충족 필요.
2. GAME-03의 stacked PR을 최신 `main` 기준으로 정렬하고 Firebase/Android 전체 CI 통과 및 보안 증빙 재확인 후 병합.
3. 승인된 Firebase 테스트 프로젝트에 Functions/Rules/Indexes와 Cloud Scheduler를 배포하고, 실계정·다중 단말 E2E·재접속·마감 워커 비용·권한·신고/차단 정책 검증.
4. 서명 AAB 신규 버전코드로 Play 내부·비공개 테스트를 **별도 승인**해 배포. 기존 `0.3.1 (3)`은 변경하지 않습니다.
5. GAME-04 다른 3종, GAME-05 시즌·랭킹, GAME-06 전체 부하·보안 QA 진행.

## 7. 운영 출시 전 주의

- 사용자 신고/차단 연동, 채팅 7일/일반 채팅 90일 데이터 보관 정책·TTL 및 계정 삭제/보존 사유 검증은 별도 작업 필요. 현재 자동 저장/삭제 정책이 최종 확정된 상태가 아닙니다.
- 별칭 결과 공개 이후의 개인 정보 노출은 게임 규칙에 따른 공개에 한정됩니다. 결과 전 UID-별칭 매핑은 모든 일반 참가자/방장에게 비공개입니다.
- 서버는 실시간 채팅 내용 자체를 AI 분석하지 않으며, 금지어/비속어 필터는 후속 운영 정책에 따라 구현해야 합니다.
- 운영 Firebase·Cloud Scheduler·Play 등록정보·릴리스 트랙은 이번 PR에서 변경하지 않습니다.

상태 정의: **GAME-03 구현 코드/테스트 PR 생성 단계**, 실제 모든 검증 통과·운영 배포 완료 아님.
