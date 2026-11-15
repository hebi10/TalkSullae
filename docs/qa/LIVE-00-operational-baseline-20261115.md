# LIVE-00 — TalkSullae 운영 전환 기준선·GAME-06 검증

확인일: 2026-11-15 (KST). 근거: GitHub main·Actions/PR, Drive 전체서비스 기획서 v1.0 및 GAME-05/SAFETY-01 추적 문서. **이 문서는 소스·자동화 증빙 기준이며 운영 Firebase의 실제 배포 상태나 실사용 경험을 증명하지 않습니다.**

## 1. 현재 기준선

| 항목 | 확인 상태 | 근거 |
|---|---|---|
| 메인 소스 | `bb25a231b13fcb42aafc8f357ebcdb00ce2cabfe` | https://github.com/hebi10/TalkSullae/commit/bb25a231b13fcb42aafc8f357ebcdb00ce2cabfe |
| 최신 main Android 소스 후보 | `0.5.0 (versionCode 6)`, `com.hebi.talksullae`, **Play 미업로드** | `app/build.gradle.kts`, LIVE-08 #58 |
| 이전 Play internal API 게시 | 2026-11-14 `api_committed=true`, `readback_confirmed`, `internal`, **0.4.1(5)** | https://github.com/hebi10/TalkSullae/actions/runs/43564763042 |
| Play 실제 테스터 설치 | 미검증 | API commit/readback과 설치 확인 구분 |
| 비공개 테스트 | 신규 0.4.1 승격 확인 없음 | 수동 트랙 승격/테스터 증빙 필요 |
| 소스의 Firebase Functions | 방·초대·게임 세션·채팅·전적·신고/삭제 및 주기 작업 정의 | `functions/index.js` |
| 운영 Firebase Functions/Rules/Indexes/Scheduler | 현재 배포된 리비전과 소스 일치 여부 미검증 | 연결된 Firebase 운영 조회/배포 검증 필요 |
| Google 로그인 실기기 | 신규/기존 Google 계정 2개 이상 실증 미확인 | Play 서명 SHA, Credential Manager/OAuth 점검 필요 |

## 2. GAME-06 정합화

- 기존 PR #46 (GAME-06, 통합 에뮬레이터 검증)을 이전 main `c9a80eb0` 기준으로 재작성하여 HEAD를 `dcb81ab6effec9f0a39273220ab1d44e44a4bccd`로 정합화했습니다.
- 변경 파일은 기존 3개로 한정: 게임 4종 연속 세션/정산 검증, Node 에뮬레이터 테스트 스크립트 편입, QA 문서.
- 해당 HEAD의 Android API 29/35/36 휴대전화·API 36 태블릿(실패한 캡처 잡만 재실행해 성공), Firebase Emulator, OPS 릴리스 안전 및 Codex 최신 리뷰를 확인했습니다. GitHub checks 10/10 success, Codex 주요 지적 0건, 미해결 리뷰 스레드 0건입니다.
- #46을 HEAD SHA 고정 squash 병합하여 main `0139a719f1a0a46653492934da3f28e8f85ec100`으로 반영했습니다. PR 병합은 Play/Firebase 운영 배포 승인이 아닙니다.
- PR: https://github.com/hebi10/TalkSullae/pull/46

## 3. 기술·운영 드리프트

