# 톡술래 Google Play 최초 등록

## 앱 정보

| 항목 | 값 |
| --- | --- |
| 이름 | 톡술래 |
| 패키지 | `com.hebi.talksullae` |
| 기본 언어 | 한국어 `ko-KR` |
| 유형 및 가격 | 앱 · 무료 |
| 카테고리 | 소셜 |
| 배포 대상 | 대한민국 비공개 테스트 |
| 소스 버전 | `0.3.0` · 버전 코드 `2` |

## 스토어 자료

- [`store/google-play/ko-KR`](../../store/google-play/ko-KR)의 이름·설명과 [출시 노트](../../play/release-notes/ko-KR.txt)를 사용합니다.
- 앱 아이콘은 512×512 PNG, 대표 그래픽은 1024×500 RGB PNG입니다. 기존 앱의 말풍선 모양과 무채색 테마를 사용했습니다.
- Android API 36 휴대전화의 `talksullae-play-store-captures` 아티팩트에 1080×1920 JPEG 화면 6장을 생성합니다. 실제 Android 화면을 가상의 방 데이터로 캡처합니다. 로그인 성공과 운영 Firebase 연결은 기기 2대에서 확인합니다.
- 스토어에서 사용하는 테스트 화면의 닉네임과 초대 코드에는 가상의 값을 사용합니다. 온라인 방을 홍보하는 화면에 로컬 게임 데모를 섞지 않습니다.

## 앱 콘텐츠 선언 검토

| 항목 | 현재 소스 근거 및 남은 확인 |
| --- | --- |
| 광고 | 앱의 의존성과 매니페스트에 광고 SDK가 없습니다. 제출 빌드의 병합 매니페스트와 의존성도 확인합니다. |
| 앱 액세스 | 시작하기 → Google 계정으로 로그인 → 온라인 방. 심사용 계정과 재사용 가능한 접속 절차를 Play Console 비공개 입력란에 등록합니다. 데모로 시작하면 계정 없이 데모를 볼 수 있습니다. |
| 데이터 보안 | Firebase Auth의 계정 식별자·이름·이메일, 방 이름·설명·가입 및 멤버 상태, Firebase SDK의 처리 항목을 제출 빌드 기준으로 확인합니다. 데이터 유형별 목적, 필수 여부, 공유 여부, 보관 및 삭제 근거를 확정합니다. |
| 개인정보처리방침 | 실제 운영 및 데이터 처리에 맞춘 공개 웹페이지를 게시하고 앱 내부에서 열 수 있게 연결합니다. |
| 계정 삭제 | 앱 내 삭제 요청 시작 경로와 공개 웹 요청 경로, Firebase Auth 및 연관 데이터 삭제 처리를 검증합니다. 현재 설정 화면은 안내만 표시합니다. |
| 사용자 생성 콘텐츠 | 방 이름·설명·표시 이름의 노출 범위를 확인하고, 이용약관 동의, 인앱 신고·운영 처리와 필요한 차단 동작을 검증합니다. 방장의 강퇴는 신고 접수 기능과 별개입니다. |
| 콘텐츠 등급 및 타깃 연령 | 소셜 상호작용, 계정, 데모에서 접근 가능한 실제 콘텐츠를 기준으로 설문에 답합니다. 연령과 등급은 설문 및 운영 결정 후 확정합니다. |

## 최초 등록 및 릴리스

1. Play Console에 앱을 생성하고 스토어 자료를 저장합니다. 개인정보처리방침, 계정 삭제 URL과 필수 선언을 제출 빌드에 맞게 등록합니다.
2. 대상 비공개 트랙과 국가·테스터를 확인합니다. 톡술래 전용 고정 업로드 키 및 서비스 계정에 앱 단위 테스트 출시 권한을 설정합니다.
3. [2-F 릴리스 절차](002f-play-closed-readiness.md)의 `build`로 서명 AAB를 검증합니다. 최초 앱 등록이나 업로드에 Console 절차가 필요한 경우 Play Console에서 같은 비공개 트랙으로 처리합니다.
4. 첫 AAB가 이미 Play에 업로드된 경우 콘솔에서 기존 파일을 선택합니다. 자동 배포에서 새 파일을 업로드할 때는 사용된 모든 코드보다 높은 버전을 준비합니다.
5. Play App Signing에서 내려받는 **앱 서명 인증서**의 SHA-1·SHA-256을 Firebase Android 앱에 등록합니다. Play 설치본으로 Google 로그인, 방 생성, 초대, 가입 승인, 멤버 위임·강퇴·탈퇴를 두 계정으로 확인합니다.
6. 이후 GitHub Actions `check → build → deploy`와 Play 트랙 재조회로 업데이트를 검증합니다. 테스터 설치는 Play Console의 게시 상태와 참여 링크에서 확인합니다.

## Google Play 공식 안내

- [앱 생성 및 설정](https://support.google.com/googleplay/android-developer/answer/9859152)
- [스토어 이미지 규격](https://support.google.com/googleplay/android-developer/answer/9866151)
- [사용자 데이터 및 계정 삭제](https://support.google.com/googleplay/android-developer/answer/10144311)
- [사용자 생성 콘텐츠](https://support.google.com/googleplay/android-developer/answer/9876937)
- [Google Play Developer API 액세스](https://developers.google.com/android-publisher/getting_started)
