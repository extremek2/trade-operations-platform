ALTER TABLE purchase_order ADD COLUMN catalog_recheck_status TEXT NOT NULL DEFAULT 'NOT_REQUIRED'
    CHECK (catalog_recheck_status IN ('NOT_REQUIRED', 'REQUIRED', 'PASSED', 'CHANGED', 'FAILED'));
ALTER TABLE purchase_order ADD COLUMN catalog_rechecked_at TIMESTAMP;
ALTER TABLE purchase_order ADD COLUMN catalog_recheck_message VARCHAR(2000);

ALTER TABLE supplier_offer_draft_line ADD COLUMN source_item_id VARCHAR(100);

ALTER TABLE purchase_order_line ADD COLUMN source_platform VARCHAR(50);
ALTER TABLE purchase_order_line ADD COLUMN source_item_id VARCHAR(100);
ALTER TABLE purchase_order_line ADD COLUMN source_reference TEXT;
ALTER TABLE purchase_order_line ADD COLUMN source_fetched_at TIMESTAMP;
ALTER TABLE purchase_order_line ADD CONSTRAINT purchase_order_line_catalog_source_check CHECK (
    (source_platform IS NULL AND source_item_id IS NULL AND source_reference IS NULL AND source_fetched_at IS NULL) OR
    (source_platform IS NOT NULL AND source_item_id IS NOT NULL AND source_reference IS NOT NULL AND source_fetched_at IS NOT NULL)
);
