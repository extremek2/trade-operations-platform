# 6단계: 운영 적용 준비와 검증

> 역사 기록: V01~V18 운영 리허설 결과는 현재 V1/V2 빈 DB 기준선의 배포 검증을 대체하지 않는다. 현재 절차는 `ops/README.md`를 따른다.

- 작성일: 2026-09-08
- 상태: 코드·설정 템플릿·격리 리허설 완료. 실제 운영 설정·외부 메일 수신·배포는 미적용.
- 검증 기준: 이 문서의 운영 이미지·복원 리허설 기록은 V01~V17 기준이다. 2026-09-12 추가된 V18은 백엔드 테스트를 통과했지만 운영 이미지·복원 리허설 재실행 전이므로 배포 전 별도로 확인한다.
- 기준: [단계별 설계](../product/identity-access-approval-design-v1.md)
- 이전 단계: [건별 외부 참여](identity-stage-5.md)
- 운영 절차: [배포·전환·복구 실행서](../../ops/README.md)

## 이번 점검에서 보완한 문제

| 확인한 문제 | 변경 |
|---|---|
| 기존 Compose가 개발 서버·소스 마운트·공개 DB 포트 중심 | 별도 운영 Compose와 정적 프런트엔드 이미지 추가. DB·API는 외부 포트 없이 내부 통신 |
| CORS가 localhost로 고정 | 환경별 허용 Origin, 운영 HTTPS·이메일 주소 일치 검사 |
| 개발 비밀키나 메일 설정 누락으로 운영 기동 가능 | production 프로필에서 DB/Flyway 생성 전에 설정 검사. 개발 JWT·테스트 outbox 키·비보안 쿠키 거부 |
| 기존 프록시 배치에서 모든 요청 제한이 하나의 IP로 합쳐질 가능성 | 직접 HTTPS 종료 Nginx가 전달 헤더를 덮어쓰고 Tomcat은 해당 프록시 IP만 신뢰 |
| DB 관리자 계정과 앱 실행 계정이 동일 | 운영 Compose의 migrate 서비스와 런타임 DB 역할 분리. DDL·Flyway 이력 변경·감사 수정 차단 |
| 정상 서버 기동과 DB 변경이 결합 | 운영의 자동 Flyway 기본 비활성화. HTTP·메일 없이 끝나는 명시적 `--migrate-only` 명령 |
| 개발 설치 폴더에서는 성공하지만 새 `npm ci`에서 실패 | 누락된 tailwindcss 하위 yaml 잠금 항목 1개 복구. Node 20의 빈 설치 환경에서 재생성·차이 확인 |
| 서버 예외 메시지가 클라이언트에 노출 | 예상하지 못한 500 응답은 일반 오류 메시지로 제한 |
| 복원된 예전 토큰·초대가 다시 사용될 수 있음 | 새 DB에만 복원하는 절차와 복구 DB의 세션·링크·발송 대기·외부 참여 철회 SQL 추가 |

새 운영 프로필은 가입 승인·직원·화물·외부 협업 릴리스를 대상으로 한다. 기존 상품 리서치 API는
공개 API이고 AI 서버의 별도 권한 설계도 아직 연결되지 않았다. 따라서 운영 프로필에서
products/clusters API를 차단하고 운영 프런트에서는 리서치 메뉴·경로를 제외한다.
개발 Compose와 개발 화면에서는 기존 리서치를 유지한다. 리서치 운영 공개는 별도 작업이다.

## 준비한 산출물

- `ops/compose.production.yml`: 개발 Compose와 독립된 PostgreSQL·API·HTTPS 정적 웹 구성.
- `ops/.env.production.example`: 실제 값 없이 검토 가능한 설정 템플릿. 운영 파일·인증서·백업은 Git 제외.
- `application-production.yml`, `ProductionConfiguration`: 설정 검증, SMTP 인증·TLS, 문서 API 비활성화.
- `frontend/Dockerfile.production`, `nginx.production.conf`: 같은 Origin의 `/api/v1`, SPA 직접 진입,
  TLS, 인증 응답·HTML no-store, 외부 actuator 차단, 요청 헤더 정리.
- `ops/scripts/backup.sh`: DB 전체 custom dump, 별도 파일, 목차 검사·체크섬. 복원 성공까지 보장하는 도구는 아니다.
- `ops/scripts/restore-to-new-db.sh`: 기존 DB 삭제·덮어쓰기 없이 새 DB 생성·복원.
- `ops/sql/preflight.sql`, `postflight.sql`: 읽기 전용 전환 전후 검사. 사용자 이메일·토큰·암호화 본문은 출력하지 않는다.
- `ops/sql/expire-pending-mail.sql`: 명시적인 만료 메일 정리 작업.
- `ops/sql/invalidate-restored-access.sql`: 복원 DB에서만 적용할 세션·외부 참여 무효화 작업.
- `ops/sql/grant-runtime-role.sql`: 마이그레이션 후 런타임 역할과 데이터 접근 권한 부여.
- `ops/scripts/test-api-image.py`: 운영 설정 차단·별도 마이그레이션·제한된 DB 계정 기동 검증.
- `ops/scripts/test-web-image.py`: 일회용 인증서·모의 API·컨테이너로 운영 웹 이미지 검사.

