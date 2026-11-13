# OPS-06 — TalkSullae 스토어 등록정보 배포 정책

- 현재 저장소의 Google Play 테스트 배포 워크플로는 internal/closed 바이너리 관련이며, 별도의 ko-KR 스토어 설명·아이콘·스크린샷 게시 워크플로는 확인되지 않습니다.
- Play 등록정보 변경은 자동 앱 CI·push·태그·이슈 댓글·기존 릴리스 승격에서 실행할 수 없습니다.
- 향후 스토어 등록정보 게시기를 구현할 경우 scripts/ops06/store_gate.py apply를 실제 수정 작업 이전, Play 인증 이전에 연결하고 로컬 정적/이미지 검증과 Play 변경 작업을 별도 수동 승인으로 분리합니다.
- 승인에는 계획된 작업과 최종 QA 완료, 사용자 요청, 정확한 현재 main SHA, KST 일일 배치 ID, 별도 적용 확인 문구가 필요합니다.
- 신규 게시기는 ko-KR 및 승인된 이미지 종류만 변경하도록 제한하며 다른 언어·바이너리·internal/closed/production 트랙을 건드려서는 안 됩니다.
- GitHub Actions 내 디버그 빌드·Android 캡처·Play API 읽기 전용 확인은 계속 수행할 수 있지만, 실제 Play 등록정보 변경과 구별하여 보고합니다.
- 본 OPS-06 PR은 새로운 스토어 게시기를 만들거나 실제 Google Play API를 호출하지 않습니다.
