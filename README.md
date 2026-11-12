# 톡술래 (TalkSullae)

친구들이 각자의 스마트폰에서 고정된 방에 참여해 익명 채팅과 심리·추리게임을 즐기고, 기록을 함께 쌓는 Android 앱입니다.

> 현재 상태: **2-D Android·Firebase 자동 QA 완료 / Google Play 비공개 테스트 미배포**. Google 로그인·온라인 방 관리는 코드로 구현했으나 실제 계정 2명 E2E 검증이 남아 있습니다. 실시간 채팅·게임·시즌은 데모 단계입니다.

## 프로젝트 방향
- Android: Kotlin + Jetpack Compose (구현됨)
- 서버: Firebase Authentication · Firestore · Cloud Functions (방·권한 구현), FCM (후속)
- 초대: 카카오톡 공유 링크 + Android App Links (카카오톡 자체 채팅방 연동 아님)
- 핵심 게임: 너 누구야?, 라이어 채팅, 금지어 채팅, 밸런스 예측
- 일반 채팅과 게임 채팅 분리, 익명 매핑 서버 관리, 방장 권한 검증
- 시즌 기록, 게임별 통계, 친구 간 누적 결과 보존

## 작업 방식
1. 기획 및 디자인 명세는 `docs/design/`에서 버전 관리하고, 원본 기획·시안은 Google Drive에 보관합니다.
2. 변경은 기능별 브랜치 + PR 기반으로 진행하고, 확인된 CI 결과를 남깁니다.
3. Android 구현 후에는 GalliPick과 비슷하게 GitHub Actions에서 빌드·테스트·캡처를 수행하도록 구성합니다.
4. **서명키, 서비스 계정, 토큰, Firebase 비공개 설정 파일은 저장소에 커밋하지 않습니다.**
5. Play 배포는 안전성 검증과 사용자 승인 범위에 따라 내부 테스트·비공개 테스트부터 시작합니다.

## 문서
- [톡술래 Google Drive 프로젝트](https://drive.google.com/drive/folders/1XskLt9niOOPdtVxVUO5eXmFMXX9S8qNI)
- [GAME-00 다중 게임 공통 아키텍처 확정](docs/architecture/GAME-00-multi-game-platform.md) — 카탈로그 v1·세션 계약·보안·서버 판정/버전 정책 (실제 게임 미구현)
- [서비스 기획서 v1.0](https://docs.google.com/document/d/13BMHPicBwI1kN1XrPsjHYfR4wkXmBz4lUDzcPecptvg/edit)
- [디자인 설계 PR #1](https://github.com/hebi10/TalkSullae/pull/1) — 1-A~1-C 디자인 문서
- [Google Drive 화면 설계 v1.0](https://docs.google.com/document/d/1d7A0zC76o42YfEy2HtHzU9IK4HYRJeQEIFr8x1zc9Ps/edit)

## 현재 범위
2-A 공통 Android UI·로컬 데모와 Android CI가 구현됐습니다. Firebase 운영 연결·실시간 채팅·게임 로직·Play 배포는 후속 단계입니다.

## Android 2-A 공통 UI 개발 기반

- 코드 작업 브랜치: [feat/2a-compose-foundation](https://github.com/hebi10/TalkSullae/tree/feat/2a-compose-foundation)
- 최초 앱 식별자: `com.hebi.talksullae` (Play 등록 전 임시 확정)
- Kotlin 2.2.21 · AGP 8.13.2 · Compose BOM 2025.10.01 · Java 17 · minSdk 26 · targetSdk 36
- 처음 실행하면 실제 계정 대신 **기기 내 UI 데모**가 표시됩니다. 내 방 → 방 채팅/게임/기록, 익명 게임 시연, 다크/라이트 전환을 확인할 수 있습니다.
- 로컬에 Android SDK와 JDK 17이 설치된 경우 다음과 같이 빌드할 수 있습니다.

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

- [공통 UI 설계·검증 범위](docs/development/002a-android-foundation.md)
- [GitHub Actions Android 검증](.github/workflows/android.yml): 단위 테스트·Lint·디버그 APK 생성
- 본 섹션은 2-A 로컬 데모 설명입니다. 온라인 방은 2-C로 별도 구현됐고, 실시간 채팅·게임 판정·점수 계산은 미구현입니다.

## Firebase·Play 상태 (2-C / 2-D)
- `app/google-services.json`은 Git에 저장하지 않으며 배포 시 비공개 Secret에서 복원합니다.
- Firebase Auth·Firestore 및 서울 Cloud Functions의 온라인 방 생성·초대·승인·강퇴·위임·탈퇴 코드가 구현되어 있습니다. 실제 Google 계정 2명 E2E는 미검증입니다.
- Android API 29·35·36 휴대폰 및 API36 태블릿의 Firebase·Android 종합 QA는 [#42951822274](https://github.com/hebi10/TalkSullae/actions/runs/42951822274)에서 통과했습니다 (4환경 화면 캡처 48장).
- [비공개 Play 배포 워크플로](.github/workflows/play-closed.yml)는 `main`에서 승인된 `check → build → deploy` 모드만 허용합니다. Play 최초 앱 등록·정책·고정 키·API 권한 설정 전에는 제출하지 않습니다.
- 릴리스 및 인증 정보는 GitHub Actions Secrets/Variables에서만 주입합니다. GitHub에 서명키·토큰·Firebase 설정값을 넣지 않습니다.
- [2-D QA·배포 미완료 추적](https://github.com/hebi10/TalkSullae/issues/11) · [2-D 원본 캡처](https://drive.google.com/file/d/1KzuuCJAPzn6J-Os0pUlGwDZGShiPDsFu/view)

