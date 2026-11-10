# 2-A — Android 프로젝트·공통 Compose UI 기반

## 상태
- 2026-11-10 · UI 데모 구현. 실제 서비스·Firebase·Play 등록 미진행.
- 1-A~1-C 디자인 명세와 검수 자료는 [Google Drive](https://drive.google.com/drive/folders/1XskLt9niOOPdtVxVUO5eXmFMXX9S8qNI).
- 디자인 PR #1이 아직 `main`에 병합되지 않았으므로 이 구현 PR은 디자인 브랜치를 기준으로 하는 **스택형 PR**이다.

## 기술 결정
- Kotlin 2.2.21, AGP 8.13.2, Gradle Wrapper 8.13, Java 17, Compose BOM 2025.10.01
- `compileSdk/targetSdk=36`, `minSdk=26`, `applicationId=com.hebi.talksullae`
- 다크/라이트 공통 테마, 48dp 이상 기본 조작 영역, 16sp 기본 본문, `imePadding` 적용
- 화면 전환용 `rememberSaveable` 로컬 상태. 별도 내비게이션 라이브러리·의존성 도입 보류.
- 고정된 예시 데이터는 `DemoCatalog`; 실제 접속 상태·온라인 메시지·투표·점수로 오인하지 않도록 각 화면에서 '데모'로 안내.

## 소스
- `MainActivity.kt`: Android 진입, edge-to-edge, Compose 컨텐츠
- `design/TalkSullaeTheme.kt`: 라이트/다크 팔레트, 글자 크기·간격 기준
- `design/UiComponents.kt`: 상단 바·공통 버튼·방/게임 카드·채팅 말풍선·안내
- `ui/TalkSullaeApp.kt`: 내 방/기록/마이, 방 채팅/게임/랭킹, 대기실→익명 채팅→정체 추리→결과 UI 데모
- `data/DemoCatalog.kt`: 기기 내 샘플 데이터
- `app/src/test`: 샘플 데이터 일관성 단위 검사
- `app/src/androidTest`: Android 계측에서 탐색 확인 (CI 실행은 추후 구성)
- `.github/workflows/android.yml`: 디버그 단위 테스트·Lint·APK 빌드

## 실행·검증
Windows PowerShell: `./gradlew.bat testDebugUnitTest lintDebug assembleDebug`
Linux/macOS: `./gradlew testDebugUnitTest lintDebug assembleDebug`
CI는 Android SDK 36을 설치하며 테스트 결과·APK를 artifact로 보관한다. **CI 성공 여부를 확인하기 전에는 빌드 통과를 주장하지 않는다.**

## 범위 밖 / 후속
- Google·카카오 로그인, 카카오톡 초대 링크, Firebase Authentication/Firestore/Functions/FCM
- 실제 방 생성·강퇴, 익명 사용자 매핑, 게임 투표·시간 동기화·서버 정산
- 신고·차단/계정 삭제의 백엔드·스토어 정책 적용
- 로그인 이전 S01/S02, S05/S09/S11/S17/S18 예외 상태별 실제 화면 연결 및 기기 QA
- 실기기 검증: 360/393/412dp, 600dp Fold, 200% 글꼴, TalkBack·한글 IME·다크/라이트
- 실제 브랜드 동물 아바타·가면 에셋 승인, 앱 서명 및 Play 테스트 등록

## 보안
- 비밀값·토큰·서명키·Google services 구성은 Git에 저장하지 않는다.
- 현재 매니페스트는 특별 권한이나 실시간 네트워크 권한을 요청하지 않는다.
- Firebase 구현은 새로운 PR에서 엄격한 권한 규칙과 서버 판정을 연결한다.
