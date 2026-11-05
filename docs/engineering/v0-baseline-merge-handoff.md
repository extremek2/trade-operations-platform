# V0 기술 기준선 병합 인계

- 인계일: 2026-11-06
- 기준 브랜치: `main`
- 기술 MVP 병합 커밋: `075598d` (PR #1)
- V0 검증 기반 병합 커밋: `bdf5693` (PR #2)
- 상태: V0V-0 기준선 보호 완료, V0V-1 실제 파일럿 3건 선정 대기
- 이전 인계: [종단 거래 사이클 기준선 인계](end-to-end-trade-cycle-handoff.md)
- 실행 원장: [V0 실데이터 검증](../validation/README.md)

## 1. 병합 결과

[PR #1](https://github.com/extremek2/trade-operations-platform/pull/1)은 가입·권한부터 공급 제안,
견적, 예상 원가, 발주, 선적 또는 국내 입고, 실제 원가, 판매 관측과 재발주 판단까지의 기술 MVP를
`main`에 병합했다.

[PR #2](https://github.com/extremek2/trade-operations-platform/pull/2)는 재현 가능한 품질 CI, V0
실데이터 검증 계획과 파일럿 케이스 템플릿을 `main`에 병합했다. PR #2는 PR #1 병합 후 기준
브랜치를 `main`으로 변경해 병합 가능 상태와 기존 CI 성공을 다시 확인했다.

현재 `main`은 다음을 포함한다.

- Spring Boot API, React Web, PostgreSQL과 Flyway V1~V18
- 조직 격리, 가입 승인, 직원 권한과 외부 건별 참여
- 상품·거래처·견적·예상 원가와 다품목 거래 사이클
- 공급 제안 CSV/XLSX/PDF/이미지 검토와 선택적 OCR·객체 저장소
- 도매꾹 샘플/라이브 조회 어댑터와 발주 전 가격·재고 재확인
- 국내 입고, 실제 원가 마감, 판매 관측과 다음 매입 결정
- 운영 Compose, 백업·복구 절차와 품질 CI

## 2. 최종 검증 기준

병합 전후 기준선에서 다음 검증을 통과했다.

| 검사 | 결과 |
|---|---|
| 백엔드 | 144개 중 실패 0, 환경 의존 2개 skip |
| 프런트엔드 | 13 suites, 69 tests 통과 |
| 브라우저 E2E | 온보딩 4개, 거래 사이클 1개 통과 |
| 프런트 프로덕션 빌드 | 성공 |
| 개발·운영 Compose 문법 | 성공 |
| OCR 런타임 이미지 | 3개 테스트 통과 |
| GitHub Actions | backend, frontend, compose, OCR, browser E2E 모두 성공 |

GitHub Actions 근거는
[실행 42093557380](https://github.com/extremek2/trade-operations-platform/actions/runs/42093557380)이다.
건너뛴 백엔드 테스트는 실제 `DOMEGGOOK_API_KEY`가 필요한 라이브 조회와
`MINIO_TEST_ENDPOINT`가 필요한 객체 저장소 왕복 테스트다.

## 3. 브랜치 정리 결과

`main`에 완전히 병합된 다음 원격 브랜치를 삭제했다.

- `develop`
- `feature/cost-scenario`
- `feature/end-to-end-trade-cycle`
- `feature/pipeline-v2`
- `feature/python-pipeline`
- `feature/quote-revision-ui`
- `feature/trade-operation-mvp`
- `feature/v0-real-data-validation`
- `refactor/core-domain-baseline`

로컬의 `feature/end-to-end-trade-cycle`, `feature/v0-real-data-validation`도 삭제했다. 삭제된 브랜치의
커밋은 `main`과 병합 PR 이력에 보존된다.

`origin/feature/ai-server`는 `main`에 없는 커밋 1개를 가진다. 해당 코드는 ADR-0001에서 제거한
Python 공동 소유 경로이므로 자동 병합하거나 삭제하지 않았다. 필요 여부를 별도 확인한 뒤 보관 또는
삭제한다.

## 4. 현재 완료 위치

### V0V-0 — 기준선 보호: 완료

- 기술 MVP와 검증 기반을 `main`에 병합
- 로컬·원격 누적 커밋 해소
- 백엔드·프런트·Compose·OCR·E2E CI 구성과 성공 확인
- 실데이터 검증 원장과 케이스 템플릿 준비
- 병합 완료 브랜치 정리

### V0V-1 — 파일럿 입력 계약: 준비 완료, 실제 입력 대기

아직 실제 파일럿 상품과 원본 증거는 제공되지 않았다. 따라서 기능 개발이나 라이브 연동 성공을
추가로 주장하지 않는다.

## 5. 다음 단계 입력

다음 작업을 시작하려면 실제 또는 실행 가능한 상품 3개를 정한다.

1. 국내 도매 API 또는 사이트 수동 확인 상품 1개
2. 실제 CSV/XLSX/PDF 견적을 가진 공급처 상품 1개
3. 실제 해외 구매·배송·통관 자료가 있거나 실행 일정이 확정된 상품 1개

각 상품에는 다음 정보가 필요하다.

- 익명 상품명과 내부 SKU
- 조달 경로와 자료 유입 방식
- 가격·MOQ·수량·통화·납기·배송 조건의 원본 위치
- 주문·결제·배송·실제 비용·판매 또는 구매의향 증거의 제한 저장 위치
- 자료 사용·보관 권한과 익명화 범위
- 현재 미확정 값과 확인 담당·예정일

API 키, 비밀번호, 개인정보와 공개할 수 없는 원본 문서는 Git이나 채팅에 넣지 않는다.

## 6. 권장 실행 순서

1. [파일럿 케이스 템플릿](../validation/v0-pilot-case-template.md)을 상품별 문서로 복사한다.
2. 원본 증거와 사용·보관 권한을 확인하고 `pilot_id`를 발급한다.
3. 새 기능 없이 기존 화면으로 제안 접수부터 다음 매입 결정까지 진행한다.
4. 단계별 시작·종료 시각, 수정 필드, 누락 값과 예상·실제 원가 차이를 기록한다.
5. 진행을 막은 문제만 재현 테스트와 함께 수정한다.
6. 세 상품을 완료한 뒤 V0.5 자동화, V1 SaaS 또는 문제·세그먼트 재정의를 선택한다.

첫 실행은 실제 해외 거래 1건을 우선한다. 국내 도매 라이브 조회나 OCR은 해당 거래에서 필요할 때만
활성화한다.

## 7. 다음 인계문서 작성 시점

다음 인계는 다음 중 하나가 발생하면 새 파일로 작성한다.

- 첫 실제 상품이 예상 원가부터 다음 결정까지 완주
- 실제 데이터 문제로 도메인 또는 migration 변경이 필요
- 도매꾹 라이브 조회 또는 실제 공급처 문서 회귀 검증 완료
- 세 상품 파일럿 완료 또는 중단 판정

인계문서에는 기준 커밋, 실제 사용한 데이터 유형, 실행한 검증, 통과·실패·skip 수, 미해결 문제와
다음 단계 완료 기준을 남긴다. 과거 인계 결과는 덮어쓰지 않는다.
