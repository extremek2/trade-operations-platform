ALTER TABLE source_artifact DROP CONSTRAINT source_artifact_source_type_check;
ALTER TABLE source_artifact
    ADD CONSTRAINT source_artifact_source_type_check CHECK (source_type IN ('MANUAL', 'CSV', 'XLSX', 'PDF', 'IMAGE'));
ALTER TABLE source_artifact ADD COLUMN storage_backend VARCHAR(20);
ALTER TABLE source_artifact ADD COLUMN object_bucket VARCHAR(255);
ALTER TABLE source_artifact ADD COLUMN object_key VARCHAR(1000);
ALTER TABLE source_artifact ADD COLUMN object_etag VARCHAR(255);
ALTER TABLE source_artifact DROP CONSTRAINT source_artifact_payload_check;
ALTER TABLE source_artifact ADD CONSTRAINT source_artifact_payload_check CHECK (
    (source_type = 'MANUAL' AND original_text IS NOT NULL AND binary_content IS NULL AND object_key IS NULL) OR
    (source_type IN ('CSV', 'XLSX') AND original_file_name IS NOT NULL AND binary_content IS NOT NULL
        AND file_size > 0 AND object_key IS NULL) OR
    (source_type IN ('PDF', 'IMAGE') AND original_file_name IS NOT NULL AND binary_content IS NULL
        AND file_size > 0 AND storage_backend = 'S3' AND object_bucket IS NOT NULL AND object_key IS NOT NULL)
);
CREATE UNIQUE INDEX uq_source_artifact_object_location
    ON source_artifact(object_bucket, object_key) WHERE object_key IS NOT NULL;

ALTER TABLE extraction_run DROP CONSTRAINT extraction_run_method_check;
ALTER TABLE extraction_run
    ADD CONSTRAINT extraction_run_method_check CHECK (method IN ('MANUAL', 'CSV', 'XLSX', 'DOCUMENT', 'PDF_TEXT', 'OCR'));
ALTER TABLE extraction_run DROP CONSTRAINT extraction_run_status_check;
ALTER TABLE extraction_run
    ADD CONSTRAINT extraction_run_status_check CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED'));
ALTER TABLE extraction_run ADD COLUMN extracted_text TEXT;
ALTER TABLE extraction_run ADD COLUMN confidence NUMERIC(5,2) CHECK (confidence IS NULL OR (confidence >= 0 AND confidence <= 100));
ALTER TABLE extraction_run ADD COLUMN page_count INTEGER CHECK (page_count IS NULL OR page_count > 0);
ALTER TABLE extraction_run ADD COLUMN preprocessing_applied BOOLEAN;
ALTER TABLE extraction_run ADD COLUMN review_required BOOLEAN;
ALTER TABLE extraction_run ADD COLUMN result_payload TEXT;
ALTER TABLE extraction_run ADD COLUMN completed_at TIMESTAMP;
