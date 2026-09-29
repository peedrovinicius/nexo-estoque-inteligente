ALTER TABLE stock_movements
  ADD COLUMN performed_by VARCHAR(120) NULL AFTER idempotency_key;

ALTER TABLE decision_audit
  ADD COLUMN actor_username VARCHAR(120) NULL AFTER rule_version;

CREATE TABLE product_change_history (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  product_id BIGINT NOT NULL,
  action_type VARCHAR(30) NOT NULL,
  actor_username VARCHAR(120) NOT NULL,
  before_snapshot JSON NULL,
  after_snapshot JSON NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_product_history_product
    FOREIGN KEY (product_id) REFERENCES products(id)
);

CREATE INDEX idx_products_active_name
  ON products(active, name, id);

CREATE INDEX idx_products_category_active
  ON products(category, active, id);

CREATE INDEX idx_stock_batches_fefo
  ON stock_batches(product_id, quantity, expires_at, received_at, id);

CREATE INDEX idx_stock_movements_product_created
  ON stock_movements(product_id, created_at, id);

CREATE INDEX idx_stock_movements_created
  ON stock_movements(created_at, id);

CREATE INDEX idx_blind_inventory_status_started
  ON blind_inventory_sessions(status, started_at, id);

CREATE INDEX idx_decision_audit_type_created
  ON decision_audit(decision_type, created_at, id);

CREATE INDEX idx_decision_audit_product_created
  ON decision_audit(product_id, created_at, id);

CREATE INDEX idx_product_history_product_created
  ON product_change_history(product_id, created_at, id);
