ALTER TABLE source_artifact DROP CONSTRAINT source_artifact_source_type_check;
ALTER TABLE source_artifact ADD CONSTRAINT source_artifact_source_type_check
    CHECK (source_type IN ('MANUAL', 'CSV', 'XLSX', 'PDF', 'IMAGE', 'API'));
ALTER TABLE source_artifact DROP CONSTRAINT source_artifact_payload_check;
ALTER TABLE source_artifact ADD CONSTRAINT source_artifact_payload_check CHECK (
    (source_type IN ('MANUAL', 'API') AND original_text IS NOT NULL AND binary_content IS NULL AND object_key IS NULL) OR
    (source_type IN ('CSV', 'XLSX') AND original_file_name IS NOT NULL AND binary_content IS NOT NULL AND file_size > 0 AND object_key IS NULL) OR
    (source_type IN ('PDF', 'IMAGE') AND original_file_name IS NOT NULL AND storage_backend = 'S3'
        AND object_bucket IS NOT NULL AND object_key IS NOT NULL AND binary_content IS NULL AND file_size > 0)
);
ALTER TABLE extraction_run DROP CONSTRAINT extraction_run_method_check;
ALTER TABLE extraction_run ADD CONSTRAINT extraction_run_method_check
    CHECK (method IN ('MANUAL', 'CSV', 'XLSX', 'DOCUMENT', 'PDF_TEXT', 'OCR', 'API'));
