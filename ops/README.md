# 운영 실행서

이 문서는 현재 V1/V2 빈 DB 기준선의 운영 절차다. 실제 운영 DB·관리자 생성·메일 발송·서비스
공개는 대상과 결과를 확인한 뒤 별도 승인받아 실행한다.

## 1. 현재 배포 범위

- React Web, Spring Boot API, PostgreSQL 15
- Flyway V1: 인증·조직·온보딩·화물·외부 협업
- Flyway V2: Product·BusinessPartner·SupplierQuote
- Research Python, AI Worker, Celery, Redis는 포함하지 않는다.
- 기존 V01~V18 DB를 V1/V2로 직접 올리는 migration 경로는 제공하지 않는다.

현재 프로젝트 DB는 보존 데이터가 없는 개발 볼륨을 삭제하고 새 기준선으로 시작한다. 데이터가 있는
DB를 발견하면 이 절차를 적용하지 말고 읽기 전용 조사, 백업, 필드 매핑, 리허설을 포함한 별도 전환
계획을 승인받는다. Flyway `baseline`이나 `repair`로 우회하지 않는다.

## 2. 배포 전 결정

- Linux Docker 서버, 실제 HTTPS 도메인, 인증서를 정한다.
- `172.30.88.0/24`가 기존 네트워크와 겹치지 않는지 확인한다.
- SMTP 제공자, TLS 방식, 발신 주소와 테스트 수신자를 정한다.
- `ops/.env.production.example`을 `ops/.env.production`으로 복사하되 기존 파일을 덮어쓰지 않는다.
- 환경 파일 권한은 600으로 제한하고 Git에 넣지 않는다.
- `JWT_SECRET`과 `MAIL_OUTBOX_KEY`는 서로 다른 값으로 생성한다. Outbox 키는 Base64 32바이트다.

문법 검사와 이미지 빌드:

```bash
./ops/scripts/compose.sh config --quiet
./ops/scripts/compose.sh build
```

프론트 API는 빌드 시 `/api/v1`로 고정된다. API·DB 포트는 외부에 공개하지 않는다. 이미지
다이제스트, 소스 커밋, Flyway 버전, 승인자를 릴리스 기록에 남긴다.

## 3. 새 DB 설치

```bash
./ops/scripts/compose.sh up -d postgres
./ops/scripts/compose.sh run --rm --no-deps migrate
./ops/scripts/compose.sh exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1' < ops/sql/postflight.sql
```

마이그레이션 계정과 앱 런타임 계정은 분리한다. 스키마 적용 후 런타임 권한을 부여한다.

```bash
./ops/scripts/compose.sh exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1' < ops/sql/grant-runtime-role.sql
```

`migrate` 프로필은 웹 포트나 메일 작업을 시작하지 않는다. 일반 production API는 자동
마이그레이션하지 않으며 스키마가 없거나 버전이 다르면 기동에 실패해야 한다.

## 4. 최초 관리자와 공개

새 DB에 초기 관리자가 없을 때 승인된 운영자가 대화형 터미널에서 실행한다.

```bash
./ops/scripts/compose.sh run --rm --no-deps api-server --bootstrap-admin
```

이 명령은 기존 계정 승격, 추가 관리자 생성, 비밀번호 덮어쓰기를 하지 않는다. 실제 SMTP 테스트
수신자가 승인되고 대기 Outbox를 확인한 뒤에만 `MAIL_DISPATCH_ENABLED=true`로 바꾼다.

```bash
./ops/scripts/compose.sh up -d api-server frontend
```

공개 전 확인:

- 가입 확인, 조직 승인, 로그인·새로고침·로그아웃을 실제 HTTPS에서 확인한다.
- 직원 권한과 외부 건별 초대·철회 후 접근 차단을 확인한다.
- 상품 등록과 조직 간 상품 조회 차단을 확인한다.
- 다른 Origin 요청이 차단되고 Secure/HttpOnly/SameSite 쿠키가 적용되는지 확인한다.
- 의도한 DB인지, 5432/8080이 외부에 열리지 않았는지 확인한다.

## 5. 백업과 복구

운영 전부터 백업 주기·보관·암호화·복원 리허설을 정한다. 백업은 개인정보와 암호문을 포함하므로
접근을 제한하며 암호화 키는 DB 백업과 별도로 보관한다.

```bash
./ops/scripts/backup.sh ops/backups
./ops/scripts/restore-to-new-db.sh /안전한경로/backup.dump trade_ops_recovered
```

- 운영 DB를 지우거나 덮어쓰지 않고 항상 새 DB로 복원한다.
- 복원 대상과 `current_database()`를 확인하고 메일·외부 트래픽을 끈다.
- 복원된 세션과 링크를 무효화한 뒤 권한·감사 이력을 대조한다.

```bash
./ops/scripts/compose.sh exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d trade_ops_recovered -v ON_ERROR_STOP=1' < ops/sql/invalidate-restored-access.sql
```

앱 이미지와 DB migration 버전이 일치하는지 확인한 뒤 연결을 전환한다. 토큰 무효화만으로 백업
이후의 권한 변경을 복구할 수 없으므로 대조가 끝나기 전에는 외부에 공개하지 않는다.

## 6. 반복 운영

- `postflight.sql`로 Flyway, 활성 관리자, 세션, 신청, 초대 정원, Outbox 상태를 확인한다.
- SMTP 실패, 미발송 Outbox, 오래된 신청을 점검한다.
- 의존성·권한·감사 로그와 실제 복원 가능성을 정기 검증한다.
- 새 테이블을 추가한 migration 후 런타임 권한 스크립트를 다시 적용한다.
- 데이터가 생긴 뒤 V1/V2를 수정하지 않고 V3부터 순방향 migration을 추가한다.
