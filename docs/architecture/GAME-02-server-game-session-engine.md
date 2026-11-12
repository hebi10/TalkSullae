# GAME-02 — 다중 게임 공통 세션 엔진 구현

상태: **서버 공통 엔진·Android 통신 계층·자동 테스트 구현 / CI 검증 대기 / 운영 Firebase 미배포**  
기준: 2026-11-12, GAME-01-FINAL PR #29 병합 완료 `main=f06a643a`  
서비스 기획: https://docs.google.com/document/d/13BMHPicBwI1kN1XrPsjHYfR4wkXmBz4lUDzcPecptvg/edit  
선행 계약: [GAME-00 공통 아키텍처](../architecture/GAME-00-multi-game-platform.md), `functions/src/game/contracts.js`

## 1. 실제 구현 범위

- GAME-00의 4종 게임 카탈로그 `identity@1`, `liar@1`, `forbidden@1`, `balance@1`을 그대로 사용하고 공통 엔진 인터페이스를 구현했습니다.
- 서버: Cloud Functions 2nd gen `asia-northeast3`의 `createGameSession`, `gameSessionAction`, `findActiveGameSession`, `getMyGameSession` Callable.
- 방장은 활성 회원으로 인증된 경우에만 게임 세션 생성·게임 시작·세션 취소를 요청할 수 있습니다. 방의 현재 활성 세션은 **한 개**로 제한합니다.
- 활성 방 회원은 게임 세션 참가·본인 준비 상태 변경·참가자로서 게임 단계 제출을 요청할 수 있습니다. 모든 액션은 `request.auth.uid`, 방/멤버 `active` 상태, 규칙 버전, 리비전, 단계, 서버 마감을 다시 검증합니다.
- 상태: `waiting → ready → (assigning) → playing → (voting) → reveal → finished`, 취소 시 `cancelled`. 실제 전이는 서버의 게임별 감사된 규칙 모듈이 결정합니다.
- 서버 deadline은 Firestore `Timestamp`로 저장하며, Android 화면에는 에포크 밀리초 형태로 전달합니다. **클라이언트가 결과·승패·마감 시각을 확정할 수 없습니다.**
- 게임 단계별 개인 제출은 `privateSubmissions`에 저장하고, 재접속은 `findActiveGameSession → getMyGameSession`으로 공개/본인 개인 상태를 다시 조회하도록 구현했습니다.
- 세션 종료 시 게임 규칙 모듈이 검증한 점수만 `results/final`로 **세션당 한 번** 기록하며, 방의 활성 세션 포인터를 비웁니다. 시즌·업적·통계 적립은 GAME-05에서 구현합니다.
- Android: `OnlineGameSessionRepository`(실시간 구독/Callable)와 `OnlineGameSessionDecoder`(공개 타입·미지원 버전/상태 거부) 구현. **실제 게임 UI 버튼과 플레이 화면은 GAME-03 단계에서 연결합니다.**

## 2. 서버 데이터 모델

```text
rooms/{roomId}
  activeGameSessionId: sessionId | null

  gameSessions/{sessionId}
    schemaVersion: 1
    catalogSchemaVersion: 1
    roomId, sessionId, gameId, rulesVersion, mode
    phase: waiting|ready|assigning|playing|voting|reveal|finished|cancelled
    revision: 0..N
    round: 1..99
    phaseEndsAt: Timestamp|null
    participants: { "<uid>": {"ready": true|false} }  # 공개 가능한 로비 정보만
    submissionCount: number
    publicState: { prompt?, notice?, options?, publishedResult? }
    createdBy, createRequestId, startedAt, createdAt, updatedAt, settlementId

    privateAssignments/{uid}
      data: { <게임별 비밀 역할/주제/별칭> }           # Admin SDK 전용

    privateSubmissions/{sha256([uid,round,phase])}
      actorUid, phase, round, data, submittedAt         # Admin SDK 전용

    processedActions/{sha256([uid,requestId])}
      actorUid, fingerprint, kind, result, createdAt   # Admin SDK 전용

    results/final
      settlementId, gameId, rulesVersion, scoresByUid, createdAt
```

