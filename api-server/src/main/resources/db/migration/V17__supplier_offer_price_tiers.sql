ALTER TABLE supplier_offer_draft_line ADD COLUMN available_quantity NUMERIC(19,6)
    CHECK (available_quantity IS NULL OR available_quantity >= 0);

CREATE TABLE supplier_offer_price_tier (
    id BIGSERIAL PRIMARY KEY,
    supplier_offer_draft_line_id BIGINT NOT NULL REFERENCES supplier_offer_draft_line(id) ON DELETE CASCADE,
    minimum_quantity NUMERIC(19,6) NOT NULL CHECK (minimum_quantity > 0),
    unit_price NUMERIC(19,4) NOT NULL CHECK (unit_price >= 0),
    CONSTRAINT uq_supplier_offer_price_tier UNIQUE (supplier_offer_draft_line_id, minimum_quantity)
);
