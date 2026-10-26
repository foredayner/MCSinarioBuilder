# AI Scenario Builder (AI 시나리오 공방)

크툴루의 부름(CoC) TRPG 시나리오 파일을 넣으면 AI가 마을 맵(기본 160×160)을 지어 주는 Minecraft 1.21.4 Fabric 모드입니다.
GM이 세션 전에 미리 지어 두는 용도입니다.

**필요한 모드:** [AI Build Assistant](https://github.com/foredayner/McTestBuilder/releases/tag/mc1.21.4-latest) (AI 연결, 건물·소품 생성, 배치를 이 모드가 맡습니다), Fabric API.

## 흐름
1. **시나리오**: 파일(.txt .md .pdf)을 고르거나 글을 붙여 넣기
2. **시트**: AI가 시대·분위기, 장소(방 구성), NPC, 단서를 정리 → 확인·수정
3. **배치도**: 160×160 땅에 장소·길·구역 배치 → 지도에서 옮기기·크기 바꾸기 → 승인
4. **짓기**: 땅 준비 → 장소별 건물(공통 스타일 규칙) → 필요한 소품 → 장면 표지·단서·NPC 자리
5. **GM 수첩**: 장소·장면 이동, 단서 목록, 시간·날씨

K키로 공방을 엽니다(OP 전용).

## 개발 단계
- [x] 1. AI Build Assistant 공개 API (설계·배치·`ai.mark`)
- [x] 2. 모드 뼈대 (CI, 공방 화면, GM 수첩)
- [x] 3. 시나리오 입력 (파일 선택, PDF 글자 추출, 서버 저장)
- [ ] 4. 시나리오 시트
- [ ] 5. 배치도
- [ ] 6. 짓기
- [ ] 7. TRPG 요소
- [ ] 8. 테스트 시나리오로 종단 테스트

## 빌드
CI가 AI Build Assistant의 최신 릴리스 jar를 `libs/`에 받아 그 위에서 빌드합니다. 로컬에서는 그 jar를 `libs/minecraft-ai-build-assistant.jar`로 두고 `./gradlew build`.

## 라이선스
MIT
