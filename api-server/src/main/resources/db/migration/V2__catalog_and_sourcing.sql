CREATE TABLE product (
    id              BIGSERIAL PRIMARY KEY,
    public_id       UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    organization_id BIGINT NOT NULL REFERENCES organization(id) ON DELETE RESTRICT,
    name            TEXT NOT NULL,
    hypothesis      TEXT,
    internal_sku    TEXT,
    brand           TEXT,
    category        TEXT,
    status          TEXT NOT NULL DEFAULT 'DISCOVERED' CHECK (status IN (
        'DISCOVERED', 'SOURCING', 'REVIEWING', 'APPROVED',
        'ACTIVE', 'REJECTED', 'DISCONTINUED'
    )),
    version         BIGINT NOT NULL DEFAULT 0,
    created_by      BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX uq_product_internal_sku ON product(organization_id, internal_sku)
    WHERE internal_sku IS NOT NULL;
CREATE INDEX idx_product_board ON product(organization_id, status, updated_at DESC);

CREATE TABLE product_status_history (
    id              BIGSERIAL PRIMARY KEY,
    product_id      BIGINT NOT NULL REFERENCES product(id) ON DELETE RESTRICT,
    from_status     TEXT,
    to_status       TEXT NOT NULL,
    reason          TEXT NOT NULL,
    changed_by      BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    changed_at      TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_product_status_history ON product_status_history(product_id, changed_at DESC);

CREATE TABLE supplier_quote (
    id                  BIGSERIAL PRIMARY KEY,
    public_id           UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    organization_id     BIGINT NOT NULL REFERENCES organization(id) ON DELETE RESTRICT,
    business_partner_id BIGINT NOT NULL REFERENCES business_partner(id) ON DELETE RESTRICT,
    previous_quote_id   BIGINT UNIQUE REFERENCES supplier_quote(id) ON DELETE RESTRICT,
    revision_number     INTEGER NOT NULL DEFAULT 1 CHECK (revision_number > 0),
    quote_number        TEXT,
    currency            VARCHAR(3) NOT NULL CHECK (currency = upper(currency)),
    status              TEXT NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'RECEIVED', 'SELECTED', 'EXPIRED', 'REJECTED')),
    quoted_at           DATE,
    valid_until         DATE,
    notes               TEXT,
    version             BIGINT NOT NULL DEFAULT 0,
    created_by          BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    CHECK (valid_until IS NULL OR quoted_at IS NULL OR valid_until >= quoted_at)
);
CREATE UNIQUE INDEX uq_supplier_quote_revision
    ON supplier_quote(organization_id, business_partner_id, quote_number, revision_number)
    WHERE quote_number IS NOT NULL;
CREATE INDEX idx_supplier_quote_partner ON supplier_quote(organization_id, business_partner_id, quoted_at DESC);

CREATE TABLE supplier_quote_line (
    id                BIGSERIAL PRIMARY KEY,
    quote_id          BIGINT NOT NULL REFERENCES supplier_quote(id) ON DELETE RESTRICT,
    product_id        BIGINT NOT NULL REFERENCES product(id) ON DELETE RESTRICT,
    line_number       INTEGER NOT NULL CHECK (line_number > 0),
    supplier_sku      TEXT,
    description       TEXT NOT NULL,
    minimum_quantity  NUMERIC(19, 6) CHECK (minimum_quantity IS NULL OR minimum_quantity > 0),
    quantity_unit     TEXT,
    unit_price        NUMERIC(19, 4) CHECK (unit_price IS NULL OR unit_price >= 0),
    country_of_origin VARCHAR(2),
    lead_time_days    INTEGER CHECK (lead_time_days IS NULL OR lead_time_days >= 0),
    notes             TEXT,
    UNIQUE (quote_id, line_number)
);
CREATE INDEX idx_supplier_quote_line_product ON supplier_quote_line(product_id);
