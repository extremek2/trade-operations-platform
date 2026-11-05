CREATE TABLE shipment_allocation (
    id                    BIGSERIAL PRIMARY KEY,
    public_id             UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    organization_id       BIGINT NOT NULL REFERENCES organization(id) ON DELETE RESTRICT,
    shipment_case_id      BIGINT NOT NULL REFERENCES shipment_case(id) ON DELETE RESTRICT,
    purchase_order_line_id BIGINT NOT NULL REFERENCES purchase_order_line(id) ON DELETE RESTRICT,
    allocated_quantity    NUMERIC(19,6) NOT NULL CHECK (allocated_quantity > 0),
    created_by            BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    created_at            TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (shipment_case_id, purchase_order_line_id)
);

CREATE TABLE receipt (
    id                    BIGSERIAL PRIMARY KEY,
    public_id             UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    organization_id       BIGINT NOT NULL REFERENCES organization(id) ON DELETE RESTRICT,
    shipment_case_id      BIGINT NOT NULL REFERENCES shipment_case(id) ON DELETE RESTRICT,
    receipt_number        VARCHAR(100) NOT NULL,
    received_at           TIMESTAMP NOT NULL,
    notes                 TEXT,
    created_by            BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    created_at            TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (organization_id, receipt_number)
);

CREATE TABLE inventory_lot (
    id                    BIGSERIAL PRIMARY KEY,
    public_id             UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    organization_id       BIGINT NOT NULL REFERENCES organization(id) ON DELETE RESTRICT,
    product_id            BIGINT NOT NULL REFERENCES product(id) ON DELETE RESTRICT,
    purchase_order_line_id BIGINT NOT NULL REFERENCES purchase_order_line(id) ON DELETE RESTRICT,
    receipt_id            BIGINT NOT NULL REFERENCES receipt(id) ON DELETE RESTRICT,
    lot_number            VARCHAR(100) NOT NULL,
    received_quantity     NUMERIC(19,6) NOT NULL CHECK (received_quantity > 0),
    sellable_quantity     NUMERIC(19,6) NOT NULL CHECK (sellable_quantity >= 0 AND sellable_quantity <= received_quantity),
    version               BIGINT NOT NULL DEFAULT 0,
    created_at            TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (organization_id, lot_number)
);

CREATE TABLE receipt_line (
    id                    BIGSERIAL PRIMARY KEY,
    receipt_id            BIGINT NOT NULL REFERENCES receipt(id) ON DELETE RESTRICT,
    shipment_allocation_id BIGINT NOT NULL REFERENCES shipment_allocation(id) ON DELETE RESTRICT,
    inventory_lot_id      BIGINT NOT NULL UNIQUE REFERENCES inventory_lot(id) ON DELETE RESTRICT,
    received_quantity     NUMERIC(19,6) NOT NULL CHECK (received_quantity > 0)
);

CREATE INDEX idx_shipment_allocation_shipment ON shipment_allocation(organization_id, shipment_case_id);
CREATE INDEX idx_shipment_allocation_po_line ON shipment_allocation(purchase_order_line_id);
CREATE INDEX idx_receipt_shipment ON receipt(organization_id, shipment_case_id, received_at DESC);
CREATE INDEX idx_inventory_lot_product ON inventory_lot(organization_id, product_id, created_at DESC);
CREATE INDEX idx_receipt_line_allocation ON receipt_line(shipment_allocation_id);
