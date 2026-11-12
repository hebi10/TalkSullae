# GAME-01-FINAL — 실시간 일반 채팅 통합 QA·Firebase 출시 게이트

기준일: 2026-11-12  
상태: **통합 자동 QA 추가 / 실계정·운영 배포 미검증**  
선행: GAME-00 PR #27 병합 → GAME-01 PR #28 (최신 HEAD CI 필수) → GAME-01-FINAL 후속 PR  
앱: `com.hebi.talksullae` / Kotlin Compose / Cloud Firestore / Cloud Functions (서울)

## 1. 구현과 검증의 경계

- **완료된 구현**: GAME-01의 실시간 일반 채팅 코드·메시지 쿼리(최근 30개), 커서 페이지, 서버 Callable, UUID 멱등성, 권한 제한, 재시도 화면.
- **추가된 GAME-01-FINAL 코드**: Firestore Emulator 두 인증 신원 통합 테스트, 65건 메시지의 30/30/5 페이징 검증, 강퇴/방 종료 후 읽기 금지 보강.
- **미완료**: Play 서명 설치본에서 Google 계정 두 개를 사용하는 실단말 검증, 실제 Firebase 배포/Functions 호출, 다중 단말 오프라인 복원, 비용·지연 부하 시험. Emulator의 사용자 두 명을 실제 Google 계정 테스트로 표시하지 않습니다.
- 채팅 기반 서비스에서 추가로 필요한 사용자 차단·메시지 신고·삭제/보존·App Check·FCM 알림은 **추후 실서비스 출시 전 별도 승인/구현 대상**입니다. 현재 코드가 해당 기능을 제공한다고 표기하지 않습니다.

## 2. 자동 QA 수용 조건

| ID | 자동 검증 | 성공 조건 |
|---|---|---|
| A01 | 활성 사용자 2명의 서버 Callable 전송 | 서로 읽기 성공, UID 및 이름 서버 결정 |
| A02 | 동일 UUID 재전송 | 하나의 Firestore 문서만 유지 |
| A03 | 같은 UUID로 다른 본문 전송 | 서버 거부 |
| A04 | 비회원·가입 대기·강퇴 사용자 | 메시지 목록/get/직접 쓰기 거부 |
| A05 | 방 종료 및 잔류 active 멤버 | 모든 메시지 읽기 거부 |
| A06 | 65개 메시지, 30개 쿼리 | 30/30/5 조회, ID 누락/중복 없음 |
| A07 | limit 31/무제한 목록 | Rules 거부 |
| A08 | 메시지 전송 속도 제한 | `resource-exhausted` 및 결과 중복 없음 |
| A09 | Android 메시지 입력/전송 UI | `RoomChatComposeTest` 통과 |
| A10 | Android API 29·35·36 및 태블릿 | 기존 2-D 통합 QA 및 테스트 캡처 통과 |

필수 명령은 기존 CI에서 자동 실행됩니다. `functions/package.json`의 `test:emulator` 스크립트에 `chatFinalEmulator.test.js`가 추가됐습니다. **CI 실패 시 안전 병합하지 않습니다.**

## 3. Play 설치본/실계정 2개 수동 QA

- T01 계정 A: 기존 Play 서명 설치본으로 Google 로그인, 온라인 방 생성, 초대 코드 발급.
- T02 계정 B: 독립 기기·계정으로 초대 코드 가입 요청. 승인 이전에는 채팅/멤버 조회 불가.
- T03 계정 A: 방장 가입 승인 후 B가 채팅 화면에 접근.
- T04 A→B 메시지 전송 후 B에 실시간 표시, B→A 회신 후 A 표시. 본인/상대 구분, 순서, 서버 시각 확인.
- T05 A/B 양쪽에서 텍스트·한글 조합·긴 본문(1,000자)·잘못된 제어문자·빈 본문 검증.
- T06 서버에서 65건 이상 메시지 보유 시 이전 메시지 30건씩 페이지 로드, 중복·누락 없음.
- T07 네트워크 일시 중단·복원: 잘못된 성공 메시지를 표시하지 않고 동일 요청 ID 재시도 시 중복 저장 없음.
- T08 B 강퇴 및 방 종료: 현재 온라인 화면 닫힘, 새 메시지 읽기/전송 거부, 앱 재실행 후 캐시로 권한이 회복되지 않음.
- T09 앱을 강제 종료하고 다시 시작: 방 목록/채팅 최신 내용 재동기화, 불확정 전송 상태 처리 확인.
- T10 다른 방의 멤버가 메시지 경로를 직접 요청해도 권한 없음; 로그/알림에 개인 정보 또는 잘못된 역할 노출 없음.

