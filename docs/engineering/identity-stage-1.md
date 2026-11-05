# 1단계: 시스템 권한과 인증 세션 기반

> 역사 기록: 이 문서의 migration 번호는 작성 당시 기준이다. 현재 빈 DB 설치 기준은 ADR-0002의 V1/V2이며 이 절차를 재실행하지 않는다.

- 작성일: 2026-09-08
- 상태: 구현·검증 완료. 이후 단계의 변경 사항은 각 단계 문서를 따른다.
- 설계: [가입 승인 및 참여 권한](../product/identity-access-approval-design-v1.md)
- 범위: 시스템 권한·세션·감사·최초 관리자 초기화. 가입 승인 업무와 화면은 2·3단계다.

## 구현 내용

- SYSTEM_ADMIN은 platform_role_assignment로 관리하며 화주 organization_member와 분리한다.
- ACCOUNT / ORGANIZATION / PLATFORM 세션을 지원한다. 조직 없는 사용자도 인증 가능하다.
- JWT의 session_id로 DB 세션을 확인하고 현재 DB 역할로 요청 권한을 구성한다.
  토큰의 role 값을 그대로 승인 근거로 사용하지 않는다.
- 사용자·조직 비활성화, 조직 소속 비활성화, 시스템 역할 철회, 로그아웃은 이후 요청부터 반영한다.
- 시스템관리자 세션은 화물 API 접근 권한을 자동으로 갖지 않는다.
- refresh 토큰은 행 잠금 아래 같은 세션에서 회전한다. 이전 refresh 토큰은 재사용할 수 없다.
  갱신 성공만으로 이미 발급된 access 토큰을 무효화하지 않는다.
- 세션 만료 시각은 최초 로그인 시 정한 기한으로 고정한다. 갱신마다 기한을 늘리지 않는다.
- 컨텍스트 전환은 기존 세션을 철회하고 서버 권한 검증 후 새 세션을 발급한다.
- 프런트엔드의 동일 탭 내 동시 refresh 요청은 하나의 진행 중인 요청을 공유한다.
  여러 탭 사이의 갱신 조정은 후속 인증 화면 단계에서 보완할 항목이다.
- identity_audit_event에 초기 SYSTEM_ADMIN 부여를 같은 트랜잭션에서 기록한다.
  승인·역할 변경 등 후속 행위의 기록은 해당 단계에서 연결한다.

## API

| 요청 | 용도 |
|---|---|
| POST /api/v1/auth/platform/login | email/password로 시스템관리자 세션 발급 |
| POST /api/v1/auth/login | 기존 화주 로그인, 소속 없는 계정은 ACCOUNT 세션 |
| POST /api/v1/auth/context | kind와 필요한 경우 organizationId로 컨텍스트 전환 |
| GET /api/v1/auth/me | 현재 사용자·컨텍스트 확인 |
| GET /api/v1/system-admin/session | 시스템관리자 전용 권한 확인 |
| POST /api/v1/auth/refresh | refresh 쿠키 교환 |
| POST /api/v1/auth/logout | refresh 쿠키의 세션 철회 |

AuthResponse.user의 기존 화주 필드는 유지한다. sessionKind와 systemAdmin을 추가했다.
조직 없는 컨텍스트에서는 organizationId, organizationName, role이 null이다.
systemAdmin은 현재 PLATFORM 컨텍스트인지 표시하며 전체 보유 역할 목록은 아니다.

1단계 완료 시점에는 일반 가입이 OWNER를 생성했다. 이후
[2단계](identity-stage-2.md)에서 승인 대기 신청으로 변경했다. 시스템관리자 페이지는
3단계에서 구현하며, 중간 버전을 가입 승인 기능 완성본으로 배포하지 않는다.

## 최초 시스템관리자 설정

실제 운영자 계정은 아직 생성하지 않았다. 아래는 해당 운영 단계 승인 후 사용할 절차다.

1. 대상 DB를 확인하고 백업한다. 서버의 datasource 환경 변수로 연결 대상을 설정한다.
2. api-server에서 실행 JAR을 만든다.
3. 대화형 터미널에서 초기화 명령을 명시적으로 실행한다.

