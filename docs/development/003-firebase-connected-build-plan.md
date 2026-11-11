# Firebase 연결 APK 빌드

목표: 기존 2-C 구현에 승인된 `talksullae` 설정을 비공개로 적용하고 설치 가능한 APK를 검증합니다.

- [x] 설정 파일의 프로젝트·패키지명·웹 OAuth 클라이언트 검증, 개인 Drive 비공개 백업
- [x] GitHub Actions Secret `FIREBASE_ANDROID_GOOGLE_SERVICES_JSON` 등록
- [ ] `.github/workflows/android.yml`에서 설정을 빌드 직전에 복원하고 대상 프로젝트·패키지명·OAuth 클라이언트 검증
- [ ] 기존 단위 테스트·Lint·Debug APK 빌드 실행, 생성된 BuildConfig·Google Services 리소스 검증
- [ ] APK 해시·Firebase 활성화 여부·서명 인증서 보고서 생성, 실제 새 인증서의 SHA-1·SHA-256 등록
- [ ] Firebase 설정 포함 APK를 개인 Drive에 전달, 실제 Google 계정 2개와 다중 기기 E2E는 별도 실기기 검증

앱 기능과 서버 코드는 변경하지 않습니다. 설정 원문·API 키·서명 개인 키는 Git이나 빌드 보고서에 포함하지 않습니다. 기존 로컬 데모 계측 작업은 운영 서버에 접속시키지 않습니다. CI Debug 서명은 빌드마다 달라질 수 있으므로 실제 전달할 APK의 인증서를 확인합니다.
