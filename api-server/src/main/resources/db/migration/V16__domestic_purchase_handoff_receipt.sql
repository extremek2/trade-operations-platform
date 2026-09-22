ALTER TABLE purchase_order ADD COLUMN procurement_route TEXT NOT NULL DEFAULT 'IMPORT'
    CHECK (procurement_route IN ('IMPORT', 'DOMESTIC_STOCK'));
ALTER TABLE purchase_order ADD COLUMN external_order_status TEXT NOT NULL DEFAULT 'NOT_PLACED'
    CHECK (external_order_status IN ('NOT_PLACED', 'PLACED'));
ALTER TABLE purchase_order ADD COLUMN external_order_reference VARCHAR(200);
ALTER TABLE purchase_order ADD COLUMN external_order_total NUMERIC(19,4)
    CHECK (external_order_total IS NULL OR external_order_total >= 0);
ALTER TABLE purchase_order ADD COLUMN external_ordered_at DATE;
ALTER TABLE purchase_order DROP CONSTRAINT purchase_order_status_check;
ALTER TABLE purchase_order ADD CONSTRAINT purchase_order_status_check
    CHECK (status IN ('DRAFT', 'APPROVED', 'PARTIALLY_SHIPPED', 'SHIPPED', 'PARTIALLY_RECEIVED', 'COMPLETED', 'CANCELLED'));

ALTER TABLE receipt ALTER COLUMN shipment_case_id DROP NOT NULL;
ALTER TABLE receipt ADD COLUMN purchase_order_id BIGINT REFERENCES purchase_order(id) ON DELETE RESTRICT;
ALTER TABLE receipt ADD CONSTRAINT receipt_source_check
    CHECK ((shipment_case_id IS NOT NULL) <> (purchase_order_id IS NOT NULL));
CREATE INDEX idx_receipt_domestic_order ON receipt(organization_id, purchase_order_id, received_at DESC)
    WHERE purchase_order_id IS NOT NULL;

ALTER TABLE receipt_line ALTER COLUMN shipment_allocation_id DROP NOT NULL;
ALTER TABLE receipt_line ADD COLUMN purchase_order_line_id BIGINT REFERENCES purchase_order_line(id) ON DELETE RESTRICT;
ALTER TABLE receipt_line ADD CONSTRAINT receipt_line_source_check
    CHECK ((shipment_allocation_id IS NOT NULL) <> (purchase_order_line_id IS NOT NULL));
CREATE INDEX idx_receipt_line_domestic_order_line ON receipt_line(purchase_order_line_id)
    WHERE purchase_order_line_id IS NOT NULL;