1. `README.md` 상단은 LIVE-08 #58에서 실제 기능 범위와 `0.5.0(6)` 소스 릴리스 후보 상태로 갱신했습니다. 하위 이력성 문서에는 당시의 2-D·데모 단계가 남아 있으므로 현재 게시 증거로 해석하지 않습니다.
2. LIVE-02/03의 실제 계정 기본 동선은 Firebase 방 목록·채팅·게임·기록·초대로 전환되었습니다. 로컬 `DemoCatalog`는 Firebase 미연결 디버그 QA 미리보기에 한정되어 남아 있습니다. 실서버 설치본의 동작은 미검증입니다.
3. LIVE-01은 서버 저장 닉네임·아바타·방 멤버 표시명 동기화 코드로 main에 병합됐습니다. 다만 `saveMyProfile` 및 `users` 규칙은 **운영 Firebase 배포와 이전 설치본 호환성 검증 전에는 실사용 가능하다고 볼 수 없습니다.**
4. 과거 PR #23은 `qa` 트랙 404 대응 코드였으나 최신 `internal` 게시 증빙과 불일치하고 Codex P2 미해결도 있어 **근거 댓글을 남기고 병합 없이 종료**했습니다. 현재 배포 경로를 과거 방식으로 바꾸지 않습니다.
5. LIVE-03은 설치된 Android 앱 전용 `talksullae://invite` 인텐트를 구현했습니다. 검증된 HTTPS App Links와 미설치 웹 설치 안내는 아직 없습니다. LIVE-05는 알림 권한·FCM 일반 문구·토큰/동의 규칙을 소스에 구현했으나 운영 Functions 트리거와 실제 수신은 미검증입니다.
6. 개인정보·안전 신고/계정 삭제는 Emulator 테스트를 통과해도 신고 처리자 운영, 실제 데이터 정리·보존 및 Scheduler 동작을 검증해야 합니다.

## 4. 운영 Firebase·Play 배포 게이트

1. 승인된 Firebase 프로젝트의 Functions 목록·배포 리비전, Firestore Rules/Indexes, Scheduler 작업 상태를 읽기 전용으로 조회합니다. 운영 데이터나 권한을 조회 불가능한 경우 미검증으로 남깁니다.
2. 배포 전 변경 규칙·스키마 호환성을 구버전 0.4.1 설치본과 대조하여 단계별 서버→클라이언트 순서를 검증합니다. 실제 배포는 별도 명시적 승인 후 실행합니다.
3. 두 개 이상 실 Google 계정에서 가입·로그인 복구, 실제 닉네임 표시, 방 생성·가입·승인·강퇴, 채팅·4종 순차 게임·정산을 확인합니다.
4. 자동 QA: Android API 29/35/36 휴대전화/API36 태블릿, Firebase Emulator, OPS-07/08 정책, 리뷰 P1/P2 해결 필수. 160~200% 확대·다크/라이트·TalkBack·폴더블 및 네트워크 단절은 실기기 추가 확인입니다.
5. Play internal은 실제 저장된 AAB/versionCode와 트랙 readback을 확인한 뒤 테스터 링크 설치·로그인을 별도로 확인합니다. closed 승격은 독립 승인/전용 워크플로를 사용합니다.

## 5. LIVE-01~08 소스 병합 결과 (2026-11-15 KST)

| 단계 | PR | main 병합 커밋 | 검증 |
|---|---|---|---|
| LIVE-01 · Google 인증·닉네임 | #50 | `9c2c0bbb254a79f18a717f7e766d2c52043c4e63` | 필수 CI 11/11, 리뷰 지적 0 |
| LIVE-02 · 온라인 홈 | #52 | `f0ccdb56d779e57971721082da2dde30c8856e32` | 필수 CI 10/10, 리뷰 지적 0 |
| LIVE-03 · 초대/가입/방 관리 | #53 | `0a18fbf5f91788619d7c45b5d5d856706b25fe65` | 필수 CI 11/11, 리뷰 지적 0 |
| LIVE-04 · 게임 복귀/채팅 재연결 | #54 | `693650fbc41360cec509b35bbcf07ab3ceede9ee` | 필수 CI 10/10, 리뷰 지적 0 |
| LIVE-05 · 전적·FCM 옵트인 | #55 | `b49d79236ec2f4de77a3648d9a99dc387d434e33` | 필수 CI 12개 성공, 보호된 선택 검사 1개 정상 skipped, 리뷰 지적 0 |
| LIVE-06 · 신고 운영/관측 | #56 | `48bf239c5e132667d6d931786fdae6b1d3397f82` | 필수 CI 10/10, 리뷰 지적 0 |

