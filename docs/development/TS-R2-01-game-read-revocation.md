# TS-R2-01 게임 조회·신고 권한 회수
- getMyGameSession, findActiveGameSession, findRecentCompletedGameSession의 권한/계정 삭제 상태/공개·비공개 결과를 동일한 읽기 전용 Firestore 트랜잭션에서 확인한다.
- 삭제 대기 계정의 메시지 신고와 멤버 관리, 자신의 방 인덱스 및 멤버 직접 조회도 차단한다. 서버 정리 작업은 Admin SDK 권한으로 진행한다.
- 삭제 요청 수락 직후 멤버 문서가 아직 active여도 조회를 거부하는 회귀 테스트를 추가한다.
- 신고 증거의 기존 보존 정책이나 계정 삭제·게임 진행 규칙은 변경하지 않는다. 운영 Firebase 변경 없음.