## 설정과 배포 경계

운영 서버·도메인·메일 제공자는 아직 지정받지 않았다. 현재 템플릿은 Linux Docker 서버에서
Nginx가 직접 HTTPS를 종료하는 배치다. 외부 로드밸런서·관리형 DB를 사용하는 환경이라면
프록시 신뢰 범위·TLS 종료 지점·연결 설정을 실제 환경에 맞게 조정해야 한다.

`MAIL_DISPATCH_ENABLED=false`가 기본이다. 준비 중에는 메일이 발송되지 않으며,
이 상태를 정상적인 공개 가입 서비스 운영으로 간주하면 안 된다. 실제 SMTP 제공자의
인증·TLS·발신 주소·도메인 검증과 테스트 수신 승인을 완료해야 공개한다.

production 프로필은 JWT 비밀값, 무작위 32바이트 outbox 키, DB 비밀번호, HTTPS Origin,
이메일 링크 경로, 토큰 수명 범위를 검사한다. 메일 활성화 시 인증과 TLS·서버 신원 확인을
요구한다. 검사는 값의 형태와 알려진 잘못된 기본값을 검증하며 비밀값의 생성 품질이나
제공자 설정의 실제 유효성까지 증명하지 않는다. 설정값 자체를 오류에 포함하지 않는다.

## 검증 범위와 남은 일

이번 검증 결과:

- 백엔드 전체 92개 통과, bootJar 성공. 실행 계정 분리 보완 후 복원·권한 제한 리허설도 재검증했다.
- 프런트엔드 전체 32개와 기존 브라우저 E2E 4개 통과.
- API·정적 웹 Docker 이미지 빌드 성공. 정적 웹은 Node 20의 깨끗한 `npm ci`부터 검증했다.
- 운영 API 이미지: 잘못된 개발 설정은 DB 연결 전에 거부, 당시 production에서 V01~V17 별도
  마이그레이션, 제한된 DB 계정으로 정상 readiness, HTTPS CORS·Secure 쿠키 확인.
- 운영 웹 이미지: 로컬 인증서로 HTTPS 검증, 이메일·관리자·직원 SPA 직접 진입, API 프록시,
  전달 IP 헤더 덮어쓰기, no-store, 외부 actuator·없는 정적 파일 차단 확인.
- Compose 예제 문법, 운영 스크립트 구문, 문서 상대 링크, `git diff --check` 통과.

자동 검증 명령은 백엔드 `./gradlew test bootJar --no-daemon`, 프런트엔드
`CI=true npm test -- --watchAll=false --runInBand`와 `npm run test:e2e`다.
이미지 검증은 프로젝트 루트에서 `python3 ops/scripts/test-api-image.py trade-ops-api:stage6-test`,
`python3 ops/scripts/test-web-image.py trade-ops-web:stage6-test`로 수행했다.
테스트용 이미지 태그는 실제 릴리스 태그·승인을 대체하지 않는다.

로컬 SMTP 서버를 실제 소켓으로 띄워 JavaMail의 한글 본문·수신자·메시지 식별 헤더와
수신 거부 시 오류 전달을 검증한다. 이는 실제 외부 제공자의 TLS·전달률·스팸 분류 검증은 아니다.
기존 outbox 테스트는 암호화, 발송 실패 재시도, 성공·만료·최종 실패 시 본문 삭제를 검증한다.

DB 리허설은 일회용 PostgreSQL에서 V13 자료 생성→pg_dump→새 DB 복원→실제 migrate-only
진입점으로 당시 최신 V17 전환→운영 SQL 검사→다시 백업·복원→복구 토큰 무효화를 수행했다.
원본 DB·OWNER·화물 보존과 복원된 외부 참여 차단을 확인한다. 실제 운영 데이터의 규모,
중복·누락·외부 연락처 매핑, 백업 소요시간과 복구 목표는 대상 DB에서 별도로 확인해야 한다.

운영 공개 전 미완료 항목은 다음과 같다.

- 실제 배포 서버·도메인·인증서·DB 연결 대상과 릴리스 이미지 식별값 확정.
- 실제 DB 사전 검사 및 백업 복원 리허설, 조직 OWNER·연락처 매핑 검토.
- 실제 관리자 초기 생성과 계정 접근 확인. 비밀번호·키는 대화나 소스에 전달하지 않는다.
- 승인받은 수신자에 대한 외부 SMTP 발송·메일함 수신·링크 접속 확인.
- 백업 주기·보관·암호화, 감사 접근 권한, 발송 실패 모니터링과 대응 담당자 확정.
- 회원 비밀번호 복구·직원 전용 가입 초대·다중 조직 선택·상품 리서치 운영 공개는 현재 기능 범위 밖이다.

구조 변경 가능성을 줄이는 기반과 검증은 갖췄지만, 실환경·실데이터 검증 없이 추가 수정이
없다고 보장하지 않는다. 특히 복원 시점 이후의 권한 변경은 백업만으로 복구되지 않으므로
감사 기록을 대조하고 권한을 정리한 후 서비스를 다시 열어야 한다.
