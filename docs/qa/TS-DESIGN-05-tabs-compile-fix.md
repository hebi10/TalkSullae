# TS-DESIGN-05 — 4차 디자인 보정 컴파일 수정 (2026-11-25)

## 실패 근거
- HEAD 411b3e81538d62935a5893f157491db68d5be419, [PR #96](https://github.com/hebi10/TalkSullae/pull/96).
- [Android CI](https://github.com/hebi10/TalkSullae/actions/runs/45542309489), [2-D QA](https://github.com/hebi10/TalkSullae/actions/runs/45542309354), [native capture](https://github.com/hebi10/TalkSullae/actions/runs/45542309353) 모두 compileDebugKotlin에서 실패.
- 공통 원인: OnlineRoomScreens.kt:526. LiveRoomSectionTabs의 마지막 매개변수가 includeMembers:Boolean이 되면서 기존 trailing lambda가 onSelect에 전달되지 않음. 세 빌드의 컴파일 로그에서 동일 오류를 확인.
- 수정: 기존 방 관리 호출에 current, onSelect를 명시하여 기존 네 탭과 선택 동작을 유지. 컴팩트 방 화면의 세 탭/상단 멤버 접근은 변경하지 않음.
- Firebase 보안 검사 및 OPS-07/OPS-09/LIVE-08은 해당 실패 SHA에서 성공. 새 SHA 검증은 별도로 필요.
- 4차 수정의 실제 PNG는 빌드 실패로 생성되지 않았다. 3차 정상 17장은 이전 기준 SHA d9c5b9de의 증빙이며 최신 화면으로 오인하지 않는다.
- 새 HEAD의 Android/통합 CI 및 17개 정상 캡처의 원본 비교가 끝나기 전 병합하지 않는다. 운영 Firebase/Google Play 변경 없음.
