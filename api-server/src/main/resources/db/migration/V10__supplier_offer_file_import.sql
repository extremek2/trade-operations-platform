ALTER TABLE source_artifact ALTER COLUMN original_text DROP NOT NULL;
ALTER TABLE source_artifact DROP CONSTRAINT source_artifact_source_type_check;
ALTER TABLE source_artifact
    ADD CONSTRAINT source_artifact_source_type_check CHECK (source_type IN ('MANUAL', 'CSV', 'XLSX'));
ALTER TABLE source_artifact ADD COLUMN original_file_name VARCHAR(255);
ALTER TABLE source_artifact ADD COLUMN binary_content BYTEA;
ALTER TABLE source_artifact ADD COLUMN file_size BIGINT CHECK (file_size IS NULL OR file_size > 0);
ALTER TABLE source_artifact ADD CONSTRAINT source_artifact_payload_check CHECK (
    (source_type = 'MANUAL' AND original_text IS NOT NULL AND binary_content IS NULL) OR
    (source_type IN ('CSV', 'XLSX') AND original_file_name IS NOT NULL AND binary_content IS NOT NULL AND file_size > 0)
);

ALTER TABLE extraction_run DROP CONSTRAINT extraction_run_method_check;
ALTER TABLE extraction_run
    ADD CONSTRAINT extraction_run_method_check CHECK (method IN ('MANUAL', 'CSV', 'XLSX'));

ALTER TABLE supplier_offer_draft_line ADD COLUMN extraction_errors TEXT;
