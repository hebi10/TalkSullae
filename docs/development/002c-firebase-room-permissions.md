# 2-C — Firebase 인증·온라인 방·멤버 권한

작성: 2026-11-10 / Android Kotlin·Jetpack Compose + Firebase Functions / 상태: 코드·에뮬레이터 검증, 운영 프로젝트 미배포

## 1. 현재 상태 및 연결 조건

Firebase 프로젝트 운영 연결·Firestore Rules 배포·Cloud Functions 배포는 진행하지 않았습니다. Firebase 앱 설정이 없는 기존 개발 빌드는 로컬 2-B 데모로 실행되고, 실제 로그인 메뉴는 비활성화됩니다.

실제 Google 로그인 전에 다음 조건이 필요합니다.

1. 본인 Firebase 프로젝트 생성 또는 명시적 연결, Android applicationId com.hebi.talksullae 등록
2. Google 로그인 제공자 활성화, OAuth 동의 화면 및 Google Cloud OAuth 웹 클라이언트 구성
3. 앱 서명 SHA-1/SHA-256 지문을 Firebase Android 앱 등록 정보에 추가
4. Google 로그인 활성화 후 내려받은 google-services.json을 개인 개발 환경의 app/google-services.json에 저장 (Git 제외)
5. Cloud Firestore Native mode 데이터베이스와 Functions 2세대(서울 asia-northeast3)의 배포·과금 정책을 확인
6. 보안 규칙 및 Callable 함수 검증 후 승인된 Firebase 프로젝트에 배포
7. App Check·서버 호출량 제한·비정상 가입 차단·개인정보 정책을 출시 전에 확정

## 2. Android 및 서버 기술 구성

Android: Firebase BoM 34.19.0, Google Services plugin 4.5.0(설정 파일 존재 시만 적용), Credential Manager 1.3.0, Google Identity ID Token 1.1.1.
Server: Cloud Functions for Firebase 7.4.0, Firebase Admin 14.5.0, Node.js 22, Firestore Rules 2.

실제 Google 로그인: Credential Manager에서 Google 계정 ID 토큰 획득 → FirebaseAuth의 GoogleAuthProvider로 로그인 → 사용자 UID 기준 온라인 방 목록 구독 → 서버 Callable 작업 가능.

온라인 모드와 기존 로컬 데모는 분리되어 있습니다. 서버에서 아직 배포하지 않은 함수는 Android UI에 성공한 것처럼 표시되지 않도록 에러/로딩 상태로 처리합니다.

## 3. 데이터 구조

- rooms/{roomId}: 방 이름·설명·방장 ownerId·활성/대기 인원·가입 승인·현재 초대 해시·상태
- rooms/{roomId}/members/{uid}: displayName, status(active/pending/rejected/removed/left), joinedAt
- rooms/{roomId}/invites/{sha256}: expiresAt, createdBy, createdAt
- userRooms/{uid}/rooms/{roomId}: roomName, status, updatedAt

Firestore 직접 클라이언트 쓰기는 모두 거부하고, 유효 사용자 자신의 userRooms 목록만 열람할 수 있게 합니다. 방 상세는 활성 멤버만, 멤버 목록은 활성 멤버만, 대기자는 자기 단일 멤버 기록만 읽습니다. 초대 해시 컬렉션은 모든 일반 클라이언트에 비공개입니다.

## 4. 서버 권한·거래 규칙

- createRoom: 인증 사용자만, 방장=생성자. 방 데이터·회원·내 방 인덱스를 원자적 배치로 작성
- createInvitation: 활성 방장만, 암호학적 난수로 32바이트 토큰 생성, Firestore에는 SHA-256만 저장, 24시간 만료, 새로 발급하면 이전 코드 폐기
- requestJoin: 방 ID와 초대 토큰 검증, 방 정원과 기존 강퇴 상태 검사, 가입 승인 설정에 따라 pending 또는 active
- changeMember: 방장만 승인/거절/강퇴/위임. 자기 자신을 강퇴·위임 대상으로 지정할 수 없음
- leaveRoom: 본인의 가입/대기 기록 변경, 방장일 경우 다른 활성 멤버가 남아 있으면 먼저 방장 위임 필요

활성 인원 최대 20명, 가입 대기 최대 30명. 초대·승인·강퇴와 인원 카운트 변경은 서버 Firestore 트랜잭션으로 처리하며, 클라이언트에서 임의로 요청한 방장/멤버 권한은 신뢰하지 않습니다.

