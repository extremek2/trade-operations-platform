# 프로젝트 문서 안내

- 최종 검토일: 2026-11-06
- 목적: 제품 범위, 목표 아키텍처, 구현 기록과 운영 적용 상태를 구분한다.

## 읽는 순서

1. [최신 V0 기술 기준선 병합 인계](engineering/v0-baseline-merge-handoff.md)에서 현재 `main` 기준점과 다음 실행 단계를 확인한다.
2. [통합 로드맵 v2](product/import-domestic-platform-roadmap-v2.md)에서 전체 제품 범위와 개발 순서를 확인한다.
3. [Import OS V0 PRD](product/prd.md)에서 이번 개발·검증 범위를 확인한다.
4. [BM Stage 1](product/bm-stage-1-market-to-bm.md), [시장조사 Stage 1](product/market-stage-1.md), [지원사업 매칭 Stage 1](product/program-stage-1.md) 순서로 IR 전 근거와 게이트를 확인한다.
5. [IR 초안](ir/README.md)에서 현재 발표 메시지, 근거와 다음 검증 과제를 확인한다.
6. [기술 스택 및 아키텍처 기준](product/TECH_STACK.md)에서 런타임·데이터 소유권·통신 원칙을 확인한다.
7. 실제 구현 여부는 `engineering/` 단계 문서와 현재 코드를 함께 확인한다.
8. 실제 배포·DB 적용 여부는 운영 실행 기록으로 별도 확인한다.

[PMF 검증·마케팅 작업 현황](pmf/README.md)은 제품 분석, 고객 발굴, 네이버 블로그 임시저장의 2026-09-15 기준 상태와 보관 자료를 정리한다.

[V0 실데이터 검증](validation/README.md)은 현재 파일럿 단계의 상태, 측정 계약, 차단 조건과 진행 기록을 유지한다.

## 문서 우선순위

문서 내용이 충돌하면 다음 순서로 판단한다.

1. 현재 코드·마이그레이션·자동 테스트
2. 승인된 ADR과 기술 기준 문서
3. 통합 로드맵
4. 모듈별 PRD·설계 문서
5. 날짜가 명시된 과거 구현·검증 기록

과거 검증 기록은 당시 사실을 보존한다. 최신 상태로 오해할 수 있는 부분에는 검증 기준 날짜와 마이그레이션 범위를 적는다.

## 제품 문서

| 문서 | 역할 | 현재 상태 |
|---|---|---|
| [통합 로드맵 v2](product/import-domestic-platform-roadmap-v2.md) | 국내외 발굴·소싱·수입·판매 전체 순서 | 기술 MVP 이후 실데이터 검증 계획 |
| [TradeGuard Ops PRD](product/prd.md) | 수입 운영 통제 모듈의 제품 가설·목표 설계 | 검증 전 PRD, 구현 완료 문서 아님 |
| [BM Stage 1](product/bm-stage-1-market-to-bm.md) | 고객·문제·대체재·가격 증거와 BM 확정 Gate | IR 전 검증 기준 |
| [시장조사 Stage 1](product/market-stage-1.md) | 공식 시장 근거, 인터뷰와 SOM 교차검증 | IR 전 검증 기준 |
| [지원사업 매칭 Stage 1](product/program-stage-1.md) | 5축 지원사업 매칭, Go/No-Go와 IR 반영 규칙 | 조건부 판정, 자격정보 확인 필요 |
| [무역 운영 도메인 v1](product/trade-operations-domain-v1.md) | PI·CI·송금·FTA·선적·통관 도메인 기준 | PRD 입력용 초안 |
| [국내 판매 상품 검증 v1](product/domestic-product-validation-module-v1.md) | 국내 판매 검증의 입력·계산·판정 세부안 | 통합 로드맵에 의해 범위·순서 일부 대체됨 |
| [가입 승인 및 참여 권한 v1](product/identity-access-approval-design-v1.md) | 계정·조직·외부 참여 권한 계약 | 1~5단계 구현 완료, 운영 적용 대기 |
| [도매 사이트 기반 발주 MVP](product/wholesale-procurement-mvp-feasibility.md) | 도매 카탈로그 조회부터 국내 매입·입고까지의 범위와 실제 검증 기준 | 샘플 수직 흐름 구현 완료, 라이브 API 실증 대기 |

## IR 문서

| 문서 | 역할 | 현재 상태 |
|---|---|---|
| [Import OS V0 IR 초안](ir/README.md) | 12장 발표 덱, Factbook, Track 판정과 슬라이드 원고 | 수업·내부검토용 초안 |

## 아키텍처 문서

