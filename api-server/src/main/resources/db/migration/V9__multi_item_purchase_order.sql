ALTER TABLE purchase_order_line ADD COLUMN cost_scenario_id BIGINT;

UPDATE purchase_order_line line
SET cost_scenario_id = purchase_order.cost_scenario_id
FROM purchase_order
WHERE line.purchase_order_id = purchase_order.id;

ALTER TABLE purchase_order_line ALTER COLUMN cost_scenario_id SET NOT NULL;
ALTER TABLE purchase_order_line
    ADD CONSTRAINT fk_purchase_order_line_cost_scenario
        FOREIGN KEY (cost_scenario_id) REFERENCES cost_scenario(id) ON DELETE RESTRICT;
ALTER TABLE purchase_order_line
    ADD CONSTRAINT uq_purchase_order_line_cost_scenario UNIQUE (cost_scenario_id);

CREATE INDEX idx_purchase_order_line_scenario ON purchase_order_line(cost_scenario_id);

ALTER TABLE purchase_order DROP COLUMN cost_scenario_id;
