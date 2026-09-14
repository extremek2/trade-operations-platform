CREATE TABLE actual_cost_item (
    id                    BIGSERIAL PRIMARY KEY,
    public_id             UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    organization_id       BIGINT NOT NULL REFERENCES organization(id) ON DELETE RESTRICT,
    purchase_order_id     BIGINT NOT NULL REFERENCES purchase_order(id) ON DELETE RESTRICT,
    cost_type             TEXT NOT NULL CHECK (cost_type IN (
        'PRODUCT', 'FREIGHT', 'DUTY', 'TAX', 'CLEARANCE', 'INSPECTION',
        'WAREHOUSE', 'DOMESTIC_DELIVERY', 'OTHER'
    )),
    description           TEXT NOT NULL,
    amount_krw            NUMERIC(19,2) NOT NULL CHECK (amount_krw >= 0),
    incurred_at           DATE NOT NULL,
    evidence_reference    TEXT,
    created_by            BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    created_at            TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE actual_cost_close (
    id                    BIGSERIAL PRIMARY KEY,
    public_id             UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    organization_id       BIGINT NOT NULL REFERENCES organization(id) ON DELETE RESTRICT,
    purchase_order_id     BIGINT NOT NULL UNIQUE REFERENCES purchase_order(id) ON DELETE RESTRICT,
    expected_total_krw    NUMERIC(19,2) NOT NULL,
    actual_total_krw      NUMERIC(19,2) NOT NULL,
    variance_krw          NUMERIC(19,2) NOT NULL,
    received_quantity     NUMERIC(19,6) NOT NULL CHECK (received_quantity > 0),
    actual_unit_cost_krw  NUMERIC(19,2) NOT NULL CHECK (actual_unit_cost_krw >= 0),
    closed_by             BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    closed_at             TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE actual_cost_allocation (
    id                    BIGSERIAL PRIMARY KEY,
    cost_close_id         BIGINT NOT NULL REFERENCES actual_cost_close(id) ON DELETE RESTRICT,
    inventory_lot_id      BIGINT NOT NULL REFERENCES inventory_lot(id) ON DELETE RESTRICT,
    allocated_amount_krw  NUMERIC(19,2) NOT NULL CHECK (allocated_amount_krw >= 0),
    UNIQUE (cost_close_id, inventory_lot_id)
);

CREATE TABLE sales_observation (
    id                    BIGSERIAL PRIMARY KEY,
    public_id             UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    organization_id       BIGINT NOT NULL REFERENCES organization(id) ON DELETE RESTRICT,
    inventory_lot_id      BIGINT NOT NULL REFERENCES inventory_lot(id) ON DELETE RESTRICT,
    channel               VARCHAR(100) NOT NULL,
    observed_at           DATE NOT NULL,
    sold_quantity         NUMERIC(19,6) NOT NULL CHECK (sold_quantity >= 0),
    returned_quantity     NUMERIC(19,6) NOT NULL CHECK (returned_quantity >= 0),
    gross_revenue_krw     NUMERIC(19,2) NOT NULL CHECK (gross_revenue_krw >= 0),
    channel_cost_krw      NUMERIC(19,2) NOT NULL CHECK (channel_cost_krw >= 0),
    notes                 TEXT,
    created_by            BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    created_at            TIMESTAMP NOT NULL DEFAULT NOW(),
    CHECK (sold_quantity > 0 OR returned_quantity > 0)
);

CREATE TABLE reorder_decision (
    id                    BIGSERIAL PRIMARY KEY,
    public_id             UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    organization_id       BIGINT NOT NULL REFERENCES organization(id) ON DELETE RESTRICT,
    purchase_order_id     BIGINT NOT NULL REFERENCES purchase_order(id) ON DELETE RESTRICT,
    product_id            BIGINT NOT NULL REFERENCES product(id) ON DELETE RESTRICT,
    decision              TEXT NOT NULL CHECK (decision IN ('REORDER', 'WATCH', 'STOP')),
    reason                TEXT NOT NULL,
    decided_by            BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    decided_at            TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_actual_cost_item_order ON actual_cost_item(organization_id, purchase_order_id, created_at);
CREATE INDEX idx_sales_observation_lot ON sales_observation(organization_id, inventory_lot_id, observed_at DESC);
CREATE INDEX idx_reorder_decision_product ON reorder_decision(organization_id, product_id, decided_at DESC);
