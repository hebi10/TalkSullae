# 2-B — Android 화면 구현·검증 보고서 (2026-11-10)

## 검증 요약

- 최신 검증 커밋: `e6942b05ab2c86d9c066ffc2e8d7d88e4b8fcda5`
- 코드 PR: [#7](https://github.com/hebi10/TalkSullae/pull/7) — `feat/2b-onboarding-rooms-invites` → `feat/2a-compose-foundation` (Draft, 미병합)
- 일반 Android CI: [#42785318850](https://github.com/hebi10/TalkSullae/actions/runs/42785318850) — **success**
- API 35 에뮬레이터 CI: [#42785318948](https://github.com/hebi10/TalkSullae/actions/runs/42785318948) — **success**
- 단위 테스트: **7/7 통과** (DemoCatalogTest 3건, RoomUiModelsTest 4건)
- Compose UI 계측: **7/7 통과** (오류·건너뜀 0건)
- Lint: **오류 0건 / 경고 7건** (검증된 SDK·라이브러리의 업데이트 안내)
- `assembleDebug`, `assembleDebugAndroidTest` 성공
- `scripts/capture_2b_ui.py`: 안내 → 데모 온보딩 → 내 방 → 방 생성 → 로컬 방 → 멤버 → 방장 초대 → 초대받은 화면 → 가입 대기, **9개 화면 캡처, error=null**
- 추가 에뮬레이터 초기 화면: 기본 글꼴 및 200% 확대 각 1장

## Drive 자료

- [2-B Android 화면 QA 폴더](https://drive.google.com/drive/folders/1LogzcnYR8B_vhtJ3cxRsqpsFUTqPSl-r)
- [전체 화면 9종 미리보기](https://drive.google.com/file/d/14ZjGfBZ8f-mswl6fz-l_pmVK5iXjdWPZ/view)
- [전체 캡처·계측 결과 ZIP](https://drive.google.com/file/d/100S78YEE4dvWJZroiz25KGZRt_2f2bJ9/view)
- [단위 테스트·Lint 보고서 ZIP](https://drive.google.com/file/d/1_e8iCnkrrIRtppP3ocZ9y0kUuErBg-Wo/view)
- [Debug APK](https://drive.google.com/file/d/1KQvmlfSImqO2hAvC2KoCa6g9qpzNDaSo/view) — **0.1.0-dev**, 18,280,041 bytes, SHA-256 `6822dd4eb9b8d3b65bb360d2f7b0216912e2fc8224850bc0f0c4e7dd5becfd31`

### 화면별 캡처

| ID | 화면 | 저장 파일 |
|---|---|---|
| S01 | 시작 | [01_welcome.png](https://drive.google.com/file/d/1hsZBGbLN8ZLT4vi9I47A6xXootKX_aBX/view) |
| S02 | 온보딩·프로필 | [02_onboarding.png](https://drive.google.com/file/d/1omqnbUh5iJFX7jFYyCltVFKDubSzLntx/view) |
| S03 | 내 방 목록 | [03_rooms.png](https://drive.google.com/file/d/1EmmkAWA7fet0HE6a83o8G8q-3BM14U7g/view) |
| S04 | 방 생성 | [04_room_create.png](https://drive.google.com/file/d/1PeSsC5LHAioArbjLwKsXC0g0fgT7TptX/view) |
| S06 | 새 방 내부 | [05_created_room.png](https://drive.google.com/file/d/1UOU3kWJNwBw2RwYzpodQSmioeRUYZSYy/view) |
| S15 | 방장 멤버 관리 | [06_member_owner.png](https://drive.google.com/file/d/1pBaopXuT9p7WeXZOSdEoIQaoq5V13_Eh/view) |
| S05 | 방장 초대 | [07_invite_owner.png](https://drive.google.com/file/d/1CbB9iKTbnQBDLggts00F8Mo2GHokEgQZ/view) |
| S05 | 초대받은 사용자 | [08_invite_guest.png](https://drive.google.com/file/d/1SjQI1aHTp6-rS9MqTyGwsXGkb17T1Srg/view) |
| S05 | 승인 대기 | [09_join_pending.png](https://drive.google.com/file/d/1nqgkN8_XNNdTXjIdMVLu2LKqrgYd6teC/view) |
| S01 | 기본 글꼴 | [10_홈_기본글꼴.png](https://drive.google.com/file/d/1fbyb9Eg_pXxJtu64uRbBSGL83c7N9TkY/view) |
| S01 | 200% 글꼴 | [11_홈_글꼴200.png](https://drive.google.com/file/d/1TDjTNOPkfRNVdJK7S_9wLgCB1LQamBIh/view) |

## 검증 시나리오

| 시나리오 | 결과 |
|---|---|
| Welcome → Onboarding → 내 방 → 게임 탭 | PASS |
| 메인 기록 탭 | PASS |
| 방 이름 미입력 시 제출 비활성 / 유효한 이름 입력 후 로컬 방 생성 | PASS |
| 초대받은 화면의 승인 대기 상태 변경 | PASS |
| 방장 강퇴 시연 확인 후 멤버 목록 갱신 | PASS |
| 일반 멤버에게 초대·강퇴 메뉴 미노출 | PASS |
| 빈 방 상태 전환 후 방 만들기 버튼 유지 | PASS |

## 시안 대비 관찰 결과

- S01/S02: 기존 다크 톤·기본 컴포넌트 기준을 유지하고, 미구현 로그인은 비활성화되어 실제 계정이 생성되지 않는다.
- S03: 1-A 디자인에서 구상한 온라인 상태·읽지 않음은 데모 데이터다. Material 3 탭과 메시지 카드 간격은 정적 시안과 완전히 동일하지 않으며 후속 실제 화면 QA에서 대조한다.
- S04: 입력 검증·가입 승인 스위치가 구현되었고, 초기 비활성 CTA가 화면 하단에 위치한다.
- S05/S15: 방장과 일반 멤버를 구분하고, 승인·강퇴·위임 기능을 시연한다. 실제 사용자 권한을 변경하지 않는다.
- 글꼴 200%: 시작 화면 첫 뷰포트에서 설명이 길어져 아래 내용은 스크롤해야 한다. 버튼 도달성과 Android TalkBack/IME 전체 조작은 **별도 QA 대상**이다.
- **확인하지 않은 상태**: 초대 거절/강퇴/만료의 모든 UI를 각각 실기기 촬영하지 않았다. 로컬 상태 화면 코드는 있으나 9종 캡처에 포함되지 않는다.
- **임시 자산**: 동물 아이콘과 최종 앱 아이콘은 아직 확정되지 않았다.

## 보안·범위 한계

- 실제 Google/Kakao 인증, Kakao SDK, App Links, Firestore, Cloud Functions, 서버 권한, 메시지 동기화, 강퇴 및 가입 승인·거절은 미구현.
- 정체 매핑·실시간 게임·점수·시즌 기록은 2-A 데모 데이터이며 실제 서비스가 아니다.
- 에뮬레이터 Android 15(API 35) 결과로 삼성 실기기·폴더블·IME·TalkBack·다른 Android 버전을 검증했다고 판단하지 않는다.
- 디버그 APK는 Play 스토어 업로드용 서명 패키지가 아니다.

## 인수 및 병합 상태

- 2-B의 **Compose UI 구현과 CI·에뮬레이터 확인은 완료**.
- 디자인 [PR #1](https://github.com/hebi10/TalkSullae/pull/1) → 2-A [PR #5](https://github.com/hebi10/TalkSullae/pull/5) → 2-B [PR #7](https://github.com/hebi10/TalkSullae/pull/7) 순서로 검토한다.
- 현재 PR #7은 Draft이며 `main` 미병합. 계정·초대·서버 권한은 후속 별도 PR에서 연결한다.