| 문서 | 상태 |
|---|---|
| [ADR-0001: Core와 Automation 소유권 분리](architecture/ADR-0001-p0-service-and-data-ownership.md) | 승인, 공동 DB 경로 제거 완료 |
| [ADR-0002: 코어 도메인과 빈 DB 기준선](architecture/ADR-0002-core-domain-and-clean-baseline.md) | 승인, 개발 기준선 적용 완료 |
| [코어 도메인 실행 계획](architecture/P0_EXECUTION_PLAN.md) | P0~P5 첫 수직 사이클 완료, P6 공급 문서 OCR 결과부터 검토 초안·다품목 거래 사이클까지 연결 |

## 구현·검증 기록

[최신 V0 기술 기준선 병합 인계](engineering/v0-baseline-merge-handoff.md)는 2026-11-06 기준
두 PR의 `main` 병합, 브랜치 정리 결과, V0V-0 완료와 V0V-1 입력 조건을 기록한다.

[최신 종단 거래 사이클 인계](engineering/end-to-end-trade-cycle-handoff.md)는 2026-11-06 기준
기술 MVP의 완료 범위, 최신 전체 검증, Git 기준선과 V0 실데이터 검증 순서를 기록한다.

`engineering/identity-stage-1.md`부터 `identity-stage-6.md`까지는 가입 승인과 건별 외부 참여 작업의 단계별 기록이다. 각 문서의 테스트 수치와 마이그레이션 범위는 작성 당시의 결과이며, 최신 전체 검증을 뜻하지 않는다.

[P6 첫 단계](engineering/supplier-offer-stage-1.md)는 수동 공급 제안 접수와 행별 추출 확인의 구현 기록이다.
[P6 두 번째 단계](engineering/supplier-offer-stage-2.md)는 일부 품목 구매 선택과 기존 견적·예상 원가 인계의 구현 기록이다.
[P6 세 번째 단계](engineering/supplier-offer-stage-3.md)는 같은 견적의 여러 원가안을 한 발주로 묶고 품목별 선적·입고·원가까지 연결한 구현 기록이다.
[P6 네 번째 단계](engineering/supplier-offer-stage-4.md)는 합성 CSV/XLSX로 검증한 원본 파일 보존·결정론적 파싱·멱등 재업로드의 구현 기록이다.
[P6 다섯 번째 단계](engineering/supplier-document-stage-5.md)는 S3 호환 원본 보관, 텍스트 PDF 우선 추출, 스캔 OCR·안티블러와 실패 경계의 구현 기록이다.
[P6 여섯 번째 단계](engineering/supplier-document-stage-6.md)는 문서 추출 텍스트를 검토 초안의 확인 대기 행으로 연결하고 화면에서 원본을 비교하는 구현 기록이다.
[P6 일곱 번째 단계](engineering/supplier-document-stage-7.md)는 실패한 문서의 원본 무결성 확인·재추출·시도 이력 조회를 위한 백엔드 구현 기록이다.
[공급처 상품 문의 초안](engineering/supplier-inquiry-draft.md)은 거래처 문의 주소·참조 등록과 상품별 Gmail 작성 창 연결을 기록한다.
[공급 문서·파일 변형 검증](engineering/supplier-sample-validation.md)은 합성 샘플의 파서·OCR 확인 결과와 실제 공급처 샘플 대기 상태를 구분한다.
[도매 카탈로그·국내 매입 작업 인계](engineering/wholesale-procurement-handoff.md)는 2026-10-10 중단 시점의 완료 커밋, 검증 결과, 미검증 경계와 재개 순서를 기록한다.

- [1단계: 시스템 권한과 인증 세션](engineering/identity-stage-1.md)
- [2단계: 조직 개설 신청 백엔드](engineering/identity-stage-2.md)
- [3단계: 가입 승인 화면과 인증 통합](engineering/identity-stage-3.md)
- [4단계: 조직 직원 권한 관리](engineering/identity-stage-4.md)
- [5단계: 외부 업체·건별 참여](engineering/identity-stage-5.md)
- [6단계: 운영 적용 준비와 검증](engineering/identity-stage-6.md)

## 유지 규칙

- 제안 기능과 구현 기능을 같은 표에서 섞지 않는다. 제안은 `목표` 또는 `구현 전`, 구현은 검증 근거와 함께 `구현 완료`로 표시한다.
- 문서의 실제 API·화면 경로는 컨트롤러와 라우터를 기준으로 갱신한다.
- 물리 테넌트 키는 `organization_id`를 사용한다. 제품 개념을 설명할 때만 “테넌트”라는 용어를 사용할 수 있다.
- 운영 데이터가 생긴 뒤에는 적용된 Flyway 파일을 수정하지 않는다. 현재 빈 DB 기준선 재작성은 ADR-0002 범위에서만 허용한다.
- 존재하지 않는 파일·데모·스킬을 완료로 표시하지 않는다.
- 개인 홈 디렉터리의 절대 경로를 공유 문서의 필수 계약으로 사용하지 않는다.
- 운영 리허설 성공과 실제 운영 DB·계정·메일·배포 적용을 구분한다.