실제 기기 QA에는 **Firebase에 함수·규칙 배포가 되어야 하므로**, Emulator 테스트 통과와 사용자 승인 없이 운영 Firebase를 변경하지 않습니다.

## 4. Firebase 배포 순서 및 조건

1. GAME-01 및 GAME-01-FINAL PRs의 최신 HEAD에서 Android·Node·Firebase Emulator CI 모두 Success.
2. 승인된 Firebase 테스트 프로젝트(운영 분리 시)에서 Functions + Security Rules 동작 검증. 실제 Firebase 프로젝트 ID, 배포 대상, IAM 권한, App Check 정책 확인.
3. 인증 설정: Google Play 앱 서명 SHA-1/SHA-256, OAuth, Cloud Functions 서비스 계정 IAM, Firebase 요금제/지역 점검.
4. 함수 `sendRoomMessage`가 인증 사용자의 요청을 올바르게 처리하는지 배포 전환 계획 수립. Firestore Rules의 방/멤버 제약과 마이그레이션(기존 메시지 데이터 없음)을 확인.
5. 운영 Rules 및 Functions 변경은 **별도 명시적 배포 승인 후** 수행. GitHub WIF 문제 [Issue #26](https://github.com/hebi10/TalkSullae/issues/26) 해소 전 자동 배포 성공을 단정하지 않음.
6. 함수 배포 확인, Rules 배포 확인 및 두 실제 계정으로 E2E 검증 후에만 출시 준비 상태 표시.
7. Google Play 신규 버전은 기존 `versionCode=3`을 재사용하지 말고 새 미사용 버전코드+기존 업로드키로 서명. 내부→비공개 테스트 트랙도 승인 절차 분리.

## 5. 비용·안전성·개인정보 운영 게이트

- 10/20명 방에서 메시지 연속 전송, 30/65/100+건 페이징, 재연결 시 리스너 정리, 동시 방 여러 개 시 Firestore reads/writes와 Cloud Functions 호출량 측정.
- 성공률·P95 전송 지연·429/permission-denied·중복 메시지 비율을 안전한 로그/지표로 측정하고 오류 예산을 지정.
- 방 탈퇴·강퇴 후 오프라인 캐시 및 개인정보 남김 범위를 검토. 앱 삭제/계정 삭제 및 보관 기간에 대한 확정 정책 필요.
- 사용자 신고/차단, 메시지 보관 기간(기획상 90일은 초안), 데이터 삭제, App Check 정책은 유료/공개 출시 전에 별도 검증·결정.

## 6. 최종 완료 판정표

| 판정 | 현재 |
|---|---|
| GAME-00 `main` 병합 | 확인 완료 |
| GAME-01 PR #28 `main` 검증 | 신규 CI 확인 대기 |
| GAME-01-FINAL Emulator QA | 테스트 추가, CI 검증 대기 |
| 두 실제 Google 계정·실기기 E2E | 미검증 |
| Firebase 운영 Functions/Rules | 미배포 |
| Google Play 신규 AAB 및 closed 출시 | 미배포 |

기록 원칙: PR 성공과 실제 Firebase 운영/Play 게시를 혼동하지 않습니다. **배포 승인 없이 인증/Cloud IAM/프로덕션 출시 변경 금지.**
