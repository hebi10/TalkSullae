# TS-01 / TS-07 — App Check 및 방 API 요청 제한

## 코드 변경
- Firebase 구성 앱의 Application 초기화에서 Play Integrity App Check provider를 설치한다. Firebase 미설정 데모는 변경하지 않는다.
- Cloud Functions v2의 `TALKSULLAE_ENFORCE_APP_CHECK=true` 설정 시 앱용 callable에서 App Check를 강제한다. 기본값 false는 기존 설치본과 마이그레이션을 위한 상태이며 보안 완료가 아니다. 운영자 custom claim 기반 callable은 별도 설정을 유지한다.
- 방 생성은 사용자별 시간당 20회, 초대 발급 20회/분, 초대 확인 60회/분, 가입 요청 30회/분. Firestore 트랜잭션으로 서버 시간 구간별 누적하며 비로그인 요청은 저장하지 않는다. 직접 SDK 읽기/쓰기는 기존 Rules의 기본 차단을 유지한다.
- roomOperationLimits 문서 키는 해시로 저장하며 원문 UID를 저장하지 않는다. expiresAt을 설정한다. 운영 적용 전 이 collection group의 TTL 설정 및 비용/정리 정책 확인이 필요하다.

## 운영 적용 전 필수 검증
1. 정확한 운영 Firebase 프로젝트와 Play 앱 서명 인증서 SHA-256으로 Play Integrity를 연결하고 App Check Android 등록 상태 확인.
2. 새 Play 서명 앱에서 정상 토큰, Google 로그인, 방/채팅/게임/FCM, 운영자 도구, 기존 0.5.0 설치본 호환성 검증. 미등록·실패 비율과 비용 모니터링.
3. 구버전 사용자의 업데이트 경로 확보 후 별도 승인으로 App Check 강제 및 Firestore enforcement 적용. Firebase Rules·Functions·Scheduler 실제 리비전 확인.
4. 잘못된 앱·미등록 요청 차단, 정상 앱 접근 가능, rate limit 동시성 테스트 및 rollback 점검. 모니터링 지표 확인 후 상태를 문서화한다.
5. 운영 Firebase 및 Google Play는 이 PR에서 변경하지 않는다.

공식 문서:
- https://firebase.google.com/docs/app-check/android/play-integrity-provider
- https://firebase.google.com/docs/app-check/cloud-functions
