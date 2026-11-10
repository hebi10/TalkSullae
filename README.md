# 톡술래 (TalkSullae)

친구들이 각자의 스마트폰에서 고정된 방에 참여해 익명 채팅과 심리·추리게임을 즐기고, 기록을 함께 쌓는 Android 앱입니다.

> 현재 상태: **서비스 및 UI/UX 기획 단계**. 이 저장소에는 아직 Android 애플리케이션 코드, Gradle 프로젝트, Firebase 설정, GitHub Actions, Play 배포 기능이 없습니다.

## 프로젝트 방향
- Android: Kotlin + Jetpack Compose (제안, 개발 단계에서 구성)
- 서버: Firebase Authentication · Firestore · Cloud Functions · FCM (연동 예정)
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
- [서비스 기획서 v1.0](https://docs.google.com/document/d/13BMHPicBwI1kN1XrPsjHYfR4wkXmBz4lUDzcPecptvg/edit)
- [디자인 설계 문서](docs/design/001-full-screen-ux-plan.md) — 디자인 작업 브랜치에서 검토

## 현재 범위
지금은 전체 화면 디자인 기획을 수행합니다. 앱 코드 구현, Firebase 프로덕션 연동, 서명 및 Play 배포는 미진행 상태입니다.
