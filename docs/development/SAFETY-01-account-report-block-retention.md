# SAFETY-01 — 계정 삭제·메시지 신고·사용자 차단·보존 개선

기준: Google Drive 서비스 기획 v1.0, 기존 Firebase 방/채팅 권한 모델, 기존 안전 신고 및 OPS-08 수동 배포 동결.

## 기능
- 서버 Callable setUserBlock: 본인 식별자로만 차단 대상 지정, 본인 차단·클라이언트 UID 위조 금지. Firestore Rules에서는 각 사용자가 자신의 차단 목록만 제한 조회 가능.
- reportChatMessage: 실제 저장된 메시지와 활성 방 멤버 자격을 서버 트랜잭션에서 확인, UUID 멱등 신고, 60초 간격 제한, 운영자 전용 신고 문서.
- requestAccountDeletion: 최근 5분 이내 Google 로그인(auth_time), 명시적 DELETE 확인, active 방장·진행 중 게임 참여자의 사전 처리 강제.
- processAccountDeletionQueue: 처리 중 재시도 가능한 서버 대기열, 본인 작성 채팅·방 가입 인덱스·개인 차단 목록·계정 인증 정리. 공동 방장/다른 구성원의 데이터는 보존하며 과도한 데이터 양은 운영자 검토로 이관.
- 메시지 만료 시각: 일반 채팅 90일, 게임 채팅 7일(생성 시각 기준). 서버 예약 정리 스케줄과 collection-group 인덱스 준비.
- 앱: 메시지별 신고·차단·해제, 설정의 삭제 요청·Google 재로그인·확인 창.

## QA
- 잘못된 targetUid/UID 위조, 신고 없는 메시지·비회원·사전 재인증 거부, 차단 목록 읽기·쓰기 Firestore Rules.
- Firebase Emulator의 실제 사용자 2명, 신고 중복·권한·계정 삭제 대기 및 데이터 정리.
- Android 기존 화면 계측·API29/35/36과 태블릿 회귀 유지.

## 제약
- 기존 신고/공동 경기 데이터의 법적 보존·완전 삭제는 실서비스 정책과 처리량에 맞춰 추가 검수해야 함.
- 기존 메시지의 expiresAt 부재와 수동 정리 마이그레이션은 별도.
- 운영 Firebase Functions/Rules/Indexes/Scheduler 변경 금지. Google Play/AAB/스토어 변경 금지.
- OPS-08 유지, 최신 PR HEAD 전체 CI·Codex 리뷰 성공 시에만 main 안전 병합.
