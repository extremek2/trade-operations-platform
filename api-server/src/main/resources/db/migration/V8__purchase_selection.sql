CREATE TABLE purchase_selection (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    organization_id BIGINT NOT NULL REFERENCES organization(id) ON DELETE RESTRICT,
    supplier_offer_draft_id BIGINT NOT NULL REFERENCES supplier_offer_draft(id) ON DELETE RESTRICT,
    supplier_quote_id BIGINT NOT NULL UNIQUE REFERENCES supplier_quote(id) ON DELETE RESTRICT,
    version BIGINT NOT NULL DEFAULT 0,
    selected_by BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    selected_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_purchase_selection_board ON purchase_selection(organization_id, selected_at DESC);

CREATE TABLE purchase_selection_line (
    id BIGSERIAL PRIMARY KEY,
    purchase_selection_id BIGINT NOT NULL REFERENCES purchase_selection(id) ON DELETE RESTRICT,
    supplier_offer_draft_line_id BIGINT NOT NULL UNIQUE REFERENCES supplier_offer_draft_line(id) ON DELETE RESTRICT,
    supplier_quote_line_id BIGINT NOT NULL UNIQUE REFERENCES supplier_quote_line(id) ON DELETE RESTRICT,
    product_id BIGINT NOT NULL REFERENCES product(id) ON DELETE RESTRICT,
    line_number INTEGER NOT NULL CHECK (line_number > 0),
    desired_quantity NUMERIC(19,6) NOT NULL CHECK (desired_quantity > 0),
    UNIQUE (purchase_selection_id, line_number)
);