- `sessionId = sha256([roomId, ownerUid, createRequestId])`로 생성됩니다. 동일 생성 요청은 같은 세션만 반환합니다.
- `processedActions`는 요청 ID + payload의 검증된 지문과 응답을 저장합니다. 재전송은 기존 결과 반환, 같은 ID에 다른 payload면 거부합니다.
- `expectedRevision`이 달라지면 `aborted` 오류로 거부합니다. 클라이언트는 최신 스냅샷을 다시 조회해야 합니다.
- 게임별 제출은 `privateSubmissions`에서 사용자·라운드·단계별로 유일하게 기록됩니다. 같은 단계의 중복 투표·추리는 거부됩니다.
- 새 게임을 추가할 때 서버 규칙 레지스트리에 승인된 `gameId@rulesVersion` 구현을 등록해야 합니다. 원격 임의 실행 코드를 불러오지 않습니다.

## 3. 서버 규칙 모듈 계약

`makeRuleRegistry([...])`는 게임 버전별로 다음 메서드가 모두 존재하는 규칙만 등록합니다.

- `prepare({session,participants,nowMillis})`: 시작 단계, 서버 제한시간, 공개 문구, 사용자별 비밀 배정을 결정합니다.
- `validateSubmission({session,actorUid,choice})`: 게임별 선택/추리를 검증하고 서버 전용 데이터만 반환합니다.
- `advance({session,submissions,nowMillis})`: 서버 마감/전체 제출 이후 다음 단계·라운드·결과를 결정합니다.
- `projectForPlayer({session,actorUid,personalData})`: 인증된 사용자에게만 자신의 비밀 정보를 투영합니다.

현재 **운영 레지스트리는 의도적으로 비어 있습니다.** 게임별 규칙이 구현되지 않은 상태에서 게임을 시작하면 `failed-precondition`으로 거부합니다. 개발/Emulator QA에서는 승인된 합성 테스트 규칙 모듈을 주입하여 로비→제출→투표→결과/1회 정산 흐름을 검증합니다. **이 테스트가 실제 4종 게임 완성을 의미하지는 않습니다.**

GAME-03에서 `identity@1` 서버 모듈을 먼저 연결하고, GAME-04에서 `liar@1`, `forbidden@1`, `balance@1`을 이어 붙입니다.

## 4. 보안과 사용자별 정보

- Firestore Client SDK는 활성 방 회원만 게임 공개 루트 문서를 단일 조회하거나 최대 10개 목록 조회할 수 있습니다. 방 종료·강퇴·탈퇴 후 접근 거부.
- `privateAssignments`, `privateSubmissions`, `processedActions`는 방장 포함 모든 앱 클라이언트의 직접 읽기/쓰기를 거부합니다.
- `results/final`은 완료(`finished`) 단계에 도달한 뒤 활성 방 회원의 단일 조회만 허용합니다. 클라이언트의 생성·수정·삭제·무제한 목록 조회는 거부합니다.
- `getMyGameSession` Callable도 `active` 멤버와 해당 세션 참가자 여부를 확인하고, `projectForPlayer`로 검증된 본인 개인 상태만 반환합니다.
- 공개 문서는 게임 역할/익명 ID 매핑을 담지 않습니다. 익명 게임의 실제 메시지 및 알림 개인정보 보호는 GAME-03에서 별도 설계/QA가 필요합니다.
- Server Admin SDK는 Firestore Rules를 우회하므로, Callable의 인증·권한 검사와 규칙 자체의 안전성 검증을 별도로 수행해야 합니다.

## 5. 상태 전이·서버 시계·정산 한계

