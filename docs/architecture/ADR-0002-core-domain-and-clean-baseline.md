# ADR-0002: 코어 도메인과 빈 DB 기준선 재정립

- 상태: 승인
- 결정일: 2026-09-15
- 범위: 코어 업무 도메인, 기존 리서치 프로토타입, Flyway 기준선
- 선행 결정: [ADR-0001](ADR-0001-p0-service-and-data-ownership.md)

## 배경

결정 당시 DB에는 40개 테이블이 있었지만 후보·견적·매입·입고·판매를 잇는 제품 핵심 모델은
구현되지 않았다. 일부 테이블은 아직 런타임 사용처가 없고, 기존 상품·클러스터·도매
프로토타입은 Spring과 Python이 같은 테이블을 함께 사용한다.

보존할 운영 데이터가 없는 지금이 적용 이력을 계속 누적하지 않고 최종 형태의 기준선을
다시 만들 수 있는 시점이다. 이후 소형 ERP까지 확장할 수 있어야 하지만, 확장 가능성을
이유로 사용하지 않는 Aggregate와 테이블을 미리 만들지는 않는다.

## 결정

### 1. 조직이 유일한 업무 소유 경계다

- 물리 테넌트 키는 `organization_id`다.
- `Workspace`를 별도 엔터티나 테이블로 만들지 않는다.
- 조직 소유 Aggregate는 다른 조직의 엔터티를 참조할 수 없다.
- 외부 API에는 UUID `public_id`를 사용하고 내부 FK에는 BIGINT를 사용할 수 있다.

### 2. 후보와 상품을 별도 Aggregate로 만들지 않는다

`Product` 하나가 발굴부터 판매 가능한 품목이 되기까지 생명주기를 가진다.

```text
DISCOVERED -> SOURCING -> REVIEWING -> APPROVED -> ACTIVE -> DISCONTINUED
      \---------------------------------------> REJECTED
```

- 초기 발굴 정보는 Product의 가설과 근거다.
- 실제 규격 구분이 필요할 때만 Product 하위에 Variant를 추가한다.
- 후보가 승인될 때 Product를 복사하거나 새 SKU 원장을 만들지 않는다.
- 판매·재고에서 필요한 내부 SKU 코드는 Product/Variant에 부여한다.
- 상태 변경 이유와 판단 근거는 덮어쓰지 않고 별도 이력으로 남긴다.

### 3. 공급처와 협업 업체를 하나의 거래처 원장으로 통합한다

기존 `partner_company`와 신규 `Supplier`를 별도로 유지하지 않고 조직 소유
`BusinessPartner`를 사용한다.

- 역할은 SUPPLIER, FORWARDER, CUSTOMS_BROKER 등으로 확장한다.
- 한 거래처는 여러 역할을 가질 수 있다.
- 연락처는 BusinessPartner에 속하지만 연락처 등록만으로 시스템 접근 권한이 생기지 않는다.
- 선적 건 참여와 이메일 초대는 현재처럼 별도의 건별 권한 객체로 유지한다.
- 향후 상대 업체가 플랫폼 Organization을 갖더라도 명시적 연결 전에는 권한을 공유하지 않는다.

### 4. Aggregate는 업무 책임에 따라 분리한다

| 모듈 | Aggregate | 현재 결정 |
|---|---|---|
| identity | Organization, User, Membership, Session | 현재 구현을 보존한다 |
| catalog | Product, 선택적 ProductVariant | 첫 수직 슬라이스에서 구현한다 |
| partner | BusinessPartner, Contact | 기존 협업 업체와 공급처를 통합한다 |
| sourcing | Quote, QuoteLine | 첫 수직 슬라이스에서 구현한다 |
| costing | CostScenario | 견적 비교 다음 단계에서 구현한다 |
| procurement | PurchaseOrder, PurchaseOrderLine, Payment | 실제 매입 흐름에서 구현한다 |
| logistics | Shipment, TransportDocument, ShipmentAllocation | 기존 Shipment를 보존하고 품목 배정은 필요할 때 추가한다 |
| inventory | Receipt, InventoryLot | 실제 입고 흐름에서 구현한다 |
| sales | SalesRecord, Settlement | 실제 판매 입력 흐름에서 구현한다 |

