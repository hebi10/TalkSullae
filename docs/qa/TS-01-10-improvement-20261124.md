# TS-01~10 개선 및 최종 QA 추적

기준일: 2026-11-24 KST. 개발 배치 기록이며 운영 적용 및 최종 QA 성공 선언이 아니다.

## 작업 현황
| 작업 | 코드/PR | 확인 상태 및 남은 조건 |
| --- | --- | --- |
| TS-01 보안·운영 | [#68](https://github.com/hebi10/TalkSullae/pull/68), [#70](https://github.com/hebi10/TalkSullae/pull/70) | 병합. 캐시 노출 방지, App Check 단계적 적용 및 호출 제한. 운영 프로젝트 등록·정책·TTL·호환성 확인 필요. |
| TS-02 초대 | [#69](https://github.com/hebi10/TalkSullae/pull/69) | 발급/재발급 확인 및 만료 안내 병합. 실제 소유 HTTPS 도메인, assetlinks.json, 설치 전후 초대 복구 검증 미완료. |
| TS-03 채팅 | #68, [#72](https://github.com/hebi10/TalkSullae/pull/72) | 전송 상태·만료 처리 병합. 삭제/암호화 미전송 복구 PR 검증 중. 안 읽은 메시지 읽음 상태 설계·구현 남음. |
| TS-04 캐릭터 | #69, #72 | 8종 선택·프로필·멤버 화면 병합. 채팅 캐릭터 PR 검증 및 구버전 호환성 확인 필요. |
| TS-05 게임 | [#71](https://github.com/hebi10/TalkSullae/pull/71) | 게임 단계/준비/타이머/결과 UX PR 수정 및 CI 재검증 중. 다중 기기 4종 실제 게임 경로 검증 필요. |
| TS-06 기록·방 관리 | #71 | 공동 순위·전적 필터·승률 개선 PR 검증 중. 방 정보 수정·종료 정책 및 친구 간 상세 전적 범위는 후속 보완. |
| TS-07 비용·성능 | #68, #70 | 메시지 만료 표시와 서버 호출 제한 병합. 운영 TTL·사용량·쿼리 비용 검증 필요. |
| TS-08 UI | #71, #72 | UI 코드 보완 진행. 최신 4환경·라이트/다크·키보드·글꼴·폴더블 실기기 검증 필요. |
| TS-09 문서 | 본 문서와 README/개인정보처리방침 예정 변경 안내 | PR 검증 중. 현행 Play Console과 운영 정책 대조는 남음. |
| TS-10 최종 QA | [QA workflow](https://github.com/hebi10/TalkSullae/actions/workflows/qa-2d.yml) | 개선 완료·PR 병합 후 정확한 최종 main SHA로 1회 수동 실행. 아직 미실행. |

## 병합된 PR 검증 근거
- #68: [Android CI](https://github.com/hebi10/TalkSullae/actions/runs/45321550701), [Firebase QA](https://github.com/hebi10/TalkSullae/actions/runs/45321550851)
- #69: [Android CI](https://github.com/hebi10/TalkSullae/actions/runs/45321766607), [Firebase QA](https://github.com/hebi10/TalkSullae/actions/runs/45321766465)
- #70: [Android CI](https://github.com/hebi10/TalkSullae/actions/runs/45321926360), [Firebase QA](https://github.com/hebi10/TalkSullae/actions/runs/45321926259)

## TS-10 완료 조건
1. 열린 PR의 최신 HEAD 검사 성공 및 안전 병합, 미구현 기능 범위 확정.
2. 최종 main 기본 CI 및 동일 SHA의 전체 Android/Firebase QA (API 29/35/36 휴대폰, API36 태블릿). 실패·미실행을 통과로 기록하지 않는다.
3. 채팅 권한 상실·캐시·삭제·신고·중복 요청·암호화 초안·계정 전환을 확인하고, 4종 게임의 참가/재접속/마감/점수·시즌 정산을 검증한다.
4. 한글 UI, 글꼴 200%, 키보드, 다크/라이트, 가로 및 확장 화면 캡처 검토. 실제 폴더블과 실제 Google 계정 다중 기기는 에뮬레이터 결과와 분리한다.
5. 운영 Firebase의 Rules, Functions, App Check 설정, TTL, 인증/알림 및 구버전 클라이언트 호환성을 별도 확인한다. 운영 변경은 별도 사용자 승인 필요.
6. 최신 캡처와 기획 자료·Drive 기록 대조, 남은 결함 기록. TS-11 릴리스는 별도 단계이며 이번 QA에서는 서명 AAB/Play 게시/승격을 실행하지 않는다.

## Play 기록
[2026-11-16 KST 실행](https://github.com/hebi10/TalkSullae/actions/runs/43733390504)은 0.5.0(6) 서명 AAB 내부 게시 API 수락과 비공개 테스트 승격 API 조회를 확인한다. 현재 Play Console 노출, 설치 가능 여부, 이후 트랙 상태는 미확인. 이번 배치에서 신규 Play 배포 및 운영 Firebase 수정 없음.
