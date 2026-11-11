# 톡술래 Google Play Alpha 등록·심사 제출 확인표

확인일: 2026-11-11 · Android 패키지 `com.hebi.talksullae` · 소스 버전 `0.3.0` / 코드 `2`  
기준 GitHub `main`: `1fc7305f1fdc19d0309992d8436cd5e5030bc577`

> **상태 구분:** Play Console 정보는 기존 연결 브라우저 작업 보고(Alpha 출시 초안, 12개 중 5개 완료)를 근거로 했습니다. 이번 세션에서는 Play Console 재조회·수정 권한을 확보하지 못했습니다. GitHub와 Google Drive에서 확인한 내용은 구분해 기록합니다. 이 문서는 **심사 제출 완료 선언이 아닙니다.**

## 1. 확인된 사항

- PR #14가 `main`에 병합되어, 앱의 런처 아이콘·한국어 스토어 문구·대표 이미지·스토어용 실제 Android 6개 화면 캡처가 포함됐습니다.
- GitHub Actions [Android verification #42971515926](https://github.com/hebi10/TalkSullae/actions/runs/42971515926), [PR #14 다환경 QA #42968969307](https://github.com/hebi10/TalkSullae/actions/runs/42968969307)는 성공했습니다.
- Google Drive `91_스토어_자료`에서 스크린샷 6장, 스토어 아이콘, 대표 그래픽 및 등록 자료 ZIP이 확인됩니다.
- Play Console 이전 작업 보고: 톡술래 기존 앱·Alpha 출시 `0.3.0 (2)` 초안 저장, 대한민국·테스터 '본인' 1명, 한국어 등록정보·이미지 저장, 필수 설정 **12개 중 5개 완료**. 이는 **기존 보고 내용**으로 이번 세션에서 콘솔을 다시 검증하지 못했습니다.
- Google Drive `92_서명키_비공개`에는 Firebase `google-services.json`이 있지만, 등록할 Play 고정 업로드 keystore 파일은 확인되지 않았습니다.
- 마지막 GitHub Actions 2-F [설정 진단 #42961973209](https://github.com/hebi10/TalkSullae/actions/runs/42961973209)은 `config_status=blocked`. 이후 Secrets가 추가되었는지는 **최신 실진단 필요**. 이 워크플로는 서명·업로드를 하지 않습니다.

## 2. Google Play 심사 필수 항목 — 상태와 입력 결정

| 항목 | 현재 상태 | 결정 및 처리 |
| --- | --- | --- |
| 스토어 공개 지원 이메일 | **미확정** | 공개 연락처를 사용자에게 확인하고 Play Console에 입력. 개인 이메일을 임의로 선택·공개하지 않음 |
| 판매자/비판매자 상태 | **미확정** | 개발자 계정 보유자가 실제 법적·사업 운영 현황으로 확정. 임의 선택 금지 |
| 앱 액세스(심사 로그인) | **절차 초안 가능 / 실제 심사 계정 미확인** | 온라인 방을 재사용 가능한 Google 테스트 계정으로 접근하도록 준비. 인증 정보는 Play Console **비공개 입력란**에만 제공 |
| 콘텐츠 등급 | **설문 미완료 보고** | 실제 사용자 간 방 이름·설명, 계정, 상호작용 및 데모/게임 상태를 사실대로 답변. 등급을 사전 단정하지 않음 |
| 타깃 연령 | **미확정** | 사용 목적상 성인 대상 권고 검토 가능하지만 단정·저장 금지. 아동 접근 제한 및 연령 확인이 필요한지 평가 |
| 개인정보처리방침 | **공개 정책 URL·최종 방침 미확인** | 실제 데이터 종류·보존·삭제·운영자 공개 연락처를 확정해 접근 가능한 정책 웹페이지 게시, 앱에도 경로 연결 |
| 데이터 보안 | **신고 미완료 보고** | 사용자·계정 데이터, Firebase SDK가 처리하는 데이터 및 삭제·공유·암호화 요건을 실측한 뒤 입력 |

**별도 필수 안전성 작업:** 계정 삭제/UGC(사용자 생성 콘텐츠) 정책 구현은 위 신고와 별개의 **실제 앱 기능 개발**이 필요합니다. Play Console에 '지원'이라고 신고하기 전에 기능과 운영 절차를 완성해야 합니다.

## 3. Play Console 앱 액세스 — 심사 안내 초안 (영문)

다음 텍스트는 **심사 전용 Google 테스트 계정을 준비한 경우에만** Play Console의 App access에 사용합니다. 실제 비밀번호·2FA·복구 정보는 GitHub/Drive 공개 문서에 기록하지 않습니다.

```text
App name: TalkSullae
Package: com.hebi.talksullae

1. Launch the app on an Android device.
2. Tap the Google sign-in option on the onboarding screen.
3. Use the dedicated, reusable reviewer Google account supplied privately in the Play Console "App access" credentials section.
4. From the home screen, open "Online room management (Firebase)".
5. Create a room, generate an invitation code, or join an existing room using the additional reviewer account if provided.
6. You can also enter the local demo screens without signing in. The chat and game preview screens do not provide live multiplayer gameplay in this build.

Note: If Google sign-in requires interactive 2-step verification, provide a reviewer-compatible sign-in method before submission. Do not assume that a personal Google account or expiring one-time code will pass review.
```

**제출 전 확인:** Google 로그인 전 과정이 Play 서명 인증서 등록 후 실제 Play 설치본에서 동작해야 합니다. Google 심사자가 언제든 재사용 가능한 액세스 경로가 필요합니다. 위 문구만으로 앱 액세스 요구사항이 완료되는 것은 아닙니다.

## 4. 데이터 보안 — 소스에 근거한 검토 행렬

| 데이터 항목 | 소스에서 확인한 처리 | Play 신고 최종 판정 |
| --- | --- | --- |
| Firebase Auth 계정 UID | 앱 계정 및 Firestore 문서 경로·권한에 사용 | User IDs 등 해당 항목·목적 검토 |
| Google 계정 표시 이름 | Firebase Auth `displayName`을 온라인 방 화면과 멤버 데이터에 사용 | Personal info / Name 등 검토 |
| Google 계정 이메일 | Firebase Auth를 사용함. Firebase SDK 처리 범위 확인 필요 | Email address 수집 여부·목적 검토 |
| 방 이름·설명 | Firestore `rooms`에 저장, 방 구성원에게 표시 | 앱 활동/사용자 생성 콘텐츠 여부 및 수집·공유 범위 검토 |
| 멤버 표시 이름·상태 | `rooms/{roomId}/members/{uid}`와 `userRooms/{uid}/rooms`에 저장 | 공개 범위 및 처리 목적 검토 |
| 초대 토큰/해시·만료 | 서버에서 초대 코드를 생성하고 해시·만료 등을 관리 | 보안 데이터 처리·보관 기간 검토 |
| Firebase SDK에서 자동 전송하는 데이터 | 의존성: Firebase Auth / Firestore / Functions | 각 SDK의 Data safety 문서와 제출된 번들 실측 후 확정 |

- Play 데이터 보안 신고는 **서버로 전송되는 SDK 수집 정보도 포함**합니다. '데이터 수집하지 않음' 또는 '제3자 공유 없음'을 추측으로 선택하지 않습니다.
- 권한 매니페스트에 `INTERNET`만 보이더라도 네트워크를 통한 데이터 처리가 없다는 뜻은 아닙니다.
- 개인정보처리방침은 실제 계정 생성/프로필/방 데이터/초대·보관 기간/삭제 기능과 일치해야 합니다. 기획서의 90일·7일 보존안은 아직 운영 구현·확정된 정책이 아닙니다.

## 5. 계정 삭제·사용자 생성 콘텐츠 기술 차단사항

현재 설정 화면의 `계정 삭제·신고·차단`은 **안내만 있는 UI**입니다. 다음을 구현하고 직접 검증하기 전까지 Play Console에 '계정 삭제 기능 제공'이라고 신고하면 안 됩니다.

1. 설정 화면에 계정 삭제 요청을 **실제로 시작하는** 진입점. 계정 본인 확인 및 오류·재시도/상태 안내.
2. 외부 공개 웹에서도 계정 삭제를 요청할 수 있는 독립 경로. 앱 제거 후에도 요청 가능해야 함.
3. Firebase Auth 사용자 정보 삭제, `userRooms` 및 멤버 문서 정리, 방장 소유 방의 권한 위임/해제, 초대·표시 이름·데이터 보관 정책 정합성. 실패 중단/재시도와 서버 권한 검사 필요.
4. 이용약관 동의 및 UGC 신고·차단·관리 절차. **방장 강퇴와 사용자 신고는 서로 다른 기능**임을 유의.
5. 개인정보처리방침에 보관·삭제 절차를 실제 구현과 일치시킴.

## 6. AAB 서명·업로드 게이트

- 현 Play Alpha 화면에 `0.3.0 (2)` 출시 **초안**이 있다는 이전 작업 기록이 있습니다. 실제 **서명된 AAB 업로드·검증·심사 제출은 확인되지 않았습니다.**
- GitHub `play-closed.yml` 워크플로는 고정 업로드 서명키·인증서 SHA-256 핀·Firebase 구성·Publisher 인증이 있어야 안전하게 진행합니다.
- 필요한 Secrets: `PLAY_UPLOAD_KEYSTORE_BASE64`, `PLAY_UPLOAD_STORE_PASSWORD`, `PLAY_UPLOAD_KEY_ALIAS`, `PLAY_UPLOAD_KEY_PASSWORD`. 필요하면 `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` 대신 승인된 WIF 방식 사용.
- 필요한 Variables: `PLAY_UPLOAD_CERT_SHA256`, `PLAY_CLOSED_TRACK`(기존 Alpha 트랙의 **실제 API ID**), WIF 사용 시 `PLAY_WORKLOAD_IDENTITY_PROVIDER`, `PLAY_SERVICE_ACCOUNT`.
- 기존 Play에 업로드된 AAB나 업로드 인증서가 있다면 **먼저 일치 확인**. 새 임시 업로드 키 생성이나 GalliPick 등 타 앱의 키 재사용을 임의로 하지 않음.
- 업로드 전 `check`로 기존 앱/트랙/최고 versionCode 조회 → `build`에서 전체 QA·고정 키 서명·번들 검증 → `deploy`는 비공개 테스트로만 제출 → API readback 및 Play Console 심사·테스터 설치 가능 여부 별도 확인.

## 7. 필요한 사용자 확정값 및 완료 정의

- [ ] Play 스토어에 **공개할 지원 이메일**
- [ ] 개발자 계정의 **판매자/비판매자** 실제 상태
- [ ] 톡술래의 **대상 연령대** 및 그에 맞는 앱 내 보호 조치
- [ ] 심사 전용 재사용 가능한 Google 로그인 및 리뷰어 접근 검증
- [ ] 공개 개인정보처리방침 URL(보관/삭제/연락처 포함)
- [ ] Data safety 각 데이터 항목·공유·삭제·암호화 최종 확인
- [ ] 계정 삭제와 UGC 신고·차단 기능 및 운영 절차
- [ ] 톡술래 고정 업로드 keystore·인증서·Publisher API 권한 등록
- [ ] **서명 AAB 실제 업로드 → 심사 제출 → 비공개 테스터 설치** 확인

참고 공식 정책:
- https://support.google.com/googleplay/android-developer/answer/9859455
- https://support.google.com/googleplay/android-developer/answer/15748846
- https://support.google.com/googleplay/android-developer/answer/13327111
- https://support.google.com/googleplay/android-developer/answer/9876937
- https://support.google.com/googleplay/android-developer/answer/10787469