| LIVE-07 · 채팅·기록 접근성 | #57 | `02416469bcd04d09195059cdb658f1476eede9e7` | 필수 CI 10/10, 리뷰 지적 0 |
| LIVE-08 · 통합·0.5.0 릴리스 게이트 | #58 | `bb25a231b13fcb42aafc8f357ebcdb00ce2cabfe` | 필수 14 checks 성공·보호된 4 jobs 건너뜀, 리뷰 지적 0 |

모든 LIVE-01~08 단계는 누적 PR 간 코드 관계를 유지한 상태로 main에 병합했습니다. 최신 main 커밋에서 Android 단위/Lint, unsigned release, OPS-08 source freeze 및 LIVE-08 source gate 5개 검사 성공을 별도로 확인했습니다. **소스 통합·에뮬레이터 검증은 운영 Firebase 배포나 새 Play AAB 게시를 뜻하지 않습니다.**

## 6. 남은 실서비스 출시 게이트

1. LIVE-01~08 병합·PR 최신 필수 CI/코드 리뷰와 main 후속 5개 검사까지 확인했습니다. Firebase 및 Play 운영 리비전, 실계정·실기기 E2E, 외부 정책 검증은 미완료입니다.
2. 운영 Firebase의 Functions, Rules, Indexes, Scheduler 배포 리비전을 읽기 전용으로 확인하고 **실제 Play `0.4.1(5)` 설치본과 신규 서버 계약의 호환성/롤백 절차를 검토**합니다. 이 채팅에서는 운영 Firebase의 리비전을 직접 확인하지 못했습니다.
3. 승인된 운영 Firebase 서버 변경을 별도로 적용한 뒤 Google 실제 계정 2~4개, 초대·방 생성·4종 게임·단일 정산·가입 승인·개인 전적·FCM 및 신고·계정 삭제를 실기기에서 검증합니다.
4. Play internal의 이전 `0.4.1(5)`은 신규 기능이 포함된 `0.5.0(6)` AAB로 갱신되지 않았습니다. 새 버전은 아직 소스 후보이며 운영 Firebase 호환성 QA, 서명 AAB/해시 확인, Play internal API commit/readback과 테스터 실설치 확인 후 비공개 테스트 승격 여부를 결정해야 합니다.
5. 개인정보 처리/연령 제한/사용자 생성 콘텐츠 신고 및 운영자 대응, 앱 링크 도메인 보유·검증, 장기 보존 데이터와 비용·알림 모니터링 설정을 완료해야 실제 운영 준비로 인정합니다.

**이번 단계에서는 main 코드와 운영 기준선 문서를 정리했으며, Firebase 운영 배포·Google Play AAB 업로드/트랙 승격/스토어 등록정보 변경을 수행하지 않았습니다.**

## 7. 운영 상태 점검 및 접근 제한 (2026-11-15)

| 확인 항목 | 근거 | 판정 |
|---|---|---|
| 현재 GitHub main | `bb25a231b13fcb42aafc8f357ebcdb00ce2cabfe` | LIVE-01~08 소스 병합 완료 |
| PR #23 | 과거 `qa` 404 대응·리뷰 미해결, 현 `internal`과 다른 경로 | 병합 없이 종료 |
| 기존 Google Play internal | `0.4.1(5)` API commit/readback 이력 | 테스터 설치·심사 확인 별개 |
| 신규 Google Play internal | `0.5.0(6)` 소스 후보, 서명/게시 AAB 없음 | 미배포 |
| 운영 Firebase Functions/Rules/Indexes/Scheduler | 이 대화의 도구로 실제 배포 리비전을 읽지 못함 | 미검증 |
| 실제 Google 계정 2~4개 | Firebase Emulator 모의 계정 테스트만 완료 | 실기기 검증 필요 |
| 신고·계정 삭제·FCM | 소스 보안 테스트 통과, 운영 처리/실수신 이력 없음 | 실운영 검증 필요 |

