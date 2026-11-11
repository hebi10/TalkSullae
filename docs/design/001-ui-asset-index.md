# 톡술래 1-A — 화면별 디자인 시안 및 검수 기록

- 작성일: 2026-11-10
- 진행 단계: **1-A 실제 디자인 시안 제작 완료 / 1-B 화면별 상세 검수 예정**
- 원본 폴더: [Google Drive · 001 톡술래 디자인 시안](https://drive.google.com/drive/folders/11Ia3hy9LTowCC-JvGRfDi-cK_n_xY5fe)
- 전체 미리보기: [00 전체 24종 이미지](https://drive.google.com/file/d/1eYkrxzr_jkYJ26fF5reQ49CbmGHPkob_/view)
- 화면 설계서: [Google Docs v1.0](https://docs.google.com/document/d/1d7A0zC76o42YfEy2HtHzU9IK4HYRJeQEIFr8x1zc9Ps/edit)
- 정적 HTML 프리뷰 소스: [design-prototype/index.html](../../design-prototype/index.html)
- 이 문서가 기록한 결과는 **구현 앱 캡처가 아닌 디자인 시안**입니다. UI 버튼·네트워크·가입·게임 판정은 구현되어 있지 않습니다.

## 1. 결과

- 기본 S01~S18 및 공통 상태·폴더블·게임별 변형 24종(다크 22종, 폴더블 1종, 라이트 1종)의 PNG를 개별 파일로 저장했습니다.
- 00_전체시안_미리보기.png 인덱스 이미지를 별도로 저장했습니다.
- 실제 앱 코드가 없는 상태에서 디자인 명세에 맞추어 **정적 HTML/CSS 화면을 렌더링한 시안**으로, 각 텍스트·점수·날짜는 시연용 예시 값입니다.
- 대표 익명 게임은 실제 이름 숨김·본인 별칭 표시·정체 매칭·승패/점수 분리 원칙을 적용했습니다.
- 전체 폴더의 원본 파일명은 01~24 순서를 유지하고 GitHub에는 디자인 생성용 HTML 프리뷰 소스와 링크를 기록합니다.

## 2. 개별 이미지

| 순서 | 화면 | Google Drive PNG |
|---:|---|---|
| 01 | S01 시작 | [01_S01_시작.png](https://drive.google.com/file/d/1Jx5Op-TvPK1bN4lWCeRvvn06z-xDGJ5I/view?usp=drivesdk) |
| 02 | S02 로그인 온보딩 | [02_S02_로그인_온보딩.png](https://drive.google.com/file/d/15suIMmusmQ2XelVMxDGhtMgZvL6utU8W/view?usp=drivesdk) |
| 03 | S03 내방목록 | [03_S03_내방목록.png](https://drive.google.com/file/d/1yUmiz1CKhekMSV-VJAy_vPtqYDwsFyWC/view?usp=drivesdk) |
| 04 | S04 방생성 | [04_S04_방생성.png](https://drive.google.com/file/d/180k4wwXt7OapE9vc6MGyG9i-w1sYUmoU/view?usp=drivesdk) |
| 05 | S05 초대 가입승인 | [05_S05_초대_가입승인.png](https://drive.google.com/file/d/1hAb572BzSqPPYjta_3UAZ9OwKzf1X0Wp/view?usp=drivesdk) |
| 06 | S06 방공통화면 | [06_S06_방공통화면.png](https://drive.google.com/file/d/1N9p96HFUieUrBJ8mj4yDR-kxmyvKfHa2/view?usp=drivesdk) |
| 07 | S07 일반채팅 | [07_S07_일반채팅.png](https://drive.google.com/file/d/1Zlr8pMCTRneD08lyLX6DYO2JU883QnN5/view?usp=drivesdk) |
| 08 | S08 게임선택 | [08_S08_게임선택.png](https://drive.google.com/file/d/12YwxaFtKv803Q5BX9hsfGVyAdgdZuF34/view?usp=drivesdk) |
| 09 | S09 대기실 | [09_S09_대기실.png](https://drive.google.com/file/d/15bNtFTkm-Fa6Mm7iY5Dg1L8yxzjmL9vO/view?usp=drivesdk) |
| 10 | S10 너누구야 익명채팅 | [10_S10_너누구야_익명채팅.png](https://drive.google.com/file/d/1rDxX43IH7MedcDT46Z9rZ_k7AvP4XGiZ/view?usp=drivesdk) |
| 11 | S11 정체추리 | [11_S11_정체추리.png](https://drive.google.com/file/d/15k_CWNl93Lc8IDRqHyx_omlozqhons62/view?usp=drivesdk) |
| 12 | S12 결과공개 | [12_S12_결과공개.png](https://drive.google.com/file/d/1Ew_6LMu0pDjPLcYL5ldh68A05HwyZrkX/view?usp=drivesdk) |
| 13 | S13 방랭킹 | [13_S13_방랭킹.png](https://drive.google.com/file/d/1CckMnacsMiuGnZKA2hMEBi88D11m01pK/view?usp=drivesdk) |
| 14 | S14 방연대기 | [14_S14_방연대기.png](https://drive.google.com/file/d/1N0AO2sHBflGzcfmQWKJ51x12Yw5qVWGF/view?usp=drivesdk) |
| 15 | S15 멤버관리 | [15_S15_멤버관리.png](https://drive.google.com/file/d/1uRhLgZid-lfrQfHVxwgCd0ifhfZkNqGP/view?usp=drivesdk) |
| 16 | S16 프로필 업적 | [16_S16_프로필_업적.png](https://drive.google.com/file/d/1sWfMscNdEnm9_Qkc_GFxRuxCNB-wNrOO/view?usp=drivesdk) |
| 17 | S17 설정 | [17_S17_설정.png](https://drive.google.com/file/d/16W4E1ydb_QBqi7d9YbFJT_1Ciwvm_S6J/view?usp=drivesdk) |
| 18 | S18 신고차단 | [18_S18_신고차단.png](https://drive.google.com/file/d/1Fg7wsgH1PkOL-cJEfAEEpu3uZxFQpT1T/view?usp=drivesdk) |
| 19 | 공통상태 | [19_공통상태.png](https://drive.google.com/file/d/1XJJnT0yHFZskukEFOd-QwmvUncGzEnEA/view?usp=drivesdk) |
| 20 | 폴더블 확장 | [20_폴더블_확장.png](https://drive.google.com/file/d/1FQi6TAsiO_uPXI7vxlO7UMSaVAgQaCvn/view?usp=drivesdk) |
| 21 | 라이어채팅 | [21_라이어채팅.png](https://drive.google.com/file/d/12G1Ub_6yxVJz5_eoWJdViRfQIr1pCWfG/view?usp=drivesdk) |
| 22 | 금지어채팅 | [22_금지어채팅.png](https://drive.google.com/file/d/1eMUZHQzWZo7I7NnYyEURHA5jphFerO95/view?usp=drivesdk) |
| 23 | 밸런스예측 | [23_밸런스예측.png](https://drive.google.com/file/d/1PbKSh00l_1zSbPL82KEtzaHN0ZnnP4oW/view?usp=drivesdk) |
| 24 | 라이트모드 방채팅 | [24_라이트모드_방채팅.png](https://drive.google.com/file/d/1Gi4j_5eIrKrFOalGom6fevTmFeCOR7b9/view?usp=drivesdk) |

## 3. 디자인 시스템 검토

| 항목 | 채택한 기준 |
|---|---|
| 기본 배경 | #101114 |
| 기본 카드 | #1C1E22 |
| 서브 카드 | #282A30 |
| 구분선 | #3A3D44 |
| 주요 텍스트 | #F4F4F5 |
| 보조 텍스트 | #A7A9B0 |
| 강조 CTA | 밝은 배경·어두운 글자(다크) / 어두운 배경·밝은 글자(라이트) |
| 상태색 | 성공·경고·오류 의미가 필요한 경우만 |
| 간격 | 4/8/12/16/24/32dp 중심 |
| 폰트 | Android 한글 시스템 글꼴 계열, 제목 22~24sp, 본문 15~16sp |
| 버튼 | 최소 48dp 목표, 키보드/시스템 UI inset 준수 |
| 카드 표현 | 그림자·그라데이션 없이 대비·정렬 중심, 필요 최소 곡률 |

## 4. 화면 범위

- S01~S05: 시작, 가입, 방 목록, 방 생성, 초대·승인.
- S06~S08: 방 셸, 일반 채팅, 게임 목록.
- S09~S12: 대기실, 익명 채팅, 정체 추리, 결과 공개.
- S13~S18: 방 랭킹·연대기, 멤버 권한, 프로필, 설정, 신고·차단.
- 확장: 공통 빈 상태·오류, 폴더블 2열, 라이어 채팅, 금지어 채팅, 밸런스 예측, 라이트 모드.

## 5. 1-B 필수 검수·수정 후보

1. **채팅 스크롤/키보드:** 한국어 조합 중 입력 영역이 가려지지 않는지 Android Compose 실화면에서 확인할 것. 정적 시안은 실제 IME 동작을 검증하지 않음.
2. **익명 무결성:** 익명 게임 중 실제 프로필·닉네임·알림·접근성 레이블을 통해 정체가 노출되지 않도록 구현·보안 규칙 테스트가 필요함.
3. **게임 결과 산식:** 정체 추리·라이어·금지어는 점수·승패 산식이 다름. UI 예시 점수는 운영 데이터가 아니며 서버 정산 규칙 확정 후 수정해야 함.
4. **문구 점검:** 안내 문구/정답·오답/라운드/대화 주제를 게임마다 분리. 일부 시안은 화면 내 실제 기능 미구현 상태의 정적 텍스트임.
5. **버튼·상태 다양화:** 비활성/로딩/실패/중복 제출, 입장 거절·강퇴, 재접속 중, 게임 취소 상태를 별도 상태 시안으로 보완.
6. **라이트 모드:** 다크 테마 대비 별도 컬러 토큰과 텍스트 대비를 실측 검토.
7. **폴더블:** 600dp 이상 너비의 분할/메시지 최대 폭은 설계 수준이며 실제 듀얼 화면/접힘 기기 검증이 필요함.
8. **아이콘 원본:** 이 화면들은 임시 동물 이모지·단순 심볼을 쓰므로 앱 아이콘/가면 캐릭터 최종 에셋 확정 필요.
9. **다중 크기:** 360/393/412dp와 200% 폰트의 실제 잘림·세로 스크롤을 Compose 화면에서 검증.
10. **메시지 보관·신고:** 일반 채팅·익명 게임 채팅·신고 자료의 공개 범위와 삭제 정책을 확정한 뒤 화면 문구에 반영.

## 6. GitHub 작업/버전 관리

- 프리뷰 소스: `design-prototype/index.html`. 브라우저에서 로컬 HTML로 열어 `?screen=S01` ~ `?screen=S24` 형태로 화면 선택. 코드 실제 구현 아님.
- 디자인 단계 문서: `docs/design/001-full-screen-ux-plan.md` (서비스 정책 기준), 본 파일 (시각 시안 인덱스).
- 실제 Android 코드/Firebase/CI/Play 배포는 **미진행**. 차기 단계에서 GalliPick식 PR→CI→화면 캡처 검증을 적용.
- 1-A 화면 시안 생성 완료 후, 1-B 단계에서 보완 항목을 UI 구체 수정 작업으로 분리.
