# P6 첫 단계: 수동 공급 제안 접수와 추출 확인

- 날짜: 2026-09-15
- 상태: 백엔드 첫 단계 구현. 구매 품목 선택과 기존 거래 흐름으로의 전환은 다음 단계.
- 기준: [코어 도메인 실행 계획](../architecture/P0_EXECUTION_PLAN.md)

## 구현 범위

수동 원본 텍스트와 공급처를 등록하면 서버가 SHA-256 해시를 가진 `SourceArtifact`, `MANUAL` 방식의
`ExtractionRun`, `REVIEW_REQUIRED` 상태의 `SupplierOfferDraft`를 생성한다. 원본 내용·원본 행 이름과
위치는 수정되지 않는다. 사용자는 각 행의 확인값을 입력하거나 행을 제외할 수 있다. 모든 행이
처리된 뒤에만 추출을 확인한다. 이 확인은 구매 품목 선택이나 발주 승인이 아니다.

`POST /api/v1/supplier-offer-drafts`, `GET /api/v1/supplier-offer-drafts`,
`GET /api/v1/supplier-offer-drafts/{draftId}`로 등록·조회한다. 행 검토는
`PUT /api/v1/supplier-offer-drafts/{draftId}/lines/{lineNumber}`, 제외는
`POST /api/v1/supplier-offer-drafts/{draftId}/lines/{lineNumber}/exclude`, 추출 확인은
`POST /api/v1/supplier-offer-drafts/{draftId}/confirm`이다. 변경 요청은 초안 `version`을 보낸다.

V7 migration은 수동 원본·실행·초안·행을 순방향으로 설치한다. 같은 조직·공급처의 동일 텍스트
해시는 중복 등록을 거절한다. 단가·MOQ·수량 단위처럼 알 수 없는 값은 null로 남는다. 확인만으로
`Product`, `SupplierQuote` 또는 발주를 만들지 않는다. 이 단계는 바이너리 파일을 저장하거나 실제
파서·OCR·외부 API를 호출하지 않는다.

## 검증과 다음 작업

단위 테스트는 행이 남아 있으면 확인을 거절하고 확인 후 수정을 막는 상태 전이를 검증한다.
PostgreSQL 통합 테스트는 V7 설치·JPA 검증, 조직 격리, 중복 원본, 버전 충돌, 읽기 전용 역할,
초안 확인 전 상품·견적 미생성을 검증한다.

검증 결과: 백엔드 전체 115개 테스트와 프런트엔드 전체 46개 테스트가 통과했다.
개발 Compose `docker compose -f docker-compose.yml config --quiet`도 통과했다. 배포 리허설 테스트는
새 V7을 적용한 PostgreSQL의 백업·복원·런타임 계정 제한을 다시 확인했다. 실제 운영 DB 적용과
외부 메일·API 검증은 수행하지 않았다.

다음 단계는 확인된 제안 행 중 일부를 구매 후보로 선택하는 별도 경계와 기존
`Product`·`SupplierQuote` 원장으로의 전환이다. 제외된 행과 원본은 계속 보존해야 한다.