**다음 작업 순서:** 운영 Firebase 리비전 읽기 전용 비교 → 구버전 앱과 호환성·롤백 확인 → 별도 승인을 전제로 서버 적용 → 실계정·실기기 E2E → 고정키 서명 AAB → 별도 승인된 Play 내부 게시 → 비공개 테스트 검증. 이 문서 수정은 운영 사용자 데이터·Play 트랙을 변경하지 않습니다.

## 8. 이전 Play 소스 0.4.1(5)과 0.5.0(6) 정적 호환성 점검

**실제 게시 소스 기준:** Google Play 내부 게시 성공 이력이 있는 Actions run [#43564763042](https://github.com/hebi10/TalkSullae/actions/runs/43564763042)의 `head_sha=c9a80eb09a70a27ce2bd500b0aedf7c2a0924987`; 현재 개발 `main=bb25a231b13fcb42aafc8f357ebcdb00ce2cabfe`.

| 항목 | 0.4.1(5) 게시 소스 | 0.5.0(6) 최신 소스 | 정적 검사 |
|---|---|---|---|
| Android Gradle 버전 | `0.4.1` / code 5 | `0.5.0` / code 6 | 코드 증가 |
| `functions/index.js` 이름으로 추출한 export | 21개 | 31개 | 기존 21개 이름 모두 유지 |
| 신규 함수 이름 | 없음 | `saveMyProfile`, `previewInvitation`, `registerPushToken`, `getModerationInbox`, `setModerationStatus`, `getMyCareer`, `notifyRoomMessage`, `notifyGameStart`, `notifyMemberApproval`, `auditSafetyBacklog` | 10개 새 export |
| Firestore Rules 정적 비교 | 기존 권한 명세 | `users/{userId}` 본인 get, `moderationAudit/{auditId}` 클라이언트 접근 금지 추가 | 기존 비공백 규칙 문장 삭제 없음 |

**위 표는 저장소 소스의 정적 비교입니다.** 현재 운영 배포된 함수 리비전·실제 Rules·Firestore Indexes와 일치한다는 뜻이 아닙니다. 함수 이름이 보존돼도 입력 필드·트랜잭션·보관 정책 등이 변경될 수 있으며 기존 Android 0.4.1 앱의 전체 호출·응답 계약 호환성을 증명하지 않습니다.

### 변경 전 읽기 전용 운영 점검

1. 승인된 Firebase 프로젝트 `talksullae` 및 서울 리전 `asia-northeast3`의 **실제 운영 Functions 목록·리비전**, Firestore Rules 배포 버전·컴포지트 색인 상태, Scheduler 작업 목록·상태를 비교합니다. GitHub 소스 파일만으로 실제 반영 여부를 표시하지 않습니다.
2. Google OAuth 웹 클라이언트/Android 서명 인증서 SHA-1·SHA-256이 기존 내부 테스트 `0.4.1(5)` 및 신버전 Play 앱 서명과 일치하는지 확인합니다. 앱 비밀값은 기록하지 않습니다.
3. 이전 21개 Callable의 이름·기존 인자·응답 스키마를 기기에서 호출하는 구버전 앱과 비교해 **무삭제/호환성 유지**를 검증합니다. 새 `users` 프로필, 신고, FCM 및 주기 함수의 운영 데이터 영향, Firestore 색인 빌드 시간과 비용을 검토합니다.
4. 운영에 영향 주는 변경은 사전 백업/롤백과 새 버전 서버 기능의 단계별 활성화를 별도 승인하고, 색인 준비 → 권한 검증 → 서버 배포 → 구버전·신버전 실기기 검증 → 신규 AAB 게시 순서로 진행합니다. 각 단계가 확인되지 않으면 다음 단계로 자동 진행하지 않습니다.

**배포 안전 경계:** 이 점검은 실제 클라우드 리비전 조회 접근이 없어 아직 운영 검증을 완료하지 못했습니다. Firebase Functions/Rules/Indexes/Scheduler 배포와 Play 테스트 트랙 게시는 실행하지 않았습니다.
