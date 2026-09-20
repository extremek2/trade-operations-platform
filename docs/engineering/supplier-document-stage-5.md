# P6 다섯 번째 단계: 공급 문서 보관과 OCR

- 날짜: 2026-09-20
- 상태: 합성 PDF·블러 스캔과 개발용 MinIO 기준 구현·검증 완료
- 운영 적용: 미승인. 실제 공급처 샘플·보관 정책·운영용 S3 제공자 결정 필요

## 구현 범위

`POST /api/v1/supplier-documents/import`는 15MB 이하 PDF, PNG, JPEG의 확장자와 magic bytes를
둘 다 검증한다. 원본은 DB `BYTEA`가 아닌 `ArtifactObjectStore` 경계를 통해 S3 호환
저장소에 보관하고 DB에는 bucket, key, ETag, 크기와 SHA-256을 남긴다. API 응답은 bucket과
key를 노출하지 않는다. 다운로드 시 크기와 SHA-256을 다시 확인한다.

OCR worker는 PDF의 내장 텍스트가 충분하면 `pdftotext`로 즉시 반환한다. 스캔 PDF와
이미지는 Tesseract(`kor+eng+jpn`)로 원본 후보와 OpenCV CLAHE·업스케일·언샤프·노이즈
제거 후보를 모두 평가하고 품질 점수가 더 나은 결과만 선택한다. 이 안티블러 경로는
모두 Apache 2.0 또는 오픈소스 런타임으로 구성하며 외부 유료 API를 호출하지 않는다.

OCR 실패는 원본 삭제나 전체 요청 rollback으로 연결하지 않는다. `ExtractionRun` 상태를
`FAILED`로 남기고 `reviewRequired=true`로 보존한다. 신뢰도 85 미만인 성공 결과도 사람
검토 대상이다.

## 개발 실행

MinIO Community는 바이너리 배포가 종료된 상태이므로 개발·테스트에서만 공식 소스의
`RELEASE.2025-10-15T17-29-55Z`를 빌드한다. 운영은 S3 계약을 유지하는 별도 제공자를
검토해야 한다.

```bash
OBJECT_STORAGE_ENABLED=true OCR_ENABLED=true docker compose --profile documents up --build
```

기본 `docker compose up`은 문서 서비스를 활성화하지 않으며, 예제 credential은 로컬 개발용이다.

## 검증 결과와 남은 경계

- 텍스트 PDF는 OCR을 우회하고 한글·SKU 텍스트를 보존했다.
- 블러 스캔은 원본·안티블러 두 후보를 실행하고 흐림 감지와 SKU 인식을 확인했다.
- 직접 빌드한 MinIO에 S3 put/get/delete 왕복과 bucket 자동 생성을 검증했다.
- PostgreSQL 통합 테스트는 보관·추출·멱등 재업로드·원본 다운로드·조직 경계·OCR
  실패 시 원본 보존·형식 위조 거절을 확인했다.
- 합성 문서는 계약 검증용이며 실제 문서의 스탬프, 손글씨, 기울임, 다단 표는 입증하지 않는다.
