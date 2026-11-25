# TS-DESIGN-03 — 2차 캡처 증빙 결함 및 보완 (2026-11-25)

- 대상 HEAD f32855270cb22c1e2cb134d0b2b6f20afb3945aa.
- [2차 네이티브 실행](https://github.com/hebi10/TalkSullae/actions/runs/45539866177), [artifact 14988019408](https://github.com/hebi10/TalkSullae/actions/runs/45539866177/artifacts/14988019408).
- CI 6종 성공. ZIP 내부 manifest passed, 12 계측 성공, 17 PNG 모두 1080×2400 및 SHA-256 일치, 누락/손상/예상외 파일 없음. **그러나 17장 전부에 Android System UI isn't responding 팝업이 표시된다.** 파일·Compose 테스트 검증과 캡처 품질 검증을 구분한다. 기존 결과는 정상 시각 비교 증빙으로 사용하지 않는다.
- 가려지지 않은 영역에서 D14/D16/D17 하단 버튼, D27 중복 헤더 제거와 시즌 이동, D29 모달 확장, D30 성장 정보 재배치의 방향만 확인했다. 실제 색상과 세부 간격 및 가려진 내용은 판정 보류.
- 원인: DesignCaptureIO는 Compose 의미 앵커와 비트맵 명암만 검증해 외부 System UI ANR 위에 렌더된 이미지도 통과했다. 앱 자체 ANR로 확정할 근거는 없다.

## 수정
1. 기존 emulator-only ANR preflight 재사용.
2. 각 캡처 직전/직후 UiAutomation interactive windows 검사. 앱 foreground 및 다른 창 방해 여부를 확인한다. 시스템 패키지의 명시적 System UI/launcher ANR만 Wait/Close 규칙으로 제한 횟수 처리한다. 앱 ANR, 알 수 없는 오류, 반복 시스템 ANR는 실패 처리한다. 비정상 프레임을 저장하지 않는다.
3. 각 PNG에 대응하는 별도 foreground marker, host exporter 검증, manifest 증빙 포함. 기존 무결성/정확한 SHA/목록/계측 검증 유지. 운영/Play 변경 없음.
4. 3차 동일 17장 캡처와 이미지 육안 검증 성공 이후 디자인 보정/병합 판단. 라이트/200%/폴더블 및 실계정 QA는 별도 미완료.

기획 시안의 색상 아바타와 실제 단색 아이콘, 화면 크기, 가상 샘플 데이터 및 기능 차이를 숨기거나 임의 점수로 비교하지 않는다.
