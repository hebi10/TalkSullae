# 톡술래 001 — 1-C 초대·예외 상태 및 접근성 보완 설계 v1.2

- 작성일: 2026-11-10 / 단계: 1-C 정적 UI 설계·이미지 제작
- 기준 기획: [톡술래 서비스 v1.0](https://docs.google.com/document/d/13BMHPicBwI1kN1XrPsjHYfR4wkXmBz4lUDzcPecptvg/edit)
- 이전 단계: [1-B 전체 화면 상세 검수](https://docs.google.com/document/d/1c9e5wLXvCrbwc6lNGqVjACKsfD54Je8vosWO86I56dA/edit)
- 시안 폴더: [1-C 화면 PNG 25종 + 소형·폴더블 검수](https://drive.google.com/drive/folders/15dP-smrSiL03mGxw4tdZmLt6Cf2-ukk4)
- 전체 미리보기: [25개 예외 상태](https://drive.google.com/file/d/1UX1uAVCgny7p50R3JSMx6vYaL38MZA9M/view)
- 정적 HTML 프리뷰: [states-1c.html](../../design-prototype/states-1c.html) · 쿼리 `?state=C01`부터 `?state=C25`까지
- 자동 검수 코드: [scripts/verify_1c_layout.py](../../scripts/verify_1c_layout.py)
- **범위:** 정적 시안 및 UI 문구·상태 분기. 실제 Android 기능, 계정 데이터 처리, Firebase 동작, TalkBack은 구현·검증되지 않음.

## 1. 원칙과 상태 분리

1. **가입 전·가입 승인 대기·거절·강퇴·초대 만료를 서로 다른 상태로 표현한다.** 승인 전의 방 채팅·게임 결과는 숨긴다.
2. **안전 조작은 영향 범위를 설명한다.** 강퇴는 방장 권한, 차단은 개인에게 보이는 메시지·알림 설정이며 서로 다른 개념이다.
3. **계정 삭제는 최종 확인을 분리한다.** 삭제 데이터, 방장 소유 방 처리, 삭제 확정 여부를 명시한다. 실제 법적·기술적 데이터 보존 범위는 미확정으로 표시한다.
4. **게임 상태는 서버 판정 전 결과를 확정처럼 표현하지 않는다.** 라운드 취소·정산 지연·재접속 중은 별도 메시지로 안내한다.
5. **게임 방장과 일반 참가자의 버튼을 분리한다.** 준비 4/5일 때 '시작 가능' 표시 금지; 일반 참가자는 준비 완료·취소만 허용한다.
6. **익명 정보는 결과 공개 전 실제 사용자와 연결하지 않는다.** 재접속 화면에서 내 별칭만 공개하고 타인의 정체는 서버에만 보관한다.
7. **정체 매칭은 완성도 검사를 우선한다.** 본인 제외, 남은 참가자 일대일 매칭, 중복 후보 검출, 제출 완료 후 잠금, 시간 초과 상태 분리.
8. **밸런스 예측은 내 선택·예측·다른 사람의 응답·마감 결과를 분리한다.** 제출 전 친구의 선택 비율/실명 답을 공개하지 않는다.
9. **확대 글꼴과 폴더블 화면에서 내용이 잘리면 스크롤한다.** 고정 하단 CTA를 남기고, 키보드·TalkBack은 Compose 구현 뒤 검증한다.

## 2. 화면별 시안 목록

| 시안 ID | 원본 화면 | 상태 | 안내 성격 | 기본 동작 | 파일 |
|---|---|---|---|---|---|
| C01 | S05 | 초대받은 사람 | 가입 전 | Google 로그인 후 참여 요청 | [PNG](https://drive.google.com/file/d/1jouLKH6vd_bqdZ7C83UcdozvZxgavGug/view?usp=drivesdk) |
| C02 | S05 | 가입 승인 대기 | 승인 대기 | 내 방으로 이동 | [PNG](https://drive.google.com/file/d/10MG5kvYADIbEaknIpWOGROe9b9bl0zpl/view?usp=drivesdk) |
| C03 | S05 | 가입 거절 안내 | 참여 불가 | 내 방으로 이동 | [PNG](https://drive.google.com/file/d/1tWHv50ARND_wcmleCC5e9Bw4h_A9Yr7X/view?usp=drivesdk) |
| C04 | S05 | 강퇴 안내 | 접근 제한 | 내 방으로 돌아가기 | [PNG](https://drive.google.com/file/d/1nRRU5Ie80vk7UatTkRiH4zJUppbwbzkJ/view?usp=drivesdk) |
| C05 | S05 | 만료된 초대 링크 | 만료됨 | 새 코드 입력 | [PNG](https://drive.google.com/file/d/1w4e7tuTk4_Njuq4bc6Lx-EqwltvR4P01/view?usp=drivesdk) |
| C06 | S17 | 계정 삭제 안내 | 계정 설정 | 삭제 절차 계속 | [PNG](https://drive.google.com/file/d/1s6sTEuEjsdikHzbTSZ5sJFqcA4NYaCE6/view?usp=drivesdk) |
| C07 | S17 | 계정 삭제 최종 확인 | 최종 확인 | 계정 영구 삭제 | [PNG](https://drive.google.com/file/d/1CW0Grm_MKUg2AOPER5TwnUL1gGy2c1PY/view?usp=drivesdk) |
| C08 | S18 | 신고 접수 완료 | 신고 접수 | 채팅으로 돌아가기 | [PNG](https://drive.google.com/file/d/15XF3rk9CWFzxRkVHBRlu5qBqg1Tkz9F3/view?usp=drivesdk) |
| C09 | S18 | 중복 신고 안내 | 이미 접수됨 | 확인 | [PNG](https://drive.google.com/file/d/1IomF0DjV_1-vuTzpjnSwVO89It9ujT5e/view?usp=drivesdk) |
| C10 | S18 | 사용자 차단 확인 | 차단 확인 | 사용자 차단 | [PNG](https://drive.google.com/file/d/1tEi3zzzlJroi0jG8-Teh1ldFCFq2OA2k/view?usp=drivesdk) |
| C11 | S19 | 게임 이탈 확인 | 진행 중 | 게임 계속하기 | [PNG](https://drive.google.com/file/d/1wwrzz9HmhckIv2WSSslfwBuSt-YU4glU/view?usp=drivesdk) |
| C12 | S19 | 게임 취소 상태 | 게임 취소 | 방 채팅으로 돌아가기 | [PNG](https://drive.google.com/file/d/1yrgkMTHOajfPApCedcFZa3cp8H_qCiu6/view?usp=drivesdk) |
| C13 | S19 | 결과 정산 지연 | 정산 진행 | 결과 다시 확인 | [PNG](https://drive.google.com/file/d/1dlMpkHfRDD7WhiIx0kg7cxUuOh_xuQby/view?usp=drivesdk) |
| C14 | S19 | 게임 재접속 | 재연결 | 게임으로 복귀 | [PNG](https://drive.google.com/file/d/1njItxMjaG6hWQLQx0UNjGG6fa5u5M6T1/view?usp=drivesdk) |
| C15 | S09 | 방장 대기실 | 방장 화면 | 게임 시작 (준비 대기) | [PNG](https://drive.google.com/file/d/1ytCpS5QDnviQJMVWmvJMzwdj63u9IgtI/view?usp=drivesdk) |
| C16 | S09 | 참가자 대기실 | 일반 참가자 | 준비 취소 | [PNG](https://drive.google.com/file/d/15rfzbj9B6EEEEgbIbS83j7G52mMVLQzk/view?usp=drivesdk) |
| C17 | S11 | 정체 추리 미완료 | 선택 필요 | 추리 제출 (비활성) | [PNG](https://drive.google.com/file/d/1gOTM_sOQ7mVbfDxB5B_bu3XhM6W-H2RW/view?usp=drivesdk) |
| C18 | S11 | 정체 추리 중복 | 선택 오류 | 중복 선택 수정 | [PNG](https://drive.google.com/file/d/12_fkOgt3kOp8jpf5lvvNa0vY0PMNP38x/view?usp=drivesdk) |
| C19 | S11 | 정체 추리 제출 완료 | 투표 완료 | 결과 대기 | [PNG](https://drive.google.com/file/d/1lDSm54RfXQSwQkdjt97FKVWNmYPq5g0d/view?usp=drivesdk) |
| C20 | S11 | 정체 추리 마감 | 시간 종료 | 결과 확인 | [PNG](https://drive.google.com/file/d/1pgyMW92I-WtnlMPwV7MHrHGfwRNWIkD9/view?usp=drivesdk) |
| C21 | S23 | 밸런스 선택 대기 | 선택 중 | 예측 선택하고 제출 | [PNG](https://drive.google.com/file/d/1olW-QWSTTIrFZg0R_BfgVFwwcwuvLPQJ/view?usp=drivesdk) |
| C22 | S23 | 밸런스 제출 완료 | 완료 | 방으로 돌아가기 | [PNG](https://drive.google.com/file/d/1O6swt9-L9CQaFqhXkdAzEYrZR7YQAJuz/view?usp=drivesdk) |
| C23 | S23 | 밸런스 결과 | 결과 공개 | 방으로 돌아가기 | [PNG](https://drive.google.com/file/d/1aikAVc2yf0McYvkyrrWFPuQIDm2VEeqN/view?usp=drivesdk) |
| C24 | 공통 | 글자 크기 200% | 접근성 검토 | 설정 확인 | [PNG](https://drive.google.com/file/d/1Kdsle0BVbpEVecNOjVTmUhA_0R_s09vB/view?usp=drivesdk) |
| C25 | 공통 | 폴더블 확장 상태 | 600dp 이상 | 게임으로 이동 | [PNG](https://drive.google.com/file/d/1q_7xfaHYd7p6wTtYjjxnBGUZtPGSMAj8/view?usp=drivesdk) |

## 3. 그룹별 UX 인수 기준

### 3.1 초대·멤버 자격 (C01~C05)
- 가입 전 C01은 방 이름·멤버 수·방장·유효 시간만 공개. 초대된 사용자가 현재 채팅 내용을 보거나 무단으로 방에 참여할 수 없어야 함.
- C02 대기 중은 상태·취소 동작 제공. 알림 권한이 없더라도 '내 방'에서 재확인 가능해야 함.
- C03 거절과 C04 강퇴는 같은 '연결 오류'로 합치지 않는다. 강퇴 후 기존 링크 재사용 방지.
- C05 만료·무효 코드는 새 코드 입력 또는 새 링크 요청을 권장. 재발급 권한은 방장만 소유.

### 3.2 계정 삭제·신고·차단 (C06~C10)
- C06 영향 범위 → C07 최종 확인 순서. 실제 삭제는 계정 인증·방장 위임·진행 중 경기 처리 조건 충족 후 서버에서 수행.
- 화면에 입력된 '삭제'는 예시이며 실제 구현에서는 사용자가 입력·검증한 후 버튼 활성화.
- 신고 C08은 접수 완료를 서버 확인 후에만 표시; C09 동일 메시지 중복 요청은 기접수 정보 표시.
- C10 차단은 대화 숨김/알림 조정 범위이며, 방장 강퇴·서버 차단과 구별. 실제 차단 대상과 영향 범위는 정책 확정 필요.
- 신고자는 신고 대상에 드러나지 않아야 하며 처리 상태는 신고자 본인에게만 공개.

### 3.3 게임 취소·재접속·방장 권한 (C11~C16)
- 게임 나가기 확인(C11)은 실수 방지용. 이탈 시 점수 계산·기권 정책은 서버 규칙에 따름.
- 인원 부족 취소(C12)는 기록·보상 확정과 구분. 이미 기록된 다른 게임 결과는 보존.
- 정산 지연(C13)에서는 잠정 승리 점수·시즌 점수를 확정 표시하지 않는다.
- 재접속(C14)은 같은 익명 별칭/비밀 역할을 사용하고 다른 참가자의 매핑을 노출하지 않는다.
- 대기실의 방장(C15)과 일반 참가자(C16)는 다른 CTA/권한을 갖는다. 상태 변경은 항상 서버 검증.

### 3.4 정체 추리·밸런스 (C17~C23)
- C17 미완료: 완료 인원 표시, 미선택 행 구분, 제출 비활성화.
- C18 중복: 한 후보가 두 익명 프로필과 연결되지 않도록 오류 행 분리, 수정 가능한 동선.
- C19 제출 완료: 선택 잠금, 결과 대기, 다른 사람의 추리 내역 비공개.
- C20 마감: 제출 불가. 미제출/기권 처리 및 점수는 룰 확정 이후 테스트할 것.
- C21~C23: 내 선택(A/B)·친구 예측·마감·정답·점수를 분리. 결과는 서버에서 자동 기록하므로 수동 저장 CTA 사용하지 않음.

### 3.5 접근성·폴더블 (C24~C25)
- 200% 텍스트 시뮬레이션은 시각적 참고. 실제 Android fontScale·TalkBack과 동일하지 않다.
- 버튼 높이: 최소 48dp 목표. 정적 프리뷰 px 검사는 실제 dp와 다르다.
- 600dp 이상에서 좌측 메뉴와 우측 정보 패널. 599px 이하에서는 메뉴를 접어 한 열로 구성한다.
- 긴 본문을 스크롤해도 주요 버튼에 닿을 수 있어야 한다. 시스템 IME·화면 회전 검증은 실제 앱에서 수행.

## 4. 디자인 시스템

- 배경 #101114, 카드 #1D1F23, 보조 표면 #272A30, 기본 글자 #F4F4F5, 보조 글자 #ADB0B7.
- 상태 구분은 텍스트·기호 우선, 오류/안전 조작에 한정한 의미색. 실제 접근성 대비 검사는 별도 수행.
- 기본 버튼·보조 버튼은 48px 이상으로 렌더링; Kotlin 구현에서 dp 적용.
- 과도한 그림자·그라데이션·마우스 호버 효과·특정 한쪽 방향만 있는 경계선은 기본 사용하지 않음.
- 임시 아이콘은 문자 심볼이며, 앱 아이콘·동물 아바타 최종 원본으로 승인된 것이 아니다.

## 5. 정적 브라우저 검사 (2026-11-10)

- 대상: **25개 상태 × 화면 4개 크기 = 총 100개 조합**
- 크기: 360×740, 393×852, 414×896, 600×820 CSS px.
- 검사 항목: 가로 오버플로, 고정 하단 CTA/제스처 영역 겹침, 버튼 높이(47.5px 미만), 버튼 텍스트 잘림, 최대 스크롤 후 설명 패널 접근, 빈 렌더링.
- **결과: 검출 항목 0개.** 360×740은 25개 중 24개 화면에서 본문 스크롤 필요(정상).
- 주의: Playwright·Chromium 정적 검사이다. 실제 Android 플랫폼 UI·Firebase/로그인/알림/게임 타이머/200% fontScale/TalkBack은 검사 대상이 아니다.

## 6. 남은 의사결정·구현 제약

- 게임별 이탈·기권·동점·종료·점수·시즌 산식은 사용자 경험 검증 후 확정할 것.
- 계정 삭제·채팅 보관 기간·탈퇴자의 게임 기록 정책은 개인정보처리방침·백엔드 계약과 일치시킬 것.
- 차단/신고 운영 범위와 서버 데이터 접근 권한을 확정해야 함.
- 동물 아바타와 브랜드 로고는 이미지 후보 제작 후 별도 승인 필요.
- 시안의 클릭 요소는 상태 표시용이며 기능성 버튼이 아니다. 실제 Compose 전환 시 semantics/role/disabled/keyboard/IME 설계 필수.
- 다음 작업: 2-A 공통 Compose 프로젝트 및 UI 구현 준비, 이후 S05/S09/S11/S17/S18 우선 구현·실기기 QA.