`TradeCase`는 위 Aggregate를 담는 상위 엔터티가 아니다. 여러 Aggregate를 잇는 장기 진행
상태가 실제로 필요해질 때 각 Aggregate의 공개 ID만 참조하는 Process Manager로 추가한다.

### 5. 계획과 실제, 미확인과 0을 구분한다

- Quote는 공급자가 제시한 조건의 버전된 스냅샷이다.
- DRAFT Quote는 같은 레코드에서 수정하며 라인 변경도 optimistic lock 버전을 증가시킨다.
- RECEIVED 이후 Quote는 수정하지 않는다. 공급 조건을 정정하거나 다시 받으면 이전 Quote를
  가리키는 새 DRAFT 개정본을 만들고, 개정 이력은 선형으로 보존한다.
- 견적번호는 조직 전체가 아니라 공급처별로 관리하며 같은 개정 계열에서는 유지한다.
- CostScenario는 Quote를 바꾸지 않고 계산 입력과 결과를 보존한다.
- 실제 비용은 Purchase/Shipment/Receipt에서 발생한 별도 기록이다.
- 금액과 수량은 부동소수점이 아닌 decimal을 사용한다.
- 미확인 값은 nullable 또는 명시적 상태로 표현하고 숫자 0으로 대체하지 않는다.
- 통화·단위·원산지는 필요한 레코드에 함께 저장한다.

### 6. 자동화는 코어 업무 모델의 입력 어댑터다

- Spring만 코어 업무 DB를 읽고 쓴다.
- Python의 기존 코어 SQLAlchemy 매핑과 직접 쓰기 경로는 퇴역시킨다.
- 첫 수직 슬라이스는 수동 입력만으로 완결한다.
- 실제 비동기 자동화가 다시 필요할 때 작업 원장과 Outbox를 그 작업과 함께 도입한다.
- 현재 필요하지 않은 `automation`, `staging`, Alembic, Callback 테이블은 미리 만들지 않는다.

### 7. 빈 DB 기준으로 Flyway 이력을 재작성한다

- V01~V18의 누적 ALTER 이력은 새 기준선으로 교체한다.
- 현재 실제 사용하는 기능과 첫 수직 슬라이스에 필요한 테이블만 생성한다.
- 미구현 추적·통관·업무·파일·AI 테이블은 기준선에서 제외한다.
- 개발 예시 데이터와 외부 공급자 목록을 운영 migration에 넣지 않는다.
- 기존 버전 업그레이드 테스트는 빈 DB 설치와 제약·회귀 테스트로 교체한다.
- 적용 시 프로젝트 PostgreSQL 볼륨을 삭제하고 빈 DB에 전체 migration을 실행한다.

## 구현 순서

1. 문서 용어와 서비스 소유권을 이 결정에 맞춘다.
2. 기존 Research/클러스터/도매 프로토타입의 UI·API·DB 직접 쓰기를 제거한다.
3. 기존 사용 테이블을 최종 형태로 정리하고 Flyway 기준선을 재작성한다.
4. Product + BusinessPartner + Quote 수직 슬라이스를 구현한다.
5. 후보 등록부터 공급 견적 비교까지 조직 격리와 상태 규칙을 검증한다.
6. 실제 업무가 다음 단계로 넘어갈 때 Costing, Procurement, Logistics 배정을 차례로 추가한다.

## 승인과 파괴적 작업 경계

이 ADR의 도메인 방향과 단계별 진행은 승인됐다. 다음 작업은 별도 실행 사실을 명시한다.

- 기존 Research/클러스터/도매 프로토타입 코드와 테이블 제거
- V01~V18 삭제 및 새 기준선 적용
- `product-research-platform_postgres_data` Docker 볼륨 삭제

다른 Docker 볼륨, 운영 DB, 외부 계정과 데이터는 이 결정의 범위에 포함되지 않는다.

## 결과

Product와 거래처 원장을 다시 만들지 않고 발굴에서 소형 ERP 업무까지 확장할 수 있다.
현재 필요하지 않은 통관·자동화 인프라는 제거하여 SQL과 도메인 모델의 크기를 실제 업무
범위에 맞춘다. 새 기능은 Aggregate와 사용자 흐름이 생길 때 같은 수직 슬라이스로 추가한다.