현재 카카오톡 자동 공유는 없으며 인증된 방장이 발급된 초대 코드를 상대에게 직접 전달하는 구조입니다. 코드 원문은 응답으로 한 번 표시되며 서버에는 해시만 남깁니다.

## 5. 보안·자동 검증

Firebase Emulator(운영 데이터 접근 없음) 검증 10개:
- 입력 정책 및 로그인 필수·초대 토큰 형식: 3개
- 실제 Firestore 트랜잭션 방 생성·초대·승인·강퇴·위임·탈퇴: 2개
- Firestore Rules: 비회원·대기자·타 계정 차단, 초대 해시 비공개, 활성 멤버의 읽기만 허용: 5개

Android 자동 검증은 Firebase 앱 설정이 없는 로컬 데모에서 컴파일·단위 테스트·UI 계측의 호환성을 확인합니다. 실제 Google OAuth/실제 프로젝트/멀티 기기 작업 성공까지 보증하지 않습니다.

## 6. 개발·배포 명령 예시

Windows PowerShell (JDK 17·Android SDK 설치 후):
    .\gradlew.bat testDebugUnitTest lintDebug assembleDebug

Functions 로컬 테스트 (Node.js 22·Java 21 설치 후):
    cd functions
    npm install
    npm run check
    npm test
    cd ..
    npx firebase-tools@15.35.0 emulators:exec --project demo-talksullae --only firestore "cd functions && npm run test:emulator"

실제 Firebase 배포는 프로젝트 연결·검증과 운영 비용 승인을 받은 후에만:
    npx firebase-tools@15.35.0 deploy --only firestore:rules,functions

로그인 토큰·서비스 계정 자격 증명·서명키·Firebase 설정 파일을 공개 저장소나 빌드 로그에 남기지 마세요.

## 7. 출시 전 미완료

1. 앱 인증·리전·Firebase 프로젝트 실제 연동, 로그인·방 생성 E2E, 별도 테스트 계정 2개로 승인·강퇴·위임 확인
2. App Check 적용, 사용자별 방 생성/가입 코드 발급 제한, 비정상 요청 Rate limit, 악성 행위 신고·차단
3. 데이터 삭제/이전 방장·탈퇴·강퇴 기록 보존, 방 영구 삭제·하위 컬렉션 삭제 정책
4. 개인정보처리방침·Google OAuth 동의 화면·Cloud Functions Blaze 비용·IAM 정책 확인
5. Android 200% 글꼴·IME·TalkBack·폴더블 실기기 등 실제 UI 접근성 검증
6. 카카오톡 공유·Android App Links와 실제 초대 수락 전용 UX 구현

## 8. GitHub·참고

2-C 코드 PR: https://github.com/hebi10/TalkSullae/pull/8
2-B 기반 PR: https://github.com/hebi10/TalkSullae/pull/7
Firebase 공식 Google 로그인: https://firebase.google.com/docs/auth/android/google-signin
Firebase Cloud Functions Callable: https://firebase.google.com/docs/functions/callable
Firestore Security Rules: https://firebase.google.com/docs/firestore/security/rules-conditions

현재 이 문서는 운영 배포 성공 보고가 아니라 개발 및 로컬 에뮬레이터 검증 명세입니다.

## 9. 2-C 보안 보완 및 QA 기준 (2026-11-10)

- 권한이 회수되거나 멤버 상태가 활성 상태에서 변경되면, Android 화면에 남아 있던 방·멤버 정보와 Firestore 구독을 즉시 정리합니다.
- 방장 혼자 남았더라도 가입 대기자가 있으면 방을 보관 처리하지 않고 먼저 가입 요청을 처리하도록 차단합니다. 승인/거절 후 정원·가입 대기 카운트의 일관성을 유지합니다.
- 비정상적으로 긴 대상 사용자 ID를 서버에서 거부합니다.
- 초대 코드 원문을 Android SavedInstanceState에 보관하지 않고 화면을 나가면 입력 내용이 사라지도록 변경했습니다.
- Quickstep(에뮬레이터 **런처**)의 ANR 대화상자만 감지·닫는 캡처 보정 도구를 추가했으며 톡술래 앱 자체의 크래시/ANR은 숨기지 않습니다. UI 캡처가 미완료면 이제 에뮬레이터 CI가 실패하도록 설정했습니다.
- 서버 보안 테스트 12개는 [GitHub 실행 #42794394374](https://github.com/hebi10/TalkSullae/actions/runs/42794394374)에서 통과했습니다.
- Firebase 프로젝트 배포, Google OAuth 실계정 로그인, 두 기기 간 승인·강퇴 연동은 **별도 통합 검증 대상**이며 아직 완료되지 않았습니다.