```bash
./gradlew bootJar
java -jar build/libs/api-server-0.0.1-SNAPSHOT.jar --bootstrap-admin
```

명령은 웹 서버를 열지 않고 Flyway 적용 후 이메일·이름·비밀번호를 터미널에서 받는다.
비밀번호는 화면에 표시하지 않으며 12자 이상, UTF-8 72바이트 이하를 요구한다.
비밀번호를 명령 인수·소스·로그로 전달하지 않는다. 입력 배열은 사용 후 지운다.
Spring PasswordEncoder 호출 과정의 String 객체까지 즉시 메모리에서 제거한다고 보장하지 않는다.

- 새 사용자 + 시스템 역할 + 감사 이력을 하나의 트랜잭션으로 생성한다.
- 이미 활성 시스템관리자인 동일 이메일로 재실행하면 변경 없이 종료한다.
- 기존 일반 사용자 승격, 폐기된 관리자 재활성화, 비밀번호 덮어쓰기는 거부한다.
- 최초 관리자 설정 이후 다른 이메일로 추가 관리자 생성도 거부한다.
- 동시 초기화는 PostgreSQL 트랜잭션 advisory lock으로 직렬화한다.
- 비대화형 실행이나 프로필만 지정한 실행은 실패한다.
- 정상 서버 실행에는 초기화 프로필과 명령을 지정하지 않는다.

## DB 전환

V14만 추가했으며 V01~V13은 수정하지 않았다.

- platform_role_assignment, identity_audit_event 추가.
- refresh_session.public_id와 session_kind 추가.
- 기존 세션은 ORGANIZATION으로 백필하고 조직 없는 세션에 대한 CHECK 제약 적용.
- 기존 조직·OWNER·화물은 그대로 유지.
- session_id가 없는 기존 access 토큰은 401로 처리한다. 유효한 기존 refresh 쿠키가 있으면
  갱신 API에서 새 access 토큰을 발급받을 수 있고, 그렇지 않으면 다시 로그인해야 한다.

DB 스키마를 되돌리는 SQL이나 자동 데이터 삭제는 제공하지 않는다. 실제 적용 및 복구
절차는 6단계에서 백업·호환 범위를 확인한 후 실행한다.

## 검증 방법

백엔드 테스트에는 Docker가 필요하다. Testcontainers가 임시 PostgreSQL 15 컨테이너를
생성하며 실제 서비스 DB를 사용하지 않는다.

```bash
# api-server 디렉터리
./gradlew test bootJar
```

관리되는 Testcontainers의 docker-java 기본 API 1.32가 현재 Docker 서버에서 거부되어,
테스트 JVM의 api.version 기본값을 1.40으로 지정했다. DOCKER_API_VERSION 환경 변수로
테스트 환경에 맞춰 변경할 수 있다.

```bash
# frontend 디렉터리
CI=true npm test -- --watchAll=false --runInBand
```

검증 범위: 새 DB 기동, V13 데이터의 V14 업그레이드, 기존 화주 로그인, 조직 없는
계정·시스템관리자, 조직·시스템 권한 분리, 권한 회수 및 계정 비활성화, 로그아웃,
만료·기존 토큰 차단, 컨텍스트 전환, 동시 refresh 1회 성공, 관리자 초기화 중복 방지와
감사 실패 시 롤백, 기존 프런트 화면 및 refresh 요청 공유.

## 이번 검증 결과 (2026-09-08)

- 백엔드 전체 테스트 23개 통과 및 bootJar 빌드 성공.
- 추가한 비대화형 관리자 초기화 테스트 1개 별도 실행 통과: 총 24개 검증.
- 프런트엔드 테스트 4개 통과.
- git diff --check 통과.
- 실제 서비스 DB 변경, 실제 관리자 생성, 이메일 발송, 배포는 수행하지 않았다.

2단계 가입 승인 백엔드 구현 결과는 [2단계 문서](identity-stage-2.md)를 참조한다.
