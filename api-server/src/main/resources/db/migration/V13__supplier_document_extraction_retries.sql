ALTER TABLE extraction_run
    DROP CONSTRAINT extraction_run_source_artifact_id_extractor_version_key;

CREATE UNIQUE INDEX uq_extraction_run_structured_source_version
    ON extraction_run(source_artifact_id, extractor_version)
    WHERE method IN ('MANUAL', 'CSV', 'XLSX');

CREATE INDEX idx_extraction_run_artifact_attempt
    ON extraction_run(source_artifact_id, id DESC);
