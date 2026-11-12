# GAME-00 — 다중 게임 공통 아키텍처 확정

상태: **구조·계약 확정 / 실제 게임 플레이 미구현**  
기준일: 2026-11-12  
대상: `hebi10/TalkSullae`, `main` 기준 `9e418153`  
기획 원본: [톡술래 전체 서비스 기획서 v1.0](https://docs.google.com/document/d/13BMHPicBwI1kN1XrPsjHYfR4wkXmBz4lUDzcPecptvg/edit) §4~§8, §11~§12

## 1. 범위와 현재 코드 구분

- **실제로 존재**: Kotlin/Compose 앱, `DemoCatalog`의 4종 게임 소개, '너 누구야?' 정적 진행 시안, Firebase Auth, Cloud Functions 기반 온라인 방/초대/멤버 권한, Firestore Rules/Emulator 테스트.
- **현재 미구현**: 서버 기반 일반 실시간 채팅, 멀티플레이 게임 세션, 게임 메시지, 역할/익명 별칭 배정, 게임별 승패·점수 확정, 재접속 복원, 시즌 정산.
- **GAME-00 결과물**: 공통 게임 메타데이터, 서버·Android 읽기 전용 계약, 보안·데이터 모델·전환 규칙·버전 관리 및 단계별 구현 명세. **게임 실행 API 또는 Firestore 규칙을 활성화하지 않음.**
- **변경 금지 범위**: 기존 방 권한 코드·로그인·앱 UI·서명·Firebase 배포/Secrets·Google Play 내/비공개 출시 경로·`versionCode=3`.

## 2. 아키텍처 경계

```text
Android / Jetpack Compose
  ├─ GameCatalog (서버 승인 목록을 보여주는 화면, 초기에는 정적 사본)
  ├─ GameLobby / GamePlayHost / VoteOrGuess / Result (공통 UI)
  ├─ 게임별 표현 컴포넌트 (identity / liar / forbidden / balance)
  └─ GameSessionView (서버가 공개해도 되는 스냅샷; 점수·역할 결정 권한 없음)
               |
               | Firebase Auth + 구독/Callable, 인증 UID는 서버 컨텍스트에서 판독
               v
Cloud Functions 2nd gen (서울 리전, 서버 권한의 유일 진입점)
  ├─ GameSessionService: 생성/참가/준비/시작/마감/재접속
  ├─ GameActionService: 인증·권한·리비전·멱등성·마감·유효성 검증
  ├─ GameRulesRegistry: 승인된 게임만 수동 등록
  │    ├─ identity@1
  │    ├─ liar@1
  │    ├─ forbidden@1
  │    └─ balance@1
  ├─ GameSettlementService: 서버 계산/세션별 단 1회 확정/방 시즌 통계
  └─ GameViewProjector: 공개 상태와 사용자별 비공개 상태를 분리
               |
               v
Cloud Firestore (게임 세션/메시지/결과/권한·비밀 정보 분리)
```

원칙:
1. Android는 **사용자 의도만 제출**합니다. 승패·점수·비밀 역할·서버 시각을 클라이언트 입력으로 신뢰하지 않습니다.
2. 각 게임은 **규칙 구현만** 소유합니다. 대기·참가·타이머·인증·부정행위 방지·재접속·정산·공통 UI는 재사용합니다.
3. 게임 엔진의 전역 공유 코어는 게임별 `if (gameId == ...)` 분기가 증가하지 않도록 규칙 모듈을 명시적으로 등록합니다. 외부 콘텐츠가 실행 가능한 코드/게임 규칙을 주입하게 하지 않습니다.
4. 게임별 고유 설정/액션은 `payload`에 담되, 서버의 **해당 게임 규칙 모듈**이 필드별 형식·자격·상태를 추가 검증합니다.

## 3. 게임 카탈로그 v1

서버 계약 기준 파일: `functions/src/game/catalog.v1.json`. 이를 기준으로 클라이언트 목록/필터를 구축하되 현재 UI의 `DemoCatalog`는 후속 단계까지 유지합니다.

| ID | 게임 | 인원 | 진행 방식 | 비밀 배정 | 익명 메시지 |
|---|---|---:|---|---|---|
| `identity` | 너 누구야? | 4~10 | 실시간 | 예 | 예 |
| `liar` | 라이어 채팅 | 4~10 | 실시간 | 예 | 아니요 |
| `forbidden` | 금지어 채팅 | 3~10 | 실시간 | 예 | 아니요 |
| `balance` | 밸런스 예측 | 2~10 | 실시간·비동기 | 아니요 | 아니요 |

- `id`: 저장되는 안정적인 영문 식별자. 표시명 변경과 무관합니다.
- `rulesVersion`: 게임 로직의 세부 규칙 스냅샷 버전. **진행 중인 세션은 규칙 변경으로 소급 수정하지 않습니다.**
- `schemaVersion`: 클라이언트/서버 공통 데이터 계약 버전. 부적합/미지원 버전은 오류·업데이트 안내 후 차단.
- `minPlayers`/`maxPlayers` 및 모드는 서버의 검증 대상이며 Android의 문구만으로 결정하지 않습니다.
- `hasSecretAssignments`, `hasAnonymousMessages`는 메타데이터이며 **비밀 값을 포함하지 않습니다**.
- 신규 게임: 카탈로그 항목 + 서버 규칙 모듈 + Android 게임별 렌더러 + 보안/게임별 테스트만 추가. 기존 게임 규칙 코드는 변경하지 않는 것을 목표로 합니다.

## 4. 게임별 모듈 계약 (GAME-02에서 실제 구현)

서버의 승인된 `GameRules` 모듈은 다음 순수 함수 계약을 따릅니다.

| 함수 | 책임 |
|---|---|
| `validateSettings(settings)` | 게임 전용 설정 및 플레이 가능 인원 검사 |
| `setupRound(context, seed)` | 암호학적으로 안전한 서버 난수 기반 역할/주제 준비 |
| `projectPublic(state)` | 결과 공개 전 공개해도 되는 정보만 생성 |
| `projectForPlayer(state, authenticatedUid)` | 해당 사용자에게 허용된 역할·선택만 반환 |
| `validateAction(state, uid, action)` | 단계·참가·마감·중복·입력 조건 검사 |
| `reduce(state, action)` | 게임별 상태를 결정적으로 계산, 외부 DB 쓰기는 하지 않음 |
| `settle(state)` | 서버가 원점수·승패·결과를 계산 |
| `restore(state, uid)` | 재접속 사용자에게 단계·익명 ID·선택 여부를 안전하게 투영 |

Firestore 트랜잭션 실행·중복 요청 키 저장·이벤트 발행은 규칙 모듈 밖 공통 서비스가 담당합니다. 클라이언트측 Kotlin 모델은 결과 표시 전용입니다.

## 5. 공통 생명주기와 원칙

공통 단계 ID는 다음을 사용합니다.

```text
waiting → ready → (assigning) → playing → (voting) → reveal → finished
  └────────────── cancelled (진행 중 서버 취소)
```

- `assigning`, `voting`은 게임에 따라 선택적입니다. 라이어의 재투표/다회차는 동일 단계를 다시 사용할 수 있으므로 **단일 고정 직선 FSM을 모든 게임에 강제하지 않습니다**. 구체적인 허용 전이는 `rulesVersion`별 서버 규칙 모듈이 판정합니다.
- 공개 상태 `phase`, `round`(1부터), `stateRevision`(0부터), `phaseEndsAt`(Firestore 서버 시각)을 분리합니다.
- 클라이언트는 서버 `phaseEndsAt`를 기준으로 **시각적인** 카운트다운을 표시할 뿐, 마감·결과 확정을 하지 않습니다. 지연·오프라인·시각 변경에도 서버가 최종 판정합니다.
- 취소·종료 후 새 메시지/투표는 서버에서 차단. 방장 오프라인일 때도 서버가 진행 상태를 유지.
- `GameSessionView`는 비밀 매핑·투표 내용·승점 확정 값을 포함하지 않는 공개 화면 계약입니다.

## 6. Firestore 경로 초안 — 배포 전 필수 보안 검증

기존 `rooms/{roomId}`와 `members` 권한 모델을 유지합니다. 게임을 방 단위로 격리하고 일반 채팅과 별도 메시지 스트림을 사용합니다.

```text
rooms/{roomId}
  members/{uid}                          # 기존 실제 권한 근거
  gameSessions/{sessionId}               # 공개 세션 메타(상태/버전/마감/리비전)
    participants/{uid}                   # 참가/준비/탈퇴 공개 가능한 정보
    publicMessages/{messageId}           # 익명 게임: aliasId만, 작성자 UID 제외
    privateAssignments/{uid}             # 서버 전용; Client SDK read/write deny
    privateSubmissions/{uid}             # 서버 전용; 미공개 투표/선택
    processedActions/{requestId}         # 중복 요청·정산 멱등 영수증
    results/{resultId}                   # 결과 공개 이후 읽기
  seasons/{seasonId}
    scores/{uid}                          # 확정된 서버 집계(세션별 중복 방지)
```

원칙:
- 모든 경로의 정확한 Firestore Rules를 **GAME-02 이전**에 Emulator로 검증. 현재 `firestore.rules`의 중첩 기타 경로 거부는 유지합니다. 미구현 상태에서 게임 경로를 열지 않습니다.
- 방장에게도 역할·정답·익명→UID 연결 정보를 공개하지 않습니다. 비밀 정보는 서비스 계정/서버 권한 경로로만 접근하며 개인 상태는 인증 UID에 한해 Callable로 투영합니다.
- 익명 채팅 공개 문서에는 실제 `uid`뿐 아니라 실제 작성자 역추적을 가능하게 하는 필드·메시지 ID 규칙·알림 내용을 넣지 않습니다.
- Public View의 필드 화이트리스트는 게임별로 **명시**. 클라이언트측 필드 숨김은 보안 통제가 아닙니다.
- 게임 세션의 `schemaVersion`/`rulesVersion`을 보존하고 서버 결과에만 `settledAt` 및 `settlementId` 기록.
- **동시 요청:** `expectedRevision`으로 상태 변경 낙관적 잠금, `requestId`로 멱등 처리, 서버 트랜잭션으로 점수 1회 지급. 메시지 1건마다 세션 루트 문서를 갱신하는 핫스팟은 금지합니다.
- 보존 초안: 일반 채팅 90일/게임 원문 7일/결과·시즌 기록 별도 보존 (기획 초안이며 확정 운영 정책 아님). TTL/정리·계정 삭제 설계는 GAME-02~05에서 검증 후 반영.

## 7. Command API 계약 초안

유저가 보내는 공통 입력은 최소 다음과 같습니다.

```json
{
  "roomId": "12345678901234567890",
  "sessionId": "session-0123456789",
  "gameId": "identity",
  "rulesVersion": 1,
  "expectedRevision": 0,
  "requestId": "183c9c18-46a1-423d-87d0-2cfa436afab9",
  "payload": {}
}
```

- `roomId`, `sessionId`, `gameId`, `rulesVersion`, `expectedRevision`, `requestId` 필수.
- `uid`, `role`, `score`, `serverTimestamp`은 외부 공통 인자로 받지 않습니다. `payload`는 게임별 별도 검증.
- 인증 UID는 `request.auth.uid`에서 읽고 참가·강퇴 여부를 Firestore 트랜잭션에서 확인해야 합니다.
- GAME-00의 `validateActionEnvelope`는 **형식 검사**일 뿐 권한, 점수, 타이머, 진행 단계 판정이 아닙니다. GAME-02 서버 API를 구현할 때 반드시 추가합니다.

## 8. Android UI 분리 전략

- `GameCatalogScreen`: 카탈로그·인원·소요 시간·모드/활성 상태.
- `GameLobbyScreen`: 준비·참가·방장 시작 버튼(권한은 서버 재검증).
- `GamePlayHost`: 세션 공개 상태 구독·게임별 진행 콘텐츠 렌더링.
- `GameMessageList`, `GameTimer`, `GameSubmissionPanel`, `GameResultScreen`: 공통 컴포넌트.
- `GameReconnect`: 서버 세션 재조회와 분리된 개인 비밀 상태 복원.
- 현재 `TalkSullaeApp.kt`의 `gameId`/`gamePhase` 로컬 데모 경로는 그대로 두고 **GAME-02~03에서만** 안전한 온라인 경로를 연결합니다.
- `GamePhase.fromWire`가 새/알 수 없는 값을 받으면 임의로 PLAYING으로 바꾸지 않습니다. 사용자에게 '앱 업데이트 또는 다시 시도'를 안내합니다.
- 기존 밝음/어두움 테마·키보드 동작·화면 크기 지원을 공통 컴포넌트에서도 유지합니다.

## 9. 성능·비용과 보안

- Firestore 구독은 **현재 세션과 필요한 최근 메시지 범위**만, 이전 메시지는 페이지 단위 조회. 화면 닫힘/강퇴 시 리스너 해제.
- 게임 타이머는 UI에서 1초마다 Firestore에 쓰지 않습니다. 서버 마감 시각을 1회 저장.
- GameSession 루트는 단계·리비전 업데이트 중심, 메시지는 하위 문서에 분리 저장해 문서 경합을 줄입니다.
- 단일 메시지·선택 액션에는 문서 읽기/쓰기 수와 P95 응답 시간을 계측. 정확한 비용과 수용량은 부하 테스트 전 추정하지 않습니다.
- 비밀 역할, 개인 정답, 원문 메시지 내용은 로깅하지 않습니다. 관찰 항목은 안전한 이벤트 타입·게임 ID·규칙 버전·서버 지연·오류 코드 등으로 제한합니다.
- Firebase App Check와 호출 빈도 제한·입력 길이 제한·신고/차단 연동은 게임 API 출시 조건입니다.

## 10. 버전 정책·기존 기능 호환

- `catalog.v1.json`은 GAME-00의 정적 승인 목록이며 운영 중 동적 코드 로딩/원격 임의 JS 실행은 지원하지 않습니다.
- 게임별 규칙이 달라지면 `rulesVersion` 증가. 이미 시작한 세션은 자신이 생성된 규칙 버전으로 완료하거나 안전하게 취소합니다.
- 공통 저장 구조가 바뀌면 `schemaVersion` 증가, 클라이언트/서버 호환성 판정 추가. 알 수 없는 ID/상태는 기본 게임으로 대체하지 않습니다.
- 현재 실시간 채팅/시즌/게임 엔진은 미구현이므로 기존 데이터 마이그레이션을 **이번 PR에서 실행하지 않습니다**.
- `functions/index.js`에 게임 API를 노출하지 않습니다. 변경된 `functions/package.json`은 pure contract 테스트 등록뿐입니다.
- `firestore.rules`, `app/build.gradle.kts`, Play Actions 및 고정 서명키는 변경하지 않습니다.

## 11. GAME-00 검증 기준

- [x] 기존 4종 게임 ID, 인원, 진행 모드, 비밀/익명 속성 규정
- [x] 서버·Android 생명주기 wire 계약 및 버전 검증 타입 추가
- [x] 서버 행동 봉투의 요청 ID, 상태 리비전 및 허용 필드 형식 검증
- [x] 기존 실행 화면/Firestore Rules/Functions export/배포 파이프라인 무변경
- [ ] **PR CI 확인 필요**: Node 단위/Firestore Emulator/Android testDebugUnitTest/Android 계측 검사
- [ ] **후속 GAME-01~03**: 실제 다중 단말 채팅·세션·역할·점수·재접속·보안 QA

## 12. 후속 작업 순서

1. **GAME-01 — 실시간 일반 채팅 기반**: 방 멤버 채팅, 메시지 전송/정렬/페이지 로딩, 권한·신고·오프라인 재시도. 일반 채팅은 게임 채팅과 별도 유지.
2. **GAME-02 — 공통 세션·서버 게임 엔진**: 서버 규칙 레지스트리·명시적 상태 전이·비밀 역할 투영·스냅샷·타이머·요청 멱등성·Emulator 보안 QA.
3. **GAME-03 — 너 누구야? 실제 멀티플레이**: 4~10명 엔드투엔드 판정·재접속·결과 검증.
4. **GAME-04 — 3종 규칙 모듈**, **GAME-05 — 통합 기록·시즌**, **GAME-06 — 부하/보안/다중 단말 QA**.

결정 기록: GAME-00은 **'게임 수 확대 전에 공통 계약부터 고정하되, 실제 게임 진행과 보안 규칙 활성화는 검증 가능한 별도 단계에서 수행한다'**로 확정합니다.