- 로비 인원 범위는 카탈로그의 min/max로 확인합니다. 모두 `ready=true`이고 최소 인원 이상인 경우에만 `ready` 단계가 됩니다. `start`는 활성 방장과 전원 유효 회원 상태를 다시 확인합니다.
- 제출은 `phaseEndsAt`이 지났다면 서버에서 `deadline-exceeded`로 거부합니다.
- 활성 참가자의 `advance` 요청은 제한시간 경과 또는 해당 단계 참가자 전원 제출 중 하나를 만족해야 합니다. 게임 서버 규칙 모듈이 다음 상태를 제시하고 엔진이 허용된 전이/라운드/시간을 검증합니다.
- 서버가 점수를 계산한 `finished` 단계에서는 `results/final` 단 한 개를 `tx.create`로 확정합니다. 동일 요청 재시도는 저장된 receipt를 반환하고, 다른 요청은 완료된 세션 변경을 거부합니다.
- **중요한 미완료 항목**: 참가자가 모두 오프라인이면 현재 엔진의 단계 전이는 별도 스케줄러 없이 자동 발생하지 않습니다. 서버는 마감 시각을 정하고 늦은 제출을 차단하지만, GAME-03 실제 게임 출시 전 Cloud Tasks/예약 워커의 전용 시간 만료 처리를 추가·검증해야 합니다.
- 리더보드·시즌 점수 지급·중복 리더보드 업데이트는 GAME-05 범위입니다. 게임 결과 1회 확정과 시즌 포인트 지급은 별개의 책임입니다.

## 6. 자동 테스트

서버 순수 테스트 `functions/test/gameEngine.test.js`: 로비 단계, 인원 제한, 전이 제약, 타이머, 공개 필드 화이트리스트, 등록되지 않은 게임 차단, 요청 형식.

Firestore Emulator `functions/test/gameSessionEmulator.test.js`: 실제 방장/멤버 권한, 생성/전송 멱등성, 준비·시작·중복 투표·서버 마감·개인 비밀 상태 복구·일회 정산·취소 후 새 세션·활성 세션 재발견.

Firestore Rules Emulator `functions/test/gameSessionRules.test.js`: 방 회원의 공개 상태 구독, 10건 쿼리 상한, 비회원/대기/강퇴 접근 차단, 비밀 역할/투표/receipt 전체 거부, 종료 전 결과 차단.

Android JVM `app/src/test/java/com/hebi/talksullae/game/online/OnlineGameSessionDecoderTest.kt`: 새 규칙/미지원 스키마 거부, 공개 상태·deadline·참가자·오프라인 캐시 모델 검증.

**PR CI 전체가 성공하기 전 병합하지 않습니다.** Android 테스트 환경은 기존 API29·35·36 휴대전화 및 API36 태블릿 QA 워크플로를 사용합니다.

## 7. 출시·배포 통제

- GAME-02는 **코드 작성 및 자동 검증 PR**입니다. 운영 Firebase Functions/Rules 배포, 앱 `versionCode` 증가, Google Play AAB 업로드, 내부/비공개 테스트 승격은 이번 단계에서 실행하지 않습니다.
- 실서비스 연결은 별도 승인 후 Firebase Emulator→승인된 개발 Firebase→실기기 Google 계정 2인 이상→운영 배포 순으로 진행합니다.
- 아직 WIF 오류를 추적 중인 [RELEASE-04 Issue #26](https://github.com/hebi10/TalkSullae/issues/26)과 혼동하지 않습니다.
- 기존 일반 채팅, 방 생성/권한·신고, 스토어 자동 배포 워크플로는 변경하지 않습니다.

## 8. 다음 단계

1. GAME-02 PR 최신 HEAD에서 Node/Firebase Emulator/Kotlin Android 빌드·QA 전부 Success 확인 후 안전 병합.
2. GAME-03 — `identity@1` 실제 게임 규칙(서버 익명 배정·채팅/추리·역할/정답 비노출), 참가자 오프라인 시 자동 마감, 전체 4~10인 게임 E2E.
3. GAME-04 — `liar@1`, `forbidden@1`, `balance@1` 개별 검증/규칙 모듈 추가.
4. GAME-05 — 시즌·통계/업적·포인트 중복 지급 방지.
5. GAME-06 — 다중 게임 사용량·부정행위·부하·실기기 QA.

완료 정의: **공통 게임 세션/서버 전이/비밀 분리 및 Android 복구 계약 구현**, 실제 게임별 규칙·실서비스 출시는 GAME-03~06과 별도입니다.
