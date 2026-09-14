CREATE TABLE purchase_order (
    id                       BIGSERIAL PRIMARY KEY,
    public_id                UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    organization_id          BIGINT NOT NULL REFERENCES organization(id) ON DELETE RESTRICT,
    supplier_id              BIGINT NOT NULL REFERENCES business_partner(id) ON DELETE RESTRICT,
    cost_scenario_id         BIGINT NOT NULL REFERENCES cost_scenario(id) ON DELETE RESTRICT,
    order_number             VARCHAR(100) NOT NULL,
    status                   TEXT NOT NULL DEFAULT 'DRAFT' CHECK (status IN (
        'DRAFT', 'APPROVED', 'PARTIALLY_SHIPPED', 'SHIPPED', 'COMPLETED', 'CANCELLED'
    )),
    payment_status           TEXT NOT NULL DEFAULT 'UNPAID' CHECK (payment_status IN ('UNPAID', 'PARTIALLY_PAID', 'PAID')),
    paid_amount              NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (paid_amount >= 0),
    paid_at                  DATE,
    payment_evidence         TEXT,
    currency                 VARCHAR(3) NOT NULL CHECK (currency = upper(currency)),
    supplier_name_snapshot   TEXT NOT NULL,
    quote_number_snapshot    TEXT,
    quote_revision_snapshot  INTEGER NOT NULL CHECK (quote_revision_snapshot > 0),
    expected_total_cost_krw  NUMERIC(19,2) NOT NULL CHECK (expected_total_cost_krw >= 0),
    ordered_at               DATE NOT NULL,
    approved_at              TIMESTAMP,
    cancelled_at             TIMESTAMP,
    notes                    TEXT,
    version                  BIGINT NOT NULL DEFAULT 0,
    created_by               BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    created_at               TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (organization_id, order_number),
    CHECK ((status IN ('DRAFT', 'CANCELLED')) OR approved_at IS NOT NULL),
    CHECK ((status = 'CANCELLED') = (cancelled_at IS NOT NULL)),
    CHECK ((payment_status = 'UNPAID' AND paid_amount = 0) OR payment_status <> 'UNPAID')
);

CREATE TABLE purchase_order_line (
    id                  BIGSERIAL PRIMARY KEY,
    public_id           UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    purchase_order_id   BIGINT NOT NULL REFERENCES purchase_order(id) ON DELETE RESTRICT,
    product_id          BIGINT NOT NULL REFERENCES product(id) ON DELETE RESTRICT,
    line_number         INTEGER NOT NULL CHECK (line_number > 0),
    product_name_snapshot TEXT NOT NULL,
    internal_sku_snapshot TEXT,
    ordered_quantity    NUMERIC(19,6) NOT NULL CHECK (ordered_quantity > 0),
    quantity_unit       TEXT NOT NULL,
    unit_price          NUMERIC(19,4) NOT NULL CHECK (unit_price >= 0),
    UNIQUE (purchase_order_id, line_number)
);

CREATE INDEX idx_purchase_order_board ON purchase_order(organization_id, created_at DESC);
CREATE INDEX idx_purchase_order_line_product ON purchase_order_line(product_id);
