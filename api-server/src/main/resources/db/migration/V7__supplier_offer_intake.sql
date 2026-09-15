CREATE TABLE source_artifact (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    organization_id BIGINT NOT NULL REFERENCES organization(id) ON DELETE RESTRICT,
    supplier_id BIGINT NOT NULL REFERENCES business_partner(id) ON DELETE RESTRICT,
    source_type TEXT NOT NULL CHECK (source_type IN ('MANUAL')),
    source_reference TEXT,
    original_text TEXT NOT NULL CHECK (length(btrim(original_text)) > 0),
    content_type TEXT NOT NULL DEFAULT 'text/plain',
    content_hash VARCHAR(64) NOT NULL,
    received_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    UNIQUE (organization_id, supplier_id, content_hash)
);
CREATE INDEX idx_source_artifact_supplier ON source_artifact(organization_id, supplier_id, received_at DESC);

CREATE TABLE extraction_run (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    source_artifact_id BIGINT NOT NULL REFERENCES source_artifact(id) ON DELETE RESTRICT,
    method TEXT NOT NULL CHECK (method IN ('MANUAL')),
    extractor_version TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('SUCCEEDED')),
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (source_artifact_id, extractor_version)
);

CREATE TABLE supplier_offer_draft (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    organization_id BIGINT NOT NULL REFERENCES organization(id) ON DELETE RESTRICT,
    supplier_id BIGINT NOT NULL REFERENCES business_partner(id) ON DELETE RESTRICT,
    extraction_run_id BIGINT NOT NULL UNIQUE REFERENCES extraction_run(id) ON DELETE RESTRICT,
    currency VARCHAR(3) NOT NULL CHECK (currency = upper(currency)),
    status TEXT NOT NULL DEFAULT 'REVIEW_REQUIRED' CHECK (status IN ('REVIEW_REQUIRED', 'CONFIRMED')),
    version BIGINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_supplier_offer_draft_board ON supplier_offer_draft(organization_id, status, created_at DESC);

CREATE TABLE supplier_offer_draft_line (
    id BIGSERIAL PRIMARY KEY,
    supplier_offer_draft_id BIGINT NOT NULL REFERENCES supplier_offer_draft(id) ON DELETE RESTRICT,
    line_number INTEGER NOT NULL CHECK (line_number > 0),
    original_name TEXT NOT NULL CHECK (length(btrim(original_name)) > 0),
    source_location TEXT,
    reviewed_name TEXT,
    supplier_sku TEXT,
    quantity_unit TEXT,
    minimum_quantity NUMERIC(19,6) CHECK (minimum_quantity IS NULL OR minimum_quantity > 0),
    unit_price NUMERIC(19,4) CHECK (unit_price IS NULL OR unit_price >= 0),
    country_of_origin VARCHAR(2),
    notes TEXT,
    status TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'CONFIRMED', 'EXCLUDED')),
    reviewed_at TIMESTAMP,
    reviewed_by BIGINT REFERENCES app_user(id) ON DELETE RESTRICT,
    UNIQUE (supplier_offer_draft_id, line_number),
    CHECK ((status = 'PENDING' AND reviewed_at IS NULL) OR (status <> 'PENDING' AND reviewed_at IS NOT NULL))
);
